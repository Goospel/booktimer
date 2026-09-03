package com.booktimer.web;

import com.booktimer.security.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

/**
 * 웹 「공부」 화면 셸 — 달력·일정 원장(Vue 섬 {@code study}).
 *
 * <p>모델이 비어 있는 얇은 셸이다: 데이터는 전부 {@code /api/study/**}가 나른다({@code /agenda}는 새 문,
 * 달력·체크는 미니앱과 같은 기존 문을 웹 세션으로 재사용 — 설계 §1.2).
 *
 * <p>{@code precommit}이 필요한 이유는 {@code th:action} 폼 때문이 아니라 <b>템플릿의
 * {@code <meta name="_csrf">}</b> 때문이다 — 섬의 POST(체크 순환·일정 추가·삭제)가 그 메타에서 토큰을
 * 읽는데, 렌더 시점에 세션이 lazy 생성되므로 응답이 이미 커밋됐으면 그 페이지만 500이 된다(T-033·T-049).
 */
@Controller
public class StudyController {

    private final CurrentUserService currentUserService;

    public StudyController(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @GetMapping("/study")
    public String study(Principal principal, HttpServletRequest request) {
        currentUserService.resolve(principal); // 인증 트리거 + 존재 확인
        CsrfTokenUtil.precommit(request);
        return "study";
    }
}
