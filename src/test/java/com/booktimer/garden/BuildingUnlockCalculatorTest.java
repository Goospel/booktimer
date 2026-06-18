package com.booktimer.garden;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 출판사 건물 해금 순수 계산 검증 (DB·Spring 무관).
 *
 * <p>세 단계:
 * <ol>
 *   <li>{@code normalize} — 표기 흔들림 흡수(null/빈/공백 → "", strip·공백 제거).</li>
 *   <li>{@code publisherCounts} — 완독책 출판사 목록 → 정규화 출판사별 권수(null/빈 제외 — N-055).</li>
 *   <li>{@code resolve} — 카탈로그+publisherCounts → 건물별 보유 판정.
 *       contains 매칭으로 표기 변형 흡수. 빈 matchName=절대 미보유 가드.</li>
 * </ol>
 */
class BuildingUnlockCalculatorTest {

    // 테스트 카탈로그 — prod 시드(V46)와 디커플. 임계값 다양하게.
    private static final Building MINUMSA =
            Building.of("minumsa", "민음사", "민음사", "🏛️", 3, 1, null);
    private static final Building MOONHAK =
            Building.of("moonhak", "문학동네", "문학동네", "📚", 5, 2, null);
    private static final Building EMPTY_MATCH =
            Building.of("empty_guard", "", "가드 검증용", "❓", 3, 99, null);
    private static final List<Building> CATALOG = List.of(MINUMSA, MOONHAK, EMPTY_MATCH);

    // ── normalize() ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("정상 출판사명 — strip 후 반환")
    void normalize_normal() {
        assertThat(BuildingUnlockCalculator.normalize("민음사")).isEqualTo("민음사");
    }

    @Test
    @DisplayName("앞뒤 공백 제거")
    void normalize_stripsWhitespace() {
        assertThat(BuildingUnlockCalculator.normalize("  민음사  ")).isEqualTo("민음사");
    }

    @Test
    @DisplayName("중간 공백 제거")
    void normalize_removesInternalSpaces() {
        assertThat(BuildingUnlockCalculator.normalize("문학 동네")).isEqualTo("문학동네");
    }

    @Test
    @DisplayName("null 입력 → 빈 문자열 (N-055)")
    void normalize_nullToEmpty() {
        assertThat(BuildingUnlockCalculator.normalize(null)).isEmpty();
    }

    @Test
    @DisplayName("빈 문자열 → 빈 문자열")
    void normalize_emptyToEmpty() {
        assertThat(BuildingUnlockCalculator.normalize("")).isEmpty();
    }

    @Test
    @DisplayName("공백만 → 빈 문자열")
    void normalize_blankToEmpty() {
        assertThat(BuildingUnlockCalculator.normalize("   ")).isEmpty();
    }

    // ── publisherCounts() ─────────────────────────────────────────────────────

    @Test
    @DisplayName("같은 출판사 여러 권 → 권수 합산")
    void publisherCounts_sameSummed() {
        Map<String, Integer> counts = BuildingUnlockCalculator.publisherCounts(
                List.of("민음사", "민음사", "민음사"));
        assertThat(counts.get("민음사")).isEqualTo(3);
    }

    @Test
    @DisplayName("서로 다른 출판사 → 각각 1")
    void publisherCounts_distinct() {
        Map<String, Integer> counts = BuildingUnlockCalculator.publisherCounts(
                List.of("민음사", "문학동네", "창비"));
        assertThat(counts).containsEntry("민음사", 1)
                .containsEntry("문학동네", 1)
                .containsEntry("창비", 1);
    }

    @Test
    @DisplayName("null·빈·공백 출판사는 카운트에서 제외 (N-055)")
    void publisherCounts_nullOrBlankExcluded() {
        Map<String, Integer> counts = BuildingUnlockCalculator.publisherCounts(
                Arrays.asList("민음사", null, "", "   "));
        assertThat(counts).containsOnlyKeys("민음사");
        assertThat(counts.get("민음사")).isEqualTo(1);
    }

    @Test
    @DisplayName("앞뒤 공백만 다른 표기 → 같은 출판사로 흡수")
    void publisherCounts_strippedSame() {
        Map<String, Integer> counts = BuildingUnlockCalculator.publisherCounts(
                List.of("민음사", " 민음사 ", "민음사"));
        assertThat(counts).hasSize(1);
        assertThat(counts.get("민음사")).isEqualTo(3);
    }

