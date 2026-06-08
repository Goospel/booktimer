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
