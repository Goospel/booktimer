package com.booktimer.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 온보딩(첫 진입 초기 설정) 폼 바인딩 객체.
 *
 * <p>사용자가 처음 정하는 값 — 표시 닉네임과 타이머 값(초기 잔여/하루 증가값/누적 상한). 닉네임은
 * 특히 소셜 로그인 사용자가 자동 배정된 임시 닉을 자기 것으로 바꾸는 입구다. 도메인은 시간을 초로
 * 저장하지만 화면은 사람이 다루기 쉬운 <b>분(정수)</b>으로 입력받는다. 분↔초 변환·"초기값 ≤ 상한"
 * 교차 검증·닉네임 중복 처리는 컨트롤러/서비스가 담당한다. Thymeleaf {@code th:field} 바인딩을 위해
 * 가변 JavaBean으로 둔다.
 */
public class OnboardingForm {

    /** 표시 닉네임(유니크). 공백 불가. */
    @NotBlank
    @Size(max = 50)
    private String nickname;

    /** 초기 잔여(시작 독서 시간, 분). 0 이상. */
    @NotNull
    @Min(0)
    private Integer initialMinutes;

    /** 하루 증가값(분). 0 이상. */
    @NotNull
    @Min(0)
    private Integer incrementMinutes;

    /** 누적 상한(분). 0 이상. */
    @NotNull
    @Min(0)
    private Integer capMinutes;

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public Integer getInitialMinutes() {
        return initialMinutes;
    }

    public void setInitialMinutes(Integer initialMinutes) {
        this.initialMinutes = initialMinutes;
    }

    public Integer getIncrementMinutes() {
        return incrementMinutes;
    }

    public void setIncrementMinutes(Integer incrementMinutes) {
        this.incrementMinutes = incrementMinutes;
    }

    public Integer getCapMinutes() {
        return capMinutes;
    }

    public void setCapMinutes(Integer capMinutes) {
        this.capMinutes = capMinutes;
    }
}
