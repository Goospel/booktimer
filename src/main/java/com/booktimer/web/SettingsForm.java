package com.booktimer.web;

import com.booktimer.user.User;
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
 * <p>옛 "누적 상한(cap)"은 per-day 부채 모델로 전환하며 사라졌다. 부채는 다시 무제한 누적되지만
 * (2026-08-14 — 7일 자동 소멸 폐지) 상한을 사용자 설정으로 되살리지는 않는다 — 줄이는 수단이
 * 더 읽기·리워드 광고로 명확해서 숫자를 스스로 깎는 설정은 의미가 없다.
 */
public class SettingsForm {

    @NotBlank
    @Size(max = User.NICKNAME_MAX_LENGTH)
    private String nickname;

    @NotBlank
    private String timezone;

    /** 하루 목표(분). 0 이상. */
    @NotNull
    @Min(0)
    private Integer incrementMinutes;

    /**
     * 밀린 부채를 헤드라인 타이머에 합산해 표시할지(표시 전용 토글). 체크박스 바인딩 — 체크 해제 시
     * 파라미터가 전송되지 않으므로 기본값 false로 두고, GET 폼은 현재 타이머 값으로 채운다.
     */
    private boolean debtCarryover;

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

    public boolean isDebtCarryover() {
        return debtCarryover;
    }

    public void setDebtCarryover(boolean debtCarryover) {
        this.debtCarryover = debtCarryover;
    }
}
