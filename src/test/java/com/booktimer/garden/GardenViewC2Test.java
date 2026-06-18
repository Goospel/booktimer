package com.booktimer.garden;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C2 풀어놓기 — GardenView 단위 테스트(Spring 컨텍스트 불필요, 순수 레코드 메서드 검증).
 *
 * <p>검증 항목:
 * <ul>
 *   <li>{@code ownedPlants()}에서 AUTHOR 제거 — 팔레트·저장 검증 집합에서 배제</li>
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
        return new GardenView(
                List.of(), 0, 0, 0, null, null,
                List.of(), 0, 0,
                List.of(), 0, 0,
                List.of(), 0, List.of(),
                authorCharacters, 0, authorCharacters.size(),
                List.of(), 0, 0
        );
    }

    @Test
    @DisplayName("C2 풀어놓기: ownedPlants()가 보유 AUTHOR 캐릭터를 포함하지 않는다")
    void ownedPlants_excludesAuthorCharacter() {
        GardenView view = viewWith(List.of(state("han_gang", true)));
        assertThat(view.ownedPlants())
                .extracting(OwnedPlant::axis)
                .doesNotContain(PlacementAxis.AUTHOR);
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
