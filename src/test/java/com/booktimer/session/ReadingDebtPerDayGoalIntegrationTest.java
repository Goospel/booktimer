package com.booktimer.session;

import com.booktimer.timer.ReadingGoalChange;
import com.booktimer.timer.ReadingGoalChangeRepository;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * per-day 목표 부채 판정 end-to-end (실제 빈·H2).
 *
 * <p>목표 변경 이력({@link ReadingGoalChange})이 실제 DB·리포지토리·{@link com.booktimer.timer.GoalSchedule}를 거쳐
 * 부채 계산에 흘러드는지 본다 — 목표를 올린 뒤에도 옛 목표를 채운 과거 날이 빠뜨린 날로 둔갑하지 않는다(N-059).
 * 순수 계산·배선은 단위 테스트가 덮으므로, 여기선 derived 쿼리(findByUserOrderByEffectiveDateAsc)와 세션 집계까지
 * 묶인 실 경로만 확인한다.
 */
@SpringBootTest
@Transactional
class ReadingDebtPerDayGoalIntegrationTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired
    private UserRegistrationService registrationService;
    @Autowired
    private ReadingGoalChangeRepository goalChangeRepository;
    @Autowired
    private ReadingSessionRepository sessionRepository;
    @Autowired
    private ReadingDebtService debtService;
    @Autowired
    private Clock clock;

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    @Test
    @DisplayName("목표를 60→70분으로 올려도, 옛 60분 목표를 채운 과거 날은 빠뜨린 날에 뜨지 않는다")
    void raisingGoal_doesNotResurrectPastDayMetUnderOldGoal() {
        User user = registrationService.register(
                "perday@booktimer.com", "rawpw1234", "독서가", SEOUL, Role.USER, today());

        // 목표 이력: 5일 전부터 60분, 1일 전부터 70분으로 인상.
        goalChangeRepository.save(ReadingGoalChange.of(user, today().minusDays(5), 3600L));
        goalChangeRepository.save(ReadingGoalChange.of(user, today().minusDays(1), 4200L));

        // 3일 전(목표가 60분이던 때) 정확히 60분 읽어 그날 목표를 채움.
        LocalDate metDay = today().minusDays(3);
        Instant start = metDay.atTime(12, 0).atZone(ZoneId.of(SEOUL)).toInstant();
        ReadingSession session = ReadingSession.start(user, start);
        session.end(start.plusSeconds(3600));
        sessionRepository.save(session);

        WeeklyDebt debt = debtService.weeklyDebt(user);

        // 현재 목표(70분)로 일괄 판정했다면 10분 부족으로 떴겠지만, 그날 목표(60분)로 보면 채운 날 → 제외.
        assertThat(debt.missedDays()).extracting(DayDebt::date).doesNotContain(metDay);
    }
}
