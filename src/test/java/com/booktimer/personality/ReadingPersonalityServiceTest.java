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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 책BTI 오케스트레이션 서비스 통합 테스트(Phase 3) — 사실 집계(Phase 2) + LLM 서술 결합과 <b>폴백</b>을 본다.
 *
 * <p>서술 생성기(LLM 포트)는 {@code @MockitoBean}으로 가짜를 끼운다 — 외부 키·네트워크 없이 두 경로를 검증한다:
 * 서술이 나오면 사실+서술을, 못 나오면(비활성/실패) <b>사실만</b> 담은 폴백을 돌려줘야 한다.
 *
 * <p>책BTI는 <b>공개(PUBLIC)+완독 책만</b>으로 뽑히므로(공개/비공개 분기 폐지 2026-06-08) 픽스처 책은 공개로 만든다.
 */
@SpringBootTest
@Transactional
class ReadingPersonalityServiceTest {

    @Autowired
    private ReadingPersonalityService service;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BookRepository bookRepository;
    @Autowired
    private ReadingPersonalityCacheRepository cacheRepository;

    @MockitoBean
    private ReadingPersonalityNarrator narrator;

    private User newUser(String email) {
        return userRepository.save(User.of(email, "$2a$10$abcdefghijklmnopqrstuv", "독자", "Asia/Seoul", Role.USER));
    }

    /** 공개+완독 책 1권 적재(성향 입력 = 공개 책). */
    private void savePublicFinished(User u, String title) {
        Book b = Book.register(u, title, null, null, null, null, null, null, null, BookStatus.FINISHED);
        b.makePublic();
        bookRepository.save(b);
    }

    @Test
    @DisplayName("분석: 서술이 나오면 사실(프로필) + 서술을 함께 담는다")
    void analyze_withNarration_combinesFactsAndNarration() {
        User u = newUser("narr@booktimer.com");
        savePublicFinished(u, "책A");
        when(narrator.narrate(any())).thenReturn(
                Optional.of(new PersonalityNarration("이 사람은 완독러다.", List.of("완독러"))));

        ReadingPersonality result = service.analyze(u);

        assertThat(result.profile().totalBooks()).isEqualTo(1); // 사실 집계됨
        assertThat(result.hasNarration()).isTrue();
        assertThat(result.narration().narrative()).isEqualTo("이 사람은 완독러다.");
        assertThat(result.narration().tags()).containsExactly("완독러");
    }

    @Test
    @DisplayName("폴백: 서술 생성이 비면(비활성/실패) 사실만 담고 서술은 없다")
    void analyze_narratorEmpty_fallsBackToFactsOnly() {
        User u = newUser("fb@booktimer.com");
        savePublicFinished(u, "책A");
        when(narrator.narrate(any())).thenReturn(Optional.empty());

        ReadingPersonality result = service.analyze(u);

        assertThat(result.profile().totalBooks()).isEqualTo(1); // 사실은 항상 존재
        assertThat(result.hasNarration()).isFalse();            // 폴백
        assertThat(result.narration()).isNull();
    }

    // --- gate(): 미니앱 광고 관문 사전 판정 (설계 §3.4 — 광고를 보여주기 *전에* 알아야 하는 둘) ---

    @Test
    @DisplayName("gate: 공개 완독 0권이면 coldStart=true — 보상 없는 광고 시청을 원천 차단하는 값")
    void gate_noFinishedBooks_coldStart() {
        User u = newUser("gate-cold@booktimer.com");

        assertThat(service.gate(u).coldStart()).isTrue();
    }

    @Test
    @DisplayName("gate: 공개 완독 1권 + 대표 없음 → (coldStart=false, hasSelected=false)")
    void gate_enoughBooksNoSelection() {
        User u = newUser("gate-ready@booktimer.com");
        savePublicFinished(u, "책A");

        ReadingPersonalityService.AnalysisGate gate = service.gate(u);

        assertThat(gate.coldStart()).isFalse();
        assertThat(gate.hasSelected()).isFalse();
    }

    @Test
    @DisplayName("gate: 대표 분석이 있으면 hasSelected=true — 버튼 문구가 「다시 분석」으로 갈린다")
    void gate_withSelectedEntry() {
        User u = newUser("gate-sel@booktimer.com");
        savePublicFinished(u, "책A");
        when(narrator.narrate(any())).thenReturn(
                Optional.of(new PersonalityNarration("서술.", List.of("태그"))));
        service.reanalyze(u); // 첫 분석은 자동 대표

        assertThat(service.gate(u).hasSelected()).isTrue();
    }

    @Test
    @DisplayName("gate는 부작용이 없다 — LLM을 부르지 않고 히스토리 행도 안 만든다 (이 메서드의 존재 이유)")
    void gate_hasNoSideEffects() {
        User u = newUser("gate-pure@booktimer.com");
        savePublicFinished(u, "책A"); // 부트스트랩 조건(책 충분 + 히스토리 빔)을 일부러 만든다
        long before = cacheRepository.count();

        service.gate(u);

        assertThat(cacheRepository.count()).isEqualTo(before); // 행이 늘지 않았다
        verifyNoInteractions(narrator);                        // LLM 호출 0
    }
}
