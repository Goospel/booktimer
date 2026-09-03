package com.booktimer.web;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 전역 예외 처리 — 컨트롤러에서 처리되지 못한 예외를 Spring 기본 whitelabel 대신 친절한 'error' 뷰로 변환한다.
 *
 * <p>예기치 못한 예외(예: 인증 주체는 있으나 도메인 사용자 미존재 → {@code IllegalStateException})가
 * 500 whitelabel로 사용자에게 그대로 노출되면 흉하다. 여기서 잡아 상태/메시지를 모델에 싣고
 * {@code templates/error.html}로 렌더한다 — 페이지를 하나로 통일.
 *
 * <p>단, <b>상태코드를 스스로 들고 오는 예외(404 {@code NoResourceFoundException} 등)는 그 코드를
 * 보존</b>한다. 안 그러면 catch-all {@code Exception} 핸들러가 404를 500으로 삼켜
 * {@code error} 로그를 도배한다({@code /favicon.ico} 매 요청마다).
 *
 * <p>보안 예외(인증/인가)는 필터 단계({@code ExceptionTranslationFilter})에서 처리되어 여기로 오지
 * 않으므로, 로그인 리다이렉트·403 흐름에는 영향을 주지 않는다.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 상태코드를 스스로 들고 오는 예외는 catch-all로 500을 씌우지 않고 <b>원래 상태코드를 보존</b>한다.
     * 404를 500으로 삼켜 로그를 도배하던 문제를 막는다. 두 타입 모두 {@link ErrorResponse}를 구현해
     * {@code getStatusCode()}를 제공한다:
     * <ul>
     *   <li>{@code NoResourceFoundException} — 없는 정적 리소스(예: {@code /favicon.ico}) → 404.
     *       <b>Boot 4(Spring 7)부터 {@code ResponseStatusException}이 아니라 {@code ServletException}
     *       + {@code ErrorResponse}</b>라 타입을 따로 잡아야 한다(T-019).</li>
     *   <li>{@code ResponseStatusException} — 코드가 명시적으로 던진 상태 예외.</li>
     * </ul>
     *
     * <p>이들은 서버 결함이 아니라 클라이언트/요청 상황이라 {@code error} 레벨로 찍지 않고
     * {@code debug}로만 남긴다. 더 구체적인 예외 타입이라 아래 {@code Exception} 핸들러보다 우선한다.
     */
    @ExceptionHandler({ResponseStatusException.class, NoResourceFoundException.class,
            HttpRequestMethodNotSupportedException.class})
    public String handleStatusException(Exception ex, Model model, HttpServletResponse response) {
        int status = ((ErrorResponse) ex).getStatusCode().value();
        response.setStatus(status);
        log.debug("상태 예외 — {} 보존하여 error 뷰로 응답: {}", status, ex.getMessage());
        model.addAttribute("status", status);
        model.addAttribute("message", status == HttpStatus.NOT_FOUND.value()
                ? "요청하신 페이지를 찾을 수 없습니다."
                : "요청을 처리할 수 없습니다.");
        return "error";
    }

    /**
     * 업로드 용량 초과 → <b>413</b>. 여기(전역)에 있어야 하는 이유가 있다.
     *
     * <p>{@code MaxUploadSizeExceededException}은 {@code DispatcherServlet.checkMultipart()}에서,
     * 즉 <b>핸들러를 고르기 전에</b> 터진다. 그래서 컨트롤러 안의 {@code @ExceptionHandler}는 이 예외를
     * 영영 못 본다(핸들러가 아직 null이다) — 컨트롤러에 두면 조용히 아래 catch-all로 떨어져 <b>500 +
     * error.html + 스택트레이스</b>가 된다. 사용자에겐 「사진이 크다」가, 로그엔 서버 결함이 찍힌다.
     *
     * <p>본문을 뷰가 아니라 평문으로 돌려주는 것은 이 예외가 API(사진 전사)에서만 나기 때문이다 —
     * 화면의 {@code errorMessage()}가 그 문구를 그대로 상태줄에 띄운다.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseBody
    public ResponseEntity<String> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        log.debug("업로드 용량 초과 — 413으로 응답: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body("사진은 3MB 이하로 올려 주세요");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleUnexpected(Exception ex, Model model) {
        log.error("처리되지 않은 예외 — error 뷰로 응답", ex);
        model.addAttribute("status", 500);
        model.addAttribute("message", "예상치 못한 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
        return "error";
    }
}
