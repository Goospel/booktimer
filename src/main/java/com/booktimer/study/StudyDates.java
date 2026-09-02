package com.booktimer.study;

import com.booktimer.user.User;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 「오늘」의 단일 출처 — <b>유저 타임존의</b> 달력 날짜.
 *
 * <p>공부 화면에서 「오늘」이 쓰이는 자리는 셋이다: 달력의 미래 잠금 · 일정 교체의 경계 · (다음 판)
 * 하루 상한의 날짜 키. 셋이 서로 다른 방식으로 오늘을 구하면 자정 근처에서 한 화면 안의 판단이 갈린다
 * ({@code StudyCalendarService.setCheck}가 이미 이 규칙을 쓰고 있어, 그것과 어긋나면 화면이 허용한
 * 탭을 서버가 400으로 거절한다).
 */
public final class StudyDates {

    private StudyDates() {
    }

    public static LocalDate today(User user, Clock clock) {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(user.getTimezone()));
    }
}
