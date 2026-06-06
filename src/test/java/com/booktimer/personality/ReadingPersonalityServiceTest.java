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
import static org.mockito.Mockito.when;

/**
 * 책BTI 오케스트레이션 서비스 통합 테스트(Phase 3) — 사실 집계(Phase 2) + LLM 서술 결합과 <b>폴백</b>을 본다.
 *
 * <p>서술 생성기(LLM 포트)는 {@code @MockitoBean}으로 가짜를 끼운다 — 외부 키·네트워크 없이 두 경로를 검증한다:
 * 서술이 나오면 사실+서술을, 못 나오면(비활성/실패) <b>사실만</b> 담은 폴백을 돌려줘야 한다.
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

    @MockitoBean
    private ReadingPersonalityNarrator narrator;

    private User newUser(String email) {
        return userRepository.save(User.of(email, "$2a$10$abcdefghijklmnopqrstuv", "독자", "Asia/Seoul", Role.USER));
    }

    @Test
    @DisplayName("분석: 서술이 나오면 사실(프로필) + 서술을 함께 담는다")
    void analyze_withNarration_combinesFactsAndNarration() {
        User u = newUser("narr@booktimer.com");
        bookRepository.save(Book.register(u, "책A", null, null, null, null, null, null, null, BookStatus.FINISHED));
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
        bookRepository.save(Book.register(u, "책A", null, null, null, null, null, null, null, BookStatus.FINISHED));
        when(narrator.narrate(any())).thenReturn(Optional.empty());

        ReadingPersonality result = service.analyze(u);

        assertThat(result.profile().totalBooks()).isEqualTo(1); // 사실은 항상 존재
        assertThat(result.hasNarration()).isFalse();            // 폴백
        assertThat(result.narration()).isNull();
    }
}
