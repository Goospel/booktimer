package com.booktimer.user;

/**
 * 사용자가 직접 켜는 독서 알림의 방식(N3, V97). 기본 {@link #OFF} — 동의 없이 켜지는 사람이 없게 한다.
 * 보내는 규칙은 {@code ReadingReminderService.dueThreshold}가 단일 출처다.
 */
public enum ReadingReminderKind {

    /** 꺼짐(기본값). */
    OFF,

    /** 매일 고른 시각까지 그날 독서 기록이 없으면 한 통. */
    DAILY,

    /** 독서 기록 없이 3일째가 되면 고른 시각에 쉬는 구간당 한 통. */
    REST
}
