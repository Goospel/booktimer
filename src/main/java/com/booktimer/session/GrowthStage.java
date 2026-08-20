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

    GROUND("🟫", "땅", 0),
    SPROUT("🌱", "새싹", 1),
    FLOWER("🌷", "꽃", 4),
    TREE("🌳", "나무", 14);

    private final String emoji;
    private final String label;
    private final int minStreak;

    GrowthStage(String emoji, String label, int minStreak) {
        this.emoji = emoji;
        this.label = label;
        this.minStreak = minStreak;
    }

    /**
     * 연속 일수 → 성장 단계. 사다리(넓게): 0=땅 · 1~3=새싹 · 4~13=꽃 · 14+=나무.
     *
     * <p>경계를 여기 다시 적지 않고 {@link #minStreak}에서 파생시킨다 — 화면이 「다음 단계까지 며칠」을
     * 그리려면 같은 사다리가 필요한데, 두 곳에 적으면 언젠가 한쪽만 고쳐진다.
     */
    public static GrowthStage of(int streak) {
        GrowthStage stage = GROUND;
        for (GrowthStage candidate : values()) {
            if (streak >= candidate.minStreak) {
                stage = candidate;
            }
        }
        return stage;
    }

    /** 이 단계에 들어가는 최소 연속 일수. */
    public int minStreak() {
        return minStreak;
    }

    /** 다음 단계 — 마지막(나무)이면 {@code null}(더 오를 곳이 없다). */
    public GrowthStage next() {
        GrowthStage[] all = values();
        return ordinal() + 1 < all.length ? all[ordinal() + 1] : null;
    }

    public String emoji() {
        return emoji;
    }

    public String label() {
        return label;
    }
}
