package com.booktimer.session;

import java.util.List;

/**
 * 한 책의 독서 상세 read model — 월별 일자 기록 + 누적 시간.
 *
 * @param monthlyHistory 그 책의 일자별 독서 기록을 월별로 묶은 것(최신 월·일 먼저)
 * @param totalSeconds   그 책의 누적 독서 시간(초)
 */
public record BookReadingDetail(List<MonthlyReadingSection> monthlyHistory, long totalSeconds) {
}
