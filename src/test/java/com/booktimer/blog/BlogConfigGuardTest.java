package com.booktimer.blog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-205 — 블로그 허용 목록의 이름 · 기본값(계획 §4.8). 테스트 컨텍스트는 같은 이름의 테스트 리소스({@code *})가 메인 파일을
 * 가려서 운영 기본값을 구조적으로 못 본다 — 그래서 파일 글자를 직접 읽는다({@code BcryptStrengthProductionGuardTest}와 같은 까닭).
 * SSM {@code /booktimer/BLOG_ALLOWED_USERS} ↔ 환경변수 ↔ 속성 사슬의 배포 쪽은 {@code deploy/tests/test-render-env.sh}가 CI에서 잰다.
 */
class BlogConfigGuardTest {

    private static final Pattern PROPERTY = Pattern.compile(
            "^booktimer\\.blog\\.allowed-users=\\$\\{BOOKTIMER_BLOG_ALLOWED_USERS:([^}]*)}\\s*$", Pattern.MULTILINE);

    /** 운영 속성 글자에서 기본값 — 줄이 없거나 꼴이 다르면 null. */
    static String defaultOf(String properties) {
        Matcher m = PROPERTY.matcher(properties);
        return m.find() ? m.group(1) : null;
    }

    @Test
    @DisplayName("REQ-205 · 운영 application.properties의 블로그 허용 목록 기본값은 none")
    void productionDefaultIsNone() throws IOException {
        assertThat(defaultOf(Files.readString(Path.of("src/main/resources/application.properties")))).isEqualTo("none");
        // 양성 대조 — 기본값을 *로 바꾼 글 · 줄이 없는 글은 이 가드를 통과하지 못한다
        assertThat(defaultOf("booktimer.blog.allowed-users=${BOOKTIMER_BLOG_ALLOWED_USERS:*}\n")).isNotEqualTo("none");
        assertThat(defaultOf("booktimer.app.link-card-users=${BOOKTIMER_APP_LINK_CARD_USERS:none}\n")).isNull();
    }

    @Test
    @DisplayName("REQ-205 · render-env.sh가 SSM BLOG_ALLOWED_USERS를 BOOKTIMER_BLOG_ALLOWED_USERS로 옮긴다")
    void renderEnvMapsSsmParameter() throws IOException {
        assertThat(Files.readString(Path.of("deploy/render-env.sh")))
                .contains("[BLOG_ALLOWED_USERS]=BOOKTIMER_BLOG_ALLOWED_USERS");
    }
}
