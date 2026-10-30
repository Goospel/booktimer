package com.booktimer.writing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 서버의 AI 시행일 상수가 처리방침 6-3절 시행일과 같은가(v1 REQ-74 · R-17).
 *
 * <p>두 날짜가 갈리면 처리방침이 약속한 날보다 먼저 국외 전송이 열리거나(30일 공지 위반), 늦게 열린다.
 * 통과하면 확정: 상수 = 실제 템플릿의 6-3절 「시행: …」. 실패하면 배제: 둘 중 한 곳만 고친 개정.
 */
class WritingNoticeDateTest {

    /** 6-3절 머리 뒤 첫 「시행: yyyy-MM-dd」 — 다른 절의 시행일을 집지 않게 절 머리부터 찾는다. */
    private static final Pattern SECTION_6_3 =
            Pattern.compile("<h2>6-3\\.[\\s\\S]*?시행: (\\d{4}-\\d{2}-\\d{2})");

    static LocalDate effectiveOf63(String html) {
        Matcher m = SECTION_6_3.matcher(html);
        assertThat(m.find()).as("처리방침에서 6-3절의 「시행: yyyy-MM-dd」를 찾지 못했다").isTrue();
        return LocalDate.parse(m.group(1));
    }

    private static String privacyHtml() throws IOException {
        return new ClassPathResource("templates/privacy.html").getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("REQ-74 · AI_NOTICE_EFFECTIVE는 처리방침 6-3절 시행일과 같다")
    void constantMatchesPrivacy() throws IOException {
        assertThat(effectiveOf63(privacyHtml())).isEqualTo(WritingService.AI_NOTICE_EFFECTIVE);
    }

    @Test
    @DisplayName("REQ-74 · 날짜를 바꾼 처리방침 픽스처는 불일치로 잡는다(양성 대조)")
    void changedFixtureIsCaught() throws IOException {
        String real = privacyHtml();
        String effective = WritingService.AI_NOTICE_EFFECTIVE.toString();
        String moved = real.replaceFirst("(<h2>6-3\\.[\\s\\S]*?시행: )" + effective,
                "$1" + WritingService.AI_NOTICE_EFFECTIVE.plusDays(1));

        assertThat(moved).as("픽스처가 실제로 6-3절 날짜를 바꿨다").isNotEqualTo(real);
        assertThat(effectiveOf63(moved)).isNotEqualTo(WritingService.AI_NOTICE_EFFECTIVE);
    }

    // ── E2(책생글 K2 — 넓어진 AI · 블로그 시행일) ─────────────────────────────────
    // 통과하면 확정: AI_EXPANDED_EFFECTIVE = 6-5절 「시행: …」 = 약관 제12조 「시행: …」. 실패하면 배제: 셋 중 한 곳만 고친 개정
    // (K2 머지가 밀려 E2를 다시 셀 때 흔히 생긴다).

    /** 절 머리 뒤 첫 「시행: yyyy-MM-dd」. */
    static LocalDate effectiveAfter(String html, String head) {
        Matcher m = Pattern.compile(Pattern.quote(head) + "[\\s\\S]*?시행: (\\d{4}-\\d{2}-\\d{2})").matcher(html);
        assertThat(m.find()).as(head + " 뒤의 「시행: yyyy-MM-dd」를 찾지 못했다").isTrue();
        return LocalDate.parse(m.group(1));
    }

    private static String termsHtml() throws IOException {
        return new ClassPathResource("templates/terms.html").getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("REQ-200 · AI_EXPANDED_EFFECTIVE(E2)는 처리방침 6-5절과 약관 제12조의 시행일과 같다")
    void expandedConstantMatchesBothDocuments() throws IOException {
        assertThat(effectiveAfter(privacyHtml(), "<h2>6-5.")).isEqualTo(WritingService.AI_EXPANDED_EFFECTIVE);
        assertThat(effectiveAfter(termsHtml(), "<h2>제12조")).isEqualTo(WritingService.AI_EXPANDED_EFFECTIVE);
    }

    @Test
    @DisplayName("REQ-200 · 6-5절 날짜를 하루 민 처리방침 픽스처는 불일치로 잡는다(양성 대조)")
    void changedExpandedFixtureIsCaught() throws IOException {
        String real = privacyHtml();
        String e2 = WritingService.AI_EXPANDED_EFFECTIVE.toString();
        String moved = real.replaceFirst("(<h2>6-5\\.[\\s\\S]*?시행: )" + e2,
                "$1" + WritingService.AI_EXPANDED_EFFECTIVE.plusDays(1));

        assertThat(moved).as("픽스처가 실제로 6-5절 날짜를 바꿨다").isNotEqualTo(real);
        assertThat(effectiveAfter(moved, "<h2>6-5.")).isNotEqualTo(WritingService.AI_EXPANDED_EFFECTIVE);
    }
}
