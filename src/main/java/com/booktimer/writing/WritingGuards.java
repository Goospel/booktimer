package com.booktimer.writing;

import com.booktimer.writing.ClaudeWritingAssistant.Questions;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 질문 가드(v1 REQ-24) — 순수 정적. 「질문만」을 구조로 강제한다.
 *
 * <p>상한을 넘거나 물음표로 끝나지 않는 질문은 <b>자르지 않고 버린다</b>(잘린 질문은 거짓이 된다). anchor만은 비운다(선택 필드).
 * 출처 대조는 정확한 부분 문자열 검사다. 가드가 보지 않는 것(「이렇게 써 보세요」류 지시 · 되풀이 · 얕음)은 프롬프트가 막는다.
 */
final class WritingGuards {

    static final int ASK_MAX = 6;
    static final int FOLLOW_MAX = 3;
    static final int QUESTION_MAX = 120;
    static final int ANCHOR_MAX = 40;
    static final int QUOTE_CHECK_MIN = 10;

    /** 끝에서 걷는 닫는 따옴표 — 「…?」처럼 물음표 뒤에 따옴표만 남은 질문을 통과시킨다. */
    private static final String CLOSERS = "’”」』'\"";

    // ponytail: ASCII ' 는 짝으로만 센다 — 1차 질문이 한국어 인용에 '…'를 썼다. 영어 소유격(Harry's)과 겹쳐 잘못 버리면
    // 2차 F3의 버린 이유에서 보인다 → 그때 짝 규칙을 좁힌다.
    private static final List<Pattern> QUOTES = List.of(
            Pattern.compile("‘([^’]*)’"), Pattern.compile("“([^”]*)”"),
            Pattern.compile("「([^」]*)」"), Pattern.compile("『([^』]*)』"),
            Pattern.compile("'([^']*)'"), Pattern.compile("\"([^\"]*)\""));

    private WritingGuards() {
    }

    /** ASK — 출처 = 재료, 따옴표 검사 없음. */
    static Optional<Questions> ask(Questions raw, String materialText) {
        return guard(raw, ASK_MAX, materialText, false);
    }

    /** FOLLOW — 출처 = 재료 + 사용자 글, 따옴표 속 10자 이상 조각이 출처에 없으면 그 질문을 버린다. */
    static Optional<Questions> follow(Questions raw, String materialText, String body) {
        return guard(raw, FOLLOW_MAX, materialText + "\n" + (body == null ? "" : body), true);
    }

    /** ‘’ '' "" “” 「」 『』 안의 조각 — 짝이 안 맞는 따옴표는 무시한다. */
    static List<String> quotedSpans(String text) {
        List<String> spans = new ArrayList<>();
        for (Pattern p : QUOTES) {
            Matcher m = p.matcher(text);
            while (m.find()) {
                spans.add(m.group(1));
            }
        }
        return spans;
    }

    /**
     * 재료 블록에서 머리줄([책] · [재료 …] · ### 여백 …)과 「옮긴 문장: 」「메모: 」 접두를 걷은 원문 — 머리줄 낱말이 anchor로
     * 통과하지 않게 한다. 「옮긴 문장: 없음」은 자리표시라 뺀다.
     */
    static String materialText(String materialBlock) {
        StringBuilder out = new StringBuilder();
        for (String line : materialBlock.split("\\R", -1)) {
            if (line.startsWith("[책]") || line.startsWith("[재료") || line.startsWith("### 여백")
                    || line.equals("옮긴 문장: 없음")) {
                continue;
            }
            String text = line.startsWith("옮긴 문장: ") ? line.substring("옮긴 문장: ".length())
                    : line.startsWith("메모: ") ? line.substring("메모: ".length()) : line;
            out.append(text).append('\n');
        }
        return out.toString();
    }

    private static Optional<Questions> guard(Questions raw, int max, String haystack, boolean checkQuotes) {
        if (raw == null || raw.questions() == null) {
            return Optional.empty();
        }
        List<Question> kept = new ArrayList<>();
        for (Question q : raw.questions()) {
            if (kept.size() == max) {
                break;
            }
            if (q == null || q.text() == null) {
                continue;
            }
            String text = q.text().strip();
            if (text.isEmpty() || text.length() > QUESTION_MAX || !endsWithQuestionMark(text)) {
                continue;
            }
            if (checkQuotes && quotedSpans(text).stream()
                    .anyMatch(span -> span.length() >= QUOTE_CHECK_MIN && !haystack.contains(span))) {
                continue;
            }
            kept.add(new Question(text, anchor(q.anchor(), haystack)));
        }
        return kept.isEmpty() ? Optional.empty() : Optional.of(new Questions(List.copyOf(kept)));
    }

    private static boolean endsWithQuestionMark(String text) {
        int end = text.length();
        while (end > 0 && (Character.isWhitespace(text.charAt(end - 1)) || CLOSERS.indexOf(text.charAt(end - 1)) >= 0)) {
            end--;
        }
        return end > 0 && (text.charAt(end - 1) == '?' || text.charAt(end - 1) == '？');
    }

    private static String anchor(String anchor, String haystack) {
        if (anchor == null) {
            return null;
        }
        String a = anchor.strip();
        return a.isEmpty() || a.length() > ANCHOR_MAX || !haystack.contains(a) ? null : a;
    }
}
