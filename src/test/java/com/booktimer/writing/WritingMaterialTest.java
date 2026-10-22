package com.booktimer.writing;

import com.booktimer.book.BookStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 글쓰기 재료 조립 — <b>네트워크·DB 없는 순수 절반</b>(v1 REQ-12 · 범위 보충 REQ-97 · REQ-48의 순수 부분).
 *
 * <p>여기서 재는 실패는 셋이다. ① 여백을 <b>글자 단위로</b> 잘라 거짓 인용을 만드는 것(통째 단위 대조군).
 * ② 빠진 개수를 말하지 않는 <b>조용한 누락</b>. ③ 상한 계산(chars)과 모델이 실제로 받는 문자열이 <b>어긋나는 것</b> —
 * 특히 REQ-97로 메모 줄을 빼는 갈래가 비용 계산에서만 빠지면 상한이 거짓이 된다.
 */
class WritingMaterialTest {

    private static final BookLine BOOK = new BookLine(7L, "데미안", "헤르만 헤세", null, BookStatus.READING,
            98_765L, 4_321, false);

    private static MaterialItem item(String date, String quote, String text) {
        return new MaterialItem(LocalDate.parse(date), quote, text);
    }

    /** 모델이 받는 문자열에서 여백 블록이 시작하는 곳부터 — chars가 세는 범위다(머리 두 줄은 고정 비용). */
    private static String blocks(String text) {
        int at = text.indexOf("### 여백");
        return at < 0 ? "" : text.substring(at);
    }

    @Test
    @DisplayName("REQ-12 · 여백이 최근순으로 실린다")
    void keepsRecentFirstOrder() {
        List<MaterialItem> recentFirst = List.of(
                item("2026-10-20", null, "가장 최근 생각"),
                item("2026-10-01", "새는 알에서 나오려고 투쟁한다", "중간 생각"),
                item("2026-09-03", null, "가장 오래된 생각"));

        WritingMaterial material = WritingMaterial.select(recentFirst, WritingMaterial.MAX_CHARS);
        String text = material.text(BOOK);

        assertThat(material.included()).containsExactlyElementsOf(recentFirst);
        assertThat(text.indexOf("가장 최근 생각"))
                .as("넘겨받은 순서(최근순) 그대로 — 뒤집힌 구현이면 오래된 것이 앞에 선다")
                .isLessThan(text.indexOf("중간 생각"));
        assertThat(text.indexOf("중간 생각")).isLessThan(text.indexOf("가장 오래된 생각"));
        assertThat(text).contains("### 여백 · 2026-10-20", "옮긴 문장: 새는 알에서 나오려고 투쟁한다", "옮긴 문장: 없음");
    }

    @Test
    @DisplayName("REQ-12 · 넘치는 첫 항목에서 멈추고 뒤의 작은 항목을 끼워 넣지 않는다")
    void stopsAtFirstOverflowWithoutBackfilling() {
        MaterialItem big1 = item("2026-10-20", null, "가".repeat(400));
        MaterialItem big2 = item("2026-10-19", null, "나".repeat(400));
        MaterialItem tiny = item("2026-10-18", null, "다");
        int max = WritingMaterial.select(List.of(big1), Integer.MAX_VALUE).chars() + 10; // big1 하나만 들어가는 상한

        WritingMaterial material = WritingMaterial.select(List.of(big1, big2, tiny), max);

        assertThat(material.included())
                .as("big2가 넘친 뒤에 오는 1자짜리 tiny를 빈틈에 끼워 넣으면 「최근 N개」가 거짓이 된다")
                .containsExactly(big1);
        assertThat(material.text(BOOK)).doesNotContain("나나", "메모: 다");
        assertThat(material.chars()).isLessThanOrEqualTo(max);
    }

