package com.booktimer.session;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 한 달치 독서 기록 묶음 (읽기 전용 뷰 모델).
 *
 * <p>{@link DailyReadingRecord}들을 유저 타임존 기준 {@link YearMonth}로 묶어, 그 달의 일자별
 * 기록(최신 일 먼저)과 월 총 독서 시간을 담는다. history 화면·책 상세의 '한 번에 한 달' 보기에 쓰인다 —
 * 기록이 쌓여도 화면이 한없이 길어지지 않게 월 단위로 끊고(◀▶ 이동), 그 달 안에서만 스크롤한다.
 *
 * @param month        유저 타임존 기준 연·월
 * @param totalSeconds 그 달의 총 독서 시간(초)
 * @param days         그 달 일자별 기록(최신 일 먼저)
 */
public record MonthlyReadingSection(YearMonth month, long totalSeconds, List<DailyReadingRecord> days) {

    /**
     * 일자별 기록(최신 일 먼저)을 유저 타임존 기준 {@link YearMonth}로 묶어 월별 섹션 목록을 만든다.
     *
     * <p>입력의 삽입 순서를 보존하는 {@link LinkedHashMap} 덕에, 입력이 최신 일 먼저면 월·일 모두 최신이
     * 먼저다. 각 섹션은 그 달 총 독서 시간을 동봉한다(월 헤더에 바로 쓰도록). 전체 독서 기록(history)과
     * 책별 상세(book-detail)가 같은 월별 보기를 공유한다.
     *
     * @param dailyDesc 일자별 기록(최신 일 먼저 권장 — 그 순서대로 묶인다)
     * @return 월별 묶음 목록(입력이 최신순이면 최신 월 먼저). 빈 입력이면 빈 목록.
     */
    public static List<MonthlyReadingSection> groupByMonth(List<DailyReadingRecord> dailyDesc) {
        Map<YearMonth, List<DailyReadingRecord>> byMonth = new LinkedHashMap<>();
        for (DailyReadingRecord record : dailyDesc) {
            byMonth.computeIfAbsent(YearMonth.from(record.date()), m -> new ArrayList<>()).add(record);
        }
        return byMonth.entrySet().stream()
                .map(e -> new MonthlyReadingSection(
                        e.getKey(),
                        e.getValue().stream().mapToLong(DailyReadingRecord::totalSeconds).sum(),
                        List.copyOf(e.getValue())))
                .toList();
    }
}
