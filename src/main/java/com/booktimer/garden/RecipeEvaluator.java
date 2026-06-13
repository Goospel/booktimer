package com.booktimer.garden;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 트랙 B 숨은 레시피 해금 판정 — 순수 계산(DB·Spring·시간 무관·전수 테스트).
 *
 * <p>책장 스냅샷({@link ShelfSnapshot})으로 만족된 레시피의 결과 식물 코드를 가린다. 트랙 A 계산기들과 같은
 * 철학으로 부수효과가 없다 — 저장(발견 이력)은 {@code GardenService}가 한다(트랙 B는 발견을 영구 저장한다는
 * 점에서 트랙 A의 "유도"와 다르다, 설계 §2.2).
 *
 * <p><b>파밍 가드(§2.4)</b>: 완독이 0이면 어떤 레시피도 만족시키지 않는다 — 읽고싶음(WANT_TO_READ)은 읽지
 * 않아도 누르는 저비용 행동이라, 읽고싶음만으로 해금되면 파밍이 된다. 개별 레시피도 완독을 요구하지만, 이
 * 중앙 가드로 미래 카운트형 레시피까지 불변식을 보장한다.
 */
public final class RecipeEvaluator {

    private RecipeEvaluator() {
    }

    /** 운영 레시피 목록({@link RecipeDefinition#ALL})으로 만족된 식물 코드를 가린다. */
    public static Set<String> satisfiedPlantCodes(ShelfSnapshot snapshot) {
        return satisfiedPlantCodes(snapshot, RecipeDefinition.ALL);
    }

    /**
     * 주어진 레시피 목록 중 스냅샷을 만족시키는 레시피의 결과 식물 코드 집합(목록 순서 유지).
     * 완독이 0이면 빈 집합(파밍 가드 §2.4).
     */
    public static Set<String> satisfiedPlantCodes(ShelfSnapshot snapshot, List<RecipeDefinition> recipes) {
        if (snapshot.finishedCount() == 0) {
            return Set.of();
        }
        Set<String> satisfied = new LinkedHashSet<>();
        for (RecipeDefinition recipe : recipes) {
            if (recipe.condition().test(snapshot)) {
                satisfied.add(recipe.plantCode());
            }
        }
        return satisfied;
    }
}
