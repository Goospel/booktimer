package com.booktimer.writing;

import com.booktimer.writing.ClaudeWritingAssistant.SentItem;

import java.util.ArrayList;
import java.util.List;

/**
 * 처리방침 6-3절 대조(v1 REQ-43) — 순수. 보내는 것의 단일 목록({@link SentItem})과 문서가 같이 움직이게 한다.
 * 테스트가 실제 {@code privacy.html}을 읽어 부른다.
 */
final class WritingPrivacyGuard {

    static final String NOTHING_ELSE = "그 밖의 이용 기록은 보내지 않습니다";

    private WritingPrivacyGuard() {
    }

    /** {@link SentItem#privacyLabel} 전부 + {@value #NOTHING_ELSE} 중 6-3절 본문에 없는 것. */
    static List<String> missingItems(String privacySection63) {
        List<String> missing = new ArrayList<>();
        for (SentItem item : SentItem.values()) {
            if (!privacySection63.contains(item.privacyLabel)) {
                missing.add(item.privacyLabel);
            }
        }
        if (!privacySection63.contains(NOTHING_ELSE)) {
            missing.add(NOTHING_ELSE);
        }
        return missing;
    }
}
