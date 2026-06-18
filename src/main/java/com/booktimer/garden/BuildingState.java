package com.booktimer.garden;

/**
 * 출판사 건물 도감 한 칸의 보유 상태 — 카탈로그 메타 + 보유 여부.
 *
 * @param building 카탈로그 건물(matchName·이름·이모지·임계 등 불변 메타)
 * @param owned    보유 여부 — matchName 정규화 기준으로 완독 권수 합이 thresholdCount 이상이면 true
 */
public record BuildingState(Building building, boolean owned) {
}
