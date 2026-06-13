package com.booktimer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 방문 통계(Google Analytics 4) 설정 — {@code booktimer.analytics.*} 프로퍼티에 바인딩된다.
 *
 * <p>"사용자가 접근 시작조차 안 하는가(유통) vs 들어와서 바로 나가는가(랜딩)"를 분리 측정하려면
 * 획득(acquisition)·첫 진입을 추적할 분석 도구가 필요하다. CloudWatch raw 로그만으론 사용자 단위
 * 행동·이탈을 구분할 수 없어 GA4를 심는다.
 *
 * <p><b>config-gated 스캐폴드</b>: {@code ga-id}가 비어 있으면 {@link #isEnabled()}가 false라
 * 템플릿({@code analytics.html} fragment)이 gtag 스크립트를 아예 렌더하지 않는다. GA4 속성 생성
 * 전까지는 ID를 비워 두고, 발급 후 환경변수로 실값을 주입하면 켜진다 — 실값은 커밋 금지
 * ({@code GA_MEASUREMENT_ID} ENV/SSM, AdSense·OAuth 키와 동일 정책). AdSense({@link AdsProperties})와
 * 같은 패턴을 복제한 것이다.
 */
@Component
@ConfigurationProperties(prefix = "booktimer.analytics")
public class AnalyticsProperties {

    /** GA4 측정 ID(예: {@code G-XXXXXXXXXX}). 비어 있으면 통계 비활성. */
    private String gaId = "";

    /** 측정 ID가 실제로 채워졌을 때만 gtag를 렌더한다(공백뿐이면 비활성). */
    public boolean isEnabled() {
        return gaId != null && !gaId.isBlank();
    }

    public String getGaId() {
        return gaId;
    }

    public void setGaId(String gaId) {
        this.gaId = gaId;
    }
}
