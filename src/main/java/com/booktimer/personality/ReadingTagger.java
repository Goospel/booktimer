package com.booktimer.personality;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 결정적 태거 — {@link ReadingProfile}에서 규칙 기반으로 짧은 태그 목록을 산출한다(책BTI Phase 6b).
 *
 * <p>같은 프로필 → 같은 태그(결정적). LLM·외부 의존 없음.
 * 출력 순서: [장르 종족 태그…] → [폭 태그] → [저자 태그].
 */
public final class ReadingTagger {

    static final double GENRE_SECOND_MIN = 0.30;
    static final double BREADTH_SPECIALIST_MIN = 0.70;
    static final int BREADTH_OMNIVORE_DISTINCT = 3;
    static final double AUTHOR_LOYAL_MIN = 0.40;

    public static final String AUTHOR_LOYAL_TAG = "한우물형";

    private ReadingTagger() {}

    /** {@link ReadingProfile} → 정렬된 태그 라벨 목록. null 또는 완독 0권이면 {@code List.of()}. */
    public static List<String> tagsOf(ReadingProfile profile) {
        if (profile == null || profile.finishedBooks() == 0) {
            return List.of();
        }

        int total = profile.finishedBooks();
        List<String> tags = new ArrayList<>();

        // ① 장르 정체성 — topGenres를 종족별로 합산
        Map<ReadingTribe, Integer> tribeCounts = aggregateTribeCounts(profile.topGenres());

        List<Map.Entry<ReadingTribe, Integer>> ranked = rankedTribes(tribeCounts);

        // 1위 종족 → 항상 태그
        if (!ranked.isEmpty()) {
            tags.add(ranked.get(0).getKey().label());
            // 2위 종족 → 점유율 ≥ 0.30 일 때만
            if (ranked.size() >= 2) {
                double secondShare = (double) ranked.get(1).getValue() / total;
                if (secondShare >= GENRE_SECOND_MIN) {
                    tags.add(ranked.get(1).getKey().label());
                }
            }
        }

        // ② 폭 — 잡식↔편식
        if (!ranked.isEmpty()) {
            double topShare = (double) ranked.get(0).getValue() / total;
            int distinctTribes = tribeCounts.size();

            if (topShare >= BREADTH_SPECIALIST_MIN) {
                tags.add("외길형");
            } else if (distinctTribes >= BREADTH_OMNIVORE_DISTINCT) {
                tags.add("잡식가");
            } else {
                tags.add("균형형");
            }
        }

        // ③ 저자 충성도
        List<LabeledCount> topAuthors = profile.topAuthors();
        if (!topAuthors.isEmpty()) {
            double loyalShare = (double) topAuthors.get(0).count() / total;
            if (loyalShare >= AUTHOR_LOYAL_MIN) {
                tags.add(AUTHOR_LOYAL_TAG);
            }
        }

        return List.copyOf(tags);
    }

    /** topGenres 목록을 종족별로 권수 합산한 Map 반환(미매핑 제외). */
    private static Map<ReadingTribe, Integer> aggregateTribeCounts(List<LabeledCount> topGenres) {
        Map<ReadingTribe, Integer> map = new LinkedHashMap<>();
        for (LabeledCount lc : topGenres) {
            ReadingTribe.ofCategory(lc.label()).ifPresent(tribe ->
                    map.merge(tribe, lc.count(), Integer::sum));
        }
        return map;
    }

    /**
     * 종족 Map을 권수 내림차순, 동률 시 라벨 오름차순으로 정렬한 Entry 리스트 반환.
     * 결정적 출력을 보장한다.
     */
    private static List<Map.Entry<ReadingTribe, Integer>> rankedTribes(
            Map<ReadingTribe, Integer> tribeCounts) {
        return tribeCounts.entrySet().stream()
                .sorted(Map.Entry.<ReadingTribe, Integer>comparingByValue().reversed()
                        .thenComparing(e -> e.getKey().label()))
                .toList();
    }
}
