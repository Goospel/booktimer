package com.booktimer.book;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * 알라딘 제휴 구매링크 신뢰 판정 — 리다이렉트로 내보내도 되는 주소인지 본다.
 *
 * <p><b>왜 필요한가</b>: {@code book.purchase_link}는 사용자 입력이다 — {@code POST /api/books}가 요청 본문의
 * 값을 검증 없이 그대로 저장한다(정상 흐름에선 알라딘 검색 결과를 그대로 되돌려주지만, 클라이언트를 거치므로
 * 임의 값이 들어올 수 있다). 그 값이 {@code "redirect:" + link}로 나가기 때문에
 * ({@link com.booktimer.web.BookController}), 판정이 없으면 <b>오픈 리다이렉트</b>가 된다: 공격자가 자기 책을
 * 공개해 두고 {@code /u/<공격자>/books/<id>/buy} 주소를 뿌리면, 우리 도메인을 한 번 거쳐 임의 사이트에 착지시킬 수
 * 있다(피싱에서 도메인 신뢰를 빌리는 전형적 수법).
 *
 * <p><b>왜 host 완전일치인가</b>: 문자열 비교로는 못 막는다. {@code startsWith("https://www.aladin.co.kr")}는
 * {@code https://www.aladin.co.kr.evil.example}에 뚫리고, "aladin이 들어있나"류 포함 비교는 쿼리스트링에 심은
 * 미끼에 뚫린다. 그래서 URL로 파싱해 스킴과 host를 각각 본다 — {@code @}(userinfo)·프로토콜 상대 URL
 * ({@code //evil}) 같은 우회도 파싱이 자연히 걸러낸다.
 */
public final class AladinLink {

    /** http도 받는다 — 알라딘 API의 {@code link} 필드가 평문으로 오기도 하고, 여기서 https로 바꾸면 제휴 추적이 깨질 수 있다. */
    private static final Set<String> TRUSTED_HOSTS = Set.of("www.aladin.co.kr", "aladin.co.kr");

    private AladinLink() {
    }

    /** 이 링크로 사용자를 리다이렉트해도 되는가(알라딘 도메인의 http/https 주소인가). */
    public static boolean isTrusted(String link) {
        if (link == null || link.isBlank()) {
            return false;
        }
        URI uri;
        try {
            uri = URI.create(link.strip());
        } catch (IllegalArgumentException e) {
            return false; // 파싱 불가 = 신뢰 불가 (예외로 새어나가지 않는다)
        }
        String scheme = uri.getScheme();
        if (scheme == null) {
            return false; // 스킴 없음 = 프로토콜 상대 URL("//evil") 또는 상대경로
        }
        scheme = scheme.toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return false; // javascript:, data: 등
        }
        String host = uri.getHost();
        return host != null && TRUSTED_HOSTS.contains(host.toLowerCase(Locale.ROOT));
    }
}
