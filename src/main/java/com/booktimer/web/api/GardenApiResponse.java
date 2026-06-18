package com.booktimer.web.api;

import com.booktimer.garden.*;

import java.time.LocalDate;
import java.util.List;

/**
 * GET /api/garden 응답 전용 DTO.
 *
 * <p>GardenView·PlacedItem 등이 JPA 엔티티(Plant·AuthorCharacter…)를 포함하므로
 * 직접 직렬화하면 lazy 로딩·순환참조·내부 필드 노출 위험이 있다. 이 record는 필요 필드만
 * 명시 매핑해 Jackson 직렬화를 안전하게 만든다. 엔티티 직접 직렬화 금지.
 */
public record GardenApiResponse(
        WorldDto world,
        String nickname,
        List<PlacedItem> placed,
        List<DecorationOption> decorationCatalog,
        CatalogDto catalog,
        List<OwnedPlantItemDto> owned,
        List<OwnedCharacterDto> characters
) {

    public record WorldDto(int width, int height) {}

    public record CatalogDto(
            List<PlantDto> plants,
            int ownedCount,
            int totalCount,
            int achievedDays,
            Integer daysToNextUnlock,
            String nextPlantName,
            List<GenrePlantDto> genrePlants,
            int ownedGenreCount,
            int totalGenreCount,
            List<DiversityPlantDto> diversityPlants,
            int ownedAuthorCount,
            int totalAuthorCount,
            List<RecipePlantDto> recipePlants,
            int mysterySlotCount,
            List<String> justDiscovered,
            List<AuthorCharacterDto> authorCharacters,
            int ownedAuthorCharacterCount,
            int totalAuthorCharacterCount,
            List<OwnedCharacterDto> ownedCharacters,
            List<BuildingDto> buildings,
            int ownedBuildingCount,
            int totalBuildingCount
    ) {}

    public record PlantDto(
            String code, String emoji, String name, String spriteId,
            boolean owned, LocalDate unlockedOn, boolean isNew
    ) {
        static PlantDto from(PlantState s) {
            return new PlantDto(
                    s.plant().getCode(), s.plant().getEmoji(), s.plant().getName(), s.plant().getSpriteId(),
                    s.owned(), s.unlockedOn(), s.isNew());
        }
    }

    public record GenrePlantDto(
            String code, String emoji, String name, String spriteId, boolean owned
    ) {
        static GenrePlantDto from(GenrePlantState s) {
            return new GenrePlantDto(
                    s.genrePlant().getCode(), s.genrePlant().getEmoji(), s.genrePlant().getName(), s.genrePlant().getSpriteId(),
                    s.owned());
        }
    }

    public record DiversityPlantDto(
            String code, String emoji, String name, String spriteId, boolean owned
    ) {
        static DiversityPlantDto from(DiversityPlantState s) {
            return new DiversityPlantDto(
                    s.plant().getCode(), s.plant().getEmoji(), s.plant().getName(), s.plant().getSpriteId(),
                    s.owned());
        }
    }

    public record RecipePlantDto(
            String code, String emoji, String name, String spriteId, boolean discovered
    ) {
        static RecipePlantDto from(DiscoveredPlantState s) {
            return new RecipePlantDto(
                    s.recipePlant().getCode(), s.recipePlant().getEmoji(), s.recipePlant().getName(), s.recipePlant().getSpriteId(),
                    s.discovered());
        }
    }

    public record AuthorCharacterDto(
            String code, String emoji, String name, String spriteId, boolean owned
    ) {
        static AuthorCharacterDto from(AuthorCharacterState s) {
            return new AuthorCharacterDto(
                    s.character().getCode(), s.character().getEmoji(), s.character().getName(), s.character().getSpriteId(),
                    s.owned());
        }
    }

    public record OwnedCharacterDto(
            String code, String emoji, String name, String spriteId
    ) {
        static OwnedCharacterDto from(OwnedCharacter c) {
            return new OwnedCharacterDto(c.code(), c.emoji(), c.name(), c.spriteId());
        }
    }

    /** 게임 팔레트용 보유 식물 — axis 포함(GardenItemMeta 호환). AUTHOR 캐릭터 제외(characters 필드 분리). */
    public record OwnedPlantItemDto(String axis, String code, String emoji, String name, String spriteId) {
        static OwnedPlantItemDto from(OwnedPlant p) {
            return new OwnedPlantItemDto(
                    p.axis() != null ? p.axis().name() : null,
                    p.code(), p.emoji(), p.name(), p.spriteId());
        }
    }

    public record BuildingDto(
            String code, String emoji, String name, String spriteId, boolean owned
    ) {
        static BuildingDto from(BuildingState s) {
            return new BuildingDto(
                    s.building().getCode(), s.building().getEmoji(), s.building().getName(), s.building().getSpriteId(),
                    s.owned());
        }
    }

    static GardenApiResponse of(
            GardenView view,
            List<PlacedItem> placed,
            List<DecorationOption> decorationCatalog,
            int worldWidth,
            int worldHeight,
            String nickname
    ) {
        return new GardenApiResponse(
                new WorldDto(worldWidth, worldHeight),
                nickname,
                placed,
                decorationCatalog,
                new CatalogDto(
                        view.plants().stream().map(PlantDto::from).toList(),
                        view.ownedCount(),
                        view.totalCount(),
                        view.achievedDays(),
                        view.daysToNextUnlock(),
                        view.nextPlantName(),
                        view.genrePlants().stream().map(GenrePlantDto::from).toList(),
                        view.ownedGenreCount(),
                        view.totalGenreCount(),
                        view.diversityPlants().stream().map(DiversityPlantDto::from).toList(),
                        view.ownedAuthorCount(),
                        view.totalAuthorCount(),
                        view.recipePlants().stream().map(RecipePlantDto::from).toList(),
                        view.mysterySlotCount(),
                        view.justDiscovered(),
                        view.authorCharacters().stream().map(AuthorCharacterDto::from).toList(),
                        view.ownedAuthorCharacterCount(),
                        view.totalAuthorCharacterCount(),
                        view.ownedCharacters().stream().map(OwnedCharacterDto::from).toList(),
                        view.buildings().stream().map(BuildingDto::from).toList(),
                        view.ownedBuildingCount(),
                        view.totalBuildingCount()),
                view.ownedPlants().stream().map(OwnedPlantItemDto::from).toList(),
                view.ownedCharacters().stream().map(OwnedCharacterDto::from).toList());
    }
}
