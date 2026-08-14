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

    /*
     * ─── 정규화 (운영 사고 2026-08-14: 대상 29종 · 저장 0건) ───────────────────────
     * 책의 title·author는 알라딘 API 원문이라 부제("코스모스 - 특별판")·역할표기
     * ("칼 세이건 (지은이), 홍승수 (옮긴이)")가 붙어 있다. 그 원문을 그대로 AND 매칭에 쓰면
     * 기사 제목에 있을 리 없는 문자열을 찾게 돼 전 종목이 기각된다.
     *
     * 아래 픽스처는 전부 <b>운영 DB의 실제 값</b>이다 — 이번 버그의 근본 원인이 이상적인 입력
     * (「데미안」/「헤르만 헤세」)으로만 검증한 것이라, 같은 실수를 막으려고 원문을 그대로 쓴다.
     */

    private static final String RAW_COSMOS_TITLE = "코스모스 - 특별판";
    private static final String RAW_COSMOS_AUTHOR = "칼 세이건 (지은이), 홍승수 (옮긴이)";
    private static final String RAW_1984_AUTHOR = "조지 오웰 (지은이), 정회성 (옮긴이)";
    private static final String RAW_STRANGER_AUTHOR = "알베르 카뮈 (지은이), 김화영 (옮긴이)";
    private static final String RAW_ODYSSEY_TITLE = "오뒷세이아 - 그리스어 원전 번역";
    private static final String RAW_ODYSSEY_AUTHOR = "호메로스 (지은이), 천병희 (옮긴이)";
    private static final String RAW_ANNA_TITLE = "안나 카레니나 세트 - 전3권";
    private static final String RAW_ANNA_AUTHOR = "레프 니콜라예비치 톨스토이 (지은이), 연진희 (옮긴이)";
    private static final String RAW_DEMIAN_AUTHOR = "헤르만 헤세 (지은이), 전영애 (옮긴이)";

    @Test
    @DisplayName("normalizeTitle(): ' - ' 뒤 부제를 잘라낸다 (운영 실값)")
    void normalizeTitle_cutsSubtitle() {
        assertThat(BookNewsMatcher.normalizeTitle(RAW_COSMOS_TITLE)).isEqualTo("코스모스");
        assertThat(BookNewsMatcher.normalizeTitle(RAW_ODYSSEY_TITLE)).isEqualTo("오뒷세이아");
        // en dash·em dash 도 부제 구분자로 쓰인다
        assertThat(BookNewsMatcher.normalizeTitle("코스모스 – 특별판")).isEqualTo("코스모스");
        assertThat(BookNewsMatcher.normalizeTitle("코스모스 — 특별판")).isEqualTo("코스모스");
    }

    @Test
    @DisplayName("normalizeTitle(): 괄호 블록을 제거한다")
    void normalizeTitle_removesParenBlocks() {
        assertThat(BookNewsMatcher.normalizeTitle("이기적 유전자 (개정판)")).isEqualTo("이기적 유전자");
        assertThat(BookNewsMatcher.normalizeTitle("데미안 (양장본 HardCover)")).isEqualTo("데미안");
    }

    @Test
    @DisplayName("normalizeTitle(): 끝에 붙은 판매단위 꼬리(세트·전3권·상·하·N권)를 뗀다")
    void normalizeTitle_stripsVolumeTail() {
        assertThat(BookNewsMatcher.normalizeTitle(RAW_ANNA_TITLE)).isEqualTo("안나 카레니나");
        assertThat(BookNewsMatcher.normalizeTitle("안나 카레니나 전3권")).isEqualTo("안나 카레니나");
        assertThat(BookNewsMatcher.normalizeTitle("토지 1권")).isEqualTo("토지");
        assertThat(BookNewsMatcher.normalizeTitle("장미의 이름 상")).isEqualTo("장미의 이름");
        assertThat(BookNewsMatcher.normalizeTitle("장미의 이름 하")).isEqualTo("장미의 이름");
    }

    @Test
    @DisplayName("normalizeTitle(): 이미 깨끗한 제목·제목 안의 하이픈은 그대로 둔다 (「1984」·「e-book」)")
    void normalizeTitle_keepsCleanTitles() {
        assertThat(BookNewsMatcher.normalizeTitle("1984")).isEqualTo("1984");
        assertThat(BookNewsMatcher.normalizeTitle("데미안")).isEqualTo("데미안");
        assertThat(BookNewsMatcher.normalizeTitle("총, 균, 쇠")).isEqualTo("총, 균, 쇠");
        // 부제 구분자는 '공백 하이픈 공백' 뿐 — 단어에 붙은 하이픈은 제목의 일부다
        assertThat(BookNewsMatcher.normalizeTitle("e-book 혁명")).isEqualTo("e-book 혁명");
    }

    @Test
    @DisplayName("normalizeTitle(): null·괄호뿐인 병리적 제목은 빈 문자열")
    void normalizeTitle_pathological() {
        assertThat(BookNewsMatcher.normalizeTitle(null)).isEmpty();
        assertThat(BookNewsMatcher.normalizeTitle("(전2권)")).isEmpty();
    }

    @Test
    @DisplayName("normalizeAuthor(): 역할표기를 떼고 첫 저자만 남긴다 (운영 실값 6종)")
    void normalizeAuthor_keepsFirstWriterOnly() {
        assertThat(BookNewsMatcher.normalizeAuthor(RAW_COSMOS_AUTHOR)).isEqualTo("칼 세이건");
        assertThat(BookNewsMatcher.normalizeAuthor(RAW_1984_AUTHOR)).isEqualTo("조지 오웰");
        assertThat(BookNewsMatcher.normalizeAuthor(RAW_STRANGER_AUTHOR)).isEqualTo("알베르 카뮈");
        assertThat(BookNewsMatcher.normalizeAuthor(RAW_ODYSSEY_AUTHOR)).isEqualTo("호메로스");
        assertThat(BookNewsMatcher.normalizeAuthor(RAW_ANNA_AUTHOR)).isEqualTo("레프 니콜라예비치 톨스토이");
        assertThat(BookNewsMatcher.normalizeAuthor(RAW_DEMIAN_AUTHOR)).isEqualTo("헤르만 헤세");
    }

    @Test
    @DisplayName("normalizeAuthor(): 이미 깨끗한 값은 불변, null·역자뿐인 값은 빈 문자열")
    void normalizeAuthor_edgeCases() {
        assertThat(BookNewsMatcher.normalizeAuthor("헤르만 헤세")).isEqualTo("헤르만 헤세");
        assertThat(BookNewsMatcher.normalizeAuthor("유발 하라리 (지은이)")).isEqualTo("유발 하라리");
        assertThat(BookNewsMatcher.normalizeAuthor(null)).isEmpty();
        assertThat(BookNewsMatcher.normalizeAuthor("홍승수 (옮긴이)")).isEmpty();
    }

    @Test
    @DisplayName("운영 원문(부제·역할표기 그대로)으로도 기사가 채택된다 — 29종 0건의 원인 회귀")
    void acceptsWithRawAladinMetadata() {
        assertThat(BookNewsMatcher.matches(
                "칼 세이건 「코스모스」 특별전 개막", RAW_COSMOS_TITLE, RAW_COSMOS_AUTHOR)).isTrue();
        assertThat(BookNewsMatcher.matches(
                "조지 오웰의 1984, 40년 만에 다시 읽는다", "1984", RAW_1984_AUTHOR)).isTrue();
        assertThat(BookNewsMatcher.matches(
                "알베르 카뮈 이방인 출간 80주년 기획전", "이방인", RAW_STRANGER_AUTHOR)).isTrue();
        assertThat(BookNewsMatcher.matches(
                "호메로스 오뒷세이아 완역본 화제", RAW_ODYSSEY_TITLE, RAW_ODYSSEY_AUTHOR)).isTrue();
        assertThat(BookNewsMatcher.matches(
                "레프 니콜라예비치 톨스토이 안나 카레니나 새 번역본", RAW_ANNA_TITLE, RAW_ANNA_AUTHOR)).isTrue();
        assertThat(BookNewsMatcher.matches(
                "헤르만 헤세 데미안 100주년 기념판 출간", "데미안", RAW_DEMIAN_AUTHOR)).isTrue();
    }

    @Test
    @DisplayName("정규화해도 오탐은 여전히 기각한다 — 원문 저자로도 「데미안 월튼」은 안 문다")
    void stillRejectsFalsePositivesWithRawMetadata() {
        assertThat(BookNewsMatcher.matches(
                "웨이코 그룹 데미안 월튼 사장 주식 매각", "데미안", RAW_DEMIAN_AUTHOR)).isFalse();
    }

    @Test
    @DisplayName("정규화 결과가 비면(괄호뿐인 제목·역자뿐인 저자) 기각 — AND를 만들 수 없다")
    void rejectsWhenNormalizedMetadataEmpty() {
        assertThat(BookNewsMatcher.matches("(전2권) 홍승수 특별판", "(전2권)", RAW_COSMOS_AUTHOR)).isFalse();
        assertThat(BookNewsMatcher.matches("코스모스 홍승수 번역 특별판", "코스모스", "홍승수 (옮긴이)")).isFalse();
    }
}
