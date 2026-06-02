package com.booktimer.session;

import java.time.LocalDate;

/**
 * 하루치 독서 기록 집계 (읽기 전용 뷰 모델).
 *
 * <p>README 2.2 — MVP에서는 "어떤 책"인지는 묻지 않고 <b>시간만</b> 기록한다. 같은 날(유저 타임존
 * 기준)의 완료된 측정 세션들을 묶어 총 독서 시간과 세션 수를 담는다.
 *
 * @param date          유저 타임존 기준 일자
 * @param totalSeconds  그날의 총 독서 시간(초)
 * @param sessionCount  그날의 완료된 측정 세션 수
 */
public record DailyReadingRecord(LocalDate date, long totalSeconds, int sessionCount) {
}
