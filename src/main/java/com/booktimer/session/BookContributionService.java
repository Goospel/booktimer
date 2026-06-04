package com.booktimer.session;

import com.booktimer.book.Book;
import com.booktimer.timer.ReadingTimerRepository;
import com.booktimer.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 한 책의 독서 상세(잔디 + 일자별 기록 + 누적 시간) 조회 유스케이스.
 *
 * <p>전체 잔디({@link ReadingContributionService})와 같은 사상이되, 집계 대상을 "그 책에 연결된 완료 세션"으로
 * 좁힌다. "오늘"은 유저 타임존 자정 경계로 정하고(N-010, {@link Clock}+{@link User#getTimezone()}),
 * 그리드 구성은 순수 빌더 {@link ContributionGraphBuilder}에 위임한다.
 *
 * <p>색 농도는 전체 잔디와 동일하게 유저의 <b>하루 목표</b>(타이머 {@code dailyIncrementSeconds}) 대비 달성 비율로
 * 정한다 — 책별 칸도 같은 기준이라야 직관적이다. 타이머가 없으면 기본 목표로 폴백한다.
 */
@Service
@Transactional(readOnly = true)
public class BookContributionService {

    private final ReadingSessionRepository sessionRepository;
    private final ReadingTimerRepository timerRepository;
    private final Clock clock;

    public BookContributionService(ReadingSessionRepository sessionRepository,
                                   ReadingTimerRepository timerRepository,
                                   Clock clock) {
        this.sessionRepository = sessionRepository;
        this.timerRepository = timerRepository;
        this.clock = clock;
    }

    public BookReadingDetail detail(User user, Book book) {
        ZoneId zone = ZoneId.of(user.getTimezone());
        LocalDate today = LocalDate.ofInstant(clock.instant(), zone);

        // 그 책의 완료 세션을 유저 타임존 일자로 묶어 총 독서 시간을 낸다(최신 일자 먼저).
        Map<LocalDate, Long> byDate = new TreeMap<>(Comparator.reverseOrder());
        for (ReadingSession session : sessionRepository.findByUserAndBook(user, book)) {
            if (session.isActive()) {
                continue; // 진행 중(미종료) 세션은 제외
            }
            LocalDate date = LocalDate.ofInstant(session.getStartedAt(), zone);
            byDate.merge(date, session.getDurationSeconds(), Long::sum);
        }

        // 이 페이지는 한 책이라 책 제목은 그 책 하나뿐(상세 화면에선 굳이 렌더하지 않음).
        List<String> titles = List.of(book.getTitle());
        List<DailyReadingRecord> dailyHistory = byDate.entrySet().stream()
                .map(e -> new DailyReadingRecord(e.getKey(), e.getValue(), titles))
                .toList();

        Map<LocalDate, Long> secondsByDate = new LinkedHashMap<>();
        long total = 0L;
        for (DailyReadingRecord record : dailyHistory) {
            secondsByDate.put(record.date(), record.totalSeconds());
            total += record.totalSeconds();
        }

        long goalSeconds = timerRepository.findByUser(user)
                .map(timer -> timer.getDailyIncrementSeconds())
                .orElse(ReadingContributionService.DEFAULT_GOAL_SECONDS);

        return new BookReadingDetail(
                ContributionGraphBuilder.build(secondsByDate, today, goalSeconds), dailyHistory, total);
    }
}
