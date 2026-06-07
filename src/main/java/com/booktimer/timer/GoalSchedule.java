package com.booktimer.timer;

import java.time.LocalDate;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

/**
 * 목표 변경 이력을 "그날 유효했던 하루 목표"로 해석하는 순수 리졸버 (DB·시간 무관).
 *
 * <p>부채는 {@code max(0, 하루목표 − 그날 읽은 초)}로 유도되는데, 하루 목표를 <b>현재 평면값 하나</b>로
 * 잡으면 사용자가 목표를 올렸을 때 옛 목표를 채웠던 과거 날이 새 목표로 재판정돼 빠뜨린 날로 둔갑한다
 * (소급 함정 — N-059). 이를 막으려 목표가 바뀐 시점들({@link ReadingGoalChange})을 받아, 어떤 날짜든
 * <b>그 날짜 이하 가장 최근 변경값</b>(floorEntry)으로 그날의 목표를 돌려준다.
 *
 * <p>첫 변경보다 이른 날짜(레거시·미온보딩)는 폴백 목표를 쓴다 — 호출자가 현재 타이머 목표/기본값을 넘긴다.
 */
public final class GoalSchedule {

    private final NavigableMap<LocalDate, Long> changesByDate;
    private final long fallbackGoalSeconds;

    private GoalSchedule(NavigableMap<LocalDate, Long> changesByDate, long fallbackGoalSeconds) {
        this.changesByDate = changesByDate;
        this.fallbackGoalSeconds = fallbackGoalSeconds;
    }

    /**
     * @param changesByDate       effective_date → goal_seconds (입력 순서 무관 — 내부에서 날짜순 정렬)
     * @param fallbackGoalSeconds 첫 변경 이전 날짜에 쓸 목표(현재 타이머 목표/기본값)
     */
    public static GoalSchedule of(Map<LocalDate, Long> changesByDate, long fallbackGoalSeconds) {
        return new GoalSchedule(new TreeMap<>(changesByDate), fallbackGoalSeconds);
    }

    /** {@code date} 그날 유효했던 하루 목표(초). date 이하 변경이 없으면 폴백. */
    public long goalFor(LocalDate date) {
        Map.Entry<LocalDate, Long> entry = changesByDate.floorEntry(date);
        return entry != null ? entry.getValue() : fallbackGoalSeconds;
    }

    /**
     * 가장 이른 변경일(=사용자가 목표를 처음 가진 시점, baseline). 이력이 비면 {@link Optional#empty()}.
     *
     * <p>이보다 이른 날은 "사용자가 아직 시작하기 전"이라 빠뜨린 날로 칠 수 없다 — {@link #goalFor}가
     * 폴백(현재 목표)을 돌려주더라도 그건 그 옛 날짜의 진짜 목표가 아니다. 호출자가 이 경계로 윈도우의
     * 가입 전 날짜를 판정에서 제외한다(입문자가 시작 전 날을 "못 지킴"으로 보지 않게).
     */
    public Optional<LocalDate> earliestEffectiveDate() {
        return changesByDate.isEmpty() ? Optional.empty() : Optional.of(changesByDate.firstKey());
    }
}
