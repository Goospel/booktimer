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

    @Test
    @DisplayName("바 fragment의 icon('x') 이름은 전부 nav-icons.html 사전 키다(오타는 빈 SVG로 조용히 샌다)")
    void railIconsExistInDictionary() throws IOException {
        String rails = Files.readString(TEMPLATES.resolve("fragments/side-rails.html"));
        String icons = Files.readString(TEMPLATES.resolve("fragments/nav-icons.html"));
        Set<String> keys = new TreeSet<>();
        Matcher k = Pattern.compile("th:case=\"'([a-z]+)'\"").matcher(icons);
        while (k.find()) keys.add(k.group(1));
        Set<String> used = new TreeSet<>();
        Matcher u = Pattern.compile("nav-icons\\s*::\\s*icon\\('([^']+)'\\)").matcher(rails);
        while (u.find()) used.add(u.group(1));

        assertThat(used).as("훑을 대상이 있어야 한다(공허 방지)").hasSize(8);
        assertThat(keys).containsAll(used);
        assertThat(used).as("백지노트 바 항목의 아이콘").contains("note");
    }

    // ── app.css 규약(주석을 걷고 본다 — 주석이 값을 인용하면 공허하게 통과한다, T-205) ──

    private static final Path APP_CSS = Path.of("src/main/resources/static/css/app.css");

    /** 셀렉터에 needle이 든 가장 안쪽 규칙들의 선언부(미디어 블록 안 규칙 포함). */
    private static java.util.List<String> declarationsOf(String needle) throws IOException {
        String css = Files.readString(APP_CSS).replaceAll("(?s)/\\*.*?\\*/", "");
        java.util.List<String> out = new java.util.ArrayList<>();
        Matcher m = Pattern.compile("([^{}]+)\\{([^{}]*)}").matcher(css);
        while (m.find()) {
            for (String sel : m.group(1).split(",")) {
                if (sel.trim().equals(needle)) out.add(m.group(2));
            }
        }
        return out;
    }

    @Test
    @DisplayName("app.css에 .rail:hover 셀렉터가 없다 — hover로 펼치면 가로 배치가 즉시 뒤집혀 스침이 보인다(지연은 rail.js)")
    void noCssHoverExpansion() throws IOException {
        String css = Files.readString(APP_CSS).replaceAll("(?s)/\\*.*?\\*/", "");
        assertThat(css).contains(".rail.is-open"); // 양성 대조 — 펼침 규칙 자체는 있다
        assertThat(css).doesNotContainPattern("\\.rail:hover");
    }

    @Test
    @DisplayName("흐린 바 opacity ≥ .7 — 12.5px 라벨 4.5:1 · 아이콘 3:1(WCAG AA)")
    void dimmedRailKeepsContrast() throws IOException {
        java.util.List<String> decls = declarationsOf("#side-rails[data-mode=\"reading\"] .rail-study");
        assertThat(decls).hasSize(1);
        Matcher o = Pattern.compile("opacity\\s*:\\s*([0-9.]+)").matcher(decls.get(0));
        assertThat(o.find()).isTrue();
        assertThat(Double.parseDouble(o.group(1))).isGreaterThanOrEqualTo(0.7);
    }

    @Test
    @DisplayName("접힌 바는 짧은 창에서 세로로 스크롤된다(overflow-y:auto) — 아래 메뉴가 잘리지 않게")
    void railScrollsVertically() throws IOException {
        java.util.List<String> decls = declarationsOf(".rail");
        assertThat(decls).anySatisfy(d -> assertThat(d).containsPattern("overflow-y\\s*:\\s*auto"));
        assertThat(decls).noneSatisfy(d -> assertThat(d).containsPattern("overflow\\s*:\\s*hidden"));
    }

    @Test
    @DisplayName("PWA 설치 칩은 바가 있는 화면에서 오른쪽 바(16+92+16) 안쪽으로 비킨다")
    void pwaChipClearsRightRail() throws IOException {
        assertThat(declarationsOf("body.has-rails #pwa-install-chip"))
                .anySatisfy(d -> assertThat(d).containsPattern("right\\s*:\\s*124px\\s*!important"));
    }
}
