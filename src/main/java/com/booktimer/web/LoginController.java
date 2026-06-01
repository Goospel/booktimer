package com.booktimer.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 커스텀 로그인 화면.
 *
 * <p>Spring Security 기본 생성 페이지 대신 우리 템플릿({@code login.html})을 보여준다.
 * 인증 처리(POST /login)는 여전히 Security 필터가 담당하고, 이 컨트롤러는 GET 화면만 그린다.
 * 에러/로그아웃/가입완료 안내는 템플릿이 쿼리 파라미터({@code ?error/?logout/?registered})로 처리한다.
 */
@Controller
public class LoginController {

    @GetMapping("/login")
    public String loginForm() {
        return "login";
    }
}
