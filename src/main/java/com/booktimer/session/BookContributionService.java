package com.booktimer.session;

import com.booktimer.book.Book;
import com.booktimer.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 한 책의 독서 상세(월별 일자 기록 + 누적 시간) 조회 유스케이스.
 *
 * <p>집계 대상을 "그 책에 연결된 완료 세션"으로 좁혀, 유저 타임존({@link User#getTimezone()}) 자정 경계로
 * 일자를 정하고(N-010) 일자별 합을 낸 뒤, 전체 독서 기록(history)과 같은 월별 보기로 묶는다
 * ({@link MonthlyReadingSection#groupByMonth}). 책 상세는 한 책이라 행에 책 제목은 렌더하지 않는다.
 */
@Service
@Transactional(readOnly = true)
public class BookContributionService {

    private final ReadingSessionRepository sessionRepository;

    public BookContributionService(ReadingSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public BookReadingDetail detail(User user, Book book) {
        ZoneId zone = ZoneId.of(user.getTimezone());

        // 그 책의 완료 세션을 유저 타임존 일자로 묶어 그날 총 독서 시간을 낸다(최신 일자 먼저).
        Map<LocalDate, Long> byDate = new TreeMap<>(Comparator.reverseOrder());
        for (ReadingSession session : sessionRepository.findByUserAndBook(user, book)) {
            if (session.isActive()) {
                continue; // 진행 중(미종료) 세션은 제외
            }
            LocalDate date = LocalDate.ofInstant(session.getStartedAt(), zone);
            byDate.merge(date, session.getDurationSeconds(), Long::sum);
        }

        // 한 책 상세라 행에 책 제목은 안 보이므로 제목 목록은 비운다.
        List<DailyReadingRecord> dailyHistory = byDate.entrySet().stream()
                .map(e -> new DailyReadingRecord(e.getKey(), e.getValue(), List.of()))
                .toList();
        long total = byDate.values().stream().mapToLong(Long::longValue).sum();

        return new BookReadingDetail(MonthlyReadingSection.groupByMonth(dailyHistory), total);
    }
}
