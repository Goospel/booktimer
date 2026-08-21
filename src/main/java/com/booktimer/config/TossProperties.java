package com.booktimer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 토스 앱인토스(미니앱) 서버 연동 설정 — {@code booktimer.toss.*} 프로퍼티에 바인딩된다.
 *
 * <p>토스 로그인 서버 연동은 <b>mTLS 필수</b>다. 클라이언트 인증서·키는 앱인토스 콘솔에서 발급받아
 * EC2에 파일로 두고, Spring Boot의 SSL 번들({@code spring.ssl.bundle.pem.<name>.*})로 읽는다 —
 * <b>레포에 인증서를 절대 커밋하지 않는다</b>(OAuth·AdSense 키와 동일 정책).
 *
 * <p>번들 이름({@link #getSslBundle()})에 해당하는 번들이 등록돼 있지 않으면 토스 호출 시점에만 실패한다
 * (기동은 정상) — 인증서가 아직 없는 개발·테스트 환경에서도 앱이 뜨고 나머지 기능이 모두 동작해야 하기
 * 때문이다(dark-launch 스캐폴드, CoupangDeeplinkProperties와 같은 패턴).
 */
@Component
@ConfigurationProperties(prefix = "booktimer.toss")
public class TossProperties {

    private static final String DEFAULT_API_BASE_URL = "https://apps-in-toss-api.toss.im";

    /** 앱인토스 파트너 API base URL. */
    private String apiBaseUrl = DEFAULT_API_BASE_URL;

    /** mTLS 클라이언트 인증서를 담은 SSL 번들 이름({@code spring.ssl.bundle.pem.<이 이름>}). */
    private String sslBundle = "toss";

    /** 메신저(푸시) 발송 설정 — {@code booktimer.toss.messenger.*}. */
    private final Messenger messenger = new Messenger();

    public Messenger getMessenger() {
        return messenger;
    }

    /**
     * 토스 메신저(서버 발송 푸시) 설정.
     *
     * <p>앱인토스는 콘솔에서 <b>문구 검수 승인을 받은 템플릿만</b> 발송할 수 있다 — 코드가 검수보다 먼저
     * 머지·배포되므로 {@link #enabled}는 기본 OFF(다크런치)이고, 승인 후 SSM으로 점등한다.
     */
    public static class Messenger {

        /** false면 발송 클라이언트 빈 자체가 등록되지 않는다(다크런치 게이트). */
        private boolean enabled;

        /** 완독 축하 템플릿의 templateSetCode(콘솔 발급). 비어 있으면 발송하지 않는다. */
        private String finishTemplateCode;

        /**
         * "오늘 목표 달성" 감지 스케줄러 점등 — false면 스케줄러 빈 자체가 없다(분당 배치가 아예 안 돈다).
         * {@link #enabled}와 <b>별개 토글</b>이라 완독 축하와 독립적으로 켜고 끌 수 있다(둘 다 true여야 실발송).
         */
        private boolean goalMetEnabled;

        /** 목표 달성 템플릿의 templateSetCode(콘솔 발급). 비어 있으면 발송하지 않는다. */
        private String goalMetTemplateCode;

        /**
         * 재참여(7일 비활동) 넛지 배치 점등 — false면 스케줄러 빈 자체가 없다(매일 19시 배치가 아예 안 돈다).
         * {@link #enabled}와 <b>별개 토글</b>이라 완독 축하·목표 달성과 독립적으로 켜고 끌 수 있다.
         */
        private boolean retentionEnabled;

        /** 재참여 넛지 템플릿의 templateSetCode(콘솔 발급). 비어 있으면 발송하지 않는다. */
        private String retentionTemplateCode;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getFinishTemplateCode() {
            return finishTemplateCode;
        }

        public void setFinishTemplateCode(String finishTemplateCode) {
            this.finishTemplateCode = finishTemplateCode;
        }

        public boolean isGoalMetEnabled() {
            return goalMetEnabled;
        }

        public void setGoalMetEnabled(boolean goalMetEnabled) {
            this.goalMetEnabled = goalMetEnabled;
        }

        public String getGoalMetTemplateCode() {
            return goalMetTemplateCode;
        }

        public void setGoalMetTemplateCode(String goalMetTemplateCode) {
            this.goalMetTemplateCode = goalMetTemplateCode;
        }

        public boolean isRetentionEnabled() {
            return retentionEnabled;
        }

        public void setRetentionEnabled(boolean retentionEnabled) {
            this.retentionEnabled = retentionEnabled;
        }

        public String getRetentionTemplateCode() {
            return retentionTemplateCode;
        }

        public void setRetentionTemplateCode(String retentionTemplateCode) {
            this.retentionTemplateCode = retentionTemplateCode;
        }
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public String getSslBundle() {
        return sslBundle;
    }

    public void setSslBundle(String sslBundle) {
        this.sslBundle = sslBundle;
    }
}
