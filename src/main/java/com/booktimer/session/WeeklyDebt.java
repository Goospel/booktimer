package com.booktimer.session;

import java.util.List;

/**
 * 누적 독서 부채 스냅샷 (읽기 전용 뷰 모델).
 *
 * <p>대시보드가 보여주는 부채 상태 = <b>오늘 부채</b>(헤드라인) + <b>빠뜨린 날</b> 목록. 단일 누적
 * 카운터(N-001) 대신 날짜별 독립 부채로 다룬다. 7일 자동 소멸은 폐지됐으므로(2026-08-14) 오래된
 * 빚도 여기 그대로 담긴다 — 지우는 수단은 더 읽어서 갚기와 리워드 광고 용서다.
 *
 * <p>따라서 {@code missedDays}는 <b>상한이 없다</b>. 전량이 필요한 곳은 합계 계산뿐이고, 화면에 내려보내는
 * 곳({@code HistoryApiController})은 최근 분량으로 잘라 응답 크기를 묶는다.
 *
 * @param todayDebtSeconds 오늘 부채(초) = max(0, 하루목표 − 오늘 읽은 양). 헤드라인 카운트다운의 시작값.
 * @param todayGoalSeconds 오늘 유효 하루 목표(초). 진행바 분모로 쓴다 — 부채 계산과 같은 trace에서 유도해 단일 출처.
 * @param missedDays       창 내 과거 빠뜨린 날(부채>0)을 <b>최근이 먼저</b> 오도록. 모두 채웠으면 빈 목록.
 */
public record WeeklyDebt(long todayDebtSeconds, long todayGoalSeconds, List<DayDebt> missedDays) {

    /** 총 부채(초) = 오늘 부채 + 빠뜨린 날 부채 합. 관리자 요약·전체 현황 표시에 쓴다. */
    public long totalDebtSeconds() {
        return todayDebtSeconds + missedDays.stream().mapToLong(DayDebt::debtSeconds).sum();
    }
}
