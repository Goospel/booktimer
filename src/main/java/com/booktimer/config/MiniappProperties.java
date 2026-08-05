package com.booktimer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 미니앱(앱인토스) 채널 설정 — {@code booktimer.miniapp.*} 프로퍼티에 바인딩된다.
 *
 * <p>미니앱 번들은 토스 인프라에서 서빙되므로 우리 {@code /api/**} 호출은 <b>교차 출처</b>다. 허용 오리진을
 * 여기서 외부화하는 이유는 <b>실제 WebView 오리진 값이 PR-0 샌드박스 실측으로만 확정</b>되기 때문이다 —
 * 코드에 추측값을 하드코딩하면 실측 후 재배포가 필요하고, 틀린 값이 조용히 남는다.
 *
 * <p>기본값은 <b>빈 목록</b>이다: 실측 전까지 교차 출처 호출을 허용하지 않는다(안전 기본값). 값이 비면
 * CORS 설정 자체를 붙이지 않아, 이미 동작 중인 동일 출처 웹 호출에는 아무 영향이 없다.
 */
@Component
@ConfigurationProperties(prefix = "booktimer.miniapp")
public class MiniappProperties {

    /** 교차 출처 호출을 허용할 오리진 목록(PR-0 실측 후 환경변수로 주입). 비면 CORS 미적용. */
    private List<String> allowedOrigins = new ArrayList<>();

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}
