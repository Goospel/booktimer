package com.booktimer.session;

import com.booktimer.book.Book;
import com.booktimer.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

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
        // 잔디·부채는 목표 이력을 스스로 해석하므로 여기선 미산정(0)으로 둔다. 세션 줄도 안 만든다
        // (대시보드마다 부르는 경로라 전 세션의 시각 문자열을 헛되이 만들지 않는다).
        return aggregate(user, session -> true, date -> 0L, false);
    }

    /**
     * 일자별 기록을 유저 타임존 기준 <b>월별로 묶어</b> 최신 월이 먼저 오도록 반환한다.
     *
     * <p>history 화면의 '한 번에 한 달' 보기용. {@link #dailyHistory}(최신 일 먼저) 결과를 그대로
     * {@link YearMonth}로 묶으므로, 삽입 순서를 보존하는 {@link LinkedHashMap} 덕에 월·일 모두 최신이
     * 먼저다. 각 섹션은 그 달 총 독서 시간을 동봉한다(월 헤더에 바로 쓰도록).
     *
     * <p>각 날에 <b>그날 유효했던 하루 목표</b>를 {@code goalFor}로 물어 실어 준다 — 기록 화면 하루
     * 막대가 「그 달 최대」가 아니라 그날 목표를 기준으로 그려지도록. 리졸버를 주입받는 이유는 목표 이력
     * 해석이 이 서비스의 몫이 아니어서다(잔디와 같은 {@code GoalSchedule}을 호출자가 넘긴다 —
     * 그래야 막대 100%와 잔디 lv4가 같은 날에 같은 답을 한다).
     *
     * <p>각 날에 <b>측정 한 건씩</b>({@link DailyReadingRecord.SessionRow})도 싣는다 — 기록 화면이 날짜를 펼쳐
     * 줄마다 책을 붙이거나 바꾸는 좌표다. 정렬은 여기 한 곳이 정한다: 실측 {@code startedAt} 오름차순 뒤에
     * 수동 기록({@code startedAt} 오름차순). 시각은 유저 타임존 {@code "HH:mm"}(날짜 귀속과 같은 TZ)이고
     * 수동 기록은 시각을 비운다(서버 앵커라 화면에 찍으면 거짓이다).
     *
     * @param user   조회 주체
     * @param goalFor 유저 타임존 일자 → 그날 하루 목표(초). 목표를 안 실을 거면 {@code d -> 0L}.
     * @return 월별 묶음 목록(최신 월 먼저). 기록이 없으면 빈 목록.
     */
    public List<MonthlyReadingSection> monthlyHistory(User user, ToLongFunction<LocalDate> goalFor) {
        return MonthlyReadingSection.groupByMonth(aggregate(user, session -> true, goalFor, true));
    }

    /**
     * 완료 세션을 유저 타임존 일자로 묶되 {@code include}를 통과한 것만 합산한다(최신 일자 먼저).
     * {@code withSessions}면 날마다 세션 줄을 싣는다({@link #monthlyHistory}만).
     */
    private List<DailyReadingRecord> aggregate(User user, Predicate<ReadingSession> include,
                                               ToLongFunction<LocalDate> goalFor, boolean withSessions) {
        ZoneId zone = ZoneId.of(user.getTimezone());
        DateTimeFormatter clock = DateTimeFormatter.ofPattern("HH:mm").withZone(zone);

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
            if (withSessions) {
                acc.sessions.add(session);
            }
        }

        return byDate.entrySet().stream()
                // e.getKey()는 이미 유저 타임존 일자다 — 그대로 물어야 목표의 자정 경계가 기록과 맞는다.
                .map(e -> new DailyReadingRecord(e.getKey(), e.getValue().seconds,
                        e.getValue().booksLongestFirst(), e.getValue().manual, goalFor.applyAsLong(e.getKey()),
                        e.getValue().sessionRows(clock)))
                .toList();
    }

    /** 실측 먼저, 그 안에서 시작 시각 순 — 수동 기록은 시각이 서버 앵커라 맨 뒤에 모은다. */
    private static final Comparator<ReadingSession> ROW_ORDER =
            Comparator.comparing(ReadingSession::isManualEntry).thenComparing(ReadingSession::getStartedAt);

    /** 하루치 누적기 — 총 독서 시간(초), 책별 누적(제목 키), 수동 입력 포함 여부, 세션 줄 재료. */
    private static final class DayAccumulator {
        long seconds = 0L;
        boolean manual = false;
        /** 제목 → 그 책 누적. 삽입 순서를 보존해 <b>동률일 때 먼저 편 책이 앞</b>에 남는다. */
        final Map<String, BookAccumulator> books = new LinkedHashMap<>();
        /** 그날 세션 — {@code withSessions}일 때만 채운다. */
        final List<ReadingSession> sessions = new ArrayList<>();

        List<DailyReadingRecord.SessionRow> sessionRows(DateTimeFormatter clock) {
            return sessions.stream().sorted(ROW_ORDER).map(s -> {
                Book book = s.getBook();
                boolean manual = s.isManualEntry();
                return new DailyReadingRecord.SessionRow(s.getId(),
                        manual ? null : clock.format(s.getStartedAt()),
                        manual ? null : clock.format(s.getEndedAt()),
                        s.getDurationSeconds(),
                        book == null ? null : book.getId(),
                        book == null ? null : book.getTitle(),
                        manual);
            }).toList();
        }

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
