package com.booktimer.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 설정 폼 바인딩 객체.
 *
 * <p>사용자가 바꾸는 값 — 닉네임/타임존(프로필)과 증가값/cap(타이머). 도메인은 초로 저장하지만
 * 화면은 사람이 다루기 쉬운 <b>분(정수)</b>으로 입력받는다. 분↔초 변환은 컨트롤러가 담당한다.
 * Thymeleaf {@code th:field} 바인딩을 위해 가변 JavaBean으로 둔다.
 */
public class SettingsForm {

    @NotBlank
    @Size(max = 30)
    private String nickname;

    @NotBlank
    private String timezone;

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

    public Integer getCapMinutes() {
        return capMinutes;
    }

    public void setCapMinutes(Integer capMinutes) {
        this.capMinutes = capMinutes;
    }
}
