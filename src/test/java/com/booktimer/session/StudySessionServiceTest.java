package com.booktimer.session;

import com.booktimer.user.Role;
import com.booktimer.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * StudySessionService 오케스트레이션 단위 테스트 (Mockito — DB/컨텍스트 무관).
 *
 * <p>독서와 <b>같은 불변식</b>(중복 start 거부 · 무세션 stop 거부 · 6h 클램프 · 방치 스윕)을 지키는지와,
 * 공부에만 있는 규칙 하나 — <b>독서 세션이 진행 중이면 공부 시작을 거부</b>한다 — 를 본다.
 * 두 원장이 같은 시간을 이중으로 세지 않게 하는 유일한 자리라 여기서 못 박는다.
 */
@ExtendWith(MockitoExtension.class)
class StudySessionServiceTest {

    private static final Instant T0 = Instant.parse("2026-09-01T09:00:00Z");

    @Mock
    private StudySessionRepository studyRepository;

    @Mock
    private ReadingSessionRepository readingRepository;

    @InjectMocks
    private StudySessionService service;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.of("student@booktimer.com", "$2a$10$abcdefghijklmnopqrstuv", "공부벌레", "Asia/Seoul", Role.USER);
    }

    // --- start ---

    @Test
    @DisplayName("start: 진행 중 세션이 없으면 새 공부 세션을 저장한다")
    void start_savesNewSession() {
        when(studyRepository.findByUserAndEndedAtIsNull(user)).thenReturn(Optional.empty());
        when(readingRepository.findByUserAndEndedAtIsNull(user)).thenReturn(Optional.empty());
        when(studyRepository.save(any(StudySession.class))).thenAnswer(returnsFirstArg());

        StudySession started = service.start(user, T0);

        assertThat(started.getStartedAt()).isEqualTo(T0);
        assertThat(started.isActive()).isTrue();
        verify(studyRepository).save(any(StudySession.class));
    }

    @Test
    @DisplayName("start: 진행 중 공부 세션이 있으면 거부한다(중복 측정 금지)")
    void start_rejectsWhenStudyActive() {
        when(studyRepository.findByUserAndEndedAtIsNull(user))
                .thenReturn(Optional.of(StudySession.start(user, T0)));

        assertThatThrownBy(() -> service.start(user, T0.plusSeconds(60)))
                .isInstanceOf(IllegalStateException.class);
        verify(studyRepository, never()).save(any(StudySession.class));
    }

    @Test
    @DisplayName("start: 진행 중 '독서' 세션이 있어도 거부한다 — 두 원장이 같은 시간을 이중으로 세지 않는다")
    void start_rejectsWhenReadingActive() {
        when(studyRepository.findByUserAndEndedAtIsNull(user)).thenReturn(Optional.empty());
        when(readingRepository.findByUserAndEndedAtIsNull(user))
                .thenReturn(Optional.of(ReadingSession.start(user, T0)));

        assertThatThrownBy(() -> service.start(user, T0.plusSeconds(60)))
                .isInstanceOf(IllegalStateException.class);
        verify(studyRepository, never()).save(any(StudySession.class));
    }

    // --- stop ---

    @Test
    @DisplayName("stop: 진행 중 세션을 종료하고 경과를 초로 계산한다")
    void stop_endsActiveSession() {
        StudySession active = StudySession.start(user, T0);
        when(studyRepository.findByUserAndEndedAtIsNull(user)).thenReturn(Optional.of(active));
        when(studyRepository.save(active)).thenReturn(active);

        StudySession stopped = service.stop(user, T0.plusSeconds(1800));

        assertThat(stopped.getDurationSeconds()).isEqualTo(1800);
        assertThat(stopped.isActive()).isFalse();
    }

    @Test
    @DisplayName("stop: 진행 중 세션이 없으면 거부한다")
    void stop_rejectsWhenNoActiveSession() {
        when(studyRepository.findByUserAndEndedAtIsNull(user)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.stop(user, T0))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("stop: 6시간을 넘긴 경과는 cap으로 잘라 인정한다(독서와 같은 상한)")
    void stop_clampsToCap() {
        StudySession active = StudySession.start(user, T0);
        when(studyRepository.findByUserAndEndedAtIsNull(user)).thenReturn(Optional.of(active));
        when(studyRepository.save(active)).thenReturn(active);

        StudySession stopped = service.stop(user, T0.plus(Duration.ofHours(21)));

        assertThat(stopped.getDurationSeconds())
                .isEqualTo(ReadingSessionService.MAX_SESSION_DURATION.toSeconds());
    }

    // --- 방치 스윕 ---

    @Test
    @DisplayName("closeStaleSessions: 방치된 세션을 정확히 cap 길이로 닫는다")
    void closeStaleSessions_closesAtCap() {
        StudySession stale = StudySession.start(user, T0);
        when(studyRepository.findByEndedAtIsNullAndStartedAtBefore(any(Instant.class)))
                .thenReturn(List.of(stale));

        int closed = service.closeStaleSessions(T0.plus(Duration.ofHours(9)));

        assertThat(closed).isEqualTo(1);
        assertThat(stale.getDurationSeconds())
                .isEqualTo(ReadingSessionService.MAX_SESSION_DURATION.toSeconds());
    }

    // --- 당일 누적 ---

    @Test
    @DisplayName("todaySeconds: 유저 타임존의 오늘 경계로 완료 세션 합을 묻는다")
    void todaySeconds_usesUserTimezoneDayBoundary() {
        // 2026-09-01T09:00Z = KST 18:00 → 오늘은 09-01(KST), 경계는 08-31T15:00Z ~ 09-01T15:00Z
        when(studyRepository.sumCompletedSeconds(
                user,
                Instant.parse("2026-08-31T15:00:00Z"),
                Instant.parse("2026-09-01T15:00:00Z")))
                .thenReturn(1500L);

        assertThat(service.todaySeconds(user, T0)).isEqualTo(1500L);
    }
}
