package com.booktimer.session;

import java.time.LocalDate;

/**
 * 하루치 독서 부채 (읽기 전용 뷰 모델).
 *
 * <p>부채는 날짜별로 독립이다(N-001의 단일 누적 카운터를 대체) — 하루 부채 = max(0, 하루목표 − 그날 읽은 초).
 * "빠뜨린 날" 목록의 한 항목으로 쓰여, 사용자가 그 날짜를 골라 사후 수동 입력으로 채울 수 있다.
 *
 * @param date        유저 타임존 기준 일자
 * @param debtSeconds 그날 못 채운 부채(초, 0 초과 — 채운 날은 목록에 안 들어옴)
 */
public record DayDebt(LocalDate date, long debtSeconds) {
}
