package com.booktimer.session;

import com.booktimer.timer.ReadingTimer;
import com.booktimer.timer.ReadingTimerRepository;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 잔디 서비스 배선 검증 — ① "오늘"은 유저 타임존 기준(N-010), ② 색 농도 목표는 유저 타이머의 증가값에서 온다.
 *
 * <p>레벨 계산 규칙 자체는 {@link ContributionGraphBuilderTest}가 본다. 여기선 서비스가 타임존·목표를
 * 올바른 소스에서 끌어와 빌더에 넘기는 배선만 본다.
 */
class ReadingContributionServiceTest {

    // 2026-06-02T15:30Z — UTC로는 6/2, Asia/Seoul(UTC+9)로는 6/3 00:30
    private static final Instant INSTANT = Instant.parse("2026-06-02T15:30:00Z");

    private static LocalDate latestDate(ContributionGraph graph) {
        return graph.weeks().stream()
                .flatMap(List::stream)
                .map(ContributionDay::date)
                .filter(d -> d != null)
                .max(Comparator.naturalOrder())
                .orElseThrow();
    }

    @Test
    @DisplayName("오늘은 유저 타임존 기준이다 — Seoul 사용자는 6/3, UTC 사용자는 6/2")
    void today_isUserTimezoneBased() {
        ReadingHistoryService history = mock(ReadingHistoryService.class);
        when(history.dailyHistory(any())).thenReturn(List.of());
        ReadingTimerRepository timers = mock(ReadingTimerRepository.class);
        when(timers.findByUser(any())).thenReturn(Optional.empty());
        Clock fixed = Clock.fixed(INSTANT, ZoneOffset.UTC);
        ReadingContributionService service = new ReadingContributionService(history, timers, fixed);

        User seoul = User.of("s@booktimer.com", "h", "서울", "Asia/Seoul", Role.USER);
        User utc = User.of("u@booktimer.com", "h", "UTC", "UTC", Role.USER);

        assertThat(latestDate(service.contributionGraph(seoul))).isEqualTo(LocalDate.of(2026, 6, 3));
        assertThat(latestDate(service.contributionGraph(utc))).isEqualTo(LocalDate.of(2026, 6, 2));
    }

    @Test
    @DisplayName("색 농도 목표는 유저 타이머의 증가값에서 온다 — 같은 독서량도 목표가 작으면 더 진해진다")
    void goal_comesFromUserTimer() {
        User user = User.of("g@booktimer.com", "h", "목표", "Asia/Seoul", Role.USER);
        LocalDate day = LocalDate.of(2026, 5, 20); // 1년 창 안의 임의 날짜
        long read = 1800L; // 30분

        ReadingHistoryService history = mock(ReadingHistoryService.class);
        when(history.dailyHistory(user)).thenReturn(List.of(new DailyReadingRecord(day, read, List.of("책"))));
        ReadingTimerRepository timers = mock(ReadingTimerRepository.class);
        Clock fixed = Clock.fixed(INSTANT, ZoneOffset.UTC);
        ReadingContributionService service = new ReadingContributionService(history, timers, fixed);

        // 목표 1시간 → 30분은 50% → lv2
        when(timers.findByUser(user)).thenReturn(Optional.of(ReadingTimer.of(3600L)));
        assertThat(levelOf(service.contributionGraph(user), day)).isEqualTo(2);

        // 목표 30분 → 30분은 100% → lv4 (목표를 따라간다)
        when(timers.findByUser(user)).thenReturn(Optional.of(ReadingTimer.of(1800L)));
        assertThat(levelOf(service.contributionGraph(user), day)).isEqualTo(4);
    }

    private static int levelOf(ContributionGraph graph, LocalDate date) {
        return graph.weeks().stream()
                .flatMap(List::stream)
                .filter(c -> date.equals(c.date()))
                .findFirst()
                .orElseThrow()
                .level();
    }
}
