package com.booktimer.chat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 킬스위치 — 스위치가 꺼졌거나 <b>키가 없으면</b> 404. 키 없이 켜면 첫 발송에서 500이 나므로 문 앞에서 닫는다.
 */
class ChatGateTest {

    private static ChatGate gate(boolean enabled, String key) {
        ChatProperties p = new ChatProperties();
        p.setEnabled(enabled);
        p.setMessageKey(key);
        return new ChatGate(p);
    }

    @Test
    void closedByDefault() {
        assertThat(new ChatProperties().isEnabled()).isFalse();
        assertNotFound(gate(false, EncryptedTextConverterTest.KEY));
    }

    @Test
    void closedWhenEnabledButKeyMissing() {
        assertNotFound(gate(true, null));
        assertNotFound(gate(true, " "));
    }

    @Test
    void openWhenEnabledWithKey() {
        assertThatCode(() -> gate(true, EncryptedTextConverterTest.KEY).requireEnabled()).doesNotThrowAnyException();
    }

    private static void assertNotFound(ChatGate gate) {
        assertThatThrownBy(gate::requireEnabled)
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
