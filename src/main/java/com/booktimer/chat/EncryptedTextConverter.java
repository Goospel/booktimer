package com.booktimer.chat;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.function.Supplier;

/**
 * 메시지 본문 컬럼 암호화 — AES-256-GCM, 저장 형식 {@code nonce(12) ‖ 암호문 ‖ tag(16)}(설계 §3 요건 3).
 *
 * <p>nonce는 매번 새로 뽑는다(같은 평문 → 다른 암호문). GCM 태그가 변조·다른 키를 잡으면 그 행은 {@code null}로
 * 읽힌다(오류 로그) — 쓰레기 글자를 돌려주지도, 조회 전체를 죽이지도 않는다. 키 자체가 없으면 던진다.
 *
 * <p>Spring 빈이다 — Hibernate가 Spring 빈 컨테이너로 이 컨버터를 받아 {@link ChatProperties}가 주입된다.
 * 키는 호출 때마다 읽어, 키 없이 기동해도(다크 머지) 앱은 뜨고 <b>실제로 암·복호화할 때만</b> 실패한다.
 */
@Component
@Converter
public class EncryptedTextConverter implements AttributeConverter<String, byte[]> {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EncryptedTextConverter.class);

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom random = new SecureRandom();
    private final Supplier<ChatProperties> properties;

    /**
     * {@code ObjectProvider}인 이유: Hibernate는 모든 엔티티의 컨버터를 EntityManagerFactory를 만들 때 생성한다.
     * {@code @DataJpaTest} 슬라이스엔 {@link ChatProperties} 빈이 없어, 직접 주입이면 대화와 무관한 슬라이스
     * 테스트까지 컨텍스트 로딩에서 전부 죽는다. 키는 실제로 암·복호화할 때만 찾는다.
     */
    @Autowired
    public EncryptedTextConverter(ObjectProvider<ChatProperties> properties) {
        this.properties = properties::getIfAvailable;
    }

    /** 단위 테스트용. */
    EncryptedTextConverter(ChatProperties properties) {
        this.properties = () -> properties;
    }

    @Override
    public byte[] convertToDatabaseColumn(String plain) {
        if (plain == null) {
            return null;
        }
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, nonce));
            byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(NONCE_BYTES + sealed.length).put(nonce).put(sealed).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("메시지 암호화 실패", e);
        }
    }

    @Override
    public String convertToEntityAttribute(byte[] stored) {
        if (stored == null) {
            return null;
        }
        if (stored.length < NONCE_BYTES) {
            log.error("메시지 복호화 불가 — 암호문이 nonce보다 짧다({}바이트)", stored.length);
            return null;
        }
        SecretKeySpec key = key(); // 키 없음·깨짐은 행이 아니라 설정 결함 — 여기서는 던진다
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(TAG_BITS, Arrays.copyOfRange(stored, 0, NONCE_BYTES)));
            byte[] plain = cipher.doFinal(stored, NONCE_BYTES, stored.length - NONCE_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            // 한 행의 결함(변조·다른 키로 쓴 행)이다. 던지면 그 행을 읽는 조회 전체(대화함·미읽음)가 500이 된다 —
            // null로 돌려주고 호출자가 그 방·그 줄만 뺀다(리뷰 사소 4). 평문 흉내는 절대 돌려주지 않는다.
            log.error("메시지 복호화 실패 — 변조됐거나 키가 다르다: {}", e.toString());
            return null;
        }
    }

    private SecretKeySpec key() {
        ChatProperties props = properties.get();
        return decodeKey(props == null ? null : props.getMessageKey());
    }

    /** base64 → AES-256 키. {@link ChatGate}가 기동 때 같은 규칙으로 한 번 검증한다. */
    static SecretKeySpec decodeKey(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException("booktimer.chat.message-key가 없다");
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(encoded.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("booktimer.chat.message-key가 base64가 아니다", e);
        }
        if (raw.length != 32) {
            throw new IllegalStateException("booktimer.chat.message-key는 32바이트(AES-256)여야 한다: " + raw.length);
        }
        return new SecretKeySpec(raw, "AES");
    }
}
