package com.booktimer.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.ForwardedHeaderFilter;

/**
 * 프록시(ALB) 뒤에서의 HTTPS 인식 설정.
 *
 * <p>운영에서 ALB가 TLS를 종료(termination)하고 평문 HTTP로 앱에 전달한다. 앱이 그대로면 자기 주소를
 * http로 인식해, 리다이렉트 URL과 특히 구글로 보내는 OAuth {@code redirect_uri}를 {@code http://...}로
 * 만든다 → 구글은 https만 허용하므로 {@code redirect_uri_mismatch}가 난다. {@link ForwardedHeaderFilter}가
 * {@code X-Forwarded-Proto/Host/Port}를 반영해 요청을 감싸면, 앱이 "나는 https로 호출됐다"를 올바로 인식한다.
 *
 * <p>{@code server.forward-headers-strategy=framework} 프로퍼티로도 같은 필터가 등록되지만, Boot 4의
 * 모듈 분리 환경에서 그 자동구성 빈이 일관되게 활성화되지 않아(컨텍스트에 등록 안 됨), 버전·환경에 무관하게
 * 확실히 동작하도록 필터를 명시 빈으로 직접 등록한다. 앱은 ALB 뒤(사설 네트워크)에만 노출되므로
 * forwarded 헤더 신뢰가 안전하다(N-021).
 */
@Configuration
public class WebConfig {

    @Bean
    public FilterRegistrationBean<ForwardedHeaderFilter> forwardedHeaderFilter() {
        FilterRegistrationBean<ForwardedHeaderFilter> registration =
                new FilterRegistrationBean<>(new ForwardedHeaderFilter());
        // 보안 필터 체인보다 먼저 실행돼 요청 스킴/호스트를 먼저 바로잡아야 한다.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
