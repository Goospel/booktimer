package com.booktimer.chat;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * 킬스위치 — 꺼졌거나 <b>키가 없으면</b> {@code /api/chat/**}를 404로 닫는다.
 *
 * <p>키 없이 스위치만 켜면 첫 발송이 암호화에서 500으로 죽는다. 그 상태를 「기능 없음」과 구별할 이유가
 * 없어서 같은 404로 둔다 — 미니앱은 {@code GET /api/chat/me}가 404면 진입점을 그리지 않는다.
 */
@Component
public class ChatGate {

    private final ChatProperties properties;

    public ChatGate(ChatProperties properties) {
        this.properties = properties;
    }

    public void requireEnabled() {
        String key = properties.getMessageKey();
        if (!properties.isEnabled() || key == null || key.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }
}
