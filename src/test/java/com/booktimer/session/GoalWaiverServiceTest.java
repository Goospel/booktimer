package com.booktimer.session;

import com.booktimer.garden.FeedingService;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 밀린 하루 용서권 지급 유스케이스 — 리워드 광고 보상(실제 빈 + H2).
 *
 * <p>핵심 경계 셋: ① <b>대상 날짜를 서버가 고른다</b>(잔여 부채 최대, 동률이면 최신 — 클라는 날짜를 못 보낸다)
 * ② <b>일일 1회 상한</b>이 애플리케이션 검사와 <b>DB unique 제약</b>으로 이중화됐다(SSV가 없어 지급 요청은
 * 클라 주장일 뿐이므로 신뢰가 아니라 상한으로 캡한다) ③ <b>파밍 차단</b> — 용서는 부채 표시에만 작용하고
 * 먹이·잔디는 불변이다.
 *
 * <p>제약 검증은 애플리케이션 검사를 <b>우회해</b> 직접 2행을 저장하고 flush를 강제한다 — 검사 코드를
 * 지워도 DB가 막는지 보는 계측기다(mock 단위테스트는 unique 제약을 검증하지 못한다).
 */
@SpringBootTest
@Transactional
class GoalWaiverServiceTest {

    private static final String SEOUL = "Asia/Seoul";
    private static final long GOAL = 3600L; // 가입 기본 하루 목표 1시간

    @Autowired GoalWaiverService goalWaiverService;
    @Autowired ReadingGoalWaiverRepository waiverRepository;
    @Autowired ReadingDebtService debtService;
    @Autowired ReadingSessionRepository sessionRepository;
    @Autowired ReadingContributionService contributionService;
    @Autowired FeedingService feedingService;
    @Autowired UserRegistrationService registrationService;
    @Autowired Clock clock;

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    private User user(String email) {
        return registrationService.register(email, "rawpw1234", "독서가", SEOUL, Role.USER, today());
    }

    /** 지정한 날에 seconds 만큼 읽은 완료 세션을 만든다(유저 TZ 정오 시작). */
    private void read(User user, LocalDate date, long seconds) {
        Instant start = date.atTime(12, 0).atZone(ZoneId.of(SEOUL)).toInstant();
        ReadingSession session = ReadingSession.start(user, start);
        session.end(start.plusSeconds(seconds));
        sessionRepository.save(session);
    }

    /**
     * 윈도우 7일을 목표 달성으로 채우고, {@code missedOffsets}로 준 날만 <b>아예 안 읽은 날</b>로 남긴다
     * (그날 부채 = 목표 전액). 오늘은 항상 달성 처리해 총 부채 = 빠뜨린 날 합이 되게 한다.
     */
    private void readMetExcept(User user, int... missedOffsets) {
        for (int i = 0; i < WeeklyDebtCalculator.WINDOW_DAYS; i++) {
            final int offset = i;
            if (Arrays.stream(missedOffsets).anyMatch(o -> o == offset)) {
                continue;
            }
            read(user, today().minusDays(i), GOAL);
        }
    }

    @Test
    @DisplayName("빠뜨린 날이 여럿이면 잔여 부채가 가장 큰 날을 고르고, 총 부채가 정확히 그만큼 줄어든다")
    void waive_picksLargestDebtDay() {
        User u = user("waive-largest@booktimer.com");
        readMetExcept(u, 3, 5);              // 3·5일 전은 달성 채움에서 빼고 아래에서 직접 정한다
        read(u, today().minusDays(3), 1800L); // 3일 전 30분만 → 부채 1800 (5일 전은 무독서 → 부채 3600)
        long before = debtService.weeklyDebt(u).totalDebtSeconds();

        GoalWaiverService.WaiveResult result = goalWaiverService.waive(u);

        assertThat(result.waivedDate()).isEqualTo(today().minusDays(5)); // 3600 > 1800
        assertThat(result.waivedSeconds()).isEqualTo(GOAL);
        assertThat(debtService.weeklyDebt(u).totalDebtSeconds()).isEqualTo(before - GOAL);
    }

    @Test
    @DisplayName("부채가 동률이면 최신 날을 고른다 — 오래된 날은 어차피 곧 윈도우 밖으로 소멸")
    void waive_tieBreaksToMostRecent() {
        User u = user("waive-tie@booktimer.com");
        readMetExcept(u, 2, 5);
        read(u, today().minusDays(2), 1800L); // 부채 1800
        read(u, today().minusDays(5), 1800L); // 부채 1800 (동률)

        assertThat(goalWaiverService.waive(u).waivedDate()).isEqualTo(today().minusDays(2));
    }

