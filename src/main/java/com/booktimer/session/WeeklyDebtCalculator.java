package com.booktimer.session;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToLongFunction;

/**
 * per-day 부채 순수 계산 로직 (DB·시간 무관).
 *
 * <p>부채는 날짜별 독립이다 — 하루 부채 = {@code max(0, 하루목표 − 그날 읽은 초)}. 목표를 초과해 읽으면
 * 그 초과분이 <b>자기보다 오래된 날</b>의 빚을 갚는다(backward-only 재분배 — 선납은 불가).
 *
 * <p><b>계산 범위(창)는 넘겨받은 {@code goalByDate} 맵이 정한다</b> — 맵의 최소 날짜부터 기준일까지.
 * 옛 모델의 "최근 7일만 세고 그 이전은 자동 소멸"은 폐지됐다(2026-08-14): 부채는 계속 누적되고, 지우는
 * 수단은 <b>더 읽어서 갚기</b>와 <b>리워드 광고 용서</b>({@code waivedDates})다. 창의 시작을 어디로 둘지는
 * 도메인 판단이라 계산기가 아니라 {@link ReadingDebtService}가 정한다(가입일·누적 시작일 하한).
 *
 * <p>"오늘"은 서버 UTC가 아니라 유저 타임존 자정 경계로 정해져야 하므로(N-010) 호출자가 결정해 넘긴다.
 * 그날 읽은 초는 완료 세션을 유저 타임존 일자로 묶은 값({@link ReadingHistoryService})에서 온다.
 *
 * <p>진단·관찰성: {@link #computeTrace}가 날짜별 원시 값과 재분배 상세를 {@link WeeklyDebtTrace}로 반환한다.
 * 기존 {@link #compute} 오버로드들은 {@code computeTrace(...).toWeeklyDebt()}로 유도되어 단일 출처를 보장한다.
 */
public final class WeeklyDebtCalculator {

    /**
     * 평면 목표 편의 오버로드({@link #compute(Map, long, LocalDate)})가 쓰는 창 길이(일) — 오늘 포함 7일.
     *
     * <p><b>프로덕션 부채 계산의 창이 아니다.</b> 실제 창은 {@code goalByDate} 맵이 정하고, 맵을 만드는
     * {@link ReadingDebtService}가 시작일을 결정한다. 이 값은 (a) 날짜별 목표가 필요 없는 테스트 편의
     * 오버로드와 (b) 목표 이력이 아예 없는 레거시 사용자의 폴백 창으로만 남았다.
     */
    public static final int WINDOW_DAYS = 7;

    /**
     * "빠뜨린 날"로 칠 최소 부채(초) — 1분. 하루 부채가 이 값 미만이면 빠뜨린 날 목록에서 제외한다.
     *
     * <p>왜: 하루 목표는 항상 <b>분 단위</b>로 입력되는데(SettingsForm·OnboardingForm의 {@code incrementMinutes}),
     * 부채는 그날 유효했던 목표로 유도하지만 목표 이력이 없으면 현재 평면 목표를 창 전체에 적용한다.
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
     * @return 오늘 부채 + 창 내 과거 빠뜨린 날(부채 1분 이상, 최근 먼저) — 1분 미만은 용서({@link #MIN_MISSED_DEBT_SECONDS})
     */
    public static WeeklyDebt compute(Map<LocalDate, Long> secondsByDate, long dailyGoalSeconds, LocalDate today) {
        return computeTraceInternal(secondsByDate, date -> dailyGoalSeconds, Set.of(),
                today.minusDays(WINDOW_DAYS - 1L), today).toWeeklyDebt();
    }

