package com.booktimer.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 처리방침 · 약관의 PR-M 문장 가드(REQ-85) — 리터럴을 문서 전체가 아니라 <b>그 절 · 조 머리 사이</b>에서 찾는다.
 * 다른 절에 들어간 문장은 통과하지 못한다. 스프링 컨텍스트 없이 실제 템플릿 원본을 읽는다.
 *
 * <p>가드가 망가져 늘 빈 목록을 내는 것을 막으려고 같은 판정에 심은 픽스처(하나를 뺀 것 · 다른 조에 넣은 것 · 옛 문장을 심은 것)를
 * 넣어 그 규칙 이름이 나오는지 먼저 단언한다(양성 대조).
 */
class LegalLiteralTest {

    /** 판정 하나 — 이름 · 문서 · 그 문서에서 찾을 구간 · 있어야(또는 없어야) 할 리터럴. */
    record Rule(String name, boolean privacy, Function<String, String> section, String literal, boolean mustExist) {
    }

    static String between(String s, String start, String end) {
        int from = s.indexOf(start);
        if (from < 0) return "";
        int to = s.indexOf(end, from + start.length());
        return to < 0 ? "" : s.substring(from, to);
    }

    /** 제3조의 n번째 항(1부터) — {@code <li>}로 자른다. */
    static String article3Item(String terms, int n) {
        String[] items = between(terms, "<h2>제3조", "<h2>제4조").split("<li>", -1);
        return items.length > n ? items[n] : "";
    }

    private static Function<String, String> privacy2() {
        return s -> between(s, "<h2>2.", "<h2>3.");
    }

    static final List<Rule> RULES = List.of(
            new Rule("privacy 2절 · 카카오 로그인 식별자", true, privacy2(), "카카오 로그인 식별자", true),
            new Rule("privacy 2절 · 구글 로그인 식별자", true, privacy2(), "구글 로그인 식별자", true),
            new Rule("privacy 2절 · 같은 이메일의 웹 계정", true, privacy2(), "같은 이메일의 웹 계정", true),
            new Rule("privacy 2절 · 처음 로그인한 구글 계정", true, privacy2(), "처음 로그인한 구글 계정", true),
            new Rule("privacy 2절 · 구글 워크스페이스 주소", true, privacy2(), "구글 워크스페이스 주소", true),
            new Rule("privacy 2절 · 옛 문장 「만 전달받아」 없음", true, privacy2(), "만 전달받아", false),
            new Rule("privacy 6-4절 · 650 Industries", true, s -> between(s, "<h2>6-4.", "<h2>7."), "650 Industries", true),
            new Rule("privacy 8절 · 주식회사 카카오", true, s -> between(s, "<h2>8.", "<h2>9."), "주식회사 카카오", true),
            new Rule("privacy 8절 · 650 Industries", true, s -> between(s, "<h2>8.", "<h2>9."), "650 Industries", true),
            new Rule("terms 제2조 · 아이패드", false, s -> between(s, "<h2>제2조", "<h2>제3조"), "아이패드", true),
            new Rule("terms 제3조 ② · 카카오 로그인 계정", false, s -> article3Item(s, 2), "카카오 로그인 계정", true),
            new Rule("terms 제3조 ② · 같은 이메일", false, s -> article3Item(s, 2), "같은 이메일", true),
            new Rule("terms 제3조 ② · 처음 로그인한 구글 계정", false, s -> article3Item(s, 2), "처음 로그인한 구글 계정", true),
            new Rule("terms 제3조 ② · 구글 워크스페이스 주소", false, s -> article3Item(s, 2), "구글 워크스페이스 주소", true),
            new Rule("terms 제3조 ④ · 구글로 다시 확인", false, s -> article3Item(s, 4), "구글로 다시 확인", true),
            new Rule("terms 제3조 ④ · 구글 워크스페이스 주소", false, s -> article3Item(s, 4), "구글 워크스페이스 주소", true),
            // 지키는 줄 — 11/23 시행 공지와 그 조각들은 이 PR이 한 글자도 바꾸지 않는다
            new Rule("지키는 줄 · privacy 6-3절 시행일", true, s -> between(s, "<h2>6-3.", "<h2>7."), "시행: 2026-11-23", true),
            new Rule("지키는 줄 · privacy 10-21 개정 공지", true, s -> s,
                    "시행일: 2026-06-03 · 2026-10-21 개정 공지 — 2절 Apple 로그인 식별자·여백·앱 글쓰기(즉시, 그중 AI 질문은 2026-11-23 시행) ·", true),
            new Rule("지키는 줄 · terms 10-21 개정 공지", false, s -> s,
                    "시행일: 2026-08-09 · 2026-10-21 개정 공지 — 제11조 생성형 AI 기능 신설(2026-11-23 시행), 문의를 제12조로 이동,", true),
            new Rule("지키는 줄 · privacy 2절 AI 질문", true, privacy2(), "(2026-11-23 시행) 그 글에 대해 받은 AI 질문", true),
            new Rule("지키는 줄 · privacy 8절 6-3절", true, s -> between(s, "<h2>8.", "<h2>9."), "2026-11-23부터는 <strong>6-3절</strong>", true));

