package com.booktimer.web;

import com.booktimer.security.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.security.Principal;

/**
 * 양옆 세로 바({@code fragments/side-rails :: rails})가 읽는 {@code rail}을 모든 뷰 모델에 싣는다.
 *
 * <p>공유 레이아웃이 없어 전 페이지 공통값은 advice로 싣는 것이 이 레포의 관례다({@link AdsModelAdvice}).
 * Thymeleaf 3.1은 표현식에서 {@code #request}를 못 쓰므로 현재 경로도 여기서 넘긴다.
 */
@ControllerAdvice
public class RailModelAdvice {

    private final CurrentUserService currentUserService;

    public RailModelAdvice(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    /** 미인증이면 null — fragment는 {@code th:if="${rail != null}"}로 지킨다. 사용자 해석은 지연. */
    @ModelAttribute("rail")
    public RailModel rail(Principal principal, HttpServletRequest request) {
        if (principal == null) return null;
        return new RailModel(request.getRequestURI(), () -> currentUserService.resolve(principal));
    }
}
