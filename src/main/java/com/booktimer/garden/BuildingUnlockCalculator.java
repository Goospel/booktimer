package com.booktimer.garden;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 출판사 건물 해금 순수 계산(DB·Spring·시간 무관).
 *
 * <p>세 단계:
 * <ol>
 *   <li>{@link #normalize} — 출판사 표기 흔들림(앞뒤 공백, 중간 공백)을 흡수한다.
 *       null/빈/공백 → {@code ""}. 역할 토큰은 없으나 공백 제거·strip으로 통일.</li>
 *   <li>{@link #publisherCounts} — 완독책 출판사 목록 → 정규화 출판사별 완독 권수.
 *       빈 정규화 결과는 제외(N-055).</li>
 *   <li>{@link #resolve} — publisherCounts에서 각 건물 {@code matchName}을 contains하는
 *       출판사의 권수 합이 {@code thresholdCount} 이상이면 owned.
 *       빈 {@code matchName}은 절대 매칭 불가(빈 contains=항상참 사고 차단).</li>
 * </ol>
 *
 * <p>contains 매칭이라 한 matchName에 여러 출판사 변형("민음사", "㈜민음사")이 합산될 수 있다 —
 * 카탈로그 큐레이션으로 모호한 matchName(부분문자열 충돌 위험)을 회피한다.
 */
public final class BuildingUnlockCalculator {

    private BuildingUnlockCalculator() {
    }

    /**
     * 출판사 표기를 정규화한다 — 표기 흔들림(공백·앞뒤 공백)을 흡수한다.
     *
     * <ol>
     *   <li>null/빈/공백 → {@code ""}</li>
     *   <li>모든 공백 제거 + strip</li>
     *   <li>결과가 빈 문자열이면 {@code ""} 반환(N-055 — 빈은 절대 매칭 불가)</li>
     * </ol>
     *
     * @param raw 원문 출판사 문자열(null 허용)
     * @return 정규화된 출판사 문자열(null 없음 — 빈 문자열로 대체)
     */
    public static String normalize(String raw) {
        if (raw == null) return "";
        String s = raw.replaceAll("\\s+", "").strip();
        return s;
    }

    /**
     * 완독책 출판사 목록을 정규화해 출판사별 완독 권수 맵으로 만든다.
     *
     * <p>빈 문자열로 정규화된 항목은 제외한다(N-055 미완성 메타 누수 가드).
     *
     * @param finishedPublishers 완독책 {@code Book.publisher} 목록(null 원소 허용 — 수동 등록 책)
     * @return 정규화 출판사명 → 완독 권수 맵. 빈 입력이면 빈 맵.
     */
    public static Map<String, Integer> publisherCounts(List<String> finishedPublishers) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String raw : finishedPublishers) {
            String normalized = normalize(raw);
            if (!normalized.isEmpty()) {
                counts.merge(normalized, 1, Integer::sum);
            }
        }
        return counts;
    }

    /**
     * 카탈로그 각 건물의 보유 여부를 판정해 도감 칸으로 돌려준다(카탈로그 순서 유지).
     *
     * <p>판정: publisherCounts에서 정규화된 key가 정규화된 {@code matchName}을
     * {@code contains}하는 권수를 모두 더해 {@code thresholdCount} 이상이면 owned.
     * {@code matchName}이 빈 문자열이면 절대 매칭 안 됨(빈 contains=항상참 사고 차단).
     *
     * @param catalog          건물 카탈로그(진열 순서)
     * @param publisherCounts  완독책 출판사 권수 맵({@link #publisherCounts} 결과)
     * @return 건물별 보유 상태(카탈로그 순서 유지)
     */
    public static List<BuildingState> resolve(List<Building> catalog,
                                              Map<String, Integer> publisherCounts) {
        return catalog.stream()
                .map(building -> {
                    String normalizedMatch = normalize(building.getMatchName());
                    // 빈 matchName은 절대 매칭 불가(빈 문자열은 모든 문자열에 contains되는 사고 차단)
                    if (normalizedMatch.isEmpty()) {
                        return new BuildingState(building, false);
                    }
                    int total = publisherCounts.entrySet().stream()
                            .filter(e -> e.getKey().contains(normalizedMatch))
                            .mapToInt(Map.Entry::getValue)
                            .sum();
                    boolean owned = building.getThresholdCount() >= 1 && total >= building.getThresholdCount();
                    return new BuildingState(building, owned);
                })
                .toList();
    }
}
