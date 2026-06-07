package com.booktimer.web;

import com.booktimer.config.AdsProperties;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 광고 설정을 모든 뷰 모델에 {@code ads}로 실어, 공유 레이아웃이 없는 각 템플릿의
 * {@code ads.html} fragment가 {@code ${ads.enabled}}로 노출 여부를 판단할 수 있게 한다.
 *
 * <p>이 앱은 Thymeleaf 레이아웃 다이얼렉트를 안 쓰고 페이지마다 {@code <head>}가 독립적이라,
 * 광고 켜짐 여부를 한 곳에서 주입하려면 전역 {@code @ModelAttribute}가 가장 얇은 방법이다.
 * JSON 응답 컨트롤러에는 그냥 무시되고, 뷰를 렌더하는 컨트롤러에만 의미가 있다.
 */
@ControllerAdvice
public class AdsModelAdvice {

    private final AdsProperties ads;

    public AdsModelAdvice(AdsProperties ads) {
        this.ads = ads;
    }

    @ModelAttribute("ads")
    public AdsProperties ads() {
        return ads;
    }
}