    /**
     * 윈도우 내 날짜별 읽은 양과 <b>날짜별 목표</b>(per-day)로 {@link WeeklyDebt}를 만든다.
     *
     * <p>각 날을 <b>그날 유효했던 목표</b>로 판정한다 — 사용자가 목표를 올려도 옛 목표를 채운 과거 날이
     * 빠뜨린 날로 둔갑하지 않는다(소급 함정 차단, N-059). 목표 이력은 {@link com.booktimer.timer.GoalSchedule}가
     * 날짜별로 풀어 준다.
     *
     * @param secondsByDate 날짜→그날 읽은 초. 키가 없는 날은 0으로 본다.
     * @param goalByDate    날짜→그날 목표(초). <b>이 맵이 계산 창을 정한다</b> — 최소 날짜부터 {@code today}까지.
     *                      중간에 빈 날은 0(=부채 없음)으로 본다.
     * @param today 유저 타임존 기준 오늘
     */
    public static WeeklyDebt compute(Map<LocalDate, Long> secondsByDate, Map<LocalDate, Long> goalByDate, LocalDate today) {
        return computeTrace(secondsByDate, goalByDate, today).toWeeklyDebt();
    }

    /**
     * 날짜별 목표로 {@link WeeklyDebtTrace}를 만든다 — 원시 부채/초과·재분배 상세까지 포함.
     *
     * <p>기존 {@link #compute(Map, Map, LocalDate)}와 동일 경로를 거쳐 {@link WeeklyDebt}를 유도할 수 있어
     * 진단 뷰와 라이브 대시보드가 항상 동일 값을 보인다.
     *
     * @param secondsByDate 날짜→그날 읽은 초
     * @param goalByDate    날짜→그날 목표(초). 없는 날은 0
     * @param today         기준일(유저 타임존 오늘 or as-of 날짜)
     */
    public static WeeklyDebtTrace computeTrace(Map<LocalDate, Long> secondsByDate,
                                               Map<LocalDate, Long> goalByDate,
                                               LocalDate today) {
        return computeTrace(secondsByDate, goalByDate, Set.of(), today);
    }

    /**
     * 용서된 날짜({@code waivedDates})까지 반영해 {@link WeeklyDebtTrace}를 만든다 — 리워드 광고 보상 경로.
     *
     * <p>용서는 그 날의 <b>잔여 부채만</b> 0으로 만든다(기존 1분 미만 용서와 같은 자리·같은 결).
     * 그날의 초과분({@code rawSurplus})은 건드리지 않아 backward 뱅킹은 불변이고, 오늘은 대상이 아니다
     * — 진행 중인 오늘을 광고로 지우는 건 습관 형성과 정면 충돌하므로 set에 오늘이 섞여 와도 무시한다.
     *
     * @param waivedDates 용서된 날짜 집합({@link ReadingGoalWaiver}). 창 밖·오늘은 무해하게 무시된다
     */
    public static WeeklyDebtTrace computeTrace(Map<LocalDate, Long> secondsByDate,
                                               Map<LocalDate, Long> goalByDate,
                                               Set<LocalDate> waivedDates,
                                               LocalDate today) {
        return computeTraceInternal(secondsByDate, date -> goalByDate.getOrDefault(date, 0L), waivedDates,
                windowStart(goalByDate, today), today);
    }

    /**
     * 창 시작일 = 목표 맵의 최소 날짜. 맵이 비었거나 최소 날짜가 기준일보다 미래면(가입 당일·미래 목표만
     * 있는 경우) 기준일 하루로 좁힌다 — 창이 비면 {@code toWeeklyDebt()}가 "오늘"을 못 찾아 터진다.
     */
    private static LocalDate windowStart(Map<LocalDate, Long> goalByDate, LocalDate today) {
        LocalDate min = goalByDate.keySet().stream().min(LocalDate::compareTo).orElse(today);
        return min.isAfter(today) ? today : min;
    }