    @Test
    @DisplayName("REQ-12 · 빠진 개수가 응답에 있다")
    void countsExcluded() {
        MaterialItem big1 = item("2026-10-20", null, "가".repeat(400));
        int max = WritingMaterial.select(List.of(big1), Integer.MAX_VALUE).chars();

        WritingMaterial material = WritingMaterial.select(
                List.of(big1, item("2026-10-19", null, "나"), item("2026-10-18", null, "다")), max);

        assertThat(material.included()).hasSize(1);
        assertThat(material.excludedCount()).isEqualTo(2);
        assertThat(material.text(BOOK)).contains("1개(전체 3개 중 최근 것부터)");
    }

    @Test
    @DisplayName("REQ-12 · chars는 모델이 받는 문자열 길이와 같다")
    void charsMatchesRenderedBlocks() {
        WritingMaterial material = WritingMaterial.select(List.of(
                item("2026-10-20", "옮긴 문장 하나", "생각 하나"),
                item("2026-10-19", null, "생각 둘\n두 줄"),
                item("2026-10-18", "생각이 점뿐인 여백", "."),
                item("2026-10-17", "생각이 빈 여백", null)), WritingMaterial.MAX_CHARS);

        String text = material.text(BOOK);

        assertThat(material.chars())
                .as("상한을 재는 수가 모델이 받는 여백 블록의 실제 길이와 같아야 한다 — REQ-97 갈래까지")
                .isEqualTo(blocks(text).length())
                .isPositive();
        assertThat(WritingMaterial.select(List.of(), WritingMaterial.MAX_CHARS).chars()).isZero();
    }

    @Test
    @DisplayName("REQ-12 · 재료 블록에 읽은 시간·횟수가 없다")
    void noReadingRecordInBlock() {
        String text = WritingMaterial.select(List.of(item("2026-10-20", null, "생각")), WritingMaterial.MAX_CHARS)
                .text(BOOK);

        assertThat(text).startsWith("[책] 데미안 / 헤르만 헤세\n");
        assertThat(text).doesNotContain("98765", "98,765", "4321", "4,321", "시간", "횟수");
    }

    @Test
    @DisplayName("REQ-97 · 생각이 비었거나 공백·문장부호뿐인 여백은 옮긴 문장 줄만 재료에 싣는다")
    void punctuationOnlyThoughtDropsMemoLine() {
        for (String thought : new String[]{".", " ", "…", "", null}) {
            String text = WritingMaterial.select(List.of(item("2026-10-20", "그 문장", thought)),
                    WritingMaterial.MAX_CHARS).text(BOOK);

            assertThat(text).as("생각=[%s]", thought).contains("옮긴 문장: 그 문장").doesNotContain("메모:");
        }
        // 양성 대조 — 보통 생각이 있는 여백은 메모 줄이 실린다(위 단언이 「메모 줄을 아예 안 싣는 구현」으로 통과하지 않게).
        String normal = WritingMaterial.select(List.of(item("2026-10-20", "그 문장", "내 생각.")),
                WritingMaterial.MAX_CHARS).text(BOOK);
        assertThat(normal).contains("옮긴 문장: 그 문장\n메모: 내 생각.\n");
    }

    @Test
    @DisplayName("REQ-48 · 재료 블록은 [책]·[재료] 머리와 여백 블록뿐이다 — 필기·백지복습 머리가 없다(순수 부분)")
    void blockHasOnlyMarginSections() {
        String text = WritingMaterial.select(List.of(item("2026-10-20", "책 문장", "여백 생각")),
                WritingMaterial.MAX_CHARS).text(BOOK);

        assertThat(text).contains("여백 생각").doesNotContain("### 필기", "### 백지복습");
        assertThat(text.lines().filter(l -> !l.isEmpty()))
                .allMatch(l -> l.startsWith("[책] ") || l.startsWith("[재료 — ") || l.startsWith("### 여백 · ")
                        || l.startsWith("옮긴 문장: ") || l.startsWith("메모: "));
    }

    @Test
    @DisplayName("책이 풀린 글의 재료 블록은 한 줄이다")
    void noBookIsOneLine() {
        assertThat(WritingMaterial.select(List.of(), WritingMaterial.MAX_CHARS).text(null))
                .isEqualTo("[책] 없음 — 재료 없이 글만 봅니다\n");
    }
}
