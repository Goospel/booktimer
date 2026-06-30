package com.booktimer.garden;

import java.util.ArrayList;
import java.util.List;

/**
 * 독서 마을 뷰 모델 — 작가(AUTHOR) 캐릭터 배회 축.
 *
 * <p>건물(BUILDING)축은 마을 컨셉 전환(작가 꾸미기 피벗)으로 은퇴됨 — 식물 4축·소품에 이어 제거.
 * DB 테이블은 보존(소프트 제거).
 *
 * @param authorCharacters           작가 캐릭터 카탈로그 전체(진열 순서, 보유·미보유 모두)
 * @param ownedAuthorCharacterCount  작가 캐릭터 보유 종 수
 * @param totalAuthorCharacterCount  작가 캐릭터 전체 종 수
 */
public record GardenView(
        List<AuthorCharacterState> authorCharacters,
        int ownedAuthorCharacterCount,
        int totalAuthorCharacterCount) {

    /**
     * 배치 가능한 보유 오브젝트 — 건물 은퇴로 항상 빈 목록.
     * 배치/편집 엔진({@link GardenLayoutService})이 보유 검증 집합으로 계속 호출하므로 메서드는 유지한다
     * (배회 전용 AUTHOR는 애초 제외). 엔진 자체 제거는 후속(PR-2).
     */
    public List<OwnedPlant> ownedPlants() {
        return List.of();
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
