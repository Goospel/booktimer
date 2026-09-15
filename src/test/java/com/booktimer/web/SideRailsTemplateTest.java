package com.booktimer.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 양옆 세로 바 include 규약 가드 — 템플릿 소스를 직접 읽는다({@link AppStylesheetTypographyTest} 꼴).
 *
 * <p>공유 레이아웃이 없어 바는 페이지마다 include 한 줄 + {@code <body class="has-rails">}로 들어간다.
 * 한쪽만 있으면 바가 본문을 덮거나(여백 없음) 빈 여백만 남는다 — 눈으로는 그 페이지를 열어봐야 보인다.
 * 설계 claude-docs/plans/2026-09-15-web-side-rails.md §5 페이지 표.
 */
class SideRailsTemplateTest {

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");
    private static final Pattern INCLUDE = Pattern.compile("fragments/side-rails\\s*::\\s*rails");
    private static final Pattern BODY = Pattern.compile("<body\\b([^>]*)>");
    private static final Pattern NAV_LINK = Pattern.compile("fragments/nav-links\\s*::\\s*navLink");

    /** 바를 넣는 페이지 — 로그인 사용자용이면서 바가 이동 링크를 전부 대신하는 화면(§5). */
    private static final Set<String> RAIL_PAGES = Set.of(
            "dashboard", "books", "book-detail", "book-readers", "history", "personality", "search",
            "profile", "follow-list", "block-list", "study", "settings", "feedback", "manual-session");

    /** 하단 타일이 남는 페이지 — 바가 없는 화면(에러·로그인 전·관리자). */
    private static final Set<String> NAV_LINK_PAGES = Set.of(
            "error", "privacy", "terms", "password-forgot",
            "admin", "admin-users", "admin-user-detail", "admin-user-debt",
            "admin-quotes", "admin-feedback", "admin-reports");

    private static Stream<Path> pages() throws IOException {
        return Files.list(TEMPLATES).filter(p -> p.toString().endsWith(".html"));
    }

    private static String name(Path p) {
        String f = p.getFileName().toString();
        return f.substring(0, f.length() - ".html".length());
    }

    private static Set<String> pagesMatching(Pattern pattern) throws IOException {
        Set<String> names = new TreeSet<>();
        try (Stream<Path> s = pages()) {
            for (Path p : (Iterable<Path>) s::iterator) {
                if (pattern.matcher(Files.readString(p)).find()) names.add(name(p));
            }
        }
        return names;
    }

    @Test
    @DisplayName("바 include 집합 == 설계 페이지 표의 14개")
    void includeSet() throws IOException {
        assertThat(pagesMatching(INCLUDE)).containsExactlyInAnyOrderElementsOf(RAIL_PAGES);
    }

    @Test
    @DisplayName("include ⇔ <body class=\"has-rails\"> (양방향)")
    void includeMatchesBodyClass() throws IOException {
        try (Stream<Path> s = pages()) {
            for (Path p : (Iterable<Path>) s::iterator) {
                String src = Files.readString(p);
                Matcher body = BODY.matcher(src);
                boolean hasRailsClass = body.find() && body.group(1).matches("(?s).*class=\"[^\"]*\\bhas-rails\\b[^\"]*\".*");
                assertThat(hasRailsClass)
                        .as("%s: include(%s)와 body.has-rails가 일치해야 한다", name(p), INCLUDE.matcher(src).find())
                        .isEqualTo(INCLUDE.matcher(src).find());
            }
        }
    }

    @Test
    @DisplayName("하단 nav-links 타일이 남는 페이지 == 바 없는 화면 목록(제거 누락·과잉 제거)")
    void navLinkResidue() throws IOException {
        assertThat(pagesMatching(NAV_LINK)).containsExactlyInAnyOrderElementsOf(NAV_LINK_PAGES);
    }

    @Test
    @DisplayName("side-rails.html의 모든 <script>에 nonce가 있다")
    void fragmentScriptsHaveNonce() throws IOException {
        String src = Files.readString(TEMPLATES.resolve("fragments/side-rails.html"));
        Matcher m = Pattern.compile("<script\\b[^>]*>").matcher(src);
        int count = 0;
        while (m.find()) {
            count++;
            assertThat(m.group()).contains("nonce=${cspNonce}");
        }
        assertThat(count).as("인라인 부트 + rail.js 모듈").isEqualTo(2);
    }
}
