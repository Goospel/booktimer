package com.booktimer.garden;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GardenView 단위 테스트(Spring 컨텍스트 불필요, 순수 레코드 메서드 검증).
 *
 * <p>검증 항목:
 * <ul>
 *   <li>{@code ownedPlants()}는 건물 은퇴로 항상 빈 목록 — 배치 대상 없음(AUTHOR는 배회 전용)</li>
 *   <li>{@code ownedCharacters()} — 보유 AUTHOR만 배회용으로 노출</li>
 *   <li>N-055: 미보유 AUTHOR가 {@code ownedCharacters()}에 새지 않는지</li>
 * </ul>
 */
class GardenViewC2Test {

    private static AuthorCharacterState state(String code, boolean owned) {
        AuthorCharacter ch = AuthorCharacter.of(code, code, code + "-캐릭터", "🪶", 1, null);
        return new AuthorCharacterState(ch, owned);
    }

    private static GardenView viewWith(List<AuthorCharacterState> authorCharacters) {
        return new GardenView(authorCharacters, 0, authorCharacters.size());
    }

    @Test
    @DisplayName("건물 은퇴: ownedPlants()는 보유 작가가 있어도 항상 빈 목록(배치 대상 0)")
    void ownedPlants_alwaysEmptyAfterBuildingRetired() {
        GardenView view = viewWith(List.of(state("han_gang", true)));
        assertThat(view.ownedPlants()).isEmpty();
    }

    @Test
    @DisplayName("ownedCharacters()가 보유한 AUTHOR 캐릭터만 반환한다")
    void ownedCharacters_returnsOwnedOnly() {
        GardenView view = viewWith(List.of(
                state("han_gang", true),
                state("bern_werber", false)
        ));
        List<OwnedCharacter> chars = view.ownedCharacters();
        assertThat(chars).hasSize(1);
        assertThat(chars.get(0).code()).isEqualTo("han_gang");
    }

    @Test
    @DisplayName("N-055: 미보유 AUTHOR 캐릭터가 ownedCharacters()에 새지 않는다")
    void ownedCharacters_excludesNonOwned() {
        GardenView view = viewWith(List.of(
                state("han_gang", false),
                state("bern_werber", false)
        ));
        assertThat(view.ownedCharacters()).isEmpty();
    }
}
