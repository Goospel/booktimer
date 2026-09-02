package com.booktimer.session;

import java.util.List;

/**
 * 독서 잔디(컨트리뷰션 그래프) 화면 모델.
 *
 * <p>GitHub 잔디와 같은 배치 — <b>열=주(週)</b>, <b>행=요일(일요일 top → 토요일 bottom)</b>. 다만 열 순서는
 * <b>뒤집혀 있다</b>: {@code weeks[0]}이 <b>오늘이 속한 최신 주(왼쪽)</b>이고 가장 오래된 주가 맨 오른쪽이다
 * ({@link ContributionGraphBuilder}가 좁은 화면에서 최근 날짜가 먼저 보이도록 뒤집는다 — 소비자는
 * oldest-first로 가정하면 안 된다). 각 주는 7칸({@link ContributionDay})으로, 미래 날짜 등 범위 밖은
 * placeholder(빈 칸)다.
 *
 * @param weeks       주 단위 열 목록. 각 원소는 7칸(일~토).
 * @param monthLabels 상단 월 라벨(열 인덱스 + 라벨)
 * @param totalSeconds 그래프 범위의 총 독서 시간(초)
 * @param activeDays  독서한 날 수(초 &gt; 0)
 * @param currentStreak 오늘 기준 현재 연속 독서 일수. 끊기면 0.
 */
public record ContributionGraph(
        List<List<ContributionDay>> weeks,
        List<MonthLabel> monthLabels,
        long totalSeconds,
        int activeDays,
        int currentStreak) {

    /**
     * 상단 월 라벨 — 해당 월이 처음 등장하는 주 열 위에 표시.
     *
     * @param weekIndex {@link #weeks}의 열 인덱스
     * @param label     표시 문자열(예: "6월")
     */
    public record MonthLabel(int weekIndex, String label) {
    }
}
