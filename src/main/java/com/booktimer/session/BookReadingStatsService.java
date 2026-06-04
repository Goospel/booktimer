package com.booktimer.session;

import com.booktimer.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 책별 누적 독서 시간 집계(읽기 전용 read model).
 *
 * <p>완료된 측정 세션({@code endedAt != null}) 중 책이 지정된 것을 책별로 묶어 총 독서 시간(초)을 낸다.
 * 책 미지정/진행 중 세션은 제외한다. MVP 규모에서는 메모리에서 묶는다(데이터가 커지면 DB 집계로).
 */
@Service
@Transactional(readOnly = true)
public class BookReadingStatsService {

    private final ReadingSessionRepository sessionRepository;

    public BookReadingStatsService(ReadingSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    /**
     * 책 id → 누적 독서 시간(초). 책이 지정되고 완료된 세션만 합산한다.
     */
    public Map<Long, Long> totalSecondsByBook(User user) {
        return totalSecondsByBook(user, book -> true);
    }

    /**
     * 타인 프로필(SNS)용 공개 집계 — <b>PUBLIC 책만</b> 책별 누적 시간을 낸다(비공개 책 시간 누출 방지, sns-design §3.5).
     */
    public Map<Long, Long> publicTotalSecondsByBook(User user) {
        return totalSecondsByBook(user, com.booktimer.book.Book::isPublic);
    }

    private Map<Long, Long> totalSecondsByBook(User user, java.util.function.Predicate<com.booktimer.book.Book> include) {
        Map<Long, Long> totals = new HashMap<>();
        for (ReadingSession session : sessionRepository.findByUser(user)) {
            if (session.isActive() || session.getBook() == null || !include.test(session.getBook())) {
                continue; // 진행 중·책 미지정·필터 미통과 세션은 집계 제외
            }
            totals.merge(session.getBook().getId(), session.getDurationSeconds(), Long::sum);
        }
        return totals;
    }
}
