package com.booktimer.garden;

/** 아이소메트릭 투영 — 정규화 격자좌표 (x,y)∈[0,1]² → 정규화 화면좌표 (sx,sy)∈[0,1]². JS normToIso와 동일 공식.
 *  다이아몬드 꼭짓점: 위(0,0)=(0.5,0.25), 오른(1,0)=(1,0.5), 아래(1,1)=(0.5,0.75), 왼(0,1)=(0,0.5). */
public final class GardenIsoProjection {

    public static final double ISO_FLATTEN = 0.5;

    public static double screenX(double x, double y) {
        return 0.5 + (x - y) * 0.5;
    }

    public static double screenY(double x, double y) {
        return 0.5 + ((x + y) / 2 - 0.5) * ISO_FLATTEN;
    }

    public static double screenXPercent(double x, double y) {
        return screenX(x, y) * 100;
    }

    public static double screenYPercent(double x, double y) {
        return screenY(x, y) * 100;
    }

    private GardenIsoProjection() {}
}
