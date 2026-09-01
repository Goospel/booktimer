package com.booktimer.session;

import com.booktimer.session.StudyCalendarService.CalendarDay;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * StudyCalendarService 단위 테스트 (Mockito — DB/컨텍스트 무관).
 *
 * <p><b>시각은 전부 절대 좌표다</b>(벽시계 의존 금지). 다만 「미래 날짜 거부」·「자정 귀속」은
 * <b>날짜 경계 로직 자체가 대상</b>이라, {@code now}를 인자로 받는 시그니처 덕에 경계 직전·직후를
 * 결정적으로 심어 잰다 — CI가 도는 시각에 결과가 흔들릴 자리가 없다.
 */
@ExtendWith(MockitoExtension.class)
class StudyCalendarServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Mock
    private StudyDailyCheckRepository checkRepository;

    @Mock
    private StudySessionRepository sessionRepository;

    @InjectMocks
    private StudyCalendarService service;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.of("student@booktimer.com", "$2a$10$abcdefghijklmnopqrstuv", "공부벌레", "Asia/Seoul", Role.USER);
    }

    /** KST 그날 정오의 절대 시각 — 어느 경계와도 12시간 떨어져 있다. */
    private static Instant noonKst(String isoDate) {
        return LocalDate.parse(isoDate).atTime(12, 0).atZone(SEOUL).toInstant();
    }

    private StudySession completed(Instant startedAt, Duration length) {
        StudySession session = StudySession.start(user, startedAt);
        session.end(startedAt.plus(length));
        return session;
    }

    // ── setCheck — 원장은 수동 체크다 ────────────────────────────────────────

    @Test
    @DisplayName("setCheck: 무기록이던 날에 지킴을 남기면 새 행을 저장한다")
    void setCheck_insertsWhenAbsent() {
        LocalDate date = LocalDate.parse("2026-08-30");
        when(checkRepository.findByUserAndCheckDate(user, date)).thenReturn(Optional.empty());

        service.setCheck(user, date, true, noonKst("2026-09-01"));

        ArgumentCaptor<StudyDailyCheck> saved = ArgumentCaptor.forClass(StudyDailyCheck.class);
        verify(checkRepository).save(saved.capture());
        assertThat(saved.getValue().getCheckDate()).isEqualTo(date);
        assertThat(saved.getValue().isKept()).isTrue();
    }

    @Test
    @DisplayName("setCheck: 이미 판정이 있는 날은 그 행을 갈아 끼운다 — 하루 한 판정(중복 행 없음)")
    void setCheck_updatesExistingRow() {
        LocalDate date = LocalDate.parse("2026-08-30");
        StudyDailyCheck existing = StudyDailyCheck.of(user, date, true);
        when(checkRepository.findByUserAndCheckDate(user, date)).thenReturn(Optional.of(existing));

        service.setCheck(user, date, false, noonKst("2026-09-01"));

        assertThat(existing.isKept()).isFalse();
        // 새 행을 만들면 UNIQUE(user_id, check_date) 위반이다 — 저장이 있더라도 그 행이어야 한다.
        ArgumentCaptor<StudyDailyCheck> saved = ArgumentCaptor.forClass(StudyDailyCheck.class);
        verify(checkRepository).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(existing);
    }

    @Test
    @DisplayName("setCheck: kept=null이면 행을 지워 무기록으로 되돌린다(3상태 순환의 마지막 칸)")
    void setCheck_nullDeletesRow() {
        LocalDate date = LocalDate.parse("2026-08-30");
        StudyDailyCheck existing = StudyDailyCheck.of(user, date, false);
        when(checkRepository.findByUserAndCheckDate(user, date)).thenReturn(Optional.of(existing));

        service.setCheck(user, date, null, noonKst("2026-09-01"));

        verify(checkRepository).delete(existing);
        verify(checkRepository, never()).save(any(StudyDailyCheck.class));
    }

    @Test
    @DisplayName("setCheck: 무기록인 날을 다시 무기록으로 두면 아무 일도 없다(없는 행을 지우지 않는다)")
    void setCheck_nullOnAbsentIsNoop() {
        LocalDate date = LocalDate.parse("2026-08-30");
        when(checkRepository.findByUserAndCheckDate(user, date)).thenReturn(Optional.empty());

        service.setCheck(user, date, null, noonKst("2026-09-01"));

        verify(checkRepository, never()).delete(any(StudyDailyCheck.class));
        verify(checkRepository, never()).save(any(StudyDailyCheck.class));
    }

    @Test
    @DisplayName("setCheck: 오늘은 체크할 수 있다 — 경계는 '오늘 초과'다")
    void setCheck_todayIsAllowed() {
        LocalDate today = LocalDate.parse("2026-09-01");
        when(checkRepository.findByUserAndCheckDate(user, today)).thenReturn(Optional.empty());

        service.setCheck(user, today, true, noonKst("2026-09-01"));

        verify(checkRepository).save(any(StudyDailyCheck.class));
    }

    @Test
    @DisplayName("setCheck: 미래 날짜는 거부한다 — 아직 오지 않은 날을 지켰다고 남길 수 없다")
    void setCheck_futureIsRejected() {
        LocalDate tomorrow = LocalDate.parse("2026-09-02");

        assertThatThrownBy(() -> service.setCheck(user, tomorrow, true, noonKst("2026-09-01")))
                .isInstanceOf(IllegalArgumentException.class)
                // 이 메시지는 컨트롤러의 IllegalArgumentException 핸들러가 400 본문으로 <b>그대로</b>
                // 내보낸다 — 사용자에게 보이는 문구라 한국어여야 한다.
                .hasMessage("미래 날짜는 체크할 수 없어요");
    }

    /**
     * <b>tz 경계</b> — 서버 시계가 UTC로 8월 31일 23:00일 때, KST 유저의 「오늘」은 이미 9월 1일이다.
     * 판정을 UTC로 하면 이 날짜가 미래로 오인돼 <b>자정 직후 한국 사용자가 오늘을 못 찍는다</b>.
     */
    @Test
    @DisplayName("setCheck: 유저 타임존 기준으로 오늘을 판정한다 — UTC 자정 직전의 KST '오늘'은 통과")
    void setCheck_usesUserTimezoneForToday() {
        LocalDate kstToday = LocalDate.parse("2026-09-01");
        Instant utcEveningBefore = Instant.parse("2026-08-31T23:00:00Z"); // = KST 2026-09-01 08:00
        when(checkRepository.findByUserAndCheckDate(user, kstToday)).thenReturn(Optional.empty());

        service.setCheck(user, kstToday, true, utcEveningBefore);

        verify(checkRepository).save(any(StudyDailyCheck.class));
    }

    // ── month — 자동 정보(측정 있음)와 원장(체크)의 합성 ─────────────────────

    @Test
    @DisplayName("month: 세션만 있는 날·체크만 있는 날·둘 다인 날이 한 목록으로 합쳐진다")
    void month_mergesSessionsAndChecks() {
        YearMonth month = YearMonth.of(2026, 8);
        when(sessionRepository.findByUserAndEndedAtIsNotNullAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                any(), any(), any()))
                .thenReturn(List.of(
                        completed(noonKst("2026-08-03"), Duration.ofMinutes(30)),
                        completed(noonKst("2026-08-05"), Duration.ofMinutes(10))));
        when(checkRepository.findByUserAndCheckDateBetween(user, LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-31")))
                .thenReturn(List.of(
                        StudyDailyCheck.of(user, LocalDate.parse("2026-08-05"), true),
                        StudyDailyCheck.of(user, LocalDate.parse("2026-08-09"), false)));

        List<CalendarDay> days = service.month(user, month);

        assertThat(days).containsExactly(
                new CalendarDay(LocalDate.parse("2026-08-03"), 1800, null),
                new CalendarDay(LocalDate.parse("2026-08-05"), 600, true),
                new CalendarDay(LocalDate.parse("2026-08-09"), 0, false));
    }

    @Test
    @DisplayName("month: 같은 날 세션 여럿은 합산된다")
    void month_sumsSessionsOfSameDay() {
        when(sessionRepository.findByUserAndEndedAtIsNotNullAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                any(), any(), any()))
                .thenReturn(List.of(
                        completed(noonKst("2026-08-03"), Duration.ofMinutes(30)),
                        completed(noonKst("2026-08-03").plus(Duration.ofHours(3)), Duration.ofMinutes(20))));
        when(checkRepository.findByUserAndCheckDateBetween(any(), any(), any())).thenReturn(List.of());

        assertThat(service.month(user, YearMonth.of(2026, 8)))
                .containsExactly(new CalendarDay(LocalDate.parse("2026-08-03"), 3000, null));
    }

    /**
     * 자정을 걸친 세션은 <b>시작한 날</b>에 전부 들어간다(1차 규칙 — {@code todaySeconds}와 같은 귀속).
     * 두 화면이 다른 규칙을 쓰면 히어로의 「오늘 공부한 시간」과 달력의 그날 점이 어긋난다.
     */
    @Test
    @DisplayName("month: 자정을 걸친 세션은 시작한 날에 귀속된다")
    void month_attributesOvernightSessionToStartDay() {
        Instant lateNight = LocalDate.parse("2026-08-10").atTime(23, 30).atZone(SEOUL).toInstant();
        when(sessionRepository.findByUserAndEndedAtIsNotNullAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                any(), any(), any()))
                .thenReturn(List.of(completed(lateNight, Duration.ofHours(1))));
        when(checkRepository.findByUserAndCheckDateBetween(any(), any(), any())).thenReturn(List.of());

        assertThat(service.month(user, YearMonth.of(2026, 8)))
                .containsExactly(new CalendarDay(LocalDate.parse("2026-08-10"), 3600, null));
    }

    /**
     * 월 범위는 <b>유저 타임존의 1일 00:00</b>부터다 — UTC로 자르면 KST 1일 새벽(=UTC 전달 말일)이
     * 통째로 빠지고, 반대쪽 끝은 다음 달 몫이 딸려 온다.
     */
    @Test
    @DisplayName("month: 조회 구간이 유저 타임존의 월 경계다(1일 00:00 ~ 다음 달 1일 00:00)")
    void month_rangeIsUserTimezoneMonthBoundary() {
        when(sessionRepository.findByUserAndEndedAtIsNotNullAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                any(), any(), any()))
                .thenReturn(List.of());
        when(checkRepository.findByUserAndCheckDateBetween(any(), any(), any())).thenReturn(List.of());

        service.month(user, YearMonth.of(2026, 8));

        ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> to = ArgumentCaptor.forClass(Instant.class);
        verify(sessionRepository).findByUserAndEndedAtIsNotNullAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                any(), from.capture(), to.capture());
        assertThat(from.getValue()).isEqualTo(Instant.parse("2026-07-31T15:00:00Z")); // KST 08-01 00:00
        assertThat(to.getValue()).isEqualTo(Instant.parse("2026-08-31T15:00:00Z"));   // KST 09-01 00:00
    }

    @Test
    @DisplayName("month: 아무것도 없는 달은 빈 목록이다(희소 — 데이터 있는 날만 싣는다)")
    void month_emptyMonthIsEmptyList() {
        when(sessionRepository.findByUserAndEndedAtIsNotNullAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                any(), any(), any()))
                .thenReturn(List.of());
        when(checkRepository.findByUserAndCheckDateBetween(any(), any(), any())).thenReturn(List.of());

        assertThat(service.month(user, YearMonth.of(2026, 8))).isEmpty();
    }
}
