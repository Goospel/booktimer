package com.booktimer.session;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;

/**
 * 7일 윈도우 per-day 부채 순수 계산 로직 (DB·시간 무관).
 *
 * <p>부채는 날짜별 독립이다 — 하루 부채 = {@code max(0, 하루목표 − 그날 읽은 초)}. 이월·뱅킹이 없어
 * 목표를 초과해 읽어도 그날 부채만 0이 되고 다른 날로 넘어가지 않는다. 활성 범위는 <b>최근 7일</b>
 * (오늘 포함)이고, 그보다 오래된 날은 계산에 넣지 않는다 — 이 윈도우가 옛 모델의 누적 상한(cap)을
 * 대체하는 자동 용서 장치다(최대 부채 = 7×목표로 자연 제한).
 *
 * <p>"오늘"은 서버 UTC가 아니라 유저 타임존 자정 경계로 정해져야 하므로(N-010) 호출자가 결정해 넘긴다.
 * 그날 읽은 초는 완료 세션을 유저 타임존 일자로 묶은 값({@link ReadingHistoryService})에서 온다.
 */
public final class WeeklyDebtCalculator {

    /** 활성 윈도우 길이(일) — 오늘 포함 최근 7일. 그 이전은 자동 용서(목록·집계 제외). */
    public static final int WINDOW_DAYS = 7;

    /**
     * "빠뜨린 날"로 칠 최소 부채(초) — 1분. 하루 부채가 이 값 미만이면 빠뜨린 날 목록에서 제외한다.
     *
     * <p>왜: 하루 목표는 항상 <b>분 단위</b>로 입력되는데(SettingsForm·OnboardingForm의 {@code incrementMinutes}),
     * 부채는 현재 평면 목표를 윈도우 7일에 일괄 적용해 유도한다(per-day 목표 스냅샷 없음 — plan.md "알려진 단순화").
     * 그래서 사용자가 목표를 분 단위로 <b>올리면</b>, 옛 목표는 채웠던 과거 날이 새 목표와의 <b>1분 미만</b> 차이로
     * "0분 부족"이라는 모순된 거짓 미충족으로 표시된다(목록엔 떴는데 표시상 0분 부족). 목표가 분 단위인 이상
     * 1분 미만 부족은 진짜 미달이 아니라 사후 목표 인상이 만든 반올림 잔재이므로 미충족으로 치지 않는다.
     */
    public static final long MIN_MISSED_DEBT_SECONDS = 60L;

    private WeeklyDebtCalculator() {
    }

    /**
     * 윈도우 내 날짜별 읽은 양과 <b>단일 평면 목표</b>로 {@link WeeklyDebt}를 만든다(목표 이력이 없거나
     * 폴백할 때). 날짜별로 목표가 달랐다면 {@link #compute(Map, Map, LocalDate)}를 쓴다.
     *
     * @param secondsByDate 날짜→그날 읽은 초(완료 세션 합). 키가 없는 날은 0으로 본다.
     * @param dailyGoalSeconds 하루 목표(초, 0 이상) — 모든 날에 동일 적용
     * @param today 유저 타임존 기준 오늘
     * @return 오늘 부채 + 윈도우 내 과거 빠뜨린 날(부채 1분 이상, 최근 먼저) — 1분 미만은 용서({@link #MIN_MISSED_DEBT_SECONDS})
     */
    public static WeeklyDebt compute(Map<LocalDate, Long> secondsByDate, long dailyGoalSeconds, LocalDate today) {
        return compute(secondsByDate, date -> dailyGoalSeconds, today);
    }

    /**
     * 윈도우 내 날짜별 읽은 양과 <b>날짜별 목표</b>(per-day)로 {@link WeeklyDebt}를 만든다.
     *
     * <p>각 날을 <b>그날 유효했던 목표</b>로 판정한다 — 사용자가 목표를 올려도 옛 목표를 채운 과거 날이
     * 빠뜨린 날로 둔갑하지 않는다(소급 함정 차단, N-059). 목표 이력은 {@link com.booktimer.timer.GoalSchedule}가
     * 날짜별로 풀어 준다.
     *
     * @param secondsByDate 날짜→그날 읽은 초. 키가 없는 날은 0으로 본다.
     * @param goalByDate    날짜→그날 목표(초). 윈도우 7일 날짜는 모두 채워져 있어야 한다(없으면 0=부채 없음으로 봄).
     * @param today 유저 타임존 기준 오늘
     */
    public static WeeklyDebt compute(Map<LocalDate, Long> secondsByDate, Map<LocalDate, Long> goalByDate, LocalDate today) {
        return compute(secondsByDate, date -> goalByDate.getOrDefault(date, 0L), today);
    }

    /** 공통 코어 — 날짜→그날 목표를 돌려주는 리졸버로 오늘 부채 + 윈도우 빠뜨린 날을 계산한다. */
    private static WeeklyDebt compute(Map<LocalDate, Long> secondsByDate, ToLongFunction<LocalDate> goalForDate, LocalDate today) {
        long todayDebt = debtOn(today, secondsByDate, goalForDate);

        // 윈도우 내 과거(today-1 .. today-(WINDOW_DAYS-1)) 중 부채가 1분 이상인 날만, 최근이 먼저 오도록.
        // 1분 미만 부채는 분 단위 목표를 사후에 올려 생긴 "0분 부족" 잔재라 제외한다(MIN_MISSED_DEBT_SECONDS).
        List<DayDebt> missed = new ArrayList<>();
        for (int offset = 1; offset < WINDOW_DAYS; offset++) {
            LocalDate date = today.minusDays(offset);
            long debt = debtOn(date, secondsByDate, goalForDate);
            if (debt >= MIN_MISSED_DEBT_SECONDS) {
                missed.add(new DayDebt(date, debt));
            }
        }
        return new WeeklyDebt(todayDebt, List.copyOf(missed));
    }

    /** 하루 부채 = max(0, 그날 목표 − 그날 읽은 초). 맵에 없는 날은 0 읽음으로 본다. */
    private static long debtOn(LocalDate date, Map<LocalDate, Long> secondsByDate, ToLongFunction<LocalDate> goalForDate) {
        long read = secondsByDate.getOrDefault(date, 0L);
        return Math.max(0L, goalForDate.applyAsLong(date) - read);
    }
}
