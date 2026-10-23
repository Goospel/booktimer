package com.booktimer.writing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 처리방침 6-3절이 실제로 보내는 항목({@link ClaudeWritingAssistant.SentItem})을 모두 적는가(v1 REQ-43 · 제28조의8 ②).
 *
 * <p>통과하면 확정: 실제 템플릿의 6-3절에 다섯 이름과 「그 밖의 이용 기록은 보내지 않습니다」가 있다. 실패하면 배제: 보내는 것을
 * 늘리고(SentItem 추가) 처리방침을 안 고친 개정. 양성 대조 — 항목 하나를 뺀 픽스처가 그 이름을 돌려주므로 「늘 빈 목록」 구현은 떨어진다.
 */
class WritingPrivacyGuardTest {

    private static String section63() throws IOException {
        String html = new ClassPathResource("templates/privacy.html").getContentAsString(StandardCharsets.UTF_8);
        int start = html.indexOf("<h2>6-3.");
        assertThat(start).as("처리방침에 6-3절 머리가 있다").isGreaterThanOrEqualTo(0);
        int end = html.indexOf("<h2>", start + 1);
        return html.substring(start, end < 0 ? html.length() : end);
    }

    @Test
    @DisplayName("REQ-43 · 처리방침 6-3절이 SentItem 이름 다섯 개와 「그 밖의 이용 기록은 보내지 않습니다」를 모두 담는다")
    void realSectionHasEverything() throws IOException {
        assertThat(ClaudeWritingAssistant.SentItem.values()).hasSize(5);
        assertThat(WritingPrivacyGuard.missingItems(section63())).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(ClaudeWritingAssistant.SentItem.class)
    @DisplayName("REQ-43 · 목록에서 항목 하나를 뺀 픽스처는 빠진 이름을 돌려준다(양성 대조 — 다섯 항목 전부)")
    void removedItemIsReported(ClaudeWritingAssistant.SentItem item) throws IOException {
        String real = section63();
        // 머리 문단에 같은 낱말이 있어도(「지금 쓰는 글」) 목록 항목이 빠진 것을 잡아야 한다
        String without = real.replace("<li>" + item.privacyLabel + "</li>", "");

        assertThat(without).as("픽스처가 실제로 항목을 뺐다").isNotEqualTo(real);
        assertThat(WritingPrivacyGuard.missingItems(without)).containsExactly(item.privacyLabel);
    }

    @Test
    @DisplayName("REQ-43 · 「그 밖의 이용 기록은 보내지 않습니다」를 뺀 픽스처와 빈 문서는 빠진 것을 돌려준다")
    void removedClosingIsReported() throws IOException {
        String withoutClosing = section63().replace("그 밖의 이용 기록은 보내지 않습니다", "");

        assertThat(WritingPrivacyGuard.missingItems(withoutClosing)).containsExactly("그 밖의 이용 기록은 보내지 않습니다");
        assertThat(WritingPrivacyGuard.missingItems("")).hasSize(6);
    }
}
