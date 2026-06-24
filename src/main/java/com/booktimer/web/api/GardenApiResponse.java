package com.booktimer.web.api;

import com.booktimer.garden.*;

import java.util.List;

/**
 * GET /api/garden 응답 전용 DTO.
 *
 * <p>식물 4축(TIME·GENRE·DIVERSITY·RECIPE)·소품(Decoration)은 마을 컨셉 전환으로 제거됨.
 * 작가(AUTHOR)·건물(BUILDING) 2축만 포함.
 */
public record GardenApiResponse(
        WorldDto world,
        String nickname,
        List<PlacedItem> placed,
        CatalogDto catalog,
        List<OwnedPlantItemDto> owned,
        List<OwnedCharacterDto> characters
) {

    public record WorldDto(int width, int height) {}

    public record CatalogDto(
            List<AuthorCharacterDto> authorCharacters,
            int ownedAuthorCharacterCount,
            int totalAuthorCharacterCount,
            List<OwnedCharacterDto> ownedCharacters,
            List<BuildingDto> buildings,
            int ownedBuildingCount,
            int totalBuildingCount
    ) {}

    public record AuthorCharacterDto(
            String code, String emoji, String name, String spriteId, boolean owned,
            String matchName
    ) {
        static AuthorCharacterDto from(AuthorCharacterState s) {
            return new AuthorCharacterDto(
                    s.character().getCode(), s.character().getEmoji(), s.character().getName(), s.character().getSpriteId(),
                    s.owned(),
                    s.character().getMatchName());
        }
    }

    public record OwnedCharacterDto(
            String code, String emoji, String name, String spriteId
    ) {
        static OwnedCharacterDto from(OwnedCharacter c) {
            return new OwnedCharacterDto(c.code(), c.emoji(), c.name(), c.spriteId());
        }
    }

    /** 게임 팔레트용 보유 건물 — axis 포함(GardenItemMeta 호환). AUTHOR 캐릭터 제외(characters 필드 분리). */
    public record OwnedPlantItemDto(String axis, String code, String emoji, String name, String spriteId) {
        static OwnedPlantItemDto from(OwnedPlant p) {
            return new OwnedPlantItemDto(
                    p.axis() != null ? p.axis().name() : null,
                    p.code(), p.emoji(), p.name(), p.spriteId());
        }
    }

    public record BuildingDto(
            String code, String emoji, String name, String spriteId, boolean owned,
            String matchName, int thresholdCount
    ) {
        static BuildingDto from(BuildingState s) {
            return new BuildingDto(
                    s.building().getCode(), s.building().getEmoji(), s.building().getName(), s.building().getSpriteId(),
                    s.owned(),
                    s.building().getMatchName(), s.building().getThresholdCount());
        }
    }

    /** 대시보드 패널용 — 도감({@link CatalogDto})만 추출한다. 마을 전용 필드(placed/world/…) 제외. */
    public static CatalogDto catalogOf(GardenView view) {
        return new CatalogDto(
                view.authorCharacters().stream().map(AuthorCharacterDto::from).toList(),
                view.ownedAuthorCharacterCount(), view.totalAuthorCharacterCount(),
                view.ownedCharacters().stream().map(OwnedCharacterDto::from).toList(),
                view.buildings().stream().map(BuildingDto::from).toList(),
                view.ownedBuildingCount(), view.totalBuildingCount());
    }

    static GardenApiResponse of(
            GardenView view,
            List<PlacedItem> placed,
            int worldWidth,
            int worldHeight,
            String nickname
    ) {
        return new GardenApiResponse(
                new WorldDto(worldWidth, worldHeight),
                nickname,
                placed,
                catalogOf(view),
                view.ownedPlants().stream().map(OwnedPlantItemDto::from).toList(),
                view.ownedCharacters().stream().map(OwnedCharacterDto::from).toList());
    }
}
