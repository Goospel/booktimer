package com.booktimer.session;

import com.booktimer.book.Book;
import com.booktimer.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 책별 누적 독서 시간 집계(읽기 전용 read model).
 *
 * <p>DB GROUP BY 집계({@link ReadingSessionRepository#sumSecondsByBook} /
 * {@link ReadingSessionRepository#sumSecondsByPublicBook})에 위임해 세션 엔티티를 메모리로 로딩하지 않는다.
 * N+1(findByUser → 책별 lazy 로딩) 제거.
 */
@Service
@Transactional(readOnly = true)
public class BookReadingStatsService {

    private final ReadingSessionRepository sessionRepository;

    public BookReadingStatsService(ReadingSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    /** 책 id → 누적 독서 시간(초). 완료·책지정 세션만(PUBLIC·PRIVATE 모두). */
    public Map<Long, Long> totalSecondsByBook(User user) {
        return toMap(sessionRepository.sumSecondsByBook(user));
    }

    /** 타인 프로필(SNS)용 공개 집계 — PUBLIC 책만 책별 누적 시간을 낸다(비공개 시간 누출 방지, sns-design §3.5). */
    public Map<Long, Long> publicTotalSecondsByBook(User user) {
        return toMap(sessionRepository.sumSecondsByPublicBook(user));
    }

    /** 한 책의 누적 시간(초) — 뮤테이션 응답에 1권만 필요할 때(전체 재집계 회피). */
    public long secondsForBook(User user, Book book) {
        return sessionRepository.sumDurationByUserAndBook(user, book);
    }

    private static Map<Long, Long> toMap(List<BookSecondsRow> rows) {
        Map<Long, Long> m = new HashMap<>();
        for (BookSecondsRow r : rows) m.put(r.bookId(), r.seconds() == null ? 0L : r.seconds());
        return m;
    }
}
