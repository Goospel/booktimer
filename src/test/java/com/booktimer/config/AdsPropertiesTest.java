package com.booktimer.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 광고 설정 불변식 — 게시자 ID가 비어 있으면 광고를 끈다(스캐폴드 상태).
 *
 * <p>사용자 확정(2026-06-07): AdSense 승인 전까지 ID 없이 자리만 만든다. 그 안전 불변식은
 * "client-id가 비면 enabled=false → 템플릿이 광고를 렌더하지 않음"이다 — 깨진 빈 광고/정책 위반 방지.
 * 이 경계(빈 문자열/공백/실값)는 순수 단위 테스트로 못 박는다.
 */
class AdsPropertiesTest {

    @Test
    @DisplayName("client-id가 빈 문자열이면 비활성 (기본 스캐폴드 상태)")
    void blankClientId_disabled() {
        AdsProperties props = new AdsProperties();
        // 기본값 — 아무것도 설정 안 함
        assertThat(props.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("client-id가 공백뿐이면 비활성 (실수로 띄어쓰기만 넣어도 안 켜진다)")
    void whitespaceClientId_disabled() {
        AdsProperties props = new AdsProperties();
        props.setClientId("   ");
        assertThat(props.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("client-id가 채워지면 활성 — 이때만 광고가 렌더된다")
    void presentClientId_enabled() {
        AdsProperties props = new AdsProperties();
        props.setClientId("ca-pub-1234567890123456");
        assertThat(props.isEnabled()).isTrue();
    }
}
