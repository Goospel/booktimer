package com.booktimer.personality;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 책BTI GET 진입(currentPersonality) 경로 통합 테스트(Phase 4) — 대표 읽기/부트스트랩/콜드스타트/폴백을 본다.
 *
 * <p>핵심 불변식: (1) 히스토리가 비었고 책이 충분하면 첫 1개를 부트스트랩 생성해 대표로 둔다, (2) 대표가 있으면
 * LLM 재호출 없이 그걸 읽는다(비용 절감), (3) <b>책장이 바뀌어도 GET은 재생성하지 않는다</b>(생성은 "다시 분석"에서만),
 * (4) 책 부족(콜드스타트)이면 LLM·저장 안 함, (5) 부트스트랩 LLM 실패면 사실만 폴백하고 행을 만들지 않는다.
 *
 * <p>"다시 분석"(reanalyze)·히스토리 교체·대표 선택·IDOR는 {@code ReadingPersonalityHistoryTest}가 본다.
 */
@SpringBootTest
@Transactional
class ReadingPersonalityServiceCacheTest {

    @Autowired
    private ReadingPersonalityService service;
    @Autowired
    private ReadingPersonalityCacheRepository cacheRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BookRepository bookRepository;

    @MockitoBean
    private ReadingPersonalityNarrator narrator;

    private User newUser(String email) {
        return userRepository.save(User.of(email, "$2a$10$abcdefghijklmnopqrstuv", "독자", "Asia/Seoul", Role.USER));
    }

    /** 콜드스타트 임계를 넘기려고 n권을 적재한다(공개+완독 — 성향은 공개 책 기반). */
    private void saveBooks(User u, int n) {
        for (int i = 0; i < n; i++) {
            Book b = Book.register(u, "책" + i, "저자" + i, null, null, null, null,
                    null, null, BookStatus.FINISHED);
            b.makePublic();
            bookRepository.save(b);
        }
    }

    private void narratorReturns(String narrative) {
        when(narrator.narrate(any())).thenReturn(Optional.of(new PersonalityNarration(narrative, List.of("태그"))));
    }

    private List<ReadingPersonalityCache> rows(User u) {
        return cacheRepository.findByUserOrderByGeneratedAtDescIdDesc(u);
    }

    @Test
    @DisplayName("첫 진입: 히스토리가 비었고 책이 충분하면 LLM을 호출해 1행을 대표로 저장한다")
    void firstVisit_bootstrapsSelectedRow() {
        User u = newUser("first@booktimer.com");
        saveBooks(u, 5);
        narratorReturns("이 사람은 완독러다.");

        ReadingPersonality result = service.currentPersonality(u);

        assertThat(result.hasNarration()).isTrue();
        assertThat(result.narration().narrative()).isEqualTo("이 사람은 완독러다.");
        verify(narrator, times(1)).narrate(any());
        assertThat(rows(u)).hasSize(1);
        assertThat(cacheRepository.findByUserAndSelectedTrue(u)).get()
                .extracting(ReadingPersonalityCache::getNarrative).isEqualTo("이 사람은 완독러다.");
        assertThat(rows(u).get(0).getInputSignature()).isNotBlank();
    }

    @Test
    @DisplayName("재진입: 대표가 있으면 LLM을 다시 부르지 않고 대표를 읽는다")
    void revisit_readsSelected_noLlmAgain() {
        User u = newUser("hit@booktimer.com");
        saveBooks(u, 5);
        narratorReturns("정독형 독자.");

        service.currentPersonality(u);                       // 첫 진입 — 부트스트랩
        ReadingPersonality second = service.currentPersonality(u); // 둘째 — 대표 읽기

        verify(narrator, times(1)).narrate(any()); // 총 1번만
        assertThat(second.hasNarration()).isTrue();
        assertThat(second.narration().narrative()).isEqualTo("정독형 독자.");
        assertThat(second.narration().tags()).containsExactly("태그");
    }

    @Test
    @DisplayName("책장이 늘어도 GET은 재생성하지 않는다(생성은 '다시 분석'에서만) — 대표를 그대로 읽는다")
    void signatureChange_doesNotRegenerateOnGet() {
        User u = newUser("sig@booktimer.com");
        saveBooks(u, 5);
        narratorReturns("초기 서술.");

        service.currentPersonality(u); // 첫 생성
        saveBooks(u, 1);               // 책장 변화(6권)
        service.currentPersonality(u); // 그래도 재생성 안 함

        verify(narrator, times(1)).narrate(any());
        assertThat(rows(u)).hasSize(1);
    }

    @Test
    @DisplayName("완독 1권이면 (정확도 낮아도) 책BTI를 부트스트랩 생성한다 — 임계=1")
    void oneBook_generatesPersonality_notColdStart() {
        User u = newUser("one@booktimer.com");
        saveBooks(u, 1); // 완독 단 1권
        narratorReturns("한 권으로 본 잠정 성향.");

        ReadingPersonality result = service.currentPersonality(u);

        assertThat(result.hasNarration()).isTrue();
        assertThat(result.narration().narrative()).isEqualTo("한 권으로 본 잠정 성향.");
        verify(narrator, times(1)).narrate(any());
        assertThat(cacheRepository.findByUserAndSelectedTrue(u)).isPresent();
    }

    @Test
    @DisplayName("콜드스타트: 완독 책이 0권이면 LLM·저장 없이 사실만 보류한다(임계=1 미만)")
    void coldStart_belowThreshold_noLlmNoRow() {
        User u = newUser("cold@booktimer.com");
        saveBooks(u, 0); // 완독 0권 — 임계(1) 미만

        ReadingPersonality result = service.currentPersonality(u);

        assertThat(result.hasNarration()).isFalse();
        assertThat(result.profile().finishedBooks()).isEqualTo(0); // 보류지만 사실(완독 0)은 있다
        verify(narrator, never()).narrate(any());
        assertThat(rows(u)).isEmpty();
    }

    @Test
    @DisplayName("부트스트랩 LLM 실패: 사실만 폴백하고 행을 만들지 않는다")
    void bootstrapLlmFails_fallbackNoRow() {
        User u = newUser("fail@booktimer.com");
        saveBooks(u, 5);
        when(narrator.narrate(any())).thenReturn(Optional.empty());

        ReadingPersonality result = service.currentPersonality(u);

        assertThat(result.hasNarration()).isFalse();
        assertThat(result.profile().totalBooks()).isEqualTo(5);
        assertThat(rows(u)).isEmpty();
    }
}
