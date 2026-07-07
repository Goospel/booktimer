package com.booktimer.garden;

import java.util.ArrayList;
import java.util.List;

/**
 * 서재 뷰 모델 — 작가(AUTHOR) 캐릭터 배회 축.
 *
 * <p>건물(BUILDING)축은 작가 꾸미기 피벗으로 은퇴됨 — 식물 4축·소품에 이어 제거.
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
     * 배회용 보유 캐릭터 목록 — AUTHOR 축 전용.
     * 배치/편집 엔진(은퇴, PR-2) 제거로 좌표 저장 경로가 사라졌다 — AUTHOR 캐릭터는 이 목록으로만 노출된다.
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
