package com.booktimer.session;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.timer.ReadingTimer;
import com.booktimer.timer.ReadingTimerRepository;
import com.booktimer.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 독서 세션 start/stop 유스케이스 오케스트레이션.
 *
 * <p>도메인 규칙은 엔티티({@link ReadingSession}, {@link ReadingTimer})에 있고, 이 서비스는
 * 레포지토리 조회·저장과 두 엔티티의 협력을 트랜잭션 경계 안에서 엮는다:
 * <ul>
 *   <li>start — 진행 중 세션이 있으면 거부, 없으면 새 세션 저장.</li>
 *   <li>stop — 진행 중 세션을 종료하고 측정량을 유저 타이머에서 차감, 둘 다 저장.</li>
 * </ul>
 */
@Service
@Transactional
public class ReadingSessionService {

    private final ReadingSessionRepository sessionRepository;
    private final ReadingTimerRepository timerRepository;
    private final BookRepository bookRepository;

    public ReadingSessionService(ReadingSessionRepository sessionRepository,
                                 ReadingTimerRepository timerRepository,
                                 BookRepository bookRepository) {
        this.sessionRepository = sessionRepository;
        this.timerRepository = timerRepository;
        this.bookRepository = bookRepository;
    }

    /**
     * 특정 책을 대상으로 새 측정 세션을 시작한다.
     *
     * <p><b>책은 필수</b>다 — "어떤 책을 얼마나 읽었는지"를 명확히 하려고 책 없는(미지정) 측정은 허용하지 않는다.
     * (과거 데이터엔 책 없는 세션이 남아 있을 수 있어 엔티티/스키마는 nullable을 유지하지만, 이 생성 경로는 막는다.)
     *
     * @throws IllegalArgumentException book 이 null 인 경우(책 미지정 측정 금지)
     * @throws IllegalStateException    이미 진행 중인 세션이 있는 경우
     */
    public ReadingSession start(User user, Instant now, Book book) {
        if (book == null) {
            throw new IllegalArgumentException("a book is required to start a reading session");
        }
        sessionRepository.findByUserAndEndedAtIsNull(user).ifPresent(s -> {
            throw new IllegalStateException("an active session already exists");
        });
        ReadingSession saved = sessionRepository.save(ReadingSession.start(user, now, book));
        // 시작한 책이 "읽고싶음"이었다면 "읽는중"으로 자동 전환(전환 시에만 저장).
        if (book.startReading()) {
            bookRepository.save(book);
        }
        return saved;
    }

    /**
     * 진행 중 세션을 종료하고 측정량을 유저의 누적 잔여에서 차감한다.
     *
     * @throws IllegalStateException 진행 중 세션이 없거나 유저 타이머가 없는 경우
     */
    public ReadingSession stop(User user, Instant now) {
        ReadingSession active = sessionRepository.findByUserAndEndedAtIsNull(user)
                .orElseThrow(() -> new IllegalStateException("no active session to stop"));
        ReadingTimer timer = timerRepository.findByUser(user)
                .orElseThrow(() -> new IllegalStateException("no timer for user"));

        active.end(now);
        timer.deduct(active.getDurationSeconds());

        sessionRepository.save(active);
        timerRepository.save(timer);
        return active;
    }
}
