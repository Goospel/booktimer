package com.booktimer.web;

import com.booktimer.book.CoupangLinkBuilder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 쿠팡 파트너스 활성 여부({@code coupangEnabled})를 모든 뷰 모델에 실어, 각 템플릿이 쿠팡 "구매" 버튼과
 * 법적 고지문구의 노출 여부를 한 값으로 판단하게 한다.
 *
 * <p>{@link AdsModelAdvice}와 동일한 이유: 이 앱은 Thymeleaf 레이아웃 다이얼렉트를 안 쓰고 페이지마다
 * {@code <head>}가 독립적이라, 한 플래그를 books·book-detail·profile 세 뷰에 한 곳에서 주입하려면
 * 전역 {@code @ModelAttribute}가 가장 얇다(컨트롤러 3곳에 중복 addAttribute 하지 않음).
 * JSON 응답 컨트롤러엔 무시되고, 뷰를 렌더하는 컨트롤러에만 의미가 있다.
 */
@ControllerAdvice
public class CoupangModelAdvice {

    private final CoupangLinkBuilder coupangLinkBuilder;

    public CoupangModelAdvice(CoupangLinkBuilder coupangLinkBuilder) {
        this.coupangLinkBuilder = coupangLinkBuilder;
    }

    @ModelAttribute("coupangEnabled")
    public boolean coupangEnabled() {
        return coupangLinkBuilder.isEnabled();
    }
}
