package com.booktimer.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 성장 잔디 단계 매핑 경계값 테스트 — 연속 일수 → 단계(땅/새싹/꽃/나무).
 *
 * <p>사다리(넓게): 0=땅 · 1~3=새싹 · 4~13=꽃 · 14+=나무. 경계가 단계 정의의 전부라 여기서 못 박는다.
 */
class GrowthStageTest {

    @Test
    @DisplayName("다음 단계는 사다리 순서를 따르고, 나무는 더 오를 곳이 없다")
    void next_followsLadderAndStopsAtTree() {
        assertThat(GrowthStage.GROUND.next()).isEqualTo(GrowthStage.SPROUT);
        assertThat(GrowthStage.SPROUT.next()).isEqualTo(GrowthStage.FLOWER);
        assertThat(GrowthStage.FLOWER.next()).isEqualTo(GrowthStage.TREE);
        assertThat(GrowthStage.TREE.next()).isNull();
    }

    @Test
    @DisplayName("단계의 최소 연속일 — of()의 경계와 같은 값이어야 한다(사다리는 한 곳에만 있다)")
    void minStreak_matchesLadder() {
        assertThat(GrowthStage.GROUND.minStreak()).isZero();
        assertThat(GrowthStage.SPROUT.minStreak()).isEqualTo(1);
        assertThat(GrowthStage.FLOWER.minStreak()).isEqualTo(4);
        assertThat(GrowthStage.TREE.minStreak()).isEqualTo(14);
        // of()가 minStreak에서 파생되는지 — 두 곳에 적힌 사다리가 어긋나는 일 자체를 막는다.
        for (GrowthStage stage : GrowthStage.values()) {
            assertThat(GrowthStage.of(stage.minStreak())).isEqualTo(stage);
        }
    }

    @Test
    @DisplayName("0일(또는 그 이하)은 땅")
    void zero_isGround() {
        assertThat(GrowthStage.of(0)).isEqualTo(GrowthStage.GROUND);
        assertThat(GrowthStage.of(-1)).isEqualTo(GrowthStage.GROUND); // 방어적: 음수도 땅
    }

    @Test
    @DisplayName("1~3일은 새싹 (경계 1·3)")
    void oneToThree_isSprout() {
        assertThat(GrowthStage.of(1)).isEqualTo(GrowthStage.SPROUT);
        assertThat(GrowthStage.of(3)).isEqualTo(GrowthStage.SPROUT);
    }

    @Test
    @DisplayName("4~13일은 꽃 (경계 4·13)")
    void fourToThirteen_isFlower() {
        assertThat(GrowthStage.of(4)).isEqualTo(GrowthStage.FLOWER);
        assertThat(GrowthStage.of(13)).isEqualTo(GrowthStage.FLOWER);
    }

    @Test
    @DisplayName("14일 이상은 나무 (경계 14·임의 큰 값)")
    void fourteenPlus_isTree() {
        assertThat(GrowthStage.of(14)).isEqualTo(GrowthStage.TREE);
        assertThat(GrowthStage.of(100)).isEqualTo(GrowthStage.TREE);
    }

    @Test
    @DisplayName("각 단계는 비어 있지 않은 이모지·라벨을 가진다")
    void everyStage_hasEmojiAndLabel() {
        for (GrowthStage stage : GrowthStage.values()) {
            assertThat(stage.emoji()).isNotBlank();
            assertThat(stage.label()).isNotBlank();
        }
    }
}
