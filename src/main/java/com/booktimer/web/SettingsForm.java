package com.booktimer.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 설정 폼 바인딩 객체.
 *
 * <p>사용자가 바꾸는 값 — 닉네임/타임존(프로필)과 하루 목표(타이머). 도메인은 초로 저장하지만
 * 화면은 사람이 다루기 쉬운 <b>분(정수)</b>으로 입력받는다. 분↔초 변환은 컨트롤러가 담당한다.
 * Thymeleaf {@code th:field} 바인딩을 위해 가변 JavaBean으로 둔다.
 *
 * <p>옛 "누적 상한(cap)"은 7일 윈도우 부채 모델로 전환하며 사라졌다 — 최대 부채가 7×목표로 자연
 * 제한되므로 별도 상한 설정이 필요 없다.
 */
public class SettingsForm {

    @NotBlank
    @Size(max = 30)
    private String nickname;

    @NotBlank
    private String timezone;

    /** 하루 목표(분). 0 이상. */
    @NotNull
    @Min(0)
    private Integer incrementMinutes;

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

    public Integer getIncrementMinutes() {
        return incrementMinutes;
    }

    public void setIncrementMinutes(Integer incrementMinutes) {
        this.incrementMinutes = incrementMinutes;
    }
}
