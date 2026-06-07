package com.booktimer.session;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 독서 세션 start/stop/수동기록 유스케이스 오케스트레이션.
 *
 * <p>도메인 규칙은 엔티티({@link ReadingSession})에 있고, 이 서비스는 레포지토리 조회·저장을
 * 트랜잭션 경계 안에서 엮는다:
 * <ul>
 *   <li>start — 진행 중 세션이 있으면 거부, 없으면 새 세션 저장.</li>
 *   <li>stop — 진행 중 세션을 종료하고 저장.</li>
 *   <li>recordManual — 측정 깜빡한 독서를 완료 세션 한 건으로 직접 기록.</li>
 * </ul>
 *
 * <p><b>부채 차감 로직이 없다.</b> 부채는 더 이상 저장된 단일 카운터가 아니라 완료 세션에서
 * 유도되므로(7일 윈도우 per-day, {@link ReadingDebtService}), <b>세션을 저장하는 것 자체가
 * 그날 부채를 줄인다.</b> 그래서 stop·recordManual 모두 타이머를 건드리지 않는다 —
 * 어느 날짜의 세션이든 그 날짜의 부채에 자연히 반영된다(과거/오늘 분기 불필요).
 */
@Service
@Transactional
public class ReadingSessionService {

    private final ReadingSessionRepository sessionRepository;
    private final BookRepository bookRepository;

    public ReadingSessionService(ReadingSessionRepository sessionRepository,
                                 BookRepository bookRepository) {
        this.sessionRepository = sessionRepository;
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
     * 이탈하지 않는다(retention). {@code start}의 <b>책 필수</b> 규칙을 따르며 시작~종료가 이미 정해진
     * 완료 세션을 만든다. 진행 중 세션 유무와 무관하다(과거 시점을 적는 것이라 충돌하지 않음).
     *
     * <p><b>부채는 세션 저장으로 자동 반영된다</b> — 부채는 날짜별로 완료 세션에서 유도되므로
     * ({@link ReadingDebtService}) 그 날짜에 세션이 한 건 생기면 그 날 부채가 그만큼 준다. 윈도우(최근 7일)
     * 안의 날짜만 기록하도록 막는 책임은 컨트롤러에 있다(여기선 날짜 정책을 모른다).
     *
     * @param user      측정 주체(필수)
     * @param startedAt 읽기 시작 시각(필수)
     * @param endedAt   읽기 종료 시각(필수, startedAt 이상)
     * @param book      읽은 책(필수 — 책 미지정 기록 금지, {@code start}와 동일)
     * @return 저장된 완료 세션
     * @throws IllegalArgumentException book 이 null 이거나 endedAt 이 startedAt 보다 이른 경우
     */
    public ReadingSession recordManual(User user, Instant startedAt, Instant endedAt, Book book) {
        if (book == null) {
            throw new IllegalArgumentException("a book is required to record a reading session");
        }
        ReadingSession session = ReadingSession.manual(user, startedAt, endedAt, book);
        sessionRepository.save(session);
        // 기록한 책이 "읽고싶음"이었다면 "읽는중"으로 자동 전환(전환 시에만 저장) — start와 동일.
        if (book.startReading()) {
            bookRepository.save(book);
        }
        return session;
    }

    /**
     * 진행 중 세션을 종료하고 저장한다. 부채 차감은 없다 — 종료된 세션이 그날 부채에 자동 반영된다.
     *
     * @throws IllegalStateException 진행 중 세션이 없는 경우
     */
    public ReadingSession stop(User user, Instant now) {
        ReadingSession active = sessionRepository.findByUserAndEndedAtIsNull(user)
                .orElseThrow(() -> new IllegalStateException("no active session to stop"));
        active.end(now);
        return sessionRepository.save(active);
    }
}
