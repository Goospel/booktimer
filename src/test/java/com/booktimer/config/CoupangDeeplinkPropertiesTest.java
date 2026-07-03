package com.booktimer.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 쿠팡 딥링크 API 설정 불변식 — access-key/secret-key/sub-id가 **셋 다** 설정돼야 활성.
 *
 * <p>dark-launch 전제: API 키는 파트너스 '최종 승인'(누적 판매 15만원) 후에야 발급되므로, 키 미발급 동안은
 * {@link #isEnabled()}가 false라 {@code CoupangDeeplinkClient}가 API 호출 자체를 시도하지 않는다
 * (raw 검색 URL로 즉시 폴백). sub-id도 게이트에 포함하는 이유: sub-id(파트너스 등록 채널 id)가 비면
 * API 호출은 성공해도 그 클릭이 **정산에서 제외**된다 — access-key/secret-key만 있고 sub-id를 깜빡하면
 * "클릭은 되는데 매출로 안 잡히는" 상태가 조용히 재현된다(T-129와 같은 증상, 한 겹 더 깊은 곳에서).
 */
class CoupangDeeplinkPropertiesTest {

    @Test
    @DisplayName("기본값(전부 미설정)이면 비활성")
    void allUnset_disabled() {
        CoupangDeeplinkProperties props = new CoupangDeeplinkProperties();
        assertThat(props.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("accessKey만 설정되면 비활성 (secretKey 없이는 서명 불가)")
    void onlyAccessKeySet_disabled() {
        CoupangDeeplinkProperties props = new CoupangDeeplinkProperties();
        props.setAccessKey("AK123");
        assertThat(props.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("secretKey만 설정되면 비활성 (accessKey 없이는 헤더 구성 불가)")
    void onlySecretKeySet_disabled() {
        CoupangDeeplinkProperties props = new CoupangDeeplinkProperties();
        props.setSecretKey("SK123");
        assertThat(props.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("두 키가 'not-configured' 센티널이면 비활성 (env 플레이스홀더 기본값)")
    void notConfiguredSentinel_disabled() {
        CoupangDeeplinkProperties props = new CoupangDeeplinkProperties();
        props.setAccessKey("not-configured");
        props.setSecretKey("not-configured");
        props.setSubId("booktimer-channel");
        assertThat(props.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("access-key·secret-key는 실값이어도 subId가 비어 있으면 비활성 (정산 제외 방지)")
    void subIdBlank_disabledEvenWithBothKeysSet() {
        CoupangDeeplinkProperties props = new CoupangDeeplinkProperties();
        props.setAccessKey("AK123");
        props.setSecretKey("SK123");
        // subId 기본값("") 그대로 — 설정 안 함
        assertThat(props.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("subId가 공백뿐이어도 비활성")
    void subIdWhitespace_disabled() {
        CoupangDeeplinkProperties props = new CoupangDeeplinkProperties();
        props.setAccessKey("AK123");
        props.setSecretKey("SK123");
        props.setSubId("   ");
        assertThat(props.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("세 값이 모두 실값이면 활성")
    void allThreeSet_enabled() {
        CoupangDeeplinkProperties props = new CoupangDeeplinkProperties();
        props.setAccessKey("AK123");
        props.setSecretKey("SK123");
        props.setSubId("booktimer-channel");
        assertThat(props.isEnabled()).isTrue();
    }
}
