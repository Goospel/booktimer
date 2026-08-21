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
 * @param currentStreak 오늘 기준 현재 연속 독서 일수(성장 잔디용). 끊기면 0.
 */
public record ContributionGraph(
        List<List<ContributionDay>> weeks,
        List<MonthLabel> monthLabels,
        long totalSeconds,
        int activeDays,
        int currentStreak) {

    /** 현재 연속 일수에 대응하는 성장 단계(땅/새싹/꽃/나무). 템플릿이 우측 상단 뱃지로 보여준다. */
    public GrowthStage growthStage() {
        return GrowthStage.of(currentStreak);
    }

    /**
     * 다음 단계까지 남은 연속 일수 — 최고 단계(나무)면 0.
     *
     * <p>화면이 「N일 더 읽으면 …이 돼요」로 그린다. 단계 이름만 보여 주던 자리에 <b>계속 읽을 이유</b>를
     * 남기는 값이라, 사다리를 아는 서버가 계산해 내려 준다(클라가 임계값을 베끼면 두 곳이 따로 늙는다).
     */
    public int daysToNextStage() {
        GrowthStage next = growthStage().next();
        return next == null ? 0 : Math.max(0, next.minStreak() - Math.max(0, currentStreak));
    }

    /** 현재 단계 안에서 얼마나 왔는지(0~100). 최고 단계면 100 — 막대가 빈 채로 남지 않는다. */
    public int growthProgressPercent() {
        GrowthStage stage = growthStage();
        GrowthStage next = stage.next();
        if (next == null) {
            return 100;
        }
        int span = next.minStreak() - stage.minStreak();
        int done = Math.max(0, currentStreak) - stage.minStreak();
        return Math.round(done * 100f / span);
    }

    /**
     * 상단 월 라벨 — 해당 월이 처음 등장하는 주 열 위에 표시.
     *
     * @param weekIndex {@link #weeks}의 열 인덱스
     * @param label     표시 문자열(예: "6월")
     */
    public record MonthLabel(int weekIndex, String label) {
    }
}
