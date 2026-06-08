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
 * 책BTI 캐시·갱신 통합 테스트(Phase 4) — analyzeCached의 캐시/재생성/콜드스타트/폴백 경로를 본다.
 *
 * <p>핵심 불변식: (1) 처음엔 LLM 호출 + 캐시 저장, (2) 책장 안 변하면 LLM 재호출 없이 캐시 사용(비용 절감),
 * (3) 시그니처 변동 or "다시 분석"(force)이면 재생성, (4) 책 부족(콜드스타트)이면 LLM·캐시 안 함,
 * (5) LLM 실패 시 — <b>직전 캐시(stale)가 있으면 그걸 내보내고(빈 화면 방지, serve-stale-on-error, N-060)</b>,
 *     없으면 사실만 폴백한다. 어느 경우든 기존 캐시는 망가뜨리지 않는다.
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

    /** 콜드스타트 임계(5권)를 넘기려고 n권을 적재한다(공개+완독 — 성향은 공개 책 기반). */
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

    @Test
    @DisplayName("첫 분석: LLM을 호출하고 결과를 캐시에 저장한다")
    void firstAnalysis_callsLlmAndCaches() {
        User u = newUser("first@booktimer.com");
        saveBooks(u, 5);
        narratorReturns("이 사람은 완독러다.");

        ReadingPersonality result = service.analyzeCached(u, false);

        assertThat(result.hasNarration()).isTrue();
        assertThat(result.narration().narrative()).isEqualTo("이 사람은 완독러다.");
        verify(narrator, times(1)).narrate(any());
        Optional<ReadingPersonalityCache> cached = cacheRepository.findByUser(u);
        assertThat(cached).isPresent();
        assertThat(cached.get().getInputSignature()).isNotBlank();
        assertThat(cached.get().getNarrative()).isEqualTo("이 사람은 완독러다.");
    }

    @Test
    @DisplayName("캐시 히트: 책장이 그대로면 LLM을 다시 부르지 않고 캐시를 쓴다")
    void cacheHit_doesNotCallLlmAgain() {
        User u = newUser("hit@booktimer.com");
        saveBooks(u, 5);
        narratorReturns("정독형 독자.");

        service.analyzeCached(u, false);              // 첫 호출 — LLM
        ReadingPersonality second = service.analyzeCached(u, false); // 둘째 — 캐시

        verify(narrator, times(1)).narrate(any()); // 총 1번만
        assertThat(second.hasNarration()).isTrue();
        assertThat(second.narration().narrative()).isEqualTo("정독형 독자.");
        assertThat(second.narration().tags()).containsExactly("태그");
    }

    @Test
    @DisplayName("시그니처 변동: 책이 늘면(책장 의미 변화) 재생성한다")
    void signatureChange_regenerates() {
        User u = newUser("sig@booktimer.com");
        saveBooks(u, 5);
        narratorReturns("초기 서술.");

        service.analyzeCached(u, false); // 첫 생성
        saveBooks(u, 1);                 // 책장 변화(6권)
        service.analyzeCached(u, false); // 재생성

        verify(narrator, times(2)).narrate(any());
    }

    @Test
    @DisplayName("다시 분석(force): 시그니처가 같아도 강제로 재생성한다")
    void force_regeneratesEvenIfSignatureSame() {
        User u = newUser("force@booktimer.com");
        saveBooks(u, 5);
        narratorReturns("서술.");

        service.analyzeCached(u, false); // 첫 생성
        service.analyzeCached(u, true);  // 강제 재생성(책장 동일)

        verify(narrator, times(2)).narrate(any());
    }

    @Test
    @DisplayName("콜드스타트: 책이 임계 미만이면 LLM·캐시 없이 사실만 보류한다")
    void coldStart_belowThreshold_noLlmNoCache() {
        User u = newUser("cold@booktimer.com");
        saveBooks(u, 4); // 임계(5) 미만

        ReadingPersonality result = service.analyzeCached(u, false);

        assertThat(result.hasNarration()).isFalse();
        assertThat(result.profile().totalBooks()).isEqualTo(4); // 사실은 있다
        verify(narrator, never()).narrate(any());
        assertThat(cacheRepository.findByUser(u)).isEmpty();
    }

    @Test
    @DisplayName("LLM 실패(직전 캐시 없음): 사실만 폴백하고 캐시는 만들지 않는다")
    void llmFails_noPriorCache_fallbackNoCache() {
        User u = newUser("fail@booktimer.com");
        saveBooks(u, 5);
        when(narrator.narrate(any())).thenReturn(Optional.empty());

        ReadingPersonality result = service.analyzeCached(u, false);

        assertThat(result.hasNarration()).isFalse();
        assertThat(result.profile().totalBooks()).isEqualTo(5);
        assertThat(cacheRepository.findByUser(u)).isEmpty();
    }

    @Test
    @DisplayName("LLM 실패(직전 캐시 있음, 시그니처 변동): stale 캐시 서술을 내보내고 빈 화면을 막는다(N-060)")
    void llmFails_withExistingCache_servesStale() {
        User u = newUser("stale@booktimer.com");
        saveBooks(u, 5);
        narratorReturns("예전에 받은 분석.");
        service.analyzeCached(u, false); // 첫 성공 — "예전에 받은 분석." 캐시됨

        saveBooks(u, 1); // 책장 변화로 시그니처 어긋남 → 재생성 트리거
        when(narrator.narrate(any())).thenReturn(Optional.empty()); // 그런데 LLM 실패/빈응답

        ReadingPersonality result = service.analyzeCached(u, false);

        // 빈 화면(factsOnly) 대신 직전 캐시(stale) 서술이 나와야 한다
        assertThat(result.hasNarration()).isTrue();
        assertThat(result.narration().narrative()).isEqualTo("예전에 받은 분석.");
        // 기존 캐시는 옛 서술 그대로 보존(실패가 캐시를 덮어쓰지 않음)
        assertThat(cacheRepository.findByUser(u)).get()
                .extracting(ReadingPersonalityCache::getNarrative).isEqualTo("예전에 받은 분석.");
    }

    @Test
    @DisplayName("다시 분석(force)했는데 LLM 실패: 직전 캐시가 있으면 stale을 내보낸다(빈 화면 금지)")
    void force_llmFails_withExistingCache_servesStale() {
        User u = newUser("forcestale@booktimer.com");
        saveBooks(u, 5);
        narratorReturns("원래 분석.");
        service.analyzeCached(u, false); // 첫 성공

        when(narrator.narrate(any())).thenReturn(Optional.empty()); // 이후 LLM 실패
        ReadingPersonality result = service.analyzeCached(u, true);  // 강제 재생성 시도

        assertThat(result.hasNarration()).isTrue();
        assertThat(result.narration().narrative()).isEqualTo("원래 분석.");
    }
}
