package com.booktimer.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 본문 폭 규약 가드 — app.css·템플릿 소스를 직접 읽는다({@link SideRailsTemplateTest} 꼴).
 *
 * <p>바 있는 화면은 기본 1160, 한 열 화면만 {@code .narrow-page} 720, 바 없는 화면은 전역 460.
 * 페이지마다 {@code .container} 폭을 따로 다는 규칙이 다시 흩어지지 않게 허용 목록으로 막는다.
 * 설계 claude-docs/plans/2026-09-15-web-wider-body.md §6 T-1.
 */
class ContainerWidthTest {

    private static final Path APP_CSS = Path.of("src/main/resources/static/css/app.css");
    private static final Path TEMPLATES = Path.of("src/main/resources/templates");

    /** 한 열(목록·폼·글) 화면 — 1160이면 「팔로우」 버튼·시간이 저 멀리 떨어진다(설계 §2-1 ④). */
    private static final Set<String> NARROW_PAGES = Set.of(
            "book-detail", "book-readers", "search", "follow-list", "block-list",
            "feedback", "manual-session", "personality");

    /** SideRailsTemplateTest.RAIL_PAGES와 같은 14개 — narrow-page는 바 있는 화면에서만 뜻이 있다. */
    private static final Set<String> RAIL_PAGES = Set.of(
            "dashboard", "books", "book-detail", "book-readers", "history", "personality", "search",
            "profile", "follow-list", "block-list", "study", "settings", "feedback", "manual-session");

    /** 주석을 걷은 app.css — 주석이 값을 인용하면 공허하게 통과한다(T-205). */
    private static String css() throws IOException {
        return Files.readString(APP_CSS).replaceAll("(?s)/\\*.*?\\*/", "");
    }

    /** (셀렉터 한 조각, 선언부) — 미디어 블록 안 규칙 포함, 가장 안쪽 규칙만. */
    private static List<String[]> rules() throws IOException {
        List<String[]> out = new ArrayList<>();
        Matcher m = Pattern.compile("([^{}]+)\\{([^{}]*)}").matcher(css());
        while (m.find()) {
            for (String sel : m.group(1).split(",")) out.add(new String[]{sel.trim(), m.group(2)});
        }
        return out;
    }

    private static List<String> declarationsOf(String selector) throws IOException {
        return rules().stream().filter(r -> r[0].equals(selector)).map(r -> r[1]).toList();
    }

    private static final Pattern MAX_WIDTH = Pattern.compile("max-width\\s*:");
    /** 셀렉터의 마지막 복합 선택자에 .container 클래스가 있다(.foo-container는 아님). */
    private static final Pattern LAST_IS_CONTAINER = Pattern.compile("(?:^|[\\s>+~])[^\\s>+~]*(?<![\\w-])\\.container(?![\\w-])[^\\s>+~]*$");

    @Test
    @DisplayName("① 바 있는 화면 기본 1160 · .narrow-page 720")
    void railDefaultsExist() throws IOException {
        assertThat(declarationsOf("body.has-rails .container"))
                .anySatisfy(d -> assertThat(d).containsPattern("max-width\\s*:\\s*1160px"));
        assertThat(declarationsOf("body.has-rails .container.narrow-page"))
                .anySatisfy(d -> assertThat(d).containsPattern("max-width\\s*:\\s*720px"));
    }

    @Test
    @DisplayName("② 전역 .container 460 그대로 — 바 없는 화면(약관·비밀번호·에러) 보호")
    void globalContainerStays460() throws IOException {
        assertThat(declarationsOf(".container"))
                .anySatisfy(d -> assertThat(d).containsPattern("max-width\\s*:\\s*460px"));
    }

    @Test
    @DisplayName("③ .container 폭 규칙은 허용 목록 4개뿐 — 페이지별 max-width 금지")
    void noPerPageContainerWidth() throws IOException {
        Set<String> found = new TreeSet<>();
        for (String[] r : rules()) {
            if (LAST_IS_CONTAINER.matcher(r[0]).find() && MAX_WIDTH.matcher(r[1]).find()) found.add(r[0]);
        }
        assertThat(found).containsExactlyInAnyOrder(
                ".container",
                "body.has-rails .container",
                "body.has-rails .container.narrow-page",
                "body.history-page.history-wide .container");
    }

    @Test
    @DisplayName("④ 데스크톱 2열 경계는 1100 하나 — 880 없음, 책장 .shelf-layout은 1100 블록 안")
    void desktopBreakpointIs1100() throws IOException {
        String css = css();
        assertThat(css.contains("min-width: 880px")).as("바 여백 전 옛 경계 880이 남았다").isFalse();
        Matcher m = Pattern.compile("@media \\(min-width: 1100px\\)").matcher(css);
        boolean shelfInside = false;
        while (m.find()) {
            int next = css.indexOf("@media", m.end());
            String block = css.substring(m.end(), next < 0 ? css.length() : next);
            if (block.contains(".shelf-layout")) shelfInside = true;
        }
        assertThat(shelfInside).as("@media (min-width: 1100px) 블록 안에 .shelf-layout").isTrue();
    }

    @Test
    @DisplayName("⑤ class=\"container narrow-page\" 템플릿 == 8개, 전부 바 있는 화면")
    void narrowPageTemplates() throws IOException {
        Set<String> found = new TreeSet<>();
        try (Stream<Path> s = Files.list(TEMPLATES)) {
            for (Path p : (Iterable<Path>) s.filter(x -> x.toString().endsWith(".html"))::iterator) {
                if (Files.readString(p).contains("class=\"container narrow-page\"")) {
                    String f = p.getFileName().toString();
                    found.add(f.substring(0, f.length() - ".html".length()));
                }
            }
        }
        assertThat(found).containsExactlyInAnyOrderElementsOf(NARROW_PAGES);
        assertThat(RAIL_PAGES).containsAll(found);
    }

    @Test
    @DisplayName("⑥ 폭 규칙이 사라진 옛 클래스(wide-page·shelf-page)가 템플릿에 없다")
    void deadClassesGone() throws IOException {
        assertThat(Files.readString(TEMPLATES.resolve("study.html")).contains("wide-page")).as("study.html의 wide-page").isFalse();
        assertThat(Files.readString(TEMPLATES.resolve("books.html")).contains("shelf-page")).as("books.html의 shelf-page").isFalse();
    }
}
