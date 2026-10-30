package com.booktimer.writing;

import com.booktimer.writing.ClaudeWritingAssistant.SentItem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 처리방침 6-3절 대조(v1 REQ-43) — 순수. 보내는 것의 단일 목록({@link SentItem})과 문서가 같이 움직이게 한다.
 * 테스트가 실제 {@code privacy.html}을 읽어 부른다.
 */
final class WritingPrivacyGuard {

    static final String NOTHING_ELSE = "그 밖의 이용 기록은 보내지 않습니다";

    private WritingPrivacyGuard() {
    }

    /**
     * {@link SentItem#privacyLabel} 전부 + {@value #NOTHING_ELSE} 중 6-3절 본문에 없는 것. 항목은 {@code <li>이름</li>}으로만 찾는다 —
     * 머리 문단에 같은 낱말(「지금 쓰는 글」)이 있어 맨 contains는 목록 항목이 빠져도 통과했다.
     */
    static List<String> missingItems(String privacySection63) {
        return missing(privacySection63, Arrays.stream(SentItem.values()).map(i -> i.privacyLabel).toList());
    }

    /** {@link BlogAiSentItem#privacyLabel} 전부 + {@value #NOTHING_ELSE} 중 6-5절 본문에 없는 것(책생글 K2 REQ-202 — 같은 규칙). */
    static List<String> missingBlogItems(String privacySection65) {
        return missing(privacySection65, Arrays.stream(BlogAiSentItem.values()).map(i -> i.privacyLabel).toList());
    }

    private static List<String> missing(String section, List<String> labels) {
        List<String> missing = new ArrayList<>();
        for (String label : labels) {
            if (!section.contains("<li>" + label + "</li>")) {
                missing.add(label);
            }
        }
        if (!section.contains(NOTHING_ELSE)) {
            missing.add(NOTHING_ELSE);
        }
        return missing;
    }
}
