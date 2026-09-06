package com.booktimer.web;

import jakarta.validation.constraints.AssertTrue;
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

    /**
     * 마케팅(재참여 넛지) 수신 동의 — <b>선택 항목, 기본 OFF</b>(체크 안 해도 가입됨). 정보통신망법 §50 사전 동의·
     * 개인정보보호법 끼워팔기 금지에 따라 필수 동의와 분리한다. 체크박스 미체크 시 false로 바인딩된다.
     */
    private boolean marketingEmailConsent = false;

    /**
     * 만 14세 이상임을 <b>본인이 확인</b>한다 — 개인정보 보호법 §22-2(만 14세 미만 아동은 법정대리인 동의 필요).
     * 서비스는 법정대리인 동의 절차를 두지 않으므로 <b>가입 자체를 막는 게이트</b>다.
     *
     * <p>마케팅 동의와 달리 <b>필수</b>다({@code @AssertTrue}) — 선택 동의와 나란히 두되 성격이 반대라,
     * 화면에서도 「(필수)」로 구분해 끼워팔기로 읽히지 않게 한다.
     *
     * <p>생년월일을 받지 않는 것은 의도다 — 나이를 <b>수집</b>하면 개인정보가 하나 늘어난다. 필요한 것은
     * 「14세 미만이 아니다」라는 사실 하나이고, 그건 확인만으로 충분하다(수집 최소화 원칙).
     *
     * <p>ponytail: 확인 사실을 계정에 저장하지 않는다 — 게이트를 통과해야만 계정이 생기므로 화면·코드가
     * 곧 증적이다. 가입 시점별 증적이 필요해지면 컬럼 + Flyway로 승격한다.
     */
    @AssertTrue(message = "만 14세 이상만 가입할 수 있어요")
    private boolean ageConfirmed = false;

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

    public boolean isMarketingEmailConsent() {
        return marketingEmailConsent;
    }

    public void setMarketingEmailConsent(boolean marketingEmailConsent) {
        this.marketingEmailConsent = marketingEmailConsent;
    }

    public boolean isAgeConfirmed() {
        return ageConfirmed;
    }

    public void setAgeConfirmed(boolean ageConfirmed) {
        this.ageConfirmed = ageConfirmed;
    }
}
