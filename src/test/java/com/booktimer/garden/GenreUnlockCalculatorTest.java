package com.booktimer.garden;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 장르 식물 해금 순수 계산 검증 (DB·Spring 무관). 경계를 전수로 본다.
 *
 * <p>두 단계: ① 완독책 category → 보유 장르 집합({@code achievedGenres}, null/빈 제외 — N-055)
 * ② 카탈로그 + 보유 장르 → 식물별 보유 판정({@code resolve}, 폴백 포함).
 * {@code primaryGenre} 추출 규칙 자체는 {@code ReadingProfileAggregatorTest}가 이미 전수하므로
 * 여기선 재검증하지 않고 "집계가 그걸 통과시키는지"만 확인한다.
 */
class GenreUnlockCalculatorTest {

    // 테스트 카탈로그 — 시드 2종(소설/경제) + 폴백 1종(genreLabel=null).
    private static final GenrePlant NOVEL = GenrePlant.of("novel", "소설/시/희곡", "소설나무", "🌳", 1);
    private static final GenrePlant ECON = GenrePlant.of("econ", "경제경영", "경제선인장", "🌵", 2);
    private static final GenrePlant FALLBACK = GenrePlant.of("wildflower", null, "이름 모를 들꽃", "🌼", 99);
    private static final List<GenrePlant> CATALOG = List.of(NOVEL, ECON, FALLBACK);

    @Test
    @DisplayName("같은 장르 여러 권 완독 → 장르 하나로 흡수")
    void sameGenreCollapsesToOne() {
        Set<String> genres = GenreUnlockCalculator.achievedGenres(List.of(
                "국내도서>소설/시/희곡>한국소설",
                "국내도서>소설/시/희곡>외국소설"));

        assertThat(genres).containsExactly("소설/시/희곡");
    }

    @Test
    @DisplayName("서로 다른 장르 N권 → 장르 N개")
    void distinctGenresEach() {
        Set<String> genres = GenreUnlockCalculator.achievedGenres(List.of(
                "국내도서>소설/시/희곡>한국소설",
                "국내도서>경제경영>마케팅"));

        assertThat(genres).containsExactlyInAnyOrder("소설/시/희곡", "경제경영");
    }

    @Test
    @DisplayName("category가 null·빈 문자열인 완독책은 장르 집계에서 제외 (N-055 미완성 메타 누수 가드)")
    void nullOrBlankCategoryExcluded() {
        Set<String> genres = GenreUnlockCalculator.achievedGenres(Arrays.asList(
                "국내도서>소설/시/희곡>한국소설",
                null,
                "   "));

        assertThat(genres).containsExactly("소설/시/희곡");
    }

    @Test
    @DisplayName("완독책이 없으면 보유 장르 0")
    void emptyInputEmptyGenres() {
        assertThat(GenreUnlockCalculator.achievedGenres(List.of())).isEmpty();
    }

    @Test
    @DisplayName("보유 장르가 식물 라벨과 일치하면 보유, 아니면 미보유 — 폴백은 미발동")
    void resolveMatchingSeedPlants() {
        List<GenrePlantState> states = GenreUnlockCalculator.resolve(CATALOG, Set.of("소설/시/희곡"));

        assertThat(state(states, "novel").owned()).isTrue();
        assertThat(state(states, "econ").owned()).isFalse();
        // 보유 장르가 전부 시드에 잡혔으므로 폴백은 발동하지 않는다.
        assertThat(state(states, "wildflower").owned()).isFalse();
    }

    @Test
    @DisplayName("시드에 없는 장르를 완독하면 폴백 식물이 보유로 잡힌다")
    void resolveUnknownGenreUnlocksFallback() {
        List<GenrePlantState> states = GenreUnlockCalculator.resolve(CATALOG, Set.of("여행"));

        assertThat(state(states, "novel").owned()).isFalse();
        assertThat(state(states, "econ").owned()).isFalse();
        assertThat(state(states, "wildflower").owned()).isTrue();
    }

    @Test
    @DisplayName("보유 장르가 전혀 없으면 폴백 포함 모든 식물 미보유")
    void resolveNoGenresAllLocked() {
        List<GenrePlantState> states = GenreUnlockCalculator.resolve(CATALOG, Set.of());

        assertThat(states).extracting(GenrePlantState::genrePlant).extracting(GenrePlant::getCode)
                .containsExactly("novel", "econ", "wildflower"); // 카탈로그 전체가 칸으로 나온다(도감)
        assertThat(states).noneMatch(GenrePlantState::owned);
    }

    @Test
    @DisplayName("시드 장르 일부 + 미지 장르를 함께 완독하면 해당 시드와 폴백이 둘 다 보유")
    void resolveSeedAndUnknownTogether() {
        List<GenrePlantState> states = GenreUnlockCalculator.resolve(CATALOG, Set.of("경제경영", "여행"));

        assertThat(state(states, "novel").owned()).isFalse();
        assertThat(state(states, "econ").owned()).isTrue();
        assertThat(state(states, "wildflower").owned()).isTrue();
    }

    private static GenrePlantState state(List<GenrePlantState> states, String code) {
        return states.stream()
                .filter(s -> s.genrePlant().getCode().equals(code))
                .findFirst()
                .orElseThrow();
    }
}
