package com.booktimer.session;

import java.util.List;

/**
 * 독서 잔디(컨트리뷰션 그래프) 화면 모델.
 *
 * <p>GitHub 잔디와 동일한 배치 — <b>열=주(週)</b>, <b>행=요일(일요일 top → 토요일 bottom)</b>.
 * 가장 오래된 주가 왼쪽, 오늘이 속한 주가 맨 오른쪽이다. 각 주는 7칸({@link ContributionDay})으로,
 * 미래 날짜 등 범위 밖은 placeholder(빈 칸)다.
 *
 * @param weeks       주 단위 열 목록. 각 원소는 7칸(일~토).
 * @param monthLabels 상단 월 라벨(열 인덱스 + 라벨)
 * @param totalSeconds 그래프 범위의 총 독서 시간(초)
 * @param activeDays  독서한 날 수(초 &gt; 0)
 */
public record ContributionGraph(
        List<List<ContributionDay>> weeks,
        List<MonthLabel> monthLabels,
        long totalSeconds,
        int activeDays) {

    /**
     * 상단 월 라벨 — 해당 월이 처음 등장하는 주 열 위에 표시.
     *
     * @param weekIndex {@link #weeks}의 열 인덱스
     * @param label     표시 문자열(예: "6월")
     */
    public record MonthLabel(int weekIndex, String label) {
    }
}
