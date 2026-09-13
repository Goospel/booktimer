package com.booktimer.session;

import com.booktimer.session.StudyHistoryService.Day;
import com.booktimer.session.StudyHistoryService.Month;
import com.booktimer.session.StudyHistoryService.StudyHistory;
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
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * StudyHistoryService 단위 테스트 (Mockito — DB/컨텍스트 무관).
 *
 * <p><b>시각은 전부 절대 좌표다</b> — {@code now}를 인자로 받는 시그니처 덕에 자정 경계·타임존 귀속을
 * 결정적으로 심어 잰다(CI가 도는 시각에 결과가 흔들릴 자리가 없다).
 */
@ExtendWith(MockitoExtension.class)
class StudyHistoryServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Mock
    private StudySessionRepository sessionRepository;

    @InjectMocks
    private StudyHistoryService service;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.of("student@booktimer.com", "$2a$10$abcdefghijklmnopqrstuv", "공부벌레", "Asia/Seoul", Role.USER);
    }

    /** KST 그날 정오의 절대 시각 — 어느 경계와도 12시간 떨어져 있다. */
    private static Instant noonKst(String isoDate) {
        return LocalDate.parse(isoDate).atTime(12, 0).atZone(SEOUL).toInstant();
    }

    private StudySession completed(User owner, Instant startedAt, Duration length) {
        StudySession session = StudySession.start(owner, startedAt);
        session.end(startedAt.plus(length));
        return session;
    }

    private void given(StudySession... sessions) {
        when(sessionRepository.findByUserAndEndedAtIsNotNull(any())).thenReturn(List.of(sessions));
    }

    /** 잔디에서 그 날짜 칸을 집는다 — 53주 × 7칸 어디에 서 있든 날짜로 찾는다. */
    private static ContributionDay cellOf(ContributionGraph graph, String isoDate) {
        return graph.weeks().stream()
                .flatMap(List::stream)
                .filter(d -> d.date() != null && d.date().equals(LocalDate.parse(isoDate)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("잔디에 " + isoDate + " 칸이 없다"));
    }

    /**
     * <b>tz 귀속</b> — UTC 8월 31일 15:30에 시작한 세션은 KST 유저에겐 9월 1일 00:30이고,
     * 로스앤젤레스 유저에겐 8월 31일 08:30이다. 서버 UTC로 묶으면 두 유저가 같은 날에 서게 된다.
     */
    @Test
    @DisplayName("history: 일자 귀속은 유저 타임존이다 — 같은 세션이 KST/LA에서 다른 날에 선다")
    void history_attributesByUserTimezone() {
        Instant startedAt = Instant.parse("2026-08-31T15:30:00Z");
        given(completed(user, startedAt, Duration.ofHours(1)));

        StudyHistory seoul = service.history(user, noonKst("2026-09-02"));
        assertThat(seoul.months()).extracting(Month::month).containsExactly(YearMonth.of(2026, 9));
        assertThat(seoul.months().get(0).days()).extracting(Day::date)
                .containsExactly(LocalDate.parse("2026-09-01"));

        User la = User.of("la@booktimer.com", "$2a$10$abcdefghijklmnopqrstuv", "엘에이", "America/Los_Angeles", Role.USER);
        StudyHistory pacific = service.history(la, noonKst("2026-09-02"));
        assertThat(pacific.months().get(0).days()).extracting(Day::date)
                .containsExactly(LocalDate.parse("2026-08-31"));
    }

    @Test
    @DisplayName("history: 같은 날은 합치고 다른 날은 가른다 — 월·일 모두 최신 먼저, 월 합계는 일 합")
    void history_groupsAndSortsNewestFirst() {
        given(completed(user, noonKst("2026-09-01"), Duration.ofMinutes(20)),
                completed(user, noonKst("2026-09-01").plus(Duration.ofHours(3)), Duration.ofMinutes(40)),
                completed(user, noonKst("2026-08-30"), Duration.ofMinutes(15)));

        StudyHistory history = service.history(user, noonKst("2026-09-02"));

        assertThat(history.months()).extracting(Month::month)
                .containsExactly(YearMonth.of(2026, 9), YearMonth.of(2026, 8));
        assertThat(history.months().get(0).totalSeconds()).isEqualTo(3600);
        assertThat(history.months().get(0).days())
                .containsExactly(new Day(LocalDate.parse("2026-09-01"), 3600));
        assertThat(history.months().get(1).days())
                .containsExactly(new Day(LocalDate.parse("2026-08-30"), 900));
    }

    /**
     * 두 범위는 <b>다르다</b> — 목록은 전 기간이고 잔디는 53주다. 하나로 착각한 구현은 옛 기록을
     * 목록에서 지우거나(잔디 범위로 자름) 잔디 총합을 부풀린다(전 기간을 다 셈).
     */
    @Test
    @DisplayName("history: 목록은 전 기간, 잔디는 53주 — 2년 전 기록은 목록엔 남고 잔디 총합엔 없다")
    void history_listIsAllTimeButGraphIs53Weeks() {
        given(completed(user, noonKst("2024-09-01"), Duration.ofHours(1)),
                completed(user, noonKst("2026-09-02"), Duration.ofHours(1)));

        StudyHistory history = service.history(user, noonKst("2026-09-02"));

        assertThat(history.months()).hasSize(2);
        assertThat(history.graph().totalSeconds()).isEqualTo(3600);
        assertThat(history.graph().activeDays()).isEqualTo(1);
    }

    /**
     * <b>결정 C</b> — 농도 분모는 고정 절대 눈금(4h)이라 목표와 무관하다. 현재 목표를 분모로 쓰면
     * 목표를 바꾼 순간 <b>과거 칸이 소급 재채색</b>된다(N-059가 독서에서 버그로 고친 그 현상).
     */
    @Test
    @DisplayName("history: 잔디 농도는 공부 목표와 무관하다 — 목표를 바꿔도 과거 칸 색이 안 움직인다")
    void history_shadeIsIndependentOfGoal() {
        given(completed(user, noonKst("2026-09-01"), Duration.ofHours(1)),
                completed(user, noonKst("2026-08-31"), Duration.ofHours(4)));

        ContributionGraph before = service.history(user, noonKst("2026-09-02")).graph();
        assertThat(cellOf(before, "2026-09-01").level()).isEqualTo(1);
        assertThat(cellOf(before, "2026-08-31").level()).isEqualTo(4);

        user.updateStudyDailyGoal(600);
        ContributionGraph after = service.history(user, noonKst("2026-09-02")).graph();

        assertThat(cellOf(after, "2026-09-01").level()).isEqualTo(1);
        assertThat(cellOf(after, "2026-08-31").level()).isEqualTo(4);
    }

    @Test
    @DisplayName("history: 기록이 없으면 목록은 비고 잔디는 53주 빈 격자다 — 가입 직후가 여기로 온다")
    void history_emptyLedger() {
        given();

        StudyHistory history = service.history(user, noonKst("2026-09-02"));

        assertThat(history.months()).isEmpty();
        assertThat(history.graph().weeks()).hasSize(53);
        assertThat(history.graph().totalSeconds()).isZero();
        assertThat(history.graph().activeDays()).isZero();
        assertThat(history.graph().currentStreak()).isZero();
    }

    /** 연속일은 빌더에 맡긴다 — 위임이 끊기지 않았는지만 본다(유예 규칙 자체는 빌더 테스트 몫). */
    @Test
    @DisplayName("history: 연속일이 빌더에서 그대로 온다 — 오늘·어제 공부하면 2")
    void history_currentStreakComesFromBuilder() {
        given(completed(user, noonKst("2026-09-02"), Duration.ofMinutes(30)),
                completed(user, noonKst("2026-09-01"), Duration.ofMinutes(30)));

        assertThat(service.history(user, noonKst("2026-09-02")).graph().currentStreak()).isEqualTo(2);
    }
}
