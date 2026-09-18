package com.booktimer.chat;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * 킬스위치 — 꺼졌거나 <b>키가 없으면</b> {@code /api/chat/**}를 404로 닫는다.
 *
 * <p>키가 비어 있는 것은 「기능 없음」과 같은 404로 둔다 — 미니앱은 {@code GET /api/chat/me}가 404면 진입점을
 * 그리지 않는다. 반면 <b>켜진 채 키가 깨져 있으면</b>(base64 아님·32바이트 아님) 기동에서 바로 죽인다 — 그대로
 * 뜨면 모든 대화 경로가 첫 암호화에서 500이 된다. 꺼져 있으면 키를 보지 않는다(운영 SSM엔 아직 키가 없다).
 *
 * <p>이 검증을 컨버터가 아니라 여기 두는 이유: 컨버터는 {@code @DataJpaTest} 슬라이스에서도 만들어지는데 이 빈은
 * 거기 없다 — 대화와 무관한 슬라이스 테스트가 설정 때문에 죽지 않는다.
 */
@Component
public class ChatGate {

    private final ChatProperties properties;

    public ChatGate(ChatProperties properties) {
        this.properties = properties;
        if (properties.isEnabled() && hasKey()) {
            EncryptedTextConverter.decodeKey(properties.getMessageKey()); // 깨졌으면 IllegalStateException → 기동 실패
        }
    }

    /** 대화 기능이 지금 열려 있는가 — 프로필 {@code dmAvailable}처럼 404를 낼 수 없는 곳이 쓴다. */
    public boolean isOpen() {
        return properties.isEnabled() && hasKey();
    }

    public void requireEnabled() {
        if (!isOpen()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    private boolean hasKey() {
        String key = properties.getMessageKey();
        return key != null && !key.isBlank();
    }
}
