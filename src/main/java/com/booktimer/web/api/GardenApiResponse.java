package com.booktimer.web.api;

import com.booktimer.garden.*;

import java.util.List;
import java.util.Map;

/**
 * GET /api/garden 응답 전용 DTO.
 *
 * <p>건물(BUILDING)축은 작가 꾸미기 피벗으로 은퇴됨 — 작가(AUTHOR)축만 포함.
 * 배치/편집 엔진 제거(PR-2): {@code placed}·{@code owned} 필드가 사라졌다 — 보기 전용 서재는
 * 좌표 저장이 없다. 응답엔 nickname·foodBalance·catalog만 남는다.
 * 먹이주기 루프: {@code foodBalance}(top-level), 각 작가 DTO에 {@code affection}.
 *
 * <p><b>제거된 필드</b>(2026-08-15): {@code world}(월드 크기 — 좌표 배치가 사라져 프론트가 안 읽는다)와
 * top-level {@code characters}({@code catalog.ownedCharacters}와 <b>같은 리스트</b>였다). 소비처는
 * {@code VillageApp}·{@code PortraitVillage}·{@code GardenDex} 셋뿐이고 모두 {@code catalog.*}만 읽는다.
 */
public record GardenApiResponse(
        String nickname,
        int foodBalance,
        CatalogDto catalog
) {

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
     * 서재 전체 응답 조립 — 먹이주기 루프 정보 포함.
     *
     * @param foodBalance          현재 먹이 잔액(달성일 − 먹인합)
     * @param affectionByCharacter code → feed_count 맵(없는 코드는 0)
     */
    static GardenApiResponse of(
            GardenView view,
            String nickname,
            int foodBalance,
            Map<String, Integer> affectionByCharacter
    ) {
        return new GardenApiResponse(
                nickname,
                foodBalance,
                new CatalogDto(
                        view.authorCharacters().stream()
                                .map(s -> AuthorCharacterDto.from(s,
                                        affectionByCharacter.getOrDefault(s.character().getCode(), 0)))
                                .toList(),
                        view.ownedAuthorCharacterCount(), view.totalAuthorCharacterCount(),
                        view.ownedCharacters().stream()
                                .map(c -> OwnedCharacterDto.from(c,
                                        affectionByCharacter.getOrDefault(c.code(), 0)))
                                .toList()));
    }
}
