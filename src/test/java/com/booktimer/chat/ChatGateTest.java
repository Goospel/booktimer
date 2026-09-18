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

    /** 켜진 채 키가 깨졌으면 기동에서 바로 죽는다 — 안 그러면 모든 대화 경로가 첫 암호화에서 500이다. */
    @Test
    void failsFastWhenSwitchedOnWithMalformedKey() {
        assertThatThrownBy(() -> gate(true, "not-base64-!!!")).isInstanceOf(IllegalStateException.class);
        String aes128 = java.util.Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> gate(true, aes128)).isInstanceOf(IllegalStateException.class);
    }

    /** 꺼져 있으면 키 형식과 무관하게 뜬다 — 운영 SSM엔 아직 키가 없다. */
    @Test
    void switchedOffIgnoresKeyShape() {
        assertThatCode(() -> gate(false, "not-base64-!!!")).doesNotThrowAnyException();
        assertNotFound(gate(false, "not-base64-!!!"));
    }

    @Test
    void openReflectsSwitchAndKey() {
        assertThat(gate(true, EncryptedTextConverterTest.KEY).isOpen()).isTrue();
        assertThat(gate(false, EncryptedTextConverterTest.KEY).isOpen()).isFalse();
        assertThat(gate(true, null).isOpen()).isFalse();
    }

    private static void assertNotFound(ChatGate gate) {
        assertThatThrownBy(gate::requireEnabled)
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
