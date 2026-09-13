package com.booktimer.study;

import com.booktimer.user.User;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 「오늘」의 단일 출처 — <b>유저 타임존의</b> 달력 날짜.
 *
 * <p>공부 화면에서 「오늘」이 쓰이는 자리는 달력의 미래 잠금과 일정 교체의 경계다 — 둘 다 <b>사용자가
 * 보는 날짜</b>라 유저 타임존이 맞다. <b>하루 상한의 날짜 키는 여기가 아니다</b>: 타임존은 사용자가
 * 설정에서 바꿀 수 있어 자정 근처에 존만 옮기면 몫이 두 배가 되므로 2026-09-13에 UTC로 갔다
 * ({@link StudyAiUsageService} javadoc의 S-3). 같은 뜻의 오늘을 서로 다른 방식으로 구하면 자정 근처에서
 * 한 화면 안의 판단이 갈린다 —
 * 화면이 허용한 탭을 서버가 400으로 거절하는 꼴이 된다. 그래서 {@code StudyCalendarService.setCheck}의
 * 미래 판정도 여기를 지난다(인라인 {@code LocalDate.ofInstant}를 두면 규칙이 둘이 된다).
 */
public final class StudyDates {

    private StudyDates() {
    }

    public static LocalDate today(User user, Clock clock) {
        return today(user, clock.instant());
    }

    /** 시각을 인자로 받는 쪽 — 결정적 테스트가 경계 직전·직후를 심을 수 있게 {@code now}를 밖에서 준다. */
    public static LocalDate today(User user, Instant now) {
        return LocalDate.ofInstant(now, zone(user));
    }

    /**
     * 유저 타임존 그 자체 — 날짜가 아니라 <b>존</b>이 필요한 자리(정답지 헤더 날짜 등)의 단일 출처.
     *
     * <p>인라인 {@code ZoneId.of(user.getTimezone())}을 두면 위 경고대로 규칙이 둘이 된다.
     */
    public static ZoneId zone(User user) {
        return ZoneId.of(user.getTimezone());
    }
}