    /**
     * trace 생성 코어. backward-only 재분배 포함.
     *
     * <p>창 날짜별로 원시 부채/초과분을 계산한 뒤, 초과분이 자기보다 오래된 날의 부채를
     * 오래된 것부터 갚는다. 오늘(가장 나중)의 부채는 갚아 줄 나중 날이 없어 항상 원시값 —
     * 선납 불가, 즉 어제 많이 읽어도 오늘 카운트다운은 줄지 않는다.
     */
    private static WeeklyDebtTrace computeTraceInternal(Map<LocalDate, Long> secondsByDate,
                                                        ToLongFunction<LocalDate> goalForDate,
                                                        Set<LocalDate> waivedDates,
                                                        LocalDate windowStart,
                                                        LocalDate today) {
        // window[0] = 창 시작일, window[n-1] = today. 창 길이는 부채 누적 시작일부터 하루씩 자란다.
        int n = (int) ChronoUnit.DAYS.between(windowStart, today) + 1;
        LocalDate[] window = new LocalDate[n];
        long[] remaining = new long[n];   // 재분배 후 잔여 부채
        long[] surplusLeft = new long[n]; // 아직 쓰지 않은 초과분
        long[] rawDeficit = new long[n];
        long[] rawSurplus = new long[n];
        long[] goalArr = new long[n];
        long[] readArr = new long[n];
        boolean[] forgiven = new boolean[n];
        boolean[] waived = new boolean[n];
        int todayIdx = n - 1;

        // 1) 날짜별 원시 부채·초과분. goal<=0(가입 전)이면 둘 다 0(가입 전 독서가 뱅킹에 안 낌).
        for (int i = 0; i < n; i++) {
            LocalDate d = windowStart.plusDays(i); // i=0 → 가장 오래됨
            window[i] = d;
            long goal = goalForDate.applyAsLong(d);
            long read = secondsByDate.getOrDefault(d, 0L);
            goalArr[i] = goal;
            readArr[i] = read;
            if (goal <= 0) {
                // rawDeficit, rawSurplus, remaining, surplusLeft 모두 0 (Java 기본값)
            } else {
                rawDeficit[i] = Math.max(0L, goal - read);
                rawSurplus[i] = Math.max(0L, read - goal);
                remaining[i] = rawDeficit[i];
                surplusLeft[i] = rawSurplus[i];
            }
            // 오늘 제외 과거 날의 1분 미만 부채는 목표 인상 반올림 잔재라 0 처리 — 초과분 낭비 방지.
            if (i < todayIdx && remaining[i] > 0 && remaining[i] < MIN_MISSED_DEBT_SECONDS) {
                forgiven[i] = true;
                remaining[i] = 0;
            }
            // 리워드 광고 보상으로 용서된 과거 날 — 부채만 소거하고 rawSurplus는 그대로 둔다(뱅킹 불변).
            // 오늘(i == todayIdx)은 제외: 진행 중인 오늘을 광고로 지우지 않는다.
            if (i < todayIdx && waivedDates.contains(d)) {
                waived[i] = true;
                remaining[i] = 0;
            }
        }

        // 2) backward-only 재분배: 오래된 부채(낮은 인덱스)를 나중 날(높은 인덱스) 초과분으로 갚는다.
        // ponytail: O(n²)이고 n=누적 경과일이라 매일 1 늘어난다(3년 ≈ 1100일 → long 산술 수백만 회, 수 ms).
        // 체감될 만큼 커지면 초과분 prefix-sum으로 O(n)으로 낮춘다.
        for (int i = 0; i < todayIdx; i++) {          // 과거 날만(오늘 제외), 오래된 것 먼저
            if (remaining[i] == 0) continue;
            for (int k = i + 1; k <= todayIdx; k++) { // i보다 나중 날(선납 불가)
                long pay = Math.min(surplusLeft[k], remaining[i]);
                remaining[i] -= pay;
                surplusLeft[k] -= pay;
                if (remaining[i] == 0) break;
            }
        }

        // 3) trace 조립. surplusConsumed = rawSurplus - surplusLeft(재분배에 쓴 양).
        List<DayDebtTrace> days = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            days.add(new DayDebtTrace(
                    window[i],
                    i == todayIdx,
                    goalArr[i],
                    readArr[i],
                    rawDeficit[i],
                    rawSurplus[i],
                    forgiven[i],
                    waived[i],
                    remaining[i],
                    rawSurplus[i] - surplusLeft[i]
            ));
        }
        return new WeeklyDebtTrace(List.copyOf(days));
    }
}
