package com.booktimer.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 요청마다 nonce를 발급해 {@code Content-Security-Policy} 헤더를 쓴다 — XSS가 스크립트를 심어도
 * 실행되지 못하게 하는 마지막 방어선.
 *
 * <p><b>왜 nonce인가</b>: 정적 문자열 정책으로는 인라인 스크립트를 허용하려면 {@code 'unsafe-inline'}을
 * 켜야 하는데, 그 순간 CSP의 XSS 방어는 사실상 사라진다. nonce는 "이 응답이 직접 심은 스크립트"와
 * "공격자가 주입한 스크립트"를 구분할 수 있는 유일한 수단이다 — 공격자는 매 요청 새로 뽑히는 값을 모른다.
 * 그래서 {@link #newNonce()}는 반드시 요청마다 새로, 예측 불가능하게({@link SecureRandom}) 뽑는다.
 *
 * <p><b>왜 {@code 'strict-dynamic'}인가</b>: AdSense·GA는 로더 스크립트가 런타임에 또 다른 스크립트를
 * 여러 호스트에서 심는다(광고 크리에이티브마다 다르다). 호스트 허용목록으로는 그 목록을 다 적을 수 없고,
 * 적어도 구글이 언제든 늘릴 수 있다. {@code 'strict-dynamic'}은 "nonce를 받은 스크립트가 심은 것은
 * 믿는다"로 신뢰를 전파해 이 문제를 푼다 — 우리가 부른 로더만 믿고, 주입된 태그는 여전히 막는다.
 *
 * <p>⚠️ 그 대가로 <b>호스트 허용목록({@code 'self'} 포함)이 무시된다</b> — 즉 외부 {@code src} 태그든
 * 우리 번들이든 <b>모든 {@code <script>}에 nonce가 있어야</b> 실행된다. 빠뜨리면 에러 없이 그 스크립트만
 * 조용히 안 돈다(화면은 멀쩡한데 기능 하나가 죽는다). 템플릿 소스 스캔 테스트가 이걸 막는다({@code CspTest}).
 *
 * <p>뒤의 {@code https: 'unsafe-inline'}은 <b>구형 브라우저용 폴백</b>이다. nonce·strict-dynamic을
 * 이해하는 브라우저는 이 둘을 무시하므로 최신 브라우저의 보안은 낮아지지 않는다(구글이 자기 태그에
 * 권장하는 폴백 체인과 같다).
 */
public class CspNonceFilter extends OncePerRequestFilter {

    /**
     * 템플릿이 {@code ${cspNonce}}로 읽는 요청 속성 이름.
     * Thymeleaf는 요청 속성을 그대로 변수로 노출한다(Spring Security의 {@code ${_csrf}}와 같은 경로).
     */
    public static final String NONCE_ATTRIBUTE = "cspNonce";

    static final String HEADER = "Content-Security-Policy";

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * {@code %s} 자리에 요청별 nonce가 들어간다.
     *
     * <ul>
     *   <li>{@code style-src 'unsafe-inline'} — 관리자 화면의 {@code <style>} 5개와 인라인 {@code style=}
     *       속성 18개가 여기 걸린다. 스크립트와 달리 CSS 주입은 위력이 훨씬 낮아, 그 18곳을 다 뜯는
     *       회귀 위험보다 이쪽이 낫다고 봤다(스크립트는 절대 이렇게 하지 않는다).</li>
     *   <li>{@code style-src·font-src}의 구글 폰트 두 호스트 — 본문 폰트(고운돋움)를 {@code app.css}가
     *       <b>{@code @import}로</b> 불러온다. 템플릿엔 {@code <link>}가 없어서 HTML만 훑으면 안 보이고,
     *       빠뜨리면 폰트만 조용히 시스템 기본으로 떨어진다(실 브라우저 확인에서 잡았다 — 단위테스트는
     *       전부 통과했다). 실제 폰트 파일은 {@code fonts.gstatic.com}에서 오므로 두 호스트가 한 쌍이다.</li>
     *   <li>{@code img-src https:} — 책 표지가 알라딘 등 외부 호스트에서 온다. 이미지 출처를 좁히려면
     *       서점이 늘 때마다 정책을 고쳐야 하는데, 얻는 보안은 거의 없다.</li>
     *   <li>{@code frame-src https:} — 광고 크리에이티브가 iframe으로 뜬다(호스트가 광고마다 다르다).</li>
     *   <li>{@code connect-src https:} — GA 비콘·광고 XHR. 같은 이유로 호스트를 못 적는다.</li>
     * </ul>
     */
    private static final String POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'nonce-%s' 'strict-dynamic' https: 'unsafe-inline'",
            "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
            "img-src 'self' data: https:",
            "font-src 'self' data: https://fonts.gstatic.com",
            "connect-src 'self' https:",
            "frame-src https:",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String nonce = newNonce();
        request.setAttribute(NONCE_ATTRIBUTE, nonce);
        response.setHeader(HEADER, POLICY.formatted(nonce));
        filterChain.doFilter(request, response);
    }

    /** 128비트 URL-safe 난수 — base64 특수문자가 CSP 문법과 부딪히지 않게 URL 인코더를 쓴다. */
    private static String newNonce() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
