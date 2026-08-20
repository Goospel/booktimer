package com.booktimer.session;

import com.booktimer.book.Book;
import com.booktimer.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
            // 책별로 시간을 누적한다 — 같은 책을 여러 번 폈으면 한 줄로 합쳐야 더미에 같은 책이 두 번 안 꽂힌다.
            // 묶는 키는 제목이다(예전 중복 제거와 같은 기준). book은 LAZY라 readOnly 트랜잭션 안에서 접근한다
            // (MVP 규모라 N+1 허용; 커지면 fetch join/집계 쿼리로).
            Book book = session.getBook();
            if (book != null) {
                BookAccumulator read = acc.books.computeIfAbsent(book.getTitle(), t -> new BookAccumulator());
                read.seconds += session.getDurationSeconds();
                if (read.coverUrl == null) {
                    read.coverUrl = book.getCoverUrl();
                }
            }
        }

        return byDate.entrySet().stream()
                .map(e -> new DailyReadingRecord(e.getKey(), e.getValue().seconds,
                        e.getValue().booksLongestFirst(), e.getValue().manual))
                .toList();
    }

    /** 하루치 누적기 — 총 독서 시간(초), 책별 누적(제목 키), 수동 입력 포함 여부. */
    private static final class DayAccumulator {
        long seconds = 0L;
        boolean manual = false;
        /** 제목 → 그 책 누적. 삽입 순서를 보존해 <b>동률일 때 먼저 편 책이 앞</b>에 남는다. */
        final Map<String, BookAccumulator> books = new LinkedHashMap<>();

        /**
         * 오래 읽은 순으로 굳힌 책 목록.
         *
         * <p>접힌 더미에서 눈에 보이는 건 맨 앞 한 장이라, 그 한 장이 <b>그날을 대표</b>해야 한다.
         * 동률은 {@link List#sort}가 안정 정렬이라 삽입(=읽은) 순서가 그대로 남는다 — 안 그러면
         * 같은 데이터를 볼 때마다 더미 순서가 뒤바뀐다.
         */
        List<BookRead> booksLongestFirst() {
            List<BookRead> list = new ArrayList<>();
            books.forEach((title, read) -> list.add(new BookRead(title, read.coverUrl, read.seconds)));
            list.sort(Comparator.comparingLong(BookRead::seconds).reversed());
            return List.copyOf(list);
        }
    }

    /** 책 한 권의 하루 누적 — 표지는 처음 만난 세션 것을 쓴다(같은 책이면 어느 세션에서 와도 같다). */
    private static final class BookAccumulator {
        long seconds = 0L;
        String coverUrl = null;
    }
}
