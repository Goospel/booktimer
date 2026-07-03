package com.booktimer.book;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

/**
 * 쿠팡 파트너스 딥링크 API의 HMAC-SHA256 서명기 — 순수 함수(네트워크 없음, {@link Instant}를 직접 받아 결정적).
 *
 * <p>인증 헤더 규격: {@code Authorization: CEA algorithm=HmacSHA256, access-key={AK}, signed-date={datetime},
 * signature={hex}}. {@code signature}는 {@code HmacSHA256(SECRET_KEY, datetime + method + path + query)}를
 * hex 인코딩한 값이고, {@code signed-date} 헤더는 서명에 쓴 datetime과 **동일한 값**이어야 한다(재생성하면 초
 * 단위 불일치로 401).
 */
@Component
public class CoupangDeeplinkSigner {

    private static final DateTimeFormatter DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    /** GMT 기준 {@code yyMMdd'T'HHmmss'Z'} datetime 문자열. */
    public String datetime(Instant now) {
        return DATETIME_FORMATTER.format(now);
    }

    /** {@code HmacSHA256(secretKey, message)}의 hex 인코딩. */
    public String signature(String secretKey, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("쿠팡 딥링크 HMAC 서명 실패", e);
        }
    }

    /**
     * {@code CEA algorithm=HmacSHA256, access-key=..., signed-date=..., signature=...} 헤더 값.
     * message = datetime + method + path + query(구분자 없이 연결, query는 딥링크 API에선 빈 문자열).
     */
    public String authorizationHeader(
            String accessKey, String secretKey, String method, String path, String query, String datetime) {
        String message = datetime + method + path + (query == null ? "" : query);
        return "CEA algorithm=HmacSHA256, access-key=" + accessKey
                + ", signed-date=" + datetime
                + ", signature=" + signature(secretKey, message);
    }
}
