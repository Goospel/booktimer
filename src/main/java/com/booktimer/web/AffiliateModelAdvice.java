package com.booktimer.web;

import com.booktimer.book.CoupangLinkBuilder;
import com.booktimer.book.Yes24LinkBuilder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 제휴 링크 제공자별 활성 여부({@code coupangEnabled}·{@code yes24Enabled})를 모든 뷰 모델에 실어, 각 템플릿이
 * 제휴 "구매" 버튼과 법적 고지문구의 노출 여부를 한 값으로 판단하게 한다(알라딘은 book.purchaseLink 유무로 판단).
 *
 * <p>{@link AdsModelAdvice}와 동일한 이유: 이 앱은 Thymeleaf 레이아웃 다이얼렉트를 안 쓰고 페이지마다
 * {@code <head>}가 독립적이라, 플래그를 books·book-detail·profile 세 뷰에 한 곳에서 주입하려면
 * 전역 {@code @ModelAttribute}가 가장 얇다(컨트롤러 3곳에 중복 addAttribute 하지 않음).
 * JSON 응답 컨트롤러엔 무시되고, 뷰를 렌더하는 컨트롤러에만 의미가 있다.
 */
@ControllerAdvice
public class AffiliateModelAdvice {

    private final CoupangLinkBuilder coupangLinkBuilder;
    private final Yes24LinkBuilder yes24LinkBuilder;

    public AffiliateModelAdvice(CoupangLinkBuilder coupangLinkBuilder, Yes24LinkBuilder yes24LinkBuilder) {
        this.coupangLinkBuilder = coupangLinkBuilder;
        this.yes24LinkBuilder = yes24LinkBuilder;
    }

    @ModelAttribute("coupangEnabled")
    public boolean coupangEnabled() {
        return coupangLinkBuilder.isEnabled();
    }

    @ModelAttribute("yes24Enabled")
    public boolean yes24Enabled() {
        return yes24LinkBuilder.isEnabled();
    }
}
