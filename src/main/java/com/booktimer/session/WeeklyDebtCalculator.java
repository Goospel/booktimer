package com.booktimer.session;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    private WeeklyDebtCalculator() {
    }

    /**
     * 윈도우 내 날짜별 읽은 양과 하루 목표로 {@link WeeklyDebt}를 만든다.
     *
     * @param secondsByDate 날짜→그날 읽은 초(완료 세션 합). 키가 없는 날은 0으로 본다.
     * @param dailyGoalSeconds 하루 목표(초, 0 이상)
     * @param today 유저 타임존 기준 오늘
     * @return 오늘 부채 + 윈도우 내 과거 빠뜨린 날(최근 먼저)
     */
    public static WeeklyDebt compute(Map<LocalDate, Long> secondsByDate, long dailyGoalSeconds, LocalDate today) {
        long todayDebt = debtOn(today, secondsByDate, dailyGoalSeconds);

        // 윈도우 내 과거(today-1 .. today-(WINDOW_DAYS-1)) 중 부채>0인 날만, 최근이 먼저 오도록.
        List<DayDebt> missed = new ArrayList<>();
        for (int offset = 1; offset < WINDOW_DAYS; offset++) {
            LocalDate date = today.minusDays(offset);
            long debt = debtOn(date, secondsByDate, dailyGoalSeconds);
            if (debt > 0) {
                missed.add(new DayDebt(date, debt));
            }
        }
        return new WeeklyDebt(todayDebt, List.copyOf(missed));
    }

    /** 하루 부채 = max(0, 목표 − 그날 읽은 초). 맵에 없는 날은 0 읽음으로 본다. */
    private static long debtOn(LocalDate date, Map<LocalDate, Long> secondsByDate, long dailyGoalSeconds) {
        long read = secondsByDate.getOrDefault(date, 0L);
        return Math.max(0L, dailyGoalSeconds - read);
    }
}
