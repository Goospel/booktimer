package com.booktimer.session;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 잔디 서비스의 "오늘" 경계 검증 — 같은 순간이라도 유저 타임존에 따라 오늘 날짜가 달라야 한다(N-010).
 *
 * <p>그리드 구성 자체는 {@link ContributionGraphBuilderTest}가 본다. 여기선 Clock+타임존 배선만 본다.
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
        when(history.dailyHistory(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        Clock fixed = Clock.fixed(INSTANT, ZoneOffset.UTC);
        ReadingContributionService service = new ReadingContributionService(history, fixed);

        User seoul = User.of("s@booktimer.com", "h", "서울", "Asia/Seoul", Role.USER);
        User utc = User.of("u@booktimer.com", "h", "UTC", "UTC", Role.USER);

        assertThat(latestDate(service.contributionGraph(seoul))).isEqualTo(LocalDate.of(2026, 6, 3));
        assertThat(latestDate(service.contributionGraph(utc))).isEqualTo(LocalDate.of(2026, 6, 2));
    }
}
