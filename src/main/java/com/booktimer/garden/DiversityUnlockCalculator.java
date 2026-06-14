package com.booktimer.garden;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 작가·출판사 다양성 식물 보유 유도 — 순수 계산(DB·Spring·시간 무관).
 *
 * <p>시간축 {@link PlantUnlockCalculator}와 같은 철학: 보유를 저장하지 않고 <b>독서 실적의 함수로 유도</b>
 * 한다(부채 모델 N-058). 두 단계:
 * <ol>
 *   <li>{@link #distinctCount} — 완독책의 작가(또는 출판사) 목록에서 서로 다른 값의 수를 센다.
 *       {@code Book.author}/{@code publisher}는 적재 시 정규화되지 않으므로(category와 달리 blankToNull 없음)
 *       여기서 strip + 빈/null 제외를 직접 한다(미완성 메타 누수 가드 N-055).</li>
 *   <li>{@link #resolve} — 카탈로그 각 식물의 {@code kind}에 맞는 카운트로 임계 도달을 판정한다.</li>
 * </ol>
 */
public final class DiversityUnlockCalculator {

    private DiversityUnlockCalculator() {
    }

    /**
     * 서로 다른(distinct) 값의 수를 센다 — null·빈·공백은 제외하고 앞뒤 공백은 strip해 같은 값으로 흡수한다.
     *
     * @param values 완독책의 작가(또는 출판사) 목록(null 원소 허용 — 수동 등록 책은 작가/출판사가 null일 수 있음)
     * @return distinct 값 수(null/빈/공백 제외 — N-055). 빈 입력이면 0.
     */
    public static int distinctCount(List<String> values) {
        Set<String> distinct = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null) {
                String stripped = value.strip();
                if (!stripped.isEmpty()) {
                    distinct.add(stripped);
                }
            }
        }
        return distinct.size();
    }

    /**
     * 카탈로그 각 식물의 보유 여부를 판정해 도감 칸으로 돌려준다(카탈로그 순서 유지).
     *
     * @param catalog        다양성 식물 카탈로그(진열 순서 — 작가 묶음 → 출판사 묶음)
     * @param authorCount    완독 distinct 작가 수
     * @param publisherCount 완독 distinct 출판사 수
     * @return 식물별 보유 상태. 작가 식물은 authorCount로, 출판사 식물은 publisherCount로 임계 판정(교차 없음).
     */
    public static List<DiversityPlantState> resolve(List<DiversityPlant> catalog,
                                                    int authorCount, int publisherCount) {
        return catalog.stream()
                .map(plant -> {
                    int count = plant.getKind() == DiversityKind.AUTHOR ? authorCount : publisherCount;
                    boolean owned = plant.getThresholdCount() >= 1 && plant.getThresholdCount() <= count;
                    return new DiversityPlantState(plant, owned);
                })
                .toList();
    }
}
