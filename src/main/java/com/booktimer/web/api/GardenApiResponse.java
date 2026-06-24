package com.booktimer.web.api;

import com.booktimer.garden.*;

import java.util.List;
import java.util.Map;

/**
 * GET /api/garden 응답 전용 DTO.
 *
 * <p>식물 4축(TIME·GENRE·DIVERSITY·RECIPE)·소품(Decoration)은 마을 컨셉 전환으로 제거됨.
 * 작가(AUTHOR)·건물(BUILDING) 2축만 포함.
 * 먹이주기 루프 추가: {@code foodBalance}(top-level), 각 작가 DTO에 {@code affection}.
 */
public record GardenApiResponse(
        WorldDto world,
        String nickname,
        int foodBalance,
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
            String matchName, int affection, int level, String title
    ) {
        static AuthorCharacterDto from(AuthorCharacterState s, int affection) {
            AffectionLevel al = AffectionLevel.of(affection);
            return new AuthorCharacterDto(
                    s.character().getCode(), s.character().getEmoji(), s.character().getName(),
                    s.character().getSpriteId(), s.owned(), s.character().getMatchName(),
                    affection, al.level(), al.title());
        }
    }

    public record OwnedCharacterDto(
            String code, String emoji, String name, String spriteId, int affection, int level, String title
    ) {
        static OwnedCharacterDto from(OwnedCharacter c, int affection) {
            AffectionLevel al = AffectionLevel.of(affection);
            return new OwnedCharacterDto(c.code(), c.emoji(), c.name(), c.spriteId(), affection, al.level(), al.title());
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
                    s.building().getCode(), s.building().getEmoji(), s.building().getName(),
                    s.building().getSpriteId(), s.owned(),
                    s.building().getMatchName(), s.building().getThresholdCount());
        }
    }

    /**
     * 대시보드 패널용 — 도감({@link CatalogDto})만 추출. affection은 0 기본(대시보드에서 불필요).
     */
    public static CatalogDto catalogOf(GardenView view) {
        return new CatalogDto(
                view.authorCharacters().stream()
                        .map(s -> AuthorCharacterDto.from(s, 0))
                        .toList(),
                view.ownedAuthorCharacterCount(), view.totalAuthorCharacterCount(),
                view.ownedCharacters().stream()
                        .map(c -> OwnedCharacterDto.from(c, 0))
                        .toList(),
                view.buildings().stream().map(BuildingDto::from).toList(),
                view.ownedBuildingCount(), view.totalBuildingCount());
    }

    /**
     * 마을 전체 응답 조립 — 먹이주기 루프 정보 포함.
     *
     * @param foodBalance          현재 먹이 잔액(달성일 − 먹인합)
     * @param affectionByCharacter code → feed_count 맵(없는 코드는 0)
     */
    static GardenApiResponse of(
            GardenView view,
            List<PlacedItem> placed,
            int worldWidth,
            int worldHeight,
            String nickname,
            int foodBalance,
            Map<String, Integer> affectionByCharacter
    ) {
        return new GardenApiResponse(
                new WorldDto(worldWidth, worldHeight),
                nickname,
                foodBalance,
                placed,
                new CatalogDto(
                        view.authorCharacters().stream()
                                .map(s -> AuthorCharacterDto.from(s,
                                        affectionByCharacter.getOrDefault(s.character().getCode(), 0)))
                                .toList(),
                        view.ownedAuthorCharacterCount(), view.totalAuthorCharacterCount(),
                        view.ownedCharacters().stream()
                                .map(c -> OwnedCharacterDto.from(c,
                                        affectionByCharacter.getOrDefault(c.code(), 0)))
                                .toList(),
                        view.buildings().stream().map(BuildingDto::from).toList(),
                        view.ownedBuildingCount(), view.totalBuildingCount()),
                view.ownedPlants().stream().map(OwnedPlantItemDto::from).toList(),
                view.ownedCharacters().stream()
                        .map(c -> OwnedCharacterDto.from(c,
                                affectionByCharacter.getOrDefault(c.code(), 0)))
                        .toList());
    }
}
