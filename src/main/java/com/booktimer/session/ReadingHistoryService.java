package com.booktimer.session;

import com.booktimer.book.Book;
import com.booktimer.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * 일자별 독서 기록 조회 유스케이스 (README 2.2).
 *
 * <p>완료된 측정 세션({@code endedAt != null})을 <b>유저 타임존 기준 일자</b>로 묶어 그날의 총
 * 독서 시간과 읽은 책 제목을 집계한다. "어떤 날인지"는 서버 UTC가 아니라 유저가 사는 곳의 자정 경계로
 * 정해져야 하므로 {@link User#getTimezone()}으로 {@link java.time.Instant}를 {@link LocalDate}로 변환한다.
 *
 * <p>MVP 규모에서는 유저의 세션을 메모리에서 묶는다. 데이터가 커지면 DB 집계 쿼리로 옮긴다.
 */
@Service
@Transactional(readOnly = true)
public class ReadingHistoryService {

    private final ReadingSessionRepository sessionRepository;

    public ReadingHistoryService(ReadingSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    /**
     * 유저의 완료된 독서 세션을 일자별로 집계해 최신 일자가 먼저 오도록 반환한다.
     *
     * @param user 조회 주체
     * @return 일자별 집계 목록(최신순). 기록이 없으면 빈 목록.
     */
    public List<DailyReadingRecord> dailyHistory(User user) {
        return aggregate(user, session -> true);
    }

    /**
     * 일자별 기록을 유저 타임존 기준 <b>월별로 묶어</b> 최신 월이 먼저 오도록 반환한다.
     *
     * <p>history 화면의 '한 번에 한 달' 보기용. {@link #dailyHistory}(최신 일 먼저) 결과를 그대로
     * {@link YearMonth}로 묶으므로, 삽입 순서를 보존하는 {@link LinkedHashMap} 덕에 월·일 모두 최신이
     * 먼저다. 각 섹션은 그 달 총 독서 시간을 동봉한다(월 헤더에 바로 쓰도록).
     *
     * @param user 조회 주체
     * @return 월별 묶음 목록(최신 월 먼저). 기록이 없으면 빈 목록.
     */
    public List<MonthlyReadingSection> monthlyHistory(User user) {
        return MonthlyReadingSection.groupByMonth(dailyHistory(user));
    }

    /** 완료 세션을 유저 타임존 일자로 묶되 {@code include}를 통과한 것만 합산한다(최신 일자 먼저). */
    private List<DailyReadingRecord> aggregate(User user, Predicate<ReadingSession> include) {
        ZoneId zone = ZoneId.of(user.getTimezone());

        // 최신 일자가 먼저 오도록 내림차순 TreeMap에 누적
        Map<LocalDate, DayAccumulator> byDate = new TreeMap<>(Comparator.reverseOrder());
        for (ReadingSession session : sessionRepository.findByUserWithBook(user)) {
            if (session.isActive() || !include.test(session)) {
                continue; // 진행 중(미종료)·필터 미통과 세션은 집계 제외
            }
            LocalDate date = LocalDate.ofInstant(session.getStartedAt(), zone);
            DayAccumulator acc = byDate.computeIfAbsent(date, d -> new DayAccumulator());
            acc.seconds += session.getDurationSeconds();
            acc.manual |= session.isManualEntry(); // 그날 수동 입력이 하나라도 있으면 "직접 채운 날"
            // 책 제목을 읽은 순서대로, 중복 없이 모은다. 책 미지정(레거시 null) 세션은 제목 없음.
            // book은 LAZY라 readOnly 트랜잭션 안에서 접근한다(MVP 규모라 N+1 허용; 커지면 fetch join/집계 쿼리로).
            Book book = session.getBook();
            if (book != null) {
                acc.titles.add(book.getTitle());
            }
        }

        return byDate.entrySet().stream()
                .map(e -> new DailyReadingRecord(e.getKey(), e.getValue().seconds,
                        List.copyOf(e.getValue().titles), e.getValue().manual))
                .toList();
    }

    /** 하루치 누적기 — 총 독서 시간(초), 읽은 책 제목(중복 제거·읽은 순서), 수동 입력 포함 여부. */
    private static final class DayAccumulator {
        long seconds = 0L;
        boolean manual = false;
        final LinkedHashSet<String> titles = new LinkedHashSet<>();
    }
}
