package com.booktimer.chat;

import com.booktimer.config.TossProperties;
import com.booktimer.toss.TossMessengerClient;
import com.booktimer.user.User;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * 새 메시지 토스 푸시 — 캠페인 {@code dmMessage}, 템플릿 변수 없음(문구에 발신자·본문을 싣지 않는다).
 *
 * <p>게이트는 세 겹이고 하나라도 비면 <b>조용히 no-op</b>이다: 메신저 클라이언트 빈({@code messenger.enabled}) ·
 * 캠페인 토글({@code dm-message-enabled}) · 템플릿 코드. 템플릿은 콘솔에서 한 번 등록하면 수정·삭제가 안 돼
 * 사용자 몫(PR-0)이고, 그 전까지 이 코드는 배포돼도 아무것도 보내지 않는다.
 */
@Service
public class ChatPushService {

    /** 게이트 OFF면 빈이 없다 — 그래도 컨텍스트는 떠야 하므로 Optional(완독 축하와 동일). */
    private final Optional<TossMessengerClient> client;
    private final TossProperties properties;

    public ChatPushService(Optional<TossMessengerClient> client, TossProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    /** @return 토스가 발송 성공을 준 경우에만 true. 절대 던지지 않는다(클라이언트 계약). */
    public boolean push(User recipient) {
        TossProperties.Messenger m = properties.getMessenger();
        String code = m.getDmMessageTemplateCode();
        if (!m.isDmMessageEnabled() || code == null || code.isBlank() || client.isEmpty()
                || recipient.getTossUserKey() == null) {
            return false;
        }
        return client.get().sendMessage(recipient.getTossUserKey(), code.strip(), Map.of());
    }
}
