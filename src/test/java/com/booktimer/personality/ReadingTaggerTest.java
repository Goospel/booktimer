package com.booktimer.personality;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReadingTaggerTest {

    // ─── 헬퍼 ───────────────────────────────────────────────────────────────

    /** topGenres·topAuthors 모두 빈 기본 프로필 */
    private static ReadingProfile emptyProfile(int finishedBooks) {
        return new ReadingProfile(finishedBooks, finishedBooks, 0, 0, 1.0, 0, 0, 0,
                0, List.of(), 0, List.of());
    }

    private static ReadingProfile profileWith(int finishedBooks,
                                               List<LabeledCount> topGenres,
                                               List<LabeledCount> topAuthors) {
        return new ReadingProfile(finishedBooks, finishedBooks, 0, 0, 1.0, 0, 0, 0,
                topAuthors.size(), topAuthors, topGenres.size(), topGenres);
    }

    private static LabeledCount lc(String label, int count) {
        return new LabeledCount(label, count);
    }

    // ─── null / 0권 안전 ────────────────────────────────────────────────────

    @Test
    @DisplayName("null 프로필 → List.of()")
    void nullProfile_returnsEmpty() {
        assertThat(ReadingTagger.tagsOf(null)).isEmpty();
    }

    @Test
    @DisplayName("finishedBooks=0 → List.of()")
    void zeroFinished_returnsEmpty() {
        assertThat(ReadingTagger.tagsOf(emptyProfile(0))).isEmpty();
    }

    // ─── 단일 종족 100% (1권 경계) ──────────────────────────────────────────

    @Test
    @DisplayName("단일 종족 100%(1권) → [종족, 외길형, 한우물형]")
    void singleTribe_100percent_1book() {
        // 완독 1권, 장르: 소설/시/희곡(→STORY 이야기파), 저자 1명 = 100%
        ReadingProfile p = profileWith(1,
                List.of(lc("소설/시/희곡", 1)),
                List.of(lc("작가A", 1)));

        List<String> tags = ReadingTagger.tagsOf(p);

        assertThat(tags).containsExactly("이야기파", "외길형", "한우물형");
    }

    // ─── 여러 대분류 → 같은 종족 합산 ──────────────────────────────────────

    @Test
    @DisplayName("소설+만화 → 같은 STORY로 합산돼 태그 하나")
    void multiCategoryMergesIntoOneTribe() {
        // 소설 2권 + 만화 1권 → STORY 3권, finishedBooks=3
        ReadingProfile p = profileWith(3,
                List.of(lc("소설/시/희곡", 2), lc("만화", 1)),
                List.of());

        List<String> tags = ReadingTagger.tagsOf(p);

        // 종족 태그는 이야기파 1개, 외길형(STORY 100%)
        assertThat(tags).contains("이야기파");
        assertThat(tags).doesNotContain("지식파", "탐구파");
    }

    // ─── 양다리 50/50 ───────────────────────────────────────────────────────

    @Test
    @DisplayName("양다리 50/50(2종족) → 두 종족 태그 + 균형형")
    void twoTribeHalfHalf() {
        // 완독 4권: 소설2 + 경제경영2 → STORY 50%, PRACTICAL 50%
        // 2위 점유율 50% ≥ 0.30 → 2위 태그 포함
        // distinctTribes=2, topShare=0.50 → 균형형
        ReadingProfile p = profileWith(4,
                List.of(lc("소설/시/희곡", 2), lc("경제경영", 2)),
                List.of());

        List<String> tags = ReadingTagger.tagsOf(p);

        // 양 종족 모두 포함
        assertThat(tags).contains("이야기파", "실용파");
        // 폭 태그: topShare=0.5 < 0.70 → 외길형 아님; distinctTribes=2 < 3 → 잡식가 아님 → 균형형
        assertThat(tags).contains("균형형");
        assertThat(tags).doesNotContain("외길형", "잡식가");
    }

    // ─── 폭 경계 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("topShare=0.70(임계 포함) → 외길형")
    void specialist_at70percent() {
        // 완독 10권: STORY 7권, KNOWLEDGE 3권 → topShare=0.70
        ReadingProfile p = profileWith(10,
                List.of(lc("소설/시/희곡", 7), lc("역사", 3)),
                List.of());

        assertThat(ReadingTagger.tagsOf(p)).contains("외길형");
    }

    @Test
    @DisplayName("topShare=0.69 + 3종족 → 잡식가")
    void omnivore_below70_threeTribes() {
        // 완독 100권: STORY 69 + KNOWLEDGE 16 + INQUIRY 15 → topShare=0.69, distinctTribes=3
        ReadingProfile p = profileWith(100,
                List.of(lc("소설/시/희곡", 69), lc("역사", 16), lc("과학", 15)),
                List.of());

        assertThat(ReadingTagger.tagsOf(p)).contains("잡식가");
        assertThat(ReadingTagger.tagsOf(p)).doesNotContain("외길형");
    }

    @Test
    @DisplayName("topShare < 0.70 + distinctTribes < 3 → 균형형")
    void balanced_notSpecialistNotOmnivore() {
        // 완독 10권: STORY 6 + KNOWLEDGE 4 → topShare=0.60, distinctTribes=2 → 균형형
        ReadingProfile p = profileWith(10,
                List.of(lc("소설/시/희곡", 6), lc("역사", 4)),
                List.of());

        assertThat(ReadingTagger.tagsOf(p)).contains("균형형");
        assertThat(ReadingTagger.tagsOf(p)).doesNotContain("외길형", "잡식가");
    }

    @Test
    @DisplayName("top-3가 3개 다른 종족(각 1/n) → 잡식가")
    void omnivore_threeDistinctTribes() {
        // 완독 3권, STORY/KNOWLEDGE/INQUIRY 각 1권 → distinctTribes=3
        ReadingProfile p = profileWith(3,
                List.of(lc("소설/시/희곡", 1), lc("역사", 1), lc("과학", 1)),
                List.of());

        assertThat(ReadingTagger.tagsOf(p)).contains("잡식가");
    }

    // ─── 2위 종족 임계 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("2위 점유율 0.30 → 태그 포함")
    void secondTribe_30percent_included() {
        // 완독 10권: STORY 7 + KNOWLEDGE 3 → 1위 70%, 2위 30%
        ReadingProfile p = profileWith(10,
                List.of(lc("소설/시/희곡", 7), lc("역사", 3)),
                List.of());

        List<String> tags = ReadingTagger.tagsOf(p);

        assertThat(tags).contains("이야기파", "지식파");
    }

    @Test
    @DisplayName("2위 점유율 0.29 → 태그 제외")
    void secondTribe_29percent_excluded() {
        // 완독 100권: STORY 71 + KNOWLEDGE 29 → 2위 29% < 0.30
        ReadingProfile p = profileWith(100,
                List.of(lc("소설/시/희곡", 71), lc("역사", 29)),
                List.of());

        List<String> tags = ReadingTagger.tagsOf(p);

        assertThat(tags).contains("이야기파");
        assertThat(tags).doesNotContain("지식파");
    }

    // ─── 저자 충성도 경계 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("최다 저자 점유율 0.40 → 한우물형")
    void loyal_40percent() {
        // 완독 10권, 저자A 4권 → 0.40
        ReadingProfile p = profileWith(10,
                List.of(lc("소설/시/희곡", 10)),
                List.of(lc("저자A", 4)));

        assertThat(ReadingTagger.tagsOf(p)).contains("한우물형");
    }

    @Test
    @DisplayName("최다 저자 점유율 0.39 → 저자 태그 없음")
    void loyal_39percent_noTag() {
        // 완독 100권, 저자A 39권 → 0.39
        ReadingProfile p = profileWith(100,
                List.of(lc("소설/시/희곡", 100)),
                List.of(lc("저자A", 39)));

        assertThat(ReadingTagger.tagsOf(p)).doesNotContain("한우물형");
    }

    @Test
    @DisplayName("topAuthors 비어있으면 저자 태그 없음")
    void noAuthors_noTag() {
        ReadingProfile p = profileWith(5, List.of(lc("소설/시/희곡", 5)), List.of());

        assertThat(ReadingTagger.tagsOf(p)).doesNotContain("한우물형");
    }

    // ─── 폴백 대분류만 (전부 미매핑) ─────────────────────────────────────────

    @Test
    @DisplayName("전부 미매핑 대분류 → 장르·폭 태그 없음(저자 태그는 가능)")
    void allUnmappedCategories_noGenreOrBreadthTag() {
        ReadingProfile p = profileWith(10,
                List.of(lc("알 수 없는 분류", 10)),
                List.of(lc("저자A", 5))); // 5/10 = 0.50 ≥ 0.40

        List<String> tags = ReadingTagger.tagsOf(p);

        // 장르 태그 없음
        assertThat(tags).doesNotContain("이야기파", "지식파", "탐구파", "실용파",
                "감성파", "수양파", "학습파", "동심파");
        // 폭 태그 없음 (매핑된 종족 없으면 폭 계산 불가)
        assertThat(tags).doesNotContain("외길형", "잡식가", "균형형");
        // 저자 태그는 가능
        assertThat(tags).contains("한우물형");
    }

    // ─── 동률 결정성 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("동률 두 종족 → 라벨 오름차순으로 안정 정렬")
    void tieBreak_labelAscending() {
        // KNOWLEDGE(지식파) vs STORY(이야기파) 동률 → 가나다 오름차순: 이야기파 < 지식파
        ReadingProfile p = profileWith(4,
                List.of(lc("역사", 2), lc("소설/시/희곡", 2)),
                List.of());

        List<String> tags = ReadingTagger.tagsOf(p);

        int storyIdx = tags.indexOf("이야기파");
        int knowledgeIdx = tags.indexOf("지식파");
        assertThat(storyIdx).isLessThan(knowledgeIdx);
    }

    // ─── 출력 순서 결정성 ────────────────────────────────────────────────────

    @Test
    @DisplayName("출력 순서: [장르 종족...] → [폭 태그] → [저자 태그]")
    void outputOrder_genreBreadthAuthor() {
        // STORY 70%, KNOWLEDGE 30%, 저자 40% → [이야기파, 지식파, 외길형, 한우물형]
        ReadingProfile p = profileWith(10,
                List.of(lc("소설/시/희곡", 7), lc("역사", 3)),
                List.of(lc("저자A", 4)));

        List<String> tags = ReadingTagger.tagsOf(p);

        assertThat(tags).containsExactly("이야기파", "지식파", "외길형", "한우물형");
    }
}
