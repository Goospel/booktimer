package com.booktimer.book;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 책 뉴스 매칭 규칙(순수 함수) — 오탐 억제 계약을 못 박는다.
 *
 * <p>계약: 기사 제목+본문에 <b>책 제목이 있고 AND 저자명도 있어야</b> 채택. 『총, 균, 쇠』처럼
 * 제목이 일반명사 조합인 책은 제목만으로는 엉뚱한 기사를 문다 — 그 오탐을 막는 게 AND 조건이다.
 * 네이버 응답의 {@code <b>} 강조 태그·HTML 엔티티는 비교 전에 벗긴다.
 */
class BookNewsMatcherTest {

    private static final String TITLE = "총, 균, 쇠";
    private static final String AUTHOR = "재레드 다이아몬드";

    @Test
    @DisplayName("제목만 있고 저자가 없는 기사는 기각 — 일반명사 제목의 오탐 차단(핵심 계약)")
    void rejectsWhenAuthorMissing() {
        assertThat(BookNewsMatcher.matches(
                "총, 균, 쇠 앞에 선 인류의 선택",
                "전쟁과 전염병의 역사를 돌아본다",
                TITLE, AUTHOR)).isFalse();
    }

    @Test
    @DisplayName("저자만 있고 제목이 없는 기사도 기각")
    void rejectsWhenTitleMissing() {
        assertThat(BookNewsMatcher.matches(
                "재레드 다이아몬드 신간 출간",
                "인류학자의 새 책이 나왔다",
                TITLE, AUTHOR)).isFalse();
    }

    @Test
    @DisplayName("제목 AND 저자가 모두 있으면 채택 (본문에 저자가 있어도 됨)")
    void acceptsWhenBothPresent() {
        assertThat(BookNewsMatcher.matches(
                "총, 균, 쇠 개정판 출간",
                "재레드 다이아몬드의 대표작이 새 번역으로 돌아왔다",
                TITLE, AUTHOR)).isTrue();
    }

    @Test
    @DisplayName("<b> 강조 태그가 낀 제목도 태그를 벗긴 뒤 매칭된다")
    void stripsHighlightTags() {
        assertThat(BookNewsMatcher.matches(
                "<b>총, 균, 쇠</b> 개정판 출간",
                "<b>재레드 다이아몬드</b>의 대표작",
                TITLE, AUTHOR)).isTrue();
    }

    @Test
    @DisplayName("HTML 엔티티(&quot; &amp; &lt; &gt; &#39; &nbsp;)를 해제한 뒤 매칭된다")
    void unescapesHtmlEntities() {
        assertThat(BookNewsMatcher.matches(
                "&quot;총, 균, 쇠&quot; 다시 읽기",
                "재레드&nbsp;다이아몬드 &amp; 인류사",
                TITLE, AUTHOR)).isTrue();
    }

    @Test
    @DisplayName("대소문자·공백 변형은 무시하고 매칭된다 (Sapiens ↔ SAPIENS, 「총,균,쇠」 붙여쓰기)")
    void ignoresCaseAndWhitespace() {
        assertThat(BookNewsMatcher.matches("SAPIENS 특별판", "Yuval Noah Harari 인터뷰",
                "Sapiens", "Yuval Noah Harari")).isTrue();
        assertThat(BookNewsMatcher.matches("총,균,쇠 다시 읽기", "재레드  다이아몬드 인터뷰",
                TITLE, AUTHOR)).isTrue();
    }

    @Test
    @DisplayName("제목·저자가 비었으면(수집 대상이 아닌 책) 항상 기각 — AND를 만들 수 없다")
    void rejectsWhenBookMetadataMissing() {
        assertThat(BookNewsMatcher.matches("총, 균, 쇠", "재레드 다이아몬드", TITLE, null)).isFalse();
        assertThat(BookNewsMatcher.matches("총, 균, 쇠", "재레드 다이아몬드", null, AUTHOR)).isFalse();
        assertThat(BookNewsMatcher.matches("총, 균, 쇠", "재레드 다이아몬드", TITLE, "  ")).isFalse();
    }

    @Test
    @DisplayName("clean(): 태그·엔티티를 벗기고 공백을 정규화한다 (표시용 — 대소문자는 보존)")
    void cleanNormalizesForDisplay() {
        assertThat(BookNewsMatcher.clean("<b>총, 균, 쇠</b>&nbsp;개정판  출간"))
                .isEqualTo("총, 균, 쇠 개정판 출간");
        assertThat(BookNewsMatcher.clean("&quot;Sapiens&quot; &amp; 인류")).isEqualTo("\"Sapiens\" & 인류");
        assertThat(BookNewsMatcher.clean(null)).isNull();
    }
}
