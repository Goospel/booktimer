package com.booktimer.garden;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 작가·출판사 다양성 식물 해금 순수 계산 검증 (DB·Spring 무관). 경계를 전수로 본다.
 *
 * <p>두 단계: ① 완독책 작가/출판사 목록 → distinct 수({@code distinctCount}, null·빈·공백 제외·strip 흡수 — N-055)
 * ② 카탈로그 + (작가 수·출판사 수) → 식물별 보유 판정({@code resolve}, kind 교차 없음).
 */
class DiversityUnlockCalculatorTest {

    // 테스트 카탈로그 — 작가 식물(임계 1·3·5) + 출판사 식물(임계 1·3). prod 시드(V38)와 디커플.
    private static final DiversityPlant A1 = DiversityPlant.of("a1", DiversityKind.AUTHOR, 1, "작가 새싹", "🖋️", 1);
    private static final DiversityPlant A3 = DiversityPlant.of("a3", DiversityKind.AUTHOR, 3, "작가 묘목", "✒️", 2);
    private static final DiversityPlant A5 = DiversityPlant.of("a5", DiversityKind.AUTHOR, 5, "작가 나무", "📜", 3);
    private static final DiversityPlant P1 = DiversityPlant.of("p1", DiversityKind.PUBLISHER, 1, "출판 새싹", "🏷️", 4);
    private static final DiversityPlant P3 = DiversityPlant.of("p3", DiversityKind.PUBLISHER, 3, "출판 묘목", "📦", 5);
    private static final List<DiversityPlant> CATALOG = List.of(A1, A3, A5, P1, P3);

    @Test
    @DisplayName("같은 값 여러 개 완독 → distinct 1로 흡수")
    void sameValueCollapsesToOne() {
        assertThat(DiversityUnlockCalculator.distinctCount(List.of("김영하", "김영하", "김영하"))).isEqualTo(1);
    }

    @Test
    @DisplayName("서로 다른 N개 → distinct N")
    void distinctValuesEach() {
        assertThat(DiversityUnlockCalculator.distinctCount(List.of("김영하", "한강", "무라카미 하루키"))).isEqualTo(3);
    }

    @Test
    @DisplayName("null·빈·공백 값은 distinct에서 제외 (N-055 미완성 메타 누수 가드)")
    void nullOrBlankExcluded() {
        assertThat(DiversityUnlockCalculator.distinctCount(Arrays.asList("김영하", null, "", "   "))).isEqualTo(1);
    }

    @Test
    @DisplayName("앞뒤 공백만 다른 값은 strip 후 같은 것으로 흡수")
    void stripBeforeDistinct() {
        assertThat(DiversityUnlockCalculator.distinctCount(List.of("김영하", " 김영하 ", "김영하  "))).isEqualTo(1);
    }

    @Test
    @DisplayName("빈 입력 → 0")
    void emptyInputZero() {
        assertThat(DiversityUnlockCalculator.distinctCount(List.of())).isZero();
    }

    @Test
    @DisplayName("임계 경계 — N-1 미보유, N 보유, N+1 보유 (작가축)")
    void thresholdBoundary() {
        // authorCount=3 → 임계 1·3 보유, 임계 5 미보유. (출판사는 0)
        List<DiversityPlantState> states = DiversityUnlockCalculator.resolve(CATALOG, 3, 0);

        assertThat(state(states, "a1").owned()).isTrue();   // 임계 1 ≤ 3
        assertThat(state(states, "a3").owned()).isTrue();   // 임계 3 = 3 (경계 — 보유)
        assertThat(state(states, "a5").owned()).isFalse();  // 임계 5 > 3 (경계 — 미보유)
    }

    @Test
    @DisplayName("작가 식물은 작가 수로, 출판사 식물은 출판사 수로 판정 — kind 교차 없음")
    void kindDoesNotCross() {
        // 작가는 많고(5) 출판사는 적은(1) 픽스처 — 출판사 임계 3이 작가 수에 휩쓸리지 않아야 한다.
        List<DiversityPlantState> states = DiversityUnlockCalculator.resolve(CATALOG, 5, 1);

        assertThat(state(states, "a5").owned()).isTrue();   // 작가 5 → 임계 5 보유
        assertThat(state(states, "p1").owned()).isTrue();   // 출판 1 → 임계 1 보유
        assertThat(state(states, "p3").owned()).isFalse();  // 출판 1 → 임계 3 미보유(작가 5에 안 휩쓸림)
    }

    @Test
    @DisplayName("카운트 0이면 모든 식물 미보유 — 도감엔 카탈로그 전체가 칸으로 나온다")
    void zeroCountAllLocked() {
        List<DiversityPlantState> states = DiversityUnlockCalculator.resolve(CATALOG, 0, 0);

        assertThat(states).extracting(s -> s.plant().getCode())
                .containsExactly("a1", "a3", "a5", "p1", "p3"); // 카탈로그 전체가 칸으로(도감)
        assertThat(states).noneMatch(DiversityPlantState::owned);
    }

    private static DiversityPlantState state(List<DiversityPlantState> states, String code) {
        return states.stream()
                .filter(s -> s.plant().getCode().equals(code))
                .findFirst()
                .orElseThrow();
    }
}
