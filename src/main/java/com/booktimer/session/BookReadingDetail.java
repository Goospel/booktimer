package com.booktimer.session;

import java.util.List;

/**
 * 한 책의 독서 상세 read model — 책별 잔디 + 일자별 기록 + 누적 시간.
 *
 * @param graph        그 책만으로 만든 컨트리뷰션 그래프(잔디)
 * @param dailyHistory 그 책의 일자별 독서 기록(최신순)
 * @param totalSeconds 그 책의 누적 독서 시간(초)
 */
public record BookReadingDetail(ContributionGraph graph, List<DailyReadingRecord> dailyHistory, long totalSeconds) {
}
