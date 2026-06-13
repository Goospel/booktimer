package com.booktimer.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 방문 통계(Google Analytics 4) 설정 불변식 — 측정 ID가 비어 있으면 통계를 끈다(스캐폴드 상태).
 *
 * <p>GA4 속성 생성 전까지 ID 없이 자리만 만든다. 안전 불변식은 "ga-id가 비면 enabled=false →
 * 템플릿이 gtag를 렌더하지 않음"이다 — 잘못된 측정·빈 스크립트 방지. AdSense({@code AdsPropertiesTest})와
 * 동일 config-gate 패턴. 이 경계(빈 문자열/공백/실값)는 순수 단위 테스트로 못 박는다.
 */
class AnalyticsPropertiesTest {

    @Test
    @DisplayName("ga-id가 빈 문자열이면 비활성 (기본 스캐폴드 상태)")
    void blankGaId_disabled() {
        AnalyticsProperties props = new AnalyticsProperties();
        // 기본값 — 아무것도 설정 안 함
        assertThat(props.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("ga-id가 공백뿐이면 비활성 (실수로 띄어쓰기만 넣어도 안 켜진다)")
    void whitespaceGaId_disabled() {
        AnalyticsProperties props = new AnalyticsProperties();
        props.setGaId("   ");
        assertThat(props.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("ga-id가 채워지면 활성 — 이때만 gtag가 렌더된다")
    void presentGaId_enabled() {
        AnalyticsProperties props = new AnalyticsProperties();
        props.setGaId("G-ABC123");
        assertThat(props.isEnabled()).isTrue();
    }
}
