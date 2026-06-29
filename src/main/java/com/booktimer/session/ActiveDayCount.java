package com.booktimer.session;

/**
 * 친구 추천 활동량 폴백(E 생성기) — 사용자별 최근 N일 distinct 활동일 수 프로젝션.
 */
public interface ActiveDayCount {
    Long getUserId();
    long getActiveDayCount();
}
