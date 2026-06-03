package com.booktimer.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 온보딩(첫 진입 초기 설정) 폼 바인딩 객체.
 *
 * <p>사용자가 처음 정하는 타이머 값 — 초기 잔여(시작값)/하루 증가값/누적 상한. 도메인은 초로
 * 저장하지만 화면은 사람이 다루기 쉬운 <b>분(정수)</b>으로 입력받는다. 분↔초 변환과
 * "초기값 ≤ 상한" 교차 검증은 컨트롤러가 담당한다. Thymeleaf {@code th:field} 바인딩을 위해
 * 가변 JavaBean으로 둔다.
 */
public class OnboardingForm {

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
