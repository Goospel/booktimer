package com.booktimer.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 웹 본문 타이포 규약 가드 — <b>기능 글자는 고운돋움 400</b>(2026-09-03).
 *
 * <p>미니앱이 「또렷한 연필」 ③-A(2026-08-24)에서 내린 결론을 웹에 옮긴 것이다. 근거 전문은
 * {@code miniapp/src/global.css} 의 {@code html body} 주석에 있다 — 요지는 셋이다.
 * ① 기본값이 다수(기능 글자)를 맡고 장식이 손드는 쪽만 안정적이다(방향이 뒤집히면 지정 안 한
 * 다음 화면이 다시 손글씨로 태어난다). ② 손글씨(Gaegu)를 <b>폴백으로도</b> 남기지 않는다 —
 * 남기면 고운돋움이 못 그리는 글리프만 손글씨로 떨어져 한 단어 안에서 서체가 갈린다.
 * ③ {@code font-weight} 를 400 으로 <b>명시</b>한다 — 700 은 개구의 얇은 획을 메우던 보정인데,
 * 고운돋움에서 700 은 전면 합성 볼드라 400/700 강조 축을 통째로 무너뜨린다.
 *
 * <p>Spring 컨텍스트 없이 <b>소스 파일을 직접 읽는다</b> — {@code PwaStaticAccessTest} 가
 * {@code manifest.json} 을 그렇게 읽는 전례를 따른다. build 산출물이 아니라 소스를 봐야
 * {@code processResources} 를 안 돌린 상태에서도 규약 위반을 잡는다.
 */
class AppStylesheetTypographyTest {

    private static final Path APP_CSS = Path.of("src/main/resources/static/css/app.css");

    /**
     * 주석을 걷어낸 css.
     *
     * <p>주석이 값을 인용하면 소스 단언이 <b>공허하게 통과</b>한다(T-205) — 예컨대 「Gaegu 를
     * 걷었다」는 설명 주석 하나가 "Gaegu 0건" 단언을 영영 빨간불로 만든다. 미니앱
     * {@code typography.test.tsx} 와 같은 처방.
     */
    private static String cssWithoutComments() throws IOException {
        return Files.readString(APP_CSS).replaceAll("(?s)/\\*.*?\\*/", "");
    }

    /**
     * {@code body { … }} 블록의 선언부.
     *
     * <p>뒤에 {@code body.landing-page}·{@code body.bookdetail-page} 스코프 규칙이 줄줄이 있어
     * <b>첫 매치</b>를 쓴다. lookbehind 는 {@code .entry-accordion-body} 같은 이름 꼬리가
     * {@code body} 로 오인되는 것을 막는다.
     */
    private static String bodyBlock() throws IOException {
        Matcher m = Pattern.compile("(?s)(?<![\\w.#-])body\\s*\\{(.*?)}").matcher(cssWithoutComments());
        assertThat(m.find()).as("app.css 에 body { … } 블록이 있어야 한다").isTrue();
        return m.group(1);
    }

    /** 블록 안의 한 선언 값(예: {@code font-family}) — 없으면 빈 문자열. */
    private static String declaration(String block, String property) {
        Matcher m = Pattern.compile("(?s)\\b" + property + "\\s*:\\s*([^;]+);").matcher(block);
        return m.find() ? m.group(1).replaceAll("\\s+", " ").trim() : "";
    }

    @Test
    @DisplayName("app.css 어디에도 손글씨 Gaegu 가 없다 (@import·폴백 스택 어느 쪽으로 새도 실패)")
    void gaegu_isGoneEntirely() throws IOException {
        // 파일 전체가 아니라 "샌 줄"만 들고 실패한다 — 4천 줄짜리 actual 덤프는 읽을 수 없다.
        List<String> leaks = cssWithoutComments().lines().filter(l -> l.contains("Gaegu")).toList();

        assertThat(leaks)
                .as("손글씨는 2026-09-03 에 걷었다 — @import 로도 폴백으로도 되살리지 않는다")
                .isEmpty();
    }

    @Test
    @DisplayName("body 의 font-family 스택 맨 앞이 고운돋움이다 (폴백으로 밀리면 실패)")
    void bodyFontFamily_startsWithGowunDodum() throws IOException {
        assertThat(declaration(bodyBlock(), "font-family"))
                .as("기능 글자가 기본값 — 고운돋움이 1순위여야 한다")
                .startsWith("\"Gowun Dodum\"");
    }

    @Test
    @DisplayName("body 의 font-weight 가 400 으로 명시돼 있다 (700 보정이 돌아오면 실패)")
    void bodyFontWeight_isExplicit400() throws IOException {
        assertThat(declaration(bodyBlock(), "font-weight"))
                .as("400 을 명시해야 결과가 결정적이고, 700/600 명시가 강조 축으로 살아난다")
                .isEqualTo("400");
    }
}
