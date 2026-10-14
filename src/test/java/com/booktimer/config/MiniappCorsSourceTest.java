package com.booktimer.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 미니앱 CORS 소스의 경로별 설정 — 연결 끊기 콜백은 허용 오리진 목록과 무관하게 열린다.
 *
 * <p>앱인토스 콘솔 「테스트하기」는 브라우저에서 콜백을 직접 fetch한다(2026-10-14 실측: 운영 프리플라이트 403).
 * 허용 목록이 비어 있는 환경(로컬·테스트 기본값)에서도 콜백 설정이 있어야 같은 화면이 어디서든 통한다.
 */
class MiniappCorsSourceTest {

    private static CorsConfiguration configFor(CorsConfigurationSource source, String path) {
        return source.getCorsConfiguration(new MockHttpServletRequest("POST", path));
    }

    @Test
    @DisplayName("허용 오리진이 비어 있어도 콜백 경로엔 어느 출처든 받는 설정이 있고, 다른 /api 경로엔 없다")
    void emptyOrigins_callbackStillOpen_otherApiClosed() {
        CorsConfigurationSource source = SecurityConfig.miniappCorsSource(new MiniappProperties());

        CorsConfiguration callback = configFor(source, "/api/toss/unlink-callback");
        assertThat(callback).isNotNull();
        assertThat(callback.checkOrigin("https://apps-in-toss.toss.im")).isEqualTo("*");
        assertThat(callback.getAllowCredentials()).isNotEqualTo(Boolean.TRUE);

        assertThat(configFor(source, "/api/other")).isNull();
        assertThat(configFor(source, "/api/toss/login")).isNull(); // 같은 접두의 형제 경로는 열지 않는다
    }

    @Test
    @DisplayName("허용 오리진이 있으면 콜백은 콜백 설정(*)을, 다른 /api 경로는 기존 목록 설정을 받는다")
    void withOrigins_callbackMatchesBeforeApiWildcard() {
        MiniappProperties props = new MiniappProperties();
        props.setAllowedOrigins(List.of("https://miniapp.test"));
        CorsConfigurationSource source = SecurityConfig.miniappCorsSource(props);

        assertThat(configFor(source, "/api/toss/unlink-callback").checkOrigin("https://apps-in-toss.toss.im"))
                .isEqualTo("*");
        CorsConfiguration other = configFor(source, "/api/other");
        assertThat(other.getAllowedOrigins()).containsExactly("https://miniapp.test");
        assertThat(other.checkOrigin("https://apps-in-toss.toss.im")).isNull();
        assertThat(configFor(source, "/api/toss/login").checkOrigin("https://apps-in-toss.toss.im")).isNull();
    }
}
