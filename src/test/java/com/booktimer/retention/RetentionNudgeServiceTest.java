package com.booktimer.retention;

import com.booktimer.email.EmailSender;
import com.booktimer.email.EmailTokenService;
import com.booktimer.email.EmailTokenType;
import com.booktimer.session.ReadingSessionRepository;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 재참여 넛지 발송 오케스트레이션 테스트 (Mockito — 발송·격리·멱등 와이어링).
 *
 * <p>대상 선정(SQL)은 {@code ReadingSessionRepositoryTest}가 경계 전수로 덮으므로, 여기선 ① cutoff=now-7일로
 * 조회 ② 대상마다 발송 + {@code recordNudgeSent} ③ 한 명 실패가 배치를 안 멈춤(격리) ④ 제목 (광고)·본문 구독해지
 * 링크(행동 수준)만 본다. 정확한 카피 문자열은 검증하지 않는다(리팩터 시 가짜 실패 방지 — N-???/브리틀 회피).
 */
@ExtendWith(MockitoExtension.class)
class RetentionNudgeServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-11T01:00:00Z");

    @Mock private ReadingSessionRepository sessionRepository;
    @Mock private UserRepository userRepository;
    @Mock private EmailSender emailSender;
    @Mock private EmailTokenService tokenService;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private RetentionNudgeService service() {
        return new RetentionNudgeService(sessionRepository, userRepository, emailSender,
                tokenService, clock, "https://booktimer.example");
    }

    private static User user(String email) {
        return User.of(email, "$2a$10$abcdefghijklmnopqrstuv", "책벌레", "Asia/Seoul", Role.USER);
    }

    @Test
    @DisplayName("sendNudges: cutoff = now-7일로 대상을 조회한다")
    void sendNudges_queriesWithSevenDayCutoff() {
        when(sessionRepository.findNudgeTargets(any())).thenReturn(List.of());

        service().sendNudges();

        verify(sessionRepository).findNudgeTargets(NOW.minus(Duration.ofDays(7)));
    }

    @Test
    @DisplayName("sendNudges: 대상마다 메일을 보내고 발송 시각을 기록한다(recordNudgeSent+save)")
    void sendNudges_sendsAndRecordsPerTarget() {
        User a = user("a@booktimer.com");
        User b = user("b@booktimer.com");
        when(sessionRepository.findNudgeTargets(any())).thenReturn(List.of(a, b));
        when(tokenService.issue(any(), eq(EmailTokenType.UNSUBSCRIBE))).thenReturn("rawtok");

        int sent = service().sendNudges();

        assertThat(sent).isEqualTo(2);
        verify(emailSender).send(eq("a@booktimer.com"), anyString(), anyString());
        verify(emailSender).send(eq("b@booktimer.com"), anyString(), anyString());
        // 멱등 상태가 박힌다 — 이 비활동 구간 재발송 차단
        assertThat(a.getLastNudgeSentAt()).isEqualTo(NOW);
        assertThat(b.getLastNudgeSentAt()).isEqualTo(NOW);
        verify(userRepository).save(a);
        verify(userRepository).save(b);
    }

    @Test
    @DisplayName("sendNudges: 한 수신자 발송 실패가 배치 전체를 중단하지 않는다(per-recipient 격리)")
    void sendNudges_isolatesPerRecipientFailure() {
        User boom = user("boom@booktimer.com");
        User ok = user("ok@booktimer.com");
        when(sessionRepository.findNudgeTargets(any())).thenReturn(List.of(boom, ok));
        when(tokenService.issue(any(), eq(EmailTokenType.UNSUBSCRIBE))).thenReturn("rawtok");
        // 첫 수신자 발송이 터져도 둘째는 발송돼야 한다
        org.mockito.Mockito.doThrow(new RuntimeException("smtp down"))
                .when(emailSender).send(eq("boom@booktimer.com"), anyString(), anyString());

        int sent = service().sendNudges();

        assertThat(sent).isEqualTo(1); // ok만 성공 집계
        verify(emailSender).send(eq("ok@booktimer.com"), anyString(), anyString());
        verify(userRepository).save(ok);
        // 실패한 수신자는 발송 시각이 안 박혀 다음 배치에서 다시 대상이 된다
        assertThat(boom.getLastNudgeSentAt()).isNull();
        verify(userRepository, never()).save(boom);
    }

    @Test
    @DisplayName("sendNudges: 제목에 (광고) 접두 + 본문에 구독해지 링크(토큰)가 들어간다(정보통신망법 §50 ②③)")
    void sendNudges_subjectHasAdPrefixAndBodyHasUnsubscribeLink() {
        User a = user("a@booktimer.com");
        when(sessionRepository.findNudgeTargets(any())).thenReturn(List.of(a));
        when(tokenService.issue(any(), eq(EmailTokenType.UNSUBSCRIBE))).thenReturn("UNSUBTOKEN");
        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);

        service().sendNudges();

        verify(emailSender).send(eq("a@booktimer.com"), subject.capture(), body.capture());
        assertThat(subject.getValue()).startsWith("(광고)");
        assertThat(body.getValue()).contains("/unsubscribe?token=UNSUBTOKEN");
    }

    @Test
    @DisplayName("sendNudges: 대상이 없으면 아무 것도 보내지 않는다")
    void sendNudges_noTargets_sendsNothing() {
        when(sessionRepository.findNudgeTargets(any())).thenReturn(List.of());

        int sent = service().sendNudges();

        assertThat(sent).isZero();
        verify(emailSender, never()).send(anyString(), anyString(), anyString());
    }
}
