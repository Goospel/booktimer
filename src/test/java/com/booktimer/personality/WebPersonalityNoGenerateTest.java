package com.booktimer.personality;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * 웹 책BTI는 <b>읽기만 한다</b> — 생성은 광고 관문을 지난 미니앱에만 남긴다(2026-09-08).
 *
 * <p><b>왜</b>: 미니앱은 리워드 광고를 봐야 성향 분석을 돌릴 수 있어 호출마다 수익이 붙는다. 웹은 그
 * 관문이 없어 <b>비용만 나가고 회수가 없다</b>. 광고를 웹에 새로 붙이는 것은 심사 이력상 번거로워
 * 생성 자체를 웹에서 걷는다(사용자 결정).
 *
 * <p><b>미니앱은 이 함정을 이미 알고 피해 놨다</b> — {@code miniapp/src/api.ts}가 {@code GET
 * /api/personality} 대신 부작용 없는 {@code /status}를 쓰는 이유가 「히스토리가 비면 첫 분석을 공짜로
 * 만들어 관문을 무력화한다」이다. 웹만 그 문이 열려 있었다.
 *
 * <p>재는 것은 <b>쌍</b>이다: ① 없으면 안 만든다 ② <b>있으면 그대로 보여준다</b>. ①만 재면
 * 「기능을 통째로 죽였다」도 초록이고, 그건 사용자가 이미 돈 주고 만든 서술을 버리는 것이다.
 */
@SpringBootTest
@Transactional
class WebPersonalityNoGenerateTest {

    @Autowired ReadingPersonalityService personalityService;
    @Autowired UserRegistrationService registrationService;
    @Autowired UserRepository userRepository;
    @Autowired BookRepository bookRepository;

    /** narrator를 목으로 둬야 「부르지 않았다」를 잴 수 있다 — 진짜는 키가 없어 조용히 빈 결과를 준다. */
    @MockitoBean ReadingPersonalityNarrator narrator;

    private User userWithFinishedBook(String loginId) {
        registrationService.register(loginId + "@booktimer.com", "pw1234qwer!!", loginId,
                "닉네임_" + loginId, "Asia/Seoul", Role.USER, LocalDate.of(2026, 1, 1));
        User user = userRepository.findByLoginId(loginId).orElseThrow();
        // 성향은 **공개 + 완독** 책으로만 집계된다 — 둘 중 하나만 빠져도 콜드스타트로 빠져
        // 「생성을 막아서 서술이 없다」와 「책이 모자라 서술이 없다」가 구분되지 않는다.
        Book book = Book.register(user, "물고기는 존재하지 않는다", "룰루 밀러", null, null, null, null,
                null, null, BookStatus.FINISHED);
        book.makePublic();
        bookRepository.save(book);
        return user;
    }

    // ── ① 없으면 만들지 않는다 (이 PR의 본체) ──

    @Test
    @DisplayName("웹 진입: 분석이 없어도 LLM을 부르지 않는다 — 사실만 돌려준다")
    void currentPersonality_doesNotGenerate() {
        User user = userWithFinishedBook("nogen1");

        ReadingPersonality result = personalityService.currentPersonality(user);

        then(narrator).should(never()).narrate(any());
        assertThat(result.hasNarration())
                .as("생성이 막혔으므로 서술이 없어야 한다(사실만 폴백)")
                .isFalse();
    }

    @Test
    @DisplayName("여러 번 열어도 마찬가지 — 「처음 한 번만 공짜」 같은 예외를 두지 않는다")
    void currentPersonality_staysReadOnlyAcrossCalls() {
        User user = userWithFinishedBook("nogen2");

        personalityService.currentPersonality(user);
        personalityService.currentPersonality(user);
        personalityService.currentPersonality(user);

        then(narrator).should(never()).narrate(any());
    }

    // ── ② 있으면 그대로 보여준다 (양성 대조군 — 이게 없으면 「기능 삭제」도 초록이다) ──

    @Test
    @DisplayName("이미 만들어 둔 서술은 웹에서도 그대로 보인다 — 돈 주고 만든 것을 버리지 않는다")
    void currentPersonality_stillShowsCachedNarration() {
        User user = userWithFinishedBook("nogen3");
        // 광고 경로(미니앱)로 한 번 만들어 둔 상태를 재현한다 — 그 경로는 이 PR이 안 건드린다.
        given(narrator.narrate(any())).willReturn(Optional.of(
                new PersonalityNarration("당신은 경계를 의심하는 독자입니다.", List.of("의심", "경계"))));
        personalityService.reanalyze(user);

        ReadingPersonality result = personalityService.currentPersonality(user);

        assertThat(result.hasNarration()).isTrue();
        assertThat(result.narration().narrative()).contains("경계를 의심하는");
    }

    // ── ③ 미니앱 경로는 그대로 (회귀 게이트) ──

    @Test
    @DisplayName("「다시 분석」(광고 경로)은 여전히 생성한다 — 미니앱은 무변경이다")
    void reanalyze_stillGenerates() {
        User user = userWithFinishedBook("nogen4");
        given(narrator.narrate(any())).willReturn(Optional.of(
                new PersonalityNarration("서술", List.of("태그"))));

        ReadingPersonality result = personalityService.reanalyze(user);

        then(narrator).should().narrate(any());
        assertThat(result.hasNarration()).isTrue();
    }
}