    @Test
    @DisplayName("빈 입력 → 빈 맵")
    void publisherCounts_emptyInput() {
        assertThat(BuildingUnlockCalculator.publisherCounts(List.of())).isEmpty();
    }

    // ── resolve() ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("임계 미만 → 미보유 (경계: 민음사 임계 3, 읽은 권수 2)")
    void resolve_belowThreshold_notOwned() {
        Map<String, Integer> counts = BuildingUnlockCalculator.publisherCounts(
                List.of("민음사", "민음사"));
        List<BuildingState> states = BuildingUnlockCalculator.resolve(CATALOG, counts);
        assertThat(state(states, "minumsa").owned()).isFalse();
    }

    @Test
    @DisplayName("임계 정확히 N → 보유 (경계: 민음사 임계 3, 읽은 권수 3)")
    void resolve_exactThreshold_owned() {
        Map<String, Integer> counts = BuildingUnlockCalculator.publisherCounts(
                List.of("민음사", "민음사", "민음사"));
        List<BuildingState> states = BuildingUnlockCalculator.resolve(CATALOG, counts);
        assertThat(state(states, "minumsa").owned()).isTrue();
    }

    @Test
    @DisplayName("임계 초과 → 보유")
    void resolve_aboveThreshold_owned() {
        Map<String, Integer> counts = BuildingUnlockCalculator.publisherCounts(
                List.of("민음사", "민음사", "민음사", "민음사"));
        List<BuildingState> states = BuildingUnlockCalculator.resolve(CATALOG, counts);
        assertThat(state(states, "minumsa").owned()).isTrue();
    }

    @Test
    @DisplayName("건물 A 임계 충족, 건물 B 미충족 → 독립 판정")
    void resolve_independent() {
        // 민음사 3권 → minumsa(임계3) 보유, moonhak(임계5) 미보유
        Map<String, Integer> counts = BuildingUnlockCalculator.publisherCounts(
                List.of("민음사", "민음사", "민음사"));
        List<BuildingState> states = BuildingUnlockCalculator.resolve(CATALOG, counts);
        assertThat(state(states, "minumsa").owned()).isTrue();
        assertThat(state(states, "moonhak").owned()).isFalse();
    }

    @Test
    @DisplayName("contains 매칭 — '㈜민음사'가 matchName '민음사'를 contains")
    void resolve_containsMatch() {
        Map<String, Integer> counts = BuildingUnlockCalculator.publisherCounts(
                List.of("㈜민음사", "㈜민음사", "㈜민음사"));
        List<BuildingState> states = BuildingUnlockCalculator.resolve(CATALOG, counts);
        assertThat(state(states, "minumsa").owned()).isTrue();
    }

    @Test
    @DisplayName("빈 matchName 건물 — 어떤 책으로도 절대 보유 안 됨 (빈 contains=항상참 사고 차단)")
    void resolve_emptyMatchName_neverOwned() {
        Map<String, Integer> counts = BuildingUnlockCalculator.publisherCounts(
                List.of("민음사", "민음사", "민음사", "문학동네", "문학동네", "문학동네", "문학동네", "문학동네"));
        List<BuildingState> states = BuildingUnlockCalculator.resolve(CATALOG, counts);
        assertThat(state(states, "empty_guard").owned()).isFalse();
    }

    @Test
    @DisplayName("null 출판사 완독책만 있을 때 아무 건물도 보유 안 됨 (N-055)")
    void resolve_nullPublishersOnly_noOwned() {
        Map<String, Integer> counts = BuildingUnlockCalculator.publisherCounts(
                Arrays.asList(null, null, null));
        List<BuildingState> states = BuildingUnlockCalculator.resolve(CATALOG, counts);
        assertThat(states).noneMatch(BuildingState::owned);
    }

    @Test
    @DisplayName("완독 0권 → 카탈로그 전체 잠금 (도감 공란)")
    void resolve_zeroBooks_allLocked() {
        Map<String, Integer> counts = BuildingUnlockCalculator.publisherCounts(List.of());
        List<BuildingState> states = BuildingUnlockCalculator.resolve(CATALOG, counts);
        assertThat(states).noneMatch(BuildingState::owned);
        assertThat(states).extracting(s -> s.building().getCode())
                .containsExactly("minumsa", "moonhak", "empty_guard");
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    private static BuildingState state(List<BuildingState> states, String code) {
        return states.stream()
                .filter(s -> s.building().getCode().equals(code))
                .findFirst()
                .orElseThrow();
    }
}
