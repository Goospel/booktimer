package com.booktimer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Google AdSense 광고 설정 — {@code booktimer.ads.*} 프로퍼티에 바인딩된다.
 *
 * <p>수익화를 위해 광고를 넣되, "마찰 최소 독서 습관"이라는 제품 정체성과 충돌하지 않도록
 * <b>부가 화면(독서 기록·책 목록·책 상세·프로필·검색)과 대시보드 하단</b>에만 1개씩 노출한다
 * (타이머가 도는 핵심 동선·온보딩·로그인엔 광고 없음).
 *
 * <p><b>config-gated 스캐폴드</b>: {@code client-id}가 비어 있으면 {@link #isEnabled()}가 false라
 * 템플릿({@code ads.html} fragment)이 광고를 아예 렌더하지 않는다. AdSense 사이트 승인 전까지는
 * ID를 비워 두어 깨진 빈 광고·정책 위반을 막고, 승인 후 환경변수로 실값을 주입하면 켜진다
 * (실값은 커밋 금지 — {@code ADSENSE_CLIENT_ID}/{@code ADSENSE_SLOT} ENV/SSM).
 */
@Component
@ConfigurationProperties(prefix = "booktimer.ads")
public class AdsProperties {

    /** AdSense 게시자 ID(예: {@code ca-pub-XXXXXXXXXXXXXXXX}). 비어 있으면 광고 비활성. */
    private String clientId = "";

    /** 광고 단위 슬롯 ID({@code data-ad-slot}). */
    private String slot = "";

    /** 게시자 ID가 실제로 채워졌을 때만 광고를 렌더한다(공백뿐이면 비활성). */
    public boolean isEnabled() {
        return clientId != null && !clientId.isBlank();
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getSlot() {
        return slot;
    }

    public void setSlot(String slot) {
        this.slot = slot;
    }
}
