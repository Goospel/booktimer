package com.booktimer.session;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
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

    private static final Logger log = LoggerFactory.getLogger(ReadingSessionService.class);

    /**
     * 한 세션에서 인정하는 최대 시간(6시간). 실시간 측정의 상한이자, 방치 세션을 닫는 기준이다
     * ({@link StaleSessionSweeper}). 수동 입력 경로의 24시간 cap과 별개 — 그쪽은 사용자의 명시적 주장이다.
     */
    public static final Duration MAX_SESSION_DURATION = Duration.ofHours(6);

    private final ReadingSessionRepository sessionRepository;
    private final BookRepository bookRepository;

    public ReadingSessionService(ReadingSessionRepository sessionRepository,
                                 BookRepository bookRepository) {
        this.sessionRepository = sessionRepository;
        this.bookRepository = bookRepository;
    }

    /**
     * 새 측정 세션을 시작한다. <b>책은 선택</b>이다(발견 1) — 무엇을 읽을지 아직 안 정했어도
     * 시작을 가로막지 않고, 나중에 "종료 후 태깅"({@code tagBook})으로 책을 연결할 수 있다.
     * 책을 주면 그 책에 세션이 연결되고, 그 책이 "읽고싶음"이면 "읽는중"으로 자동 전환한다.
     *
     * <p>(책 없는 세션은 잔디·연속일·부채엔 시간 기반으로 정상 반영되고, 책별 통계에선 자연히 빠진다 —
     * 집계 쿼리가 이미 그렇게 갈린다. {@link ReadingSessionRepository})
     *
     * @param book 측정 대상 책(선택 — null이면 책 미지정 세션)
     * @throws IllegalStateException 이미 진행 중인 세션이 있는 경우
     */
    public ReadingSession start(User user, Instant now, Book book) {
        sessionRepository.findByUserAndEndedAtIsNull(user).ifPresent(s -> {
            throw new IllegalStateException("an active session already exists");
        });
        ReadingSession saved = sessionRepository.save(ReadingSession.start(user, now, book));
        // 책을 지정했고 그 책이 "읽고싶음"이었다면 "읽는중"으로 자동 전환(전환 시에만 저장).
        if (book != null && book.startReading(now)) {
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
     * ({@link ReadingDebtService}) 그 날짜에 세션이 한 건 생기면 그 날 부채가 그만큼 준다. 부채 창
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
        // 시작 시각 스탬프는 "적은 시각"인 startedAt으로 — 뒤늦게 적어도 실제 읽기 시작 시점이 남는다.
        if (book.startReading(startedAt)) {
            bookRepository.save(book);
        }
        return session;
    }

    /**
     * 진행 중 세션을 종료하고 저장한다. 부채 차감은 없다 — 종료된 세션이 그날 부채에 자동 반영된다.
     *
     * <p><b>상한 클램프</b>: 경과가 {@link #MAX_SESSION_DURATION}을 넘으면 {@code startedAt + cap}으로
     * 잘라 인정한다 — 끝내기를 깜빡한 21시간짜리 세션이 통계·잔디를 왜곡한 실측 사례 때문(2026-08-13).
     * 클램프는 <b>서비스 정책</b>이라 엔티티 불변식({@code durationSeconds = endedAt - startedAt})은 그대로다.
     *
     * @throws IllegalStateException 진행 중 세션이 없는 경우
     */
    public ReadingSession stop(User user, Instant now) {
        ReadingSession active = sessionRepository.findByUserAndEndedAtIsNull(user)
                .orElseThrow(() -> new IllegalStateException("no active session to stop"));
        active.end(clampToCap(active.getStartedAt(), now));
        return sessionRepository.save(active);
    }

    /**
     * <b>방치 세션 자동 종료</b> — cap을 넘겨 열려 있는 세션들을 정확히 cap 길이로 닫는다.
     * {@link StaleSessionSweeper}가 주기적으로 부른다(트랜잭션 경계는 여기).
     *
     * <p>경합 방어: 조회와 종료 사이에 사용자가 stop을 눌러 이미 닫힌 세션은 {@code end()}가
     * {@link IllegalStateException}을 던진다 — 한 건 실패가 나머지를 막지 않게 건별로 스킵한다.
     *
     * @return 실제로 닫은 세션 수
     */
    public int closeStaleSessions(Instant now) {
        int closed = 0;
        for (ReadingSession session : sessionRepository.findByEndedAtIsNullAndStartedAtBefore(now.minus(MAX_SESSION_DURATION))) {
            try {
                session.end(session.getStartedAt().plus(MAX_SESSION_DURATION));
            } catch (IllegalStateException alreadyEnded) {
                log.info("stale session {} already ended, skipping: {}", session.getId(), alreadyEnded.getMessage());
                continue;
            }
            sessionRepository.save(session);
            closed++;
        }
        return closed;
    }

    /** 경과가 cap을 초과하면 {@code startedAt + cap}, 아니면 {@code now} 그대로. */
    private static Instant clampToCap(Instant startedAt, Instant now) {
        Instant cap = startedAt.plus(MAX_SESSION_DURATION);
        return now.isAfter(cap) ? cap : now;
    }

    /**
     * <b>종료 후 태깅</b>(발견 1) — 책 없이 측정한 세션에 나중에 책을 연결한다. 책 없이 시작한 측정을
     * 종료한 뒤 "무슨 책이었나요?"로 되돌아보며 붙이는 경로다.
     *
     * <p>소유 경계(IDOR): 그 세션이 {@code user}의 것이어야 한다 — 아니면 없는 것으로 취급(404 마스킹).
     * 책 소유 검증은 호출부(컨트롤러)가 {@code findByIdAndUser}로 이미 마친 뒤 넘긴다. 태깅한 책이
     * "읽고싶음"이면 {@code start}와 동일하게 "읽는중"으로 자동 전환한다.
     *
     * @param sessionId 태깅할 세션 id
     * @param book      연결할 책(호출부에서 소유 검증 완료)
     * @return 책이 연결된 세션
     * @throws IllegalArgumentException 해당 사용자의 그 세션이 없는 경우(IDOR — 컨트롤러가 404로 마스킹)
     * @throws IllegalStateException    이미 책이 지정된 세션인 경우(컨트롤러가 409로)
     */
    public ReadingSession tagBook(User user, Long sessionId, Book book) {
        ReadingSession session = sessionRepository.findByIdAndUser(sessionId, user)
                .orElseThrow(() -> new IllegalArgumentException("session not found for user"));
        session.tagBook(book); // 이미 책 있으면 IllegalStateException
        // 태깅한 책이 "읽고싶음"이었다면 "읽는중"으로 자동 전환(측정 시작과 동일).
        // 시작 시각은 그 세션이 시작된 시각 — 태깅 시점(지금)이 아니라 실제로 읽기 시작한 때다.
        if (book.startReading(session.getStartedAt())) {
            bookRepository.save(book);
        }
        return sessionRepository.save(session);
    }
}
