package com.booktimer.session;

import java.util.List;

/**
 * 최근 7일 윈도우 기준 독서 부채 스냅샷 (읽기 전용 뷰 모델).
 *
 * <p>대시보드가 보여주는 부채 상태 = <b>오늘 부채</b>(헤드라인) + <b>이번 주 빠뜨린 날</b> 목록.
 * 단일 누적 카운터(N-001) 대신 날짜별 독립 부채로 다루며, 윈도우(7일) 밖의 빚은 자동 용서되어
 * 여기 담기지 않는다(입문자 친화 — 죄책감 누적 차단, plan.md 레버 ③ 흡수).
 *
 * @param todayDebtSeconds 오늘 부채(초) = max(0, 하루목표 − 오늘 읽은 양). 헤드라인 카운트다운의 시작값.
 * @param missedDays       윈도우 내 과거 빠뜨린 날(부채>0)을 <b>최근이 먼저</b> 오도록. 모두 채웠으면 빈 목록.
 */
public record WeeklyDebt(long todayDebtSeconds, List<DayDebt> missedDays) {

    /** 총 부채(초) = 오늘 부채 + 빠뜨린 날 부채 합. 관리자 요약·전체 현황 표시에 쓴다. */
    public long totalDebtSeconds() {
        return todayDebtSeconds + missedDays.stream().mapToLong(DayDebt::debtSeconds).sum();
    }
}
