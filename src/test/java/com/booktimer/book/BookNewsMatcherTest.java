package com.booktimer.book;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 책 뉴스 매칭 규칙(순수 함수) — 오탐 억제 계약을 못 박는다.
 *
 * <p>계약: <b>기사 제목</b>에 책 제목이 있고 AND 저자명도 있어야 채택. 구글 뉴스 RSS의
 * {@code description}은 리다이렉트 링크가 든 {@code <a>} 태그뿐이라 매칭에 쓸 근거가 없어
 * 제목만 본다(실측 2026-08-14).
 *
 * <p>AND가 없으면 어떤 오탐이 들어오는지는 아래 {@code rejectsRealWorldFalsePositives}가
 * 실측 사례로 못 박는다 — 전부 구글 뉴스가 실제로 돌려준 기사 제목이다.
 */
class BookNewsMatcherTest {

    private static final String TITLE = "총, 균, 쇠";
    private static final String AUTHOR = "재레드 다이아몬드";

    @Test
    @DisplayName("제목만 있고 저자가 없는 기사는 기각 — 일반명사 제목의 오탐 차단(핵심 계약)")
    void rejectsWhenAuthorMissing() {
        assertThat(BookNewsMatcher.matches("총, 균, 쇠 앞에 선 인류의 선택", TITLE, AUTHOR)).isFalse();
    }

    @Test
    @DisplayName("저자만 있고 제목이 없는 기사도 기각")
    void rejectsWhenTitleMissing() {
        assertThat(BookNewsMatcher.matches("재레드 다이아몬드 신간 출간", TITLE, AUTHOR)).isFalse();
    }

    @Test
    @DisplayName("실측 오탐을 기각한다 — 책 제목이 기사에 우연히 섞인 경우(구글 뉴스 실응답)")
    void rejectsRealWorldFalsePositives() {
        assertThat(BookNewsMatcher.matches(
                "웨이코 그룹 데미안 월튼 사장 주식 매각", "데미안", "헤르만 헤세")).isFalse();
        assertThat(BookNewsMatcher.matches(
                "삼겹살 사피엔스", "사피엔스", "유발 하라리")).isFalse();
        assertThat(BookNewsMatcher.matches(
                "보유세의 시간, 미움받을 용기[세상읽기]", "미움받을 용기", "기시미 이치로")).isFalse();
    }

    @Test
    @DisplayName("제목 AND 저자가 모두 기사 제목에 있으면 채택 (구글 뉴스 실응답)")
    void acceptsWhenBothPresent() {
        assertThat(BookNewsMatcher.matches(
                "미움받을 용기 기시미 이치로의 인생철학", "미움받을 용기", "기시미 이치로")).isTrue();
        assertThat(BookNewsMatcher.matches("총, 균, 쇠 개정판 — 재레드 다이아몬드", TITLE, AUTHOR)).isTrue();
    }

    @Test
    @DisplayName("HTML 엔티티(&quot; &amp; &lt; &gt; &#39; &nbsp;)를 해제한 뒤 매칭된다")
    void unescapesHtmlEntities() {
        assertThat(BookNewsMatcher.matches(
                "&quot;총, 균, 쇠&quot; 다시 읽기 — 재레드&nbsp;다이아몬드", TITLE, AUTHOR)).isTrue();
    }

    @Test
    @DisplayName("대소문자·공백 변형은 무시하고 매칭된다 (Sapiens ↔ SAPIENS, 「총,균,쇠」 붙여쓰기)")
    void ignoresCaseAndWhitespace() {
        assertThat(BookNewsMatcher.matches("SAPIENS 특별판 - Yuval Noah Harari 인터뷰",
                "Sapiens", "Yuval Noah Harari")).isTrue();
        assertThat(BookNewsMatcher.matches("총,균,쇠 다시 읽기 — 재레드  다이아몬드 인터뷰",
                TITLE, AUTHOR)).isTrue();
    }

    @Test
    @DisplayName("제목·저자가 비었으면(수집 대상이 아닌 책) 항상 기각 — AND를 만들 수 없다")
    void rejectsWhenBookMetadataMissing() {
        assertThat(BookNewsMatcher.matches("총, 균, 쇠 — 재레드 다이아몬드", TITLE, null)).isFalse();
        assertThat(BookNewsMatcher.matches("총, 균, 쇠 — 재레드 다이아몬드", null, AUTHOR)).isFalse();
        assertThat(BookNewsMatcher.matches("총, 균, 쇠 — 재레드 다이아몬드", TITLE, "  ")).isFalse();
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
