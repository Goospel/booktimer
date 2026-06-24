package com.booktimer.garden;

import java.util.ArrayList;
import java.util.List;

/**
 * 독서 마을 뷰 모델 — 작가(AUTHOR) 캐릭터 배회 + 건물(BUILDING) 배치 2축만.
 *
 * <p>식물 4축(TIME·GENRE·DIVERSITY·RECIPE)·소품(Decoration)은 마을 컨셉 전환으로 제거됨.
 * DB 테이블은 보존(재매핑 후속), Java 코드 레이어만 소프트 제거.
 *
 * @param authorCharacters           작가 캐릭터 카탈로그 전체(진열 순서, 보유·미보유 모두)
 * @param ownedAuthorCharacterCount  작가 캐릭터 보유 종 수
 * @param totalAuthorCharacterCount  작가 캐릭터 전체 종 수
 * @param buildings                  출판사 건물 카탈로그 전체(진열 순서, 보유·미보유 모두)
 * @param ownedBuildingCount         건물 보유 종 수
 * @param totalBuildingCount         건물 전체 종 수
 */
public record GardenView(
        List<AuthorCharacterState> authorCharacters,
        int ownedAuthorCharacterCount,
        int totalAuthorCharacterCount,
        List<BuildingState> buildings,
        int ownedBuildingCount,
        int totalBuildingCount) {

    /**
     * 배치 가능한 보유 오브젝트 — BUILDING 축만. AUTHOR는 배회 전용이라 제외.
     * 이 결과가 팔레트 풀이자 {@link GardenLayoutService#save}의 보유 검증 집합으로 쓰인다(설계 §4).
     */
    public List<OwnedPlant> ownedPlants() {
        List<OwnedPlant> owned = new ArrayList<>();
        for (BuildingState s : buildings) {
            if (s.owned()) {
                owned.add(new OwnedPlant(PlacementAxis.BUILDING, s.building().getCode(),
                        s.building().getEmoji(), s.building().getName(), s.building().getSpriteId()));
            }
        }
        return owned;
    }

    /**
     * 배회용 보유 캐릭터 목록 — AUTHOR 축 전용.
     * AUTHOR 캐릭터는 ownedPlants()·팔레트·좌표 저장 경로에서 완전히 분리된다(C2 풀어놓기).
     */
    public List<OwnedCharacter> ownedCharacters() {
        List<OwnedCharacter> chars = new ArrayList<>();
        for (AuthorCharacterState s : authorCharacters) {
            if (s.owned()) {
                chars.add(new OwnedCharacter(s.character().getCode(),
                        s.character().getEmoji(), s.character().getName(), s.character().getSpriteId()));
            }
        }
        return chars;
    }
}
