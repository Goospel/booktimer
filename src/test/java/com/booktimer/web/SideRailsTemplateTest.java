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
            "admin-quotes", "admin-feedback", "admin-reports", "admin-chat");

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
        assertThat(used).as("필기 바 항목의 아이콘").contains("notes");
        assertThat(used).as("홈 항목은 로고(a.brand-home)가 대신한다 — 바에 없다").doesNotContain("home");
    }

    /** 로고 링크 `<a class="brand-home" …> … </a>` 안쪽(여러 줄). 없으면 null. */
    private static final Pattern BRAND_HOME =
            Pattern.compile("<a\\b[^>]*class=\"brand-home\"[^>]*>(.*?)</a>", Pattern.DOTALL);

    @Test
    @DisplayName("바 쓰는 페이지 전수에 로고 홈 링크 + 접근 이름의 「홈」이 있다 — 바에서 홈 항목을 뺀 뒤 유일한 홈 길")
    void railPagesKeepLogoHomeLink() throws IOException {
        // 바에 홈 항목이 없으니 홈으로 가는 길은 이 로고 하나다(설계 2026-09-16-rail-home-entry).
        // 어느 한 템플릿에서 로고가 떨어지면 그 페이지는 홈으로 갈 길이 없는 막다른 길이 되는데,
        // 렌더된 그 페이지를 열어보기 전엔 안 보인다 — include ⇔ has-rails 가드와 같은 이유로 전수로 잠근다.
        // 목록은 손으로 열거하지 않고 include 집합에서 계산한다(열거하면 새 페이지가 조용히 빠진다).
        Set<String> railPages = pagesMatching(INCLUDE);
        assertThat(railPages).as("훑을 대상이 있어야 한다(공허 방지)").isNotEmpty();
        try (Stream<Path> s = pages()) {
            for (Path p : (Iterable<Path>) s::iterator) {
                if (!railPages.contains(name(p))) continue;
                Matcher m = BRAND_HOME.matcher(Files.readString(p));
                assertThat(m.find()).as("%s: 바에 홈 항목이 없으니 로고가 유일한 홈 링크다", name(p)).isTrue();
                // 접근 이름은 내용에서 계산된다 — title 속성은 내용이 있으면 이름이 되지 않는다.
                // sr-only 텍스트라 보이는 텍스트(h1)를 덮지 않고 더한다(WCAG 2.5.3 Label in Name 유지).
                assertThat(m.group(1))
                        .as("%s: 스크린리더 링크 목록에 「홈」이 나와야 한다", name(p))
                        .contains("class=\"sr-only\">홈<");
            }
        }
    }

    @Test
    @DisplayName("바에 홈 링크가 없고, 홈이 열릴 모드는 data-remember로 나간다(2026-09-16-rail-home-entry)")
    void noHomeLinkButRemembersMode() throws IOException {
        String src = Files.readString(TEMPLATES.resolve("fragments/side-rails.html"));
        assertThat(src).as("홈 링크 잔존 — 로고가 홈이다").doesNotContain("th:href=\"@{/}\"");
        assertThat(src).as("양성 대조 — 다른 링크는 그대로").contains("th:href=\"@{/books}\"");
        assertThat(src).contains("data-remember=${rail.rememberMode}");
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

    // ── 바 하나 + 스위치(설계 2026-09-17-single-rail-mode-switch) ──

    private static int count(String src, String needle) {
        int n = 0;
        for (int i = src.indexOf(needle); i >= 0; i = src.indexOf(needle, i + 1)) n++;
        return n;
    }

    @Test
    @DisplayName("fragment의 바는 <aside class=\"rail\"> 하나, 안에 독서·공부 nav 각 하나(D1·D9)")
    void singleRailWithTwoNavs() throws IOException {
        String src = Files.readString(TEMPLATES.resolve("fragments/side-rails.html"));
        assertThat(count(src, "<aside")).as("바가 둘이면 모드가 흐림으로만 드러난다").isEqualTo(1);
        assertThat(count(src, "<aside class=\"rail\">")).isEqualTo(1);
        assertThat(count(src, "<nav class=\"rail-nav-reading\" aria-label=\"독서 이동\">")).isEqualTo(1);
        assertThat(count(src, "<nav class=\"rail-nav-study\" aria-label=\"공부 이동\">")).isEqualTo(1);
    }

    @Test
    @DisplayName("app.css에 옛 두 바 셀렉터(.rail-reading·.rail-study)가 없고 모드 nav 셀렉터가 있다")
    void noOldTwoRailSelectors() throws IOException {
        String css = Files.readString(APP_CSS).replaceAll("(?s)/\\*.*?\\*/", "");
        assertThat(css).as("양성 대조 — 모드별 nav 숨김 규칙").contains(".rail-nav-study");
        assertThat(css).doesNotContainPattern("\\.rail-study\\b").doesNotContainPattern("\\.rail-reading\\b");
    }

    @Test
    @DisplayName("PWA 칩 오른쪽 바 우회 규칙이 없다(오른쪽 바가 사라졌다) — 음성 판정 전용: 칩 발화는 브라우저 몫이라 양성 대조군 없음")
    void noPwaChipRightRailOffset() throws IOException {
        assertThat(declarationsOf("body.has-rails #pwa-install-chip")).isEmpty();
    }

    @Test
    @DisplayName("하단 여백은 바 있는 화면 전부(body.has-rails) — 스위치가 모든 바 화면에 뜬다(D7)")
    void railPagesClearSwitchAtBottom() throws IOException {
        assertThat(declarationsOf("body.has-rails"))
                .anySatisfy(d -> assertThat(d).containsPattern("padding-bottom\\s*:"));
    }

    @Test
    @DisplayName("비홈 SSR 스위치는 홈에서 안 그린다(홈은 Vue ModeToggle) — th:unless 홈")
    void ssrSwitchSkipsHome() throws IOException {
        String src = Files.readString(TEMPLATES.resolve("fragments/side-rails.html"));
        assertThat(src).containsPattern("<div class=\"dash-mode-toggle-wrap\" th:unless=\"\\$\\{rail\\.activeKey == 'home'}\">");
    }

    @Test
    @DisplayName("접힌 바는 짧은 창에서 세로로 스크롤된다(overflow-y:auto) — 아래 메뉴가 잘리지 않게")
    void railScrollsVertically() throws IOException {
        java.util.List<String> decls = declarationsOf(".rail");
        assertThat(decls).anySatisfy(d -> assertThat(d).containsPattern("overflow-y\\s*:\\s*auto"));
        assertThat(decls).noneSatisfy(d -> assertThat(d).containsPattern("overflow\\s*:\\s*hidden"));
    }

    // ── 본문으로 건너뛰기(skip link) ──
    // 바가 .container보다 DOM 앞이라 로고에 닿기까지 Tab 10번이었다(바 링크 9개를 지나서).
    // 스킵 링크가 바보다 앞에 있어야만 「첫 Tab = 건너뛰기」가 성립한다 — 뒤로 밀리면 존재해도 무의미하다.

    private static final String MAIN_ID = "main-content";
    /** 도착 지점 태그 — id가 붙은 여는 태그 하나. */
    private static final Pattern MAIN_TAG = Pattern.compile("<[a-zA-Z]+\\b[^>]*\\bid=\"" + MAIN_ID + "\"[^>]*>");

    @Test
    @DisplayName("스킵 링크가 바 <aside>보다 먼저 나오고 본문 id를 가리킨다 — 순서가 뒤집히면 첫 Tab이 바에 잡힌다")
    void skipLinkComesBeforeRails() throws IOException {
        String src = Files.readString(TEMPLATES.resolve("fragments/side-rails.html"));
        int skip = src.indexOf("href=\"#" + MAIN_ID + "\"");
        int aside = src.indexOf("<aside");
        assertThat(skip).as("스킵 링크가 fragment에 있어야 한다").isGreaterThanOrEqualTo(0);
        assertThat(aside).as("양성 대조 — 바 <aside>는 그대로 있다").isGreaterThanOrEqualTo(0);
        assertThat(skip).as("스킵 링크는 바보다 앞이어야 한다(문서 순서 = Tab 순서)").isLessThan(aside);
    }

    @Test
    @DisplayName("바 쓰는 페이지 전수에 도착 지점(id + tabindex=\"-1\")이 하나씩 있다")
    void railPagesHaveSkipTarget() throws IOException {
        // 목록은 손으로 열거하지 않고 include 집합에서 계산한다(열거하면 새 페이지가 조용히 빠진다).
        Set<String> railPages = pagesMatching(INCLUDE);
        assertThat(railPages).as("훑을 대상이 있어야 한다(공허 방지)").isNotEmpty();
        try (Stream<Path> s = pages()) {
            for (Path p : (Iterable<Path>) s::iterator) {
                if (!railPages.contains(name(p))) continue;
                Matcher m = MAIN_TAG.matcher(Files.readString(p));
                assertThat(m.find()).as("%s: 스킵 링크가 갈 곳이 없다(id=%s 누락)", name(p), MAIN_ID).isTrue();
                // tabindex="-1" 없이는 브라우저에 따라 앵커 이동 뒤 포커스가 문서 맨 앞에 남는다.
                assertThat(m.group()).as("%s: 도착 지점이 포커스를 받아야 한다", name(p)).contains("tabindex=\"-1\"");
                assertThat(m.find()).as("%s: id가 둘이면 앵커가 어디로 갈지 정해지지 않는다", name(p)).isFalse();
            }
        }
    }

    @Test
    @DisplayName("스킵 링크는 포커스 전엔 화면 밖, 포커스하면 들어온다 — .sr-only(항상 숨김)를 쓰면 안 된다")
    void skipLinkRevealsOnFocus() throws IOException {
        String css = Files.readString(APP_CSS).replaceAll("(?s)/\\*.*?\\*/", "");
        assertThat(declarationsOf(".skip-link"))
                .as("기본 규칙이 있어야 한다")
                .anySatisfy(d -> assertThat(d).containsPattern("position\\s*:\\s*fixed"));
        assertThat(declarationsOf(".skip-link:focus"))
                .as("포커스 시 화면 안으로 들어오는 규칙")
                .anySatisfy(d -> assertThat(d).containsPattern("top\\s*:"));
        assertThat(css).as("바(z-index 95) 위에 떠야 가려지지 않는다").containsPattern("\\.skip-link\\b[^{]*\\{[^}]*z-index");
        String fragment = Files.readString(TEMPLATES.resolve("fragments/side-rails.html"));
        assertThat(fragment).as("sr-only는 포커스해도 안 보인다 — 스킵 링크엔 쓸 수 없다")
                .doesNotContainPattern("class=\"[^\"]*\\bsr-only\\b[^\"]*\"[^>]*href=\"#");
    }
}
