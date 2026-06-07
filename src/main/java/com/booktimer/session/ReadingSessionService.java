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
     * 측정 시작을 깜빡한 독서를 <b>나중에 수동으로 기록</b>한다 — 이미 끝난 한 번의 측정을 직접 적는 경로.
     *
     * <p>실시간 측정({@link #start}/{@link #stop})과 결과가 같아야 사용자가 "어차피 기록 안 됐네" 하고
     * 이탈하지 않는다(retention). 그래서 {@code start}의 <b>책 필수</b> 규칙과 {@code stop}의 <b>누적 차감</b>을
     * 합쳐, 시작~종료가 이미 정해진 완료 세션을 만들고 그만큼 유저 타이머에서 갚는다. 진행 중 세션 유무와
     * 무관하다(과거 시점을 적는 것이라 충돌하지 않음).
     *
     * <p><b>차감은 "지금"이 아니라 누적 잔여 단일 값에 적용된다</b> — 잔여는 날짜별 원장이 아니라 하나의 누적
     * 부채(N-001)이므로, 과거 날짜를 기록해도 현재 잔여를 갚는다(실시간 측정과 동일한 모델). 잔여보다 크게
     * 읽었어도 0 밑으로는 안 내려간다({@link ReadingTimer#deduct}).
     *
     * @param user      측정 주체(필수)
     * @param startedAt 읽기 시작 시각(필수)
     * @param endedAt   읽기 종료 시각(필수, startedAt 이상)
     * @param book      읽은 책(필수 — 책 미지정 기록 금지, {@code start}와 동일)
     * @return 저장된 완료 세션
     * @throws IllegalArgumentException book 이 null 이거나 endedAt 이 startedAt 보다 이른 경우
     * @throws IllegalStateException    유저 타이머가 없는 경우
     */
    public ReadingSession recordManual(User user, Instant startedAt, Instant endedAt, Book book) {
        if (book == null) {
            throw new IllegalArgumentException("a book is required to record a reading session");
        }
        ReadingTimer timer = timerRepository.findByUser(user)
                .orElseThrow(() -> new IllegalStateException("no timer for user"));

        ReadingSession session = ReadingSession.start(user, startedAt, book);
        session.end(endedAt); // endedAt >= startedAt 검증 + durationSeconds 계산
        timer.deduct(session.getDurationSeconds());

        sessionRepository.save(session);
        timerRepository.save(timer);
        // 기록한 책이 "읽고싶음"이었다면 "읽는중"으로 자동 전환(전환 시에만 저장) — start와 동일.
        if (book.startReading()) {
            bookRepository.save(book);
        }
        return session;
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
