package com.booktimer.garden;

/**
 * 독서 마을 월드 좌표계 상수 — 프론트 좌표 변환·카메라 핏의 기준 종횡비(5:4).
 *
 * <p>배치/편집 엔진(은퇴, PR-2) 제거 전엔 {@code GardenLayoutService}가 이 상수를 보유했으나,
 * 보기 전용 마을에서도 월드 크기는 {@code /api/garden} 응답의 {@code world}로 클라이언트에 전달되어야
 * 하므로(좌표계 단일 출처) 엔진과 무관한 이 holder로 분리했다.
 */
public final class GardenWorld {

    /** 정원 월드 가로 픽셀(고정 종횡비 — 프론트 좌표 변환·카메라 핏 기준). */
    public static final int WORLD_WIDTH = 1000;
    /** 정원 월드 세로 픽셀(고정 종횡비 5:4). */
    public static final int WORLD_HEIGHT = 800;

    private GardenWorld() {
    }
}
