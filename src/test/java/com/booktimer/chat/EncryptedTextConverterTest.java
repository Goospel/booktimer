package com.booktimer.chat;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 메시지 본문 AES-256-GCM 컬럼 암호화(설계 §3 요건 3).
 *
 * <p>단독으로 잡는 실패: 평문이 그대로 컬럼에 실리는 것, nonce 재사용(같은 글 → 같은 암호문 → 빈도 분석),
 * 변조를 조용히 통과시키는 것(GCM 태그 미검증), 다른 키로 읽히는 것, 키가 없거나 길이가 틀린 채 도는 것.
 */
class EncryptedTextConverterTest {

    static final String KEY = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII));
    static final String OTHER_KEY = Base64.getEncoder()
            .encodeToString("fedcba9876543210fedcba9876543210".getBytes(StandardCharsets.US_ASCII));

    static EncryptedTextConverter converter(String key) {
        ChatProperties props = new ChatProperties();
        props.setMessageKey(key);
        return new EncryptedTextConverter(props);
    }

    @Test
    void roundTripsKoreanText() {
        EncryptedTextConverter c = converter(KEY);
        String text = "이 문장 너무 좋다 — 3장 끝부분 🙂";

        assertThat(c.convertToEntityAttribute(c.convertToDatabaseColumn(text))).isEqualTo(text);
    }

    @Test
    void ciphertextHidesPlaintextAndUsesFreshNonce() {
        EncryptedTextConverter c = converter(KEY);
        String text = "secret-plain-marker";

        byte[] first = c.convertToDatabaseColumn(text);
        byte[] second = c.convertToDatabaseColumn(text);

        assertThat(new String(first, StandardCharsets.ISO_8859_1)).doesNotContain(text);
        assertThat(first).isNotEqualTo(second); // 같은 평문이라도 nonce가 달라 암호문이 다르다
        assertThat(first.length).isEqualTo(12 + text.getBytes(StandardCharsets.UTF_8).length + 16);
    }

    @Test
    void rejectsTamperedCiphertext() {
        EncryptedTextConverter c = converter(KEY);
        byte[] stored = c.convertToDatabaseColumn("변조 금지");
        byte[] tampered = Arrays.copyOf(stored, stored.length);
        tampered[tampered.length - 1] ^= 0x01;

        assertThatThrownBy(() -> c.convertToEntityAttribute(tampered)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anotherKeyCannotRead() {
        byte[] stored = converter(KEY).convertToDatabaseColumn("우리끼리");

        assertThatThrownBy(() -> converter(OTHER_KEY).convertToEntityAttribute(stored))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refusesToRunWithoutAValidKey() {
        assertThatThrownBy(() -> converter(null).convertToDatabaseColumn("x"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> converter("").convertToDatabaseColumn("x"))
                .isInstanceOf(IllegalStateException.class);
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]); // AES-128 길이 — 256만 받는다
        assertThatThrownBy(() -> converter(shortKey).convertToDatabaseColumn("x"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void nullStaysNull() {
        EncryptedTextConverter c = converter(KEY);
        assertThat(c.convertToDatabaseColumn(null)).isNull();
        assertThat(c.convertToEntityAttribute(null)).isNull();
    }
}
