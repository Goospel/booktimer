package com.booktimer.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 폼 바인딩 객체.
 *
 * <p>웹 입력 검증(형식/필수)을 담당하는 표현 계층 DTO다 — 도메인 {@code User}와 분리한다.
 * Thymeleaf {@code th:field} 바인딩을 위해 가변 JavaBean(게터/세터)로 둔다.
 * 평문 비밀번호를 받아 컨트롤러가 등록 서비스(해싱 담당)로 넘긴다.
 */
public class SignupForm {

    @NotBlank
    @Email
    private String email;

    /** 로그인 아이디(공개 @핸들·로그인 식별자). 형식은 도메인과 동일(영문/숫자/_ 3~20자). 불변 — 가입에서 확정. */
    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9_]{3,20}$", message = "아이디는 영문/숫자/_ 3~20자여야 합니다")
    private String loginId;

    @NotBlank
    @Size(min = 8, max = 72) // BCrypt 입력 상한 72바이트
    private String password;

    @NotBlank
    @Size(max = 30)
    private String nickname;

    @NotBlank
    private String timezone = "Asia/Seoul";

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getLoginId() {
        return loginId;
    }

    public void setLoginId(String loginId) {
        this.loginId = loginId;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }
}
