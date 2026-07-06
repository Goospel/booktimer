package com.booktimer.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// login_id는 소셜 로그인 사용자만 여기서 정한다(로컬은 가입에서 확정 — 입력칸 없음). 그래서 선택값:
// 빈 값을 허용하되(로컬), 입력하면 형식을 검증한다. "필수 여부"는 컨트롤러가 user.loginId==null로 판단한다.

/**
 * 온보딩(첫 진입 초기 설정) 폼 바인딩 객체.
 *
 * <p>사용자가 처음 정하는 값 — 표시 닉네임과 <b>하루 목표</b>. 닉네임은 특히 소셜 로그인 사용자가
 * 자동 배정된 임시 닉을 자기 것으로 바꾸는 입구다. 도메인은 시간을 초로 저장하지만 화면은 사람이
 * 다루기 쉬운 <b>분(정수)</b>으로 입력받는다. 분↔초 변환·닉네임 중복 처리는 컨트롤러/서비스가
 * 담당한다. Thymeleaf {@code th:field} 바인딩을 위해 가변 JavaBean으로 둔다.
 *
 * <p>옛 "초기 잔여"·"누적 상한"은 7일 윈도우 부채 모델로 전환하며 사라졌다 — 시작 부채라는 개념이
 * 없고(오늘부터 그날 목표가 곧 그날 부채), 상한은 윈도우(7일)가 대체한다.
 */
public class OnboardingForm {

    /**
     * 로그인/식별/검색용 공개 아이디(@핸들). <b>한번 정하면 바꿀 수 없다</b>. 영소문자/숫자/언더스코어
     * 3~20자(대문자 입력은 서버가 소문자로 정규화). 예약어 차단·유니크는 도메인/서비스가 검증한다.
     *
     * <p>선택값(빈 값 허용) — 로컬 가입자는 가입에서 이미 받아 입력칸이 없다. 소셜 로그인 사용자는 컨트롤러가
     * {@code user.loginId==null}로 필수를 강제한다. 입력하면 형식만 검증한다(빈 문자열 통과 → 컨트롤러 위임).
     */
    @Pattern(regexp = "^$|^[A-Za-z0-9_]{3,20}$",
            message = "아이디는 영문/숫자/_ 3~20자여야 합니다")
    private String loginId;

    /** 표시 닉네임(중복 허용·수정 가능). 공백 불가. */
    @NotBlank
    @Size(max = 50)
    private String nickname;

    /** 하루 목표(분). 최소 1분 — 0분은 "안 읽어도 됨"이라 목표의 의미가 사라진다(발견 10). */
    @NotNull
    @Min(1)
    private Integer incrementMinutes;

    public String getLoginId() {
        return loginId;
    }

    public void setLoginId(String loginId) {
        this.loginId = loginId;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public Integer getIncrementMinutes() {
        return incrementMinutes;
    }

    public void setIncrementMinutes(Integer incrementMinutes) {
        this.incrementMinutes = incrementMinutes;
    }
}
