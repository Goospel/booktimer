package com.booktimer.garden;

import com.booktimer.garden.ShelfSnapshot.BookMeta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 트랙 B 숨은 레시피 해금 순수 계산 검증 (DB·Spring 무관). 경계를 전수로 본다.
 *
 * <p>두 부분: ① {@link ShelfSnapshot#of}가 완독/읽고싶음 책 메타를 장르·작가·권수로 집계(null 제외 — N-055)
 * ② {@link RecipeEvaluator}가 그 스냅샷으로 만족된 레시피 식물코드를 가린다. 핵심 불변식:
 * <b>완독 ≥1 필수</b>(읽고싶음만으론 어떤 레시피도 해금 안 됨 — 파밍 가드 §2.4).
 *
 * <p>레시피 규칙({@code primaryGenre} 라벨 추출 등)은 {@code ReadingProfileAggregatorTest}가 전수하므로
 * 여기선 조합 판정·집계·파밍 가드만 본다. 운영 레시피 목록({@link RecipeDefinition#ALL})은 베타 예시라
 * 여기 테스트는 자체 정의 레시피로 메커닉을 고정한다(목록이 교체돼도 메커닉 테스트는 안 깨짐).
 */
class RecipeEvaluatorTest {

    // 완독 소설 + 읽고싶음 과학 → "lotus" (메타 매칭형)
    private static final RecipeDefinition LOTUS = new RecipeDefinition("lotus",
            s -> s.finishedGenres().contains("소설/시/희곡") && s.wantToReadGenres().contains("과학"));
    // 완독 서로 다른 장르 3종 이상 → "explorer" (카운트형)
    private static final RecipeDefinition EXPLORER = new RecipeDefinition("explorer",
            s -> s.finishedGenres().size() >= 3);
    // 같은 작가 완독 2권 이상 → "regular" (작가축)
    private static final RecipeDefinition REGULAR = new RecipeDefinition("regular",
            s -> s.finishedAuthorCounts().values().stream().anyMatch(c -> c >= 2));
    private static final List<RecipeDefinition> RECIPES = List.of(LOTUS, EXPLORER, REGULAR);

    private static BookMeta book(String author, String category) {
        return new BookMeta(author, category);
    }

    @Test
    @DisplayName("완독·읽고싶음 조건이 둘 다 충족돼야 메타 매칭 레시피가 만족된다")
    void metaRecipe_needsBothSides() {
        ShelfSnapshot both = ShelfSnapshot.of(
                List.of(book("작가", "국내도서>소설/시/희곡>한국소설")),
                List.of(book("작가", "국내도서>과학>물리")));

        assertThat(RecipeEvaluator.satisfiedPlantCodes(both, RECIPES)).contains("lotus");
    }

    @Test
    @DisplayName("한쪽만 있으면 메타 매칭 레시피는 불만족 — 완독 소설만, 읽고싶음 과학 없음")
    void metaRecipe_oneSideOnly_notSatisfied() {
        ShelfSnapshot finishedOnly = ShelfSnapshot.of(
                List.of(book("작가", "국내도서>소설/시/희곡>한국소설")),
                List.of());

        assertThat(RecipeEvaluator.satisfiedPlantCodes(finishedOnly, RECIPES)).doesNotContain("lotus");
    }

    @Test
    @DisplayName("완독 0이면 읽고싶음이 아무리 많아도 어떤 레시피도 만족 안 됨 (파밍 가드 §2.4)")
    void noFinished_neverUnlocks() {
        ShelfSnapshot wantOnly = ShelfSnapshot.of(
                List.of(),
                List.of(book("a", "국내도서>소설/시/희곡>x"),
                        book("b", "국내도서>과학>y"),
                        book("c", "국내도서>인문학>z")));

        assertThat(RecipeEvaluator.satisfiedPlantCodes(wantOnly, RECIPES)).isEmpty();
    }

    @Test
    @DisplayName("category가 null인 완독책은 장르 집계에서 빠진다 (N-055 미완성 메타 누수 가드)")
    void nullCategoryExcludedFromGenres() {
        // 완독 3권이지만 장르가 잡히는 건 2권(소설·과학) — null은 제외 → explorer(3종) 불만족
        ShelfSnapshot snap = ShelfSnapshot.of(
                Arrays.asList(book("작가", "국내도서>소설/시/희곡>한국소설"),
                        book("작가", "국내도서>과학>물리"),
                        book("작가", null)),
                List.of());

        assertThat(snap.finishedGenres()).containsExactlyInAnyOrder("소설/시/희곡", "과학");
        assertThat(RecipeEvaluator.satisfiedPlantCodes(snap, RECIPES)).doesNotContain("explorer");
    }

    @Test
    @DisplayName("서로 다른 장르 3종 완독 → 카운트형 레시피 만족")
    void distinctGenres_satisfiesCountRecipe() {
        ShelfSnapshot snap = ShelfSnapshot.of(
                List.of(book("작가", "국내도서>소설/시/희곡>한국소설"),
                        book("작가", "국내도서>과학>물리"),
                        book("작가", "국내도서>인문학>철학")),
                List.of());

        assertThat(RecipeEvaluator.satisfiedPlantCodes(snap, RECIPES)).contains("explorer");
    }

    @Test
    @DisplayName("같은 장르 여러 권은 1종으로 흡수 — 카운트형(3종) 불만족")
    void sameGenreCollapses() {
        ShelfSnapshot snap = ShelfSnapshot.of(
                List.of(book("작가", "국내도서>소설/시/희곡>한국소설"),
                        book("작가", "국내도서>소설/시/희곡>외국소설"),
                        book("작가", "국내도서>소설/시/희곡>시")),
                List.of());

        assertThat(snap.finishedGenres()).containsExactly("소설/시/희곡");
        assertThat(RecipeEvaluator.satisfiedPlantCodes(snap, RECIPES)).doesNotContain("explorer");
    }

    @Test
    @DisplayName("같은 작가 완독 2권 → 작가축 레시피 만족, author null은 집계 제외 (N-055)")
    void sameAuthorTwice_satisfies_nullAuthorExcluded() {
        ShelfSnapshot twoSame = ShelfSnapshot.of(
                List.of(book("김작가", "국내도서>소설/시/희곡>한국소설"),
                        book("김작가", "국내도서>소설/시/희곡>외국소설")),
                List.of());
        assertThat(RecipeEvaluator.satisfiedPlantCodes(twoSame, RECIPES)).contains("regular");

        // author가 null인 두 권은 같은 작가로 묶이지 않는다 → 불만족
        ShelfSnapshot nullAuthors = ShelfSnapshot.of(
                Arrays.asList(book(null, "국내도서>소설/시/희곡>한국소설"),
                        book(null, "국내도서>소설/시/희곡>외국소설")),
                List.of());
        assertThat(RecipeEvaluator.satisfiedPlantCodes(nullAuthors, RECIPES)).doesNotContain("regular");
    }

    @Test
    @DisplayName("여러 레시피가 동시에 만족되면 모두 반환된다")
    void multipleRecipesSatisfiedTogether() {
        ShelfSnapshot snap = ShelfSnapshot.of(
                List.of(book("김작가", "국내도서>소설/시/희곡>한국소설"),
                        book("김작가", "국내도서>과학>물리"),
                        book("김작가", "국내도서>인문학>철학")),
                List.of(book("x", "국내도서>과학>화학")));

        // lotus(소설완독+과학읽고싶음) + explorer(3종) + regular(김작가 3권)
        assertThat(RecipeEvaluator.satisfiedPlantCodes(snap, RECIPES))
                .containsExactlyInAnyOrder("lotus", "explorer", "regular");
    }

    @Test
    @DisplayName("기본 오버로드는 운영 레시피 목록(ALL)을 쓴다 — 비어 있지 않다")
    void defaultOverloadUsesAllRecipes() {
        assertThat(RecipeDefinition.ALL).isNotEmpty();
        // 완독 0이면 ALL로도 항상 비어 있다(파밍 가드).
        ShelfSnapshot empty = ShelfSnapshot.of(List.of(), List.of());
        assertThat(RecipeEvaluator.satisfiedPlantCodes(empty)).isEmpty();
    }
}
