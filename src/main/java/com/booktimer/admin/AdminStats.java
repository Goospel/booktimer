package com.booktimer.admin;

/**
 * 운영자 대시보드 통계 요약(읽기 전용 집계 스냅샷, N-037).
 *
 * <p>모든 값은 기존 테이블을 count/sum 으로 요약한 것이며 새로 저장하지 않는다. "가입자 수"는
 * 운영자(ADMIN)를 제외한 {@code Role.USER} 수다 — 운영 계정이 지표를 부풀리지 않게 한다.
 *
 * @param totalUsers              가입자 수(USER 역할만, ADMIN 제외)
 * @param onboardedUsers          온보딩 완료 가입자 수(USER 중 onboarded=true)
 * @param activeUsers             최근 {@code activeWindowDays}일 내 측정한 distinct 사용자 수
 * @param activeWindowDays        활성 사용자 집계 창(일)
 * @param totalBooks              등록된 책 총 수
 * @param totalSessions           측정 세션 총 수
 * @param totalReadingSeconds     누적 독서 시간(초) — 전 세션 측정 길이 합
 * @param avgReadingSecondsPerUser 가입자 1인당 평균 독서 시간(초). 가입자 0명이면 0(0 나눗셈 회피).
 */
public record AdminStats(
        long totalUsers,
        long onboardedUsers,
        long activeUsers,
        int activeWindowDays,
        long totalBooks,
        long totalSessions,
        long totalReadingSeconds,
        long avgReadingSecondsPerUser) {
}
