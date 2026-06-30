package com.booktimer.web.api;

import com.booktimer.garden.*;

import java.util.List;
import java.util.Map;

/**
 * GET /api/garden 응답 전용 DTO.
 *
 * <p>건물(BUILDING)축은 마을 컨셉 전환(작가 꾸미기 피벗)으로 은퇴됨 — 작가(AUTHOR)축만 포함.
 * 배치/편집 엔진 제거(PR-2): {@code placed}·{@code owned} 필드가 사라졌다 — 보기 전용 마을은
 * 좌표 저장이 없다. 응답엔 world·nickname·catalog·characters·foodBalance만 남는다.
 * 먹이주기 루프: {@code foodBalance}(top-level), 각 작가 DTO에 {@code affection}.
 */
public record GardenApiResponse(
        WorldDto world,
        String nickname,
        int foodBalance,
        CatalogDto catalog,
        List<OwnedCharacterDto> characters
) {

    public record WorldDto(int width, int height) {}

    public record CatalogDto(
            List<AuthorCharacterDto> authorCharacters,
            int ownedAuthorCharacterCount,
            int totalAuthorCharacterCount,
            List<OwnedCharacterDto> ownedCharacters
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
                        .toList());
    }

    /**
     * 마을 전체 응답 조립 — 먹이주기 루프 정보 포함.
     *
     * @param foodBalance          현재 먹이 잔액(달성일 − 먹인합)
     * @param affectionByCharacter code → feed_count 맵(없는 코드는 0)
     */
    static GardenApiResponse of(
            GardenView view,
            int worldWidth,
            int worldHeight,
            String nickname,
            int foodBalance,
            Map<String, Integer> affectionByCharacter
    ) {
        List<OwnedCharacterDto> characters = view.ownedCharacters().stream()
                .map(c -> OwnedCharacterDto.from(c, affectionByCharacter.getOrDefault(c.code(), 0)))
                .toList();
        return new GardenApiResponse(
                new WorldDto(worldWidth, worldHeight),
                nickname,
                foodBalance,
                new CatalogDto(
                        view.authorCharacters().stream()
                                .map(s -> AuthorCharacterDto.from(s,
                                        affectionByCharacter.getOrDefault(s.character().getCode(), 0)))
                                .toList(),
                        view.ownedAuthorCharacterCount(), view.totalAuthorCharacterCount(),
                        characters),
                characters);
    }
}
