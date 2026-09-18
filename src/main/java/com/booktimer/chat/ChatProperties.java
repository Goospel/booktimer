package com.booktimer.chat;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 맞팔 DM 설정 — {@code booktimer.chat.*}(설계 2026-09-18 §5-2).
 *
 * <p>{@link #enabled}는 기본 OFF다 — 서버가 미니앱 심사·채널톡 접수보다 먼저 배포되므로(다크 머지),
 * 꺼져 있으면 {@code /api/chat/**}가 전부 404다({@link ChatGate}). 되돌리기도 SSM 한 줄이다.
 *
 * <p>{@link #messageKey}는 메시지 본문 AES-256-GCM 키(base64, 32바이트)다. <b>유출되면 전 메시지가 노출</b>되니
 * SSM SecureString으로만 주입하고 레포에 두지 않는다. 키 회전은 v2(설계 §7-3).
 */
@Component
@ConfigurationProperties(prefix = "booktimer.chat")
public class ChatProperties {

    private boolean enabled;

    private String messageKey;

    /** 새 메시지 푸시 간격 — 방·수신자당 이 시간에 1통. */
    private int pushIntervalMinutes = 30;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public void setMessageKey(String messageKey) {
        this.messageKey = messageKey;
    }

    public int getPushIntervalMinutes() {
        return pushIntervalMinutes;
    }

    public void setPushIntervalMinutes(int pushIntervalMinutes) {
        this.pushIntervalMinutes = pushIntervalMinutes;
    }
}
