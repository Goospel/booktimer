package com.booktimer.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 전역 예외 처리 — 컨트롤러에서 처리되지 못한 예외를 Spring 기본 whitelabel 대신 친절한 'error' 뷰로 변환한다.
 *
 * <p>예기치 못한 예외(예: 인증 주체는 있으나 도메인 사용자 미존재 → {@code IllegalStateException})가
 * 500 whitelabel로 사용자에게 그대로 노출되면 흉하다. 여기서 잡아 상태/메시지를 모델에 싣고
 * {@code templates/error.html}로 렌더한다. (404 등 컨트롤러를 거치지 않는 오류는 Spring Boot의
 * BasicErrorController가 같은 error.html로 렌더한다 — 페이지를 하나로 통일.)
 *
 * <p>보안 예외(인증/인가)는 필터 단계({@code ExceptionTranslationFilter})에서 처리되어 여기로 오지
 * 않으므로, 로그인 리다이렉트·403 흐름에는 영향을 주지 않는다.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleUnexpected(Exception ex, Model model) {
        log.error("처리되지 않은 예외 — error 뷰로 응답", ex);
        model.addAttribute("status", 500);
        model.addAttribute("message", "예상치 못한 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
        return "error";
    }
}
