package com.booktimer.admin;

import com.booktimer.session.WeeklyDebtTrace;

import java.time.LocalDate;

/**
 * 관리자 부채 진단 뷰 DTO — 임의 기준일(asOf)까지의 누적 창 계산 추적.
 *
 * @param loginId  조회 대상 사용자 로그인 아이디
 * @param nickname 닉네임(화면 표시용)
 * @param asOf     기준일(이 날이 윈도우 마지막 날). 미지정이면 유저 TZ 오늘
 * @param today    유저 TZ 기준 실제 오늘(네비게이션 빠른 이동 링크용)
 * @param trace    asOf 윈도우 날짜별 부채 추적 결과
 */
public record AdminDebtView(
        String loginId,
        String nickname,
        LocalDate asOf,
        LocalDate today,
        WeeklyDebtTrace trace
) {
}
