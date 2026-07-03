package com.booktimer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 쿠팡 파트너스 딥링크 API 설정 — {@code booktimer.coupang.*} 프로퍼티에 바인딩된다.
 *
 * <p><b>dark-launch 스캐폴드</b>: 딥링크 API 키(access-key/secret-key)는 파트너스 '최종 승인'
 * (누적 판매 15만원 이상) 후에야 발급된다. 그 전까지는 {@link #isEnabled()}가 false라
 * {@code CoupangLinkBuilder}가 쿠팡 버튼·고지문구를 자동으로 숨기고, {@code CoupangDeeplinkClient}는
 * API 호출을 시도하지 않고 raw 검색 URL로 즉시 폴백한다. 키 확보 후 환경변수 주입만으로 점등된다
 * (실값은 커밋 금지 — {@code BOOKTIMER_COUPANG_ACCESS_KEY}/{@code SECRET_KEY} ENV/SSM).
 */
@Component
@ConfigurationProperties(prefix = "booktimer.coupang")
public class CoupangDeeplinkProperties {

    private static final String NOT_CONFIGURED = "not-configured";
    private static final String DEFAULT_API_BASE_URL =
            "https://api-gateway.coupang.com";

    /** 딥링크 API access-key. 미설정("not-configured")이면 비활성. */
    private String accessKey = NOT_CONFIGURED;

    /** 딥링크 API secret-key(HMAC 서명용). 미설정이면 비활성. */
    private String secretKey = NOT_CONFIGURED;

    /** 파트너스 콘솔에 등록한 활동 채널 id(정산 반영에 필요 — 미등록 subId는 정산 제외). */
    private String subId = "";

    /** 딥링크 API base URL. */
    private String apiBaseUrl = DEFAULT_API_BASE_URL;

    /** {@code X-Requested-By} 헤더 값(임의 식별자). */
    private String requestedBy = "booktimer";

    /**
     * access-key·secret-key·sub-id가 셋 다 실값으로 설정됐을 때만 딥링크 API를 사용한다. sub-id를
     * 게이트에 포함하는 이유: 키가 있어도 sub-id(파트너스 등록 채널 id)가 비면 API 호출은 성공하지만
     * 그 클릭이 정산에서 제외된다 — "클릭은 되는데 매출로 안 잡히는" 상태가 조용히 생기는 걸 막는다.
     */
    public boolean isEnabled() {
        return isConfigured(accessKey) && isConfigured(secretKey) && subId != null && !subId.isBlank();
    }

    private static boolean isConfigured(String value) {
        return value != null && !value.isBlank() && !NOT_CONFIGURED.equals(value);
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getSubId() {
        return subId;
    }

    public void setSubId(String subId) {
        this.subId = subId;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
    }
}
