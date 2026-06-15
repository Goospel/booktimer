package com.booktimer.session;

import java.time.YearMonth;
import java.util.List;

/**
 * 한 달치 독서 기록 묶음 (읽기 전용 뷰 모델).
 *
 * <p>{@link DailyReadingRecord}들을 유저 타임존 기준 {@link YearMonth}로 묶어, 그 달의 일자별
 * 기록(최신 일 먼저)과 월 총 독서 시간을 담는다. history 화면의 '한 번에 한 달' 보기에 쓰인다 —
 * 기록이 쌓여도 화면이 한없이 길어지지 않게 월 단위로 끊고(◀▶ 이동), 그 달 안에서만 스크롤한다.
 *
 * @param month        유저 타임존 기준 연·월
 * @param totalSeconds 그 달의 총 독서 시간(초)
 * @param days         그 달 일자별 기록(최신 일 먼저)
 */
public record MonthlyReadingSection(YearMonth month, long totalSeconds, List<DailyReadingRecord> days) {
}
