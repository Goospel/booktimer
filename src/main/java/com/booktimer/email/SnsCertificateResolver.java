package com.booktimer.email;

import java.security.PublicKey;

/**
 * SNS 서명 검증에 쓸 공개키를 {@code SigningCertURL}에서 얻는 포트.
 *
 * <p>실구현({@link HttpsSnsCertificateResolver})은 URL 호스트가 {@code sns.<region>.amazonaws.com}인지
 * 검증(위조 인증서·SSRF 차단)한 뒤 인증서를 내려받아 공개키를 추출·캐시한다. 테스트는 이 포트를 목으로 바꿔
 * 고정 키페어의 공개키를 돌려주므로, 네트워크·실인증서 없이 서명 검증 로직을 결정적으로 검증한다.
 */
public interface SnsCertificateResolver {

    /**
     * @param signingCertUrl SNS 메시지의 {@code SigningCertURL}
     * @return 서명 검증용 공개키
     * @throws RuntimeException URL이 신뢰할 수 없거나(호스트 불일치) 인증서를 얻지 못하면
     */
    PublicKey resolve(String signingCertUrl);
}
