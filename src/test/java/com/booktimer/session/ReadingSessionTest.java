package com.booktimer.session;

import com.booktimer.user.Role;
import com.booktimer.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ReadingSession 도메인 메서드 테스트 (DB 무관 — 객체만 생성해 검증).
 *
 * <p>세션은 start로 시작(진행 중)하고 end로 종료한다. 종료 시
 * durationSeconds = endedAt - startedAt 을 계산한다. (이 값이 추후 ReadingTimer
 * remainingSeconds 차감에 쓰인다 — 다음 증분.)
 */
class ReadingSessionTest {

    private static final Instant T0 = Instant.parse("2026-06-01T09:00:00Z");

    private User sampleUser() {
        return User.of("reader@booktimer.com", "$2a$10$abcdefghijklmnopqrstuv", "책벌레", "Asia/Seoul", Role.USER);
    }

    @Test
    @DisplayName("start: 유저와 연결되고 시작시각 설정, 진행 중(종료 전) 상태")
    void start_initializesActiveSession() {
        User user = sampleUser();

        ReadingSession session = ReadingSession.start(user, T0);

        assertThat(session.getUser()).isSameAs(user);
        assertThat(session.getStartedAt()).isEqualTo(T0);
        assertThat(session.getEndedAt()).isNull();
        assertThat(session.getDurationSeconds()).isZero();
        assertThat(session.isActive()).isTrue();
    }

    @Test
    @DisplayName("start: user가 null이면 예외")
    void start_nullUser_throws() {
        assertThatThrownBy(() -> ReadingSession.start(null, T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("start: startedAt이 null이면 예외")
    void start_nullStartedAt_throws() {
        assertThatThrownBy(() -> ReadingSession.start(sampleUser(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("end: 종료시각 설정 + duration(초) 계산, 진행 중 아님")
    void end_setsDurationAndDeactivates() {
        ReadingSession session = ReadingSession.start(sampleUser(), T0);

        session.end(T0.plusSeconds(5400)); // 90분

        assertThat(session.getEndedAt()).isEqualTo(T0.plusSeconds(5400));
        assertThat(session.getDurationSeconds()).isEqualTo(5400L);
        assertThat(session.isActive()).isFalse();
    }

    @Test
    @DisplayName("end: endedAt이 startedAt보다 이르면(시계 역행) 예외")
    void end_beforeStart_throws() {
        ReadingSession session = ReadingSession.start(sampleUser(), T0);

        assertThatThrownBy(() -> session.end(T0.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("end: endedAt이 null이면 예외")
    void end_nullEndedAt_throws() {
        ReadingSession session = ReadingSession.start(sampleUser(), T0);

        assertThatThrownBy(() -> session.end(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("end: 이미 종료된 세션을 다시 종료하면 예외")
    void end_alreadyEnded_throws() {
        ReadingSession session = ReadingSession.start(sampleUser(), T0);
        session.end(T0.plusSeconds(60));

        assertThatThrownBy(() -> session.end(T0.plusSeconds(120)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("end: startedAt == endedAt(0초)도 허용 — duration 0")
    void end_zeroDuration_allowed() {
        ReadingSession session = ReadingSession.start(sampleUser(), T0);

        session.end(T0);

        assertThat(session.getDurationSeconds()).isZero();
        assertThat(session.isActive()).isFalse();
    }

    @Test
    @DisplayName("manual: 수동 입력 완료 세션을 만든다 — manualEntry=true, 이미 종료됨")
    void manual_createsCompletedManualEntry() {
        ReadingSession session = ReadingSession.manual(sampleUser(), T0, T0.plusSeconds(3600), null);

        assertThat(session.isManualEntry()).isTrue();
        assertThat(session.isActive()).isFalse();
        assertThat(session.getDurationSeconds()).isEqualTo(3600L);
    }

    @Test
    @DisplayName("start: 실시간 측정은 수동 입력이 아니다 (manualEntry=false)")
    void start_isNotManualEntry() {
        ReadingSession session = ReadingSession.start(sampleUser(), T0);

        assertThat(session.isManualEntry()).isFalse();
    }
}
