package com.booktimer.garden;

/**
 * 배치 가능한 보유 식물 한 종 (팔레트·검증용 — 축 무관 단일 표현).
 *
 * <p>네 수집축({@link PlantState}·{@link GenrePlantState}·{@link DiversityPlantState}·{@link DiscoveredPlantState})은
 * 메타 구조가 제각각이라, 배치는 이들을 {@code (axis, code, emoji, name)} 하나로 평탄화해 다룬다. {@link GardenView#ownedPlants()}가
 * 보유분만 추려 이 형태로 내준다 — 팔레트 렌더의 풀이자 {@code GardenLayoutService.save}의 보유 검증 집합이다.
 *
 * @param axis  소속 수집축 (code가 축 간 비유니크라 식별의 절반, 설계 §1)
 * @param code  그 축 카탈로그의 식물 code
 * @param emoji 비주얼 이모지
 * @param name  표시 이름(한글)
 */
public record OwnedPlant(PlacementAxis axis, String code, String emoji, String name) {
}
