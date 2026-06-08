package com.booktimer.session;

/**
 * 성장 잔디 — 현재 연속 독서 일수(streak)에 따라 자라는 식물 단계.
 *
 * <p>잔디 컨셉의 유희적 연장: 연속으로 잔디를 심을수록 땅 → 새싹 → 꽃 → 나무로 자란다.
 * 연속이 끊기면(어제·오늘 모두 안 읽음) {@link #GROUND}으로 되돌아간다.
 *
 * <p>사다리(넓게): {@code 0=땅 · 1~3=새싹 · 4~13=꽃 · 14+=나무}. 임계값·이모지를 한 곳에 모아
 * 나중에 쉽게 튜닝하거나 실제 이미지(SVG)로 교체할 수 있게 한다(단계 매핑만 유지).
 */
public enum GrowthStage {

    GROUND("🟫", "땅"),     // 🟫
    SPROUT("🌱", "새싹"),   // 🌱
    FLOWER("🌷", "꽃"),     // 🌷
    TREE("🌳", "나무");     // 🌳

    private final String emoji;
    private final String label;

    GrowthStage(String emoji, String label) {
        this.emoji = emoji;
        this.label = label;
    }

    /** 연속 일수 → 성장 단계. 사다리(넓게): 0=땅 · 1~3=새싹 · 4~13=꽃 · 14+=나무. */
    public static GrowthStage of(int streak) {
        if (streak <= 0) {
            return GROUND;
        }
        if (streak <= 3) {
            return SPROUT;
        }
        if (streak <= 13) {
            return FLOWER;
        }
        return TREE;
    }

    public String emoji() {
        return emoji;
    }

    public String label() {
        return label;
    }
}