    /** 공백(줄바꿈 · 들여쓰기)을 한 칸으로 — 문장이 줄을 넘어가도 같은 리터럴로 찾는다. */
    static String squash(String html) {
        return html.replaceAll("\\s+", " ");
    }

    /** @return 지켜지지 않은 규칙 이름(빈 목록 = 전부 지켜짐) */
    static List<String> violations(String privacyHtml, String termsHtml) {
        String privacy = squash(privacyHtml);
        String terms = squash(termsHtml);
        List<String> broken = new ArrayList<>();
        for (Rule r : RULES) {
            boolean found = r.section().apply(r.privacy() ? privacy : terms).contains(r.literal());
            if (found != r.mustExist()) broken.add(r.name());
        }
        return broken;
    }

    private static String template(String name) throws IOException {
        return new ClassPathResource("templates/" + name).getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("REQ-85 · 처리방침·약관의 그 절·조 안에 카카오·구글 식별자·Expo·같은 이메일·처음 로그인한 구글 계정·구글 워크스페이스 주소·구글로 다시 확인·아이패드 문장이 있고 옛 문장 「만 전달받아」가 없으며 지키는 줄 넷이 그대로다(뺀 픽스처·다른 조 픽스처·옛 문장 픽스처는 그 이름을 돌려준다)")
    void legalLiteralsInTheirSections() throws IOException {
        String privacy = template("privacy.html");
        String terms = template("terms.html");

        assertThat(violations(privacy, terms)).isEmpty();

        // 양성 대조 ① — 하나를 뺀 픽스처
        assertThat(violations(privacy.replace("카카오 로그인 식별자", "카카오 식별값"), terms))
                .containsExactly("privacy 2절 · 카카오 로그인 식별자");
        // ② — 다른 조에 넣은 픽스처: 제3조 ④의 문장을 ⑤로 옮기면 ④ 규칙이 잡는다
        String moved = squash(terms).replace("구글로 다시 확인", "구글 확인");
        moved = moved.replace("그 밖의 계정은 토스 연결만 해제됩니다.", "그 밖의 계정은 토스 연결만 해제됩니다. 구글로 다시 확인");
        assertThat(violations(privacy, moved)).containsExactly("terms 제3조 ④ · 구글로 다시 확인");
        // ③ — 옛 문장을 심은 픽스처
        String planted = squash(privacy).replace("<h2>3.", "<p>이메일과 이름만 전달받아</p><h2>3.");
        assertThat(violations(planted, terms)).containsExactly("privacy 2절 · 옛 문장 「만 전달받아」 없음");
        // ④ — 지키는 줄을 건드린 픽스처
        assertThat(violations(privacy, terms.replace("2026-10-21 개정 공지 — 제11조", "2026-10-23 개정 공지 — 제11조")))
                .containsExactly("지키는 줄 · terms 10-21 개정 공지");
    }
}