    @Test
    @DisplayName("빠뜨린 날이 없으면 지급하지 않는다 → IllegalArgumentException (400)")
    void waive_noMissedDay_rejected() {
        User u = user("waive-none@booktimer.com");
        readMetExcept(u);

        assertThatThrownBy(() -> goalWaiverService.waive(u))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(waiverRepository.findByUserAndWaivedDateGreaterThanEqual(u, today().minusDays(6))).isEmpty();
    }

    @Test
    @DisplayName("오늘 부채만 있으면 지급하지 않는다 — 진행 중인 오늘은 광고로 지울 수 없다")
    void waive_onlyTodayDebt_rejected() {
        User u = user("waive-today@booktimer.com");
        readMetExcept(u, 0); // 오늘만 안 읽음

        assertThat(debtService.weeklyDebt(u).todayDebtSeconds()).isEqualTo(GOAL); // 오늘 부채는 있다
        assertThatThrownBy(() -> goalWaiverService.waive(u))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("1분 미만 부채인 날은 지급 대상이 아니다 — 이미 기존 메커니즘이 용서한 날")
    void waive_subMinuteDebtOnly_rejected() {
        User u = user("waive-subminute@booktimer.com");
        readMetExcept(u, 3);
        read(u, today().minusDays(3), GOAL - 30L); // 30초 부족 → forgivenSubMinute로 이미 0

        assertThatThrownBy(() -> goalWaiverService.waive(u))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("일일 상한: 같은 날 2회째 지급은 거부된다 → IllegalStateException (409)")
    void waive_secondTimeSameDay_rejected() {
        User u = user("waive-cap@booktimer.com");
        readMetExcept(u, 2, 4);

        goalWaiverService.waive(u); // 1회차 OK

        assertThatThrownBy(() -> goalWaiverService.waive(u))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("일일 상한은 DB 제약이 최종 방어 — 애플리케이션 검사를 우회해도 (user, granted_on) 유니크가 막는다")
    void dailyCap_enforcedByDbConstraint() {
        User u = user("waive-dbcap@booktimer.com");
        waiverRepository.saveAndFlush(ReadingGoalWaiver.create(u, today().minusDays(2), today()));

        // IDENTITY 생성이라 INSERT가 save 시점에 실행된다 — save·flush를 함께 감싸 어느 쪽에서 터져도 잡는다.
        assertThatThrownBy(() -> {
            waiverRepository.save(ReadingGoalWaiver.create(u, today().minusDays(3), today())); // 같은 granted_on
            waiverRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("중복 용서는 DB 제약이 막는다 — (user, waived_date) 유니크")
    void duplicateWaivedDate_enforcedByDbConstraint() {
        User u = user("waive-dupdate@booktimer.com");
        LocalDate target = today().minusDays(2);
        waiverRepository.saveAndFlush(ReadingGoalWaiver.create(u, target, today()));

        assertThatThrownBy(() -> {
            waiverRepository.save(ReadingGoalWaiver.create(u, target, today().minusDays(1))); // 같은 waived_date
            waiverRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("파밍 차단: 용서 전후로 먹이 잔액과 연속일이 불변 — 용서는 부채 표시에만 작용한다")
    void waive_doesNotAffectFoodOrStreak() {
        User u = user("waive-farm@booktimer.com");
        readMetExcept(u, 3);
        int foodBefore = feedingService.foodBalance(u);
        int streakBefore = contributionService.contributionGraph(u).currentStreak();

        goalWaiverService.waive(u);

        assertThat(feedingService.foodBalance(u)).isEqualTo(foodBefore);
        assertThat(contributionService.contributionGraph(u).currentStreak()).isEqualTo(streakBefore);
    }

    // --- availableFor (미니앱 버튼 노출 조건) ---

    @Test
    @DisplayName("availableFor: 빠뜨린 날 있음 + 오늘 미사용 → true")
    void availableFor_missedDayAndUnused_true() {
        User u = user("avail-yes@booktimer.com");
        readMetExcept(u, 2);

        assertThat(goalWaiverService.availableFor(u)).isTrue();
    }

    @Test
    @DisplayName("availableFor: 빠뜨린 날 없으면 false")
    void availableFor_noMissedDay_false() {
        User u = user("avail-nomissed@booktimer.com");
        readMetExcept(u);

        assertThat(goalWaiverService.availableFor(u)).isFalse();
    }

    @Test
    @DisplayName("availableFor: 오늘 이미 지급받았으면 false (남은 빠뜨린 날이 있어도)")
    void availableFor_alreadyGrantedToday_false() {
        User u = user("avail-used@booktimer.com");
        readMetExcept(u, 2, 4);

        goalWaiverService.waive(u);

        assertThat(debtService.weeklyDebt(u).missedDays()).isNotEmpty(); // 아직 빠뜨린 날은 남았다
        assertThat(goalWaiverService.availableFor(u)).isFalse();
    }
}
