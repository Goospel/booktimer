package com.booktimer.retention;

import com.booktimer.config.TossProperties;
import com.booktimer.session.ReadingSessionRepository;
import com.booktimer.toss.TossMessengerClient;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 토스 재참여 넛지 발송 오케스트레이션 테스트 (Mockito — 게이트·발송·격리·멱등 와이어링).
 *
 * <p>대상 선정(SQL)은 {@code ReadingSessionRepositoryTest}가 경계 전수로 덮으므로, 여기선 다크런치 2중 게이트와
 * "성공한 사람만 마킹" 계약, per-user 격리만 본다({@link RetentionNudgeServiceTest}와 같은 계약).
 */
@ExtendWith(MockitoExtension.class)
class TossRetentionNudgeServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-18T10:00:00Z");
    private static final String TEMPLATE = "booktimer-retention-nudge";

    @Mock private ReadingSessionRepository sessionRepository;
    @Mock private UserRepository userRepository;
    @Mock private TossMessengerClient client;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private TossProperties properties(String templateCode) {
        TossProperties p = new TossProperties();
        p.getMessenger().setRetentionTemplateCode(templateCode);
        return p;
    }

    private TossRetentionNudgeService service(String templateCode, Optional<TossMessengerClient> messenger) {
        return new TossRetentionNudgeService(sessionRepository, userRepository, messenger,
                properties(templateCode), clock);
    }

    private static User tossUser(String email, String userKey) {
        User u = User.of(email, "$2a$10$abcdefghijklmnopqrstuv", "책벌레", "Asia/Seoul", Role.USER);
        u.linkTossUserKey(userKey);
        return u;
    }

    @Test
    @DisplayName("sendNudges: 템플릿 코드가 비어 있으면 조회도 발송도 하지 않는다 (콘솔 검수 전 다크런치)")
    void sendNudges_blankTemplateCode_doesNothing() {
        int sent = service("  ", Optional.of(client)).sendNudges();

        assertThat(sent).isZero();
        verifyNoInteractions(sessionRepository);
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("sendNudges: 메신저 게이트 OFF(클라이언트 빈 없음)면 no-op — client.get() NPE 금지")
    void sendNudges_noMessengerClient_doesNothing() {
        int sent = service(TEMPLATE, Optional.empty()).sendNudges();

        assertThat(sent).isZero();
        verifyNoInteractions(sessionRepository);
    }

    @Test
    @DisplayName("sendNudges: cutoff=now-7일로 조회하고, 발송 성공 시 발송 시각을 기록한다(recordNudgeSent+save)")
    void sendNudges_sendsAndRecordsOnSuccess() {
        User a = tossUser("a@booktimer.com", "tkey-a");
        when(sessionRepository.findTossNudgeTargets(any())).thenReturn(List.of(a));
        when(client.sendMessage(eq("tkey-a"), eq(TEMPLATE), any())).thenReturn(true);

        int sent = service(TEMPLATE, Optional.of(client)).sendNudges();

        assertThat(sent).isEqualTo(1);
        verify(sessionRepository).findTossNudgeTargets(NOW.minus(Duration.ofDays(7)));
        // 멱등 상태가 박힌다 — 이 비활동 구간 재발송 차단(이메일 넛지와 공유하는 컬럼)
        assertThat(a.getLastNudgeSentAt()).isEqualTo(NOW);
        verify(userRepository).save(a);
    }

    @Test
    @DisplayName("sendNudges: 발송 실패(토스 거부)면 마킹하지 않는다 — 다음 배치에서 재시도")
    void sendNudges_sendFailure_doesNotMark() {
        User a = tossUser("a@booktimer.com", "tkey-a");
        when(sessionRepository.findTossNudgeTargets(any())).thenReturn(List.of(a));
        when(client.sendMessage(anyString(), anyString(), any())).thenReturn(false);

        int sent = service(TEMPLATE, Optional.of(client)).sendNudges();

        assertThat(sent).isZero();
        assertThat(a.getLastNudgeSentAt()).isNull();
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("sendNudges: 한 명 실패가 배치를 멈추지 않는다 (per-user 격리)")
    void sendNudges_isolatesPerUserFailure() {
        User boom = tossUser("boom@booktimer.com", "tkey-boom");
        User ok = tossUser("ok@booktimer.com", "tkey-ok");
        when(sessionRepository.findTossNudgeTargets(any())).thenReturn(List.of(boom, ok));
        when(client.sendMessage(anyString(), anyString(), any())).thenReturn(true);
        doThrow(new RuntimeException("db down")).when(userRepository).save(boom);

        int sent = service(TEMPLATE, Optional.of(client)).sendNudges();

        assertThat(sent).isEqualTo(1); // ok만 성공 집계
        verify(client).sendMessage(eq("tkey-ok"), eq(TEMPLATE), any());
        verify(userRepository).save(ok);
    }

    @Test
    @DisplayName("sendNudges: 대상이 없으면 발송하지 않고 정상 종료한다")
    void sendNudges_noTargets_sendsNothing() {
        when(sessionRepository.findTossNudgeTargets(any())).thenReturn(List.of());

        int sent = service(TEMPLATE, Optional.of(client)).sendNudges();

        assertThat(sent).isZero();
        verify(client, never()).sendMessage(anyString(), anyString(), any(Map.class));
    }
}
