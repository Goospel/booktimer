package com.booktimer.session;

import java.time.LocalDate;

/**
 * 독서 잔디(컨트리뷰션 그래프)의 한 칸 = 하루.
 *
 * <p>GitHub 잔디처럼 1년치 날짜를 칸으로 깔고, 그날 읽은 시간만큼 색 농도(level)를 올린다.
 * 그리드의 가장자리(첫 주의 빈 앞부분 / 마지막 주의 미래 날짜)는 날짜 없는 <b>placeholder</b>로
 * 둬서 화면에서 빈 칸으로 렌더한다.
 *
 * @param date         유저 타임존 기준 일자. {@code null}이면 placeholder(빈 칸).
 * @param totalSeconds 그날 총 독서 시간(초)
 * @param level        색 농도 단계 0~4 (0=없음). 임계는 {@link ContributionGraphBuilder}가 정한다.
 */
public record ContributionDay(LocalDate date, long totalSeconds, int level) {

    /** 날짜 없는 빈 칸(그리드 가장자리 채움용). */
    public static ContributionDay placeholder() {
        return new ContributionDay(null, 0L, 0);
    }

    public boolean isPlaceholder() {
        return date == null;
    }
}
