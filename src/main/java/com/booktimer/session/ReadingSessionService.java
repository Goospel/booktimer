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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
 * <p><b>자정 분할</b>: 종료 시각이 확정되는 세 경로(stop · closeStaleSessions · recordManual)는
 * 저장 직전에 구간을 <b>유저 타임존 자정</b>으로 잘라 조각마다 한 행씩 저장한다
 * ({@link #splitByMidnight}). 날짜 귀속은 {@code startedAt}의 유저 TZ 날짜라
 * ({@link ReadingHistoryService}) 자정을 걸친 독서가 통째로 시작일에 잡히던 어긋남을 저장 시점에
 * 없애는 것이 목적이다 — 기록·부채·잔디·오늘 읽은 시간이 전부 세션 행에서 유도되므로 소비처는
 * 한 줄도 바뀌지 않는다. 규칙 셋: ① 상한 클램프가 <b>분할보다 먼저</b>다(상한은 「한 번의 물리적
 * 독서」에 대한 정책이라 원본 구간에 건다) ② 경계가 끝점과 일치하면 자르지 않는다(0초 조각 금지)
 * ③ 진행 중 세션은 절대 분할하지 않는다. 분할 기준 타임존은 <b>저장 시점의 스냅샷</b>이고 과거
 * 저장분의 소급 재분할·마이그레이션은 없다(레거시 행은 여전히 자정을 걸칠 수 있다).
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
        // 자정을 걸친 수동 기록은 조각마다 한 행 — 모든 조각이 manualEntry=true·같은 책이다.
        ReadingSession last = null;
        for (Segment segment : splitByMidnight(startedAt, endedAt, ZoneId.of(user.getTimezone()))) {
            last = sessionRepository.save(
                    ReadingSession.manual(user, segment.start(), segment.end(), book));
        }
        // 기록한 책이 "읽고싶음"이었다면 "읽는중"으로 자동 전환(전환 시에만 저장) — start와 동일.
        // 시작 시각 스탬프는 "적은 시각"인 startedAt으로 — 뒤늦게 적어도 실제 읽기 시작 시점이 남는다
        // (분할해도 최초 조각의 시작 = 원본 startedAt이라 의미가 그대로다).
        if (book.startReading(startedAt)) {
            bookRepository.save(book);
        }
        return last;
    }

    /** 자정 분할 조각 — 시각 쌍 하나가 저장될 한 행이 된다. */
    record Segment(Instant start, Instant end) {
    }

    /**
     * {@code [startedAt, endedAt]}을 {@code zone}의 자정 경계로 자른다. 항상 1개 이상을 돌려주고,
     * 조각들은 빈틈·겹침 없이 인접한다(앞 {@code end} == 뒤 {@code start} — 이 등치가 곧 조각 링크다,
     * {@link #tagBook}).
     *
     * <p><b>공부 세션도 이 함수를 쓴다</b>({@link StudySessionService}) — 순수 함수라 독서 의존이 0이고,
     * 복제하면 아래 0초 조각·DST 규칙을 두 번 밟게 된다. 여기 손댈 땐 두 원장을 함께 본다.
     *
     * <p>경계가 끝점과 일치하면 자르지 않는다(<b>0초 조각 금지</b>) — 정확히 자정에 끝난 독서는 1행이다.
     * 조각 수를 2개로 특수화하지 않는 이유: 수동 입력 24시간이 DST 짧은 날(23시간)을 끼면 자정을 2회
     * 넘어 3조각이 실제로 나온다. 경계 계산에 {@code atStartOfDay(zone)}를 쓰는 것도 같은 이유다 —
     * 자정이 DST로 <b>존재하지 않는</b> 날(예: America/Santiago)에도 그날의 첫 유효 시각을 돌려준다.
     */
    static List<Segment> splitByMidnight(Instant startedAt, Instant endedAt, ZoneId zone) {
        List<Segment> segments = new ArrayList<>();
        Instant cursor = startedAt;
        while (true) {
            // plusDays(1)이라 경계는 항상 cursor보다 미래 — 자정에 시작해도 무한루프가 없다.
            Instant nextMidnight = LocalDate.ofInstant(cursor, zone).plusDays(1).atStartOfDay(zone).toInstant();
            if (!nextMidnight.isBefore(endedAt)) {
                segments.add(new Segment(cursor, endedAt));
                return segments;
            }
            segments.add(new Segment(cursor, nextMidnight));
            cursor = nextMidnight;
        }
    }

    /**
     * 진행 중 세션을 {@code endedAt}으로 닫되 자정 경계로 잘라 저장한다 — 기존 행이 첫 조각이 되고
     * ({@code startedAt} 불변) 나머지 조각은 새 완료 행으로 저장된다. 모든 조각이 같은 책·같은
     * {@code manualEntry}(=false)를 갖는다.
     *
     * @return 마지막 조각({@code endedAt}이 속한 쪽) — 호출부가 응답의 세션 id로 쓴다.
     */
    private ReadingSession endSplitAndSave(ReadingSession open, Instant endedAt) {
        List<Segment> segments = splitByMidnight(
                open.getStartedAt(), endedAt, ZoneId.of(open.getUser().getTimezone()));
        open.end(segments.get(0).end()); // 이미 종료된 세션이면 여기서 IllegalStateException(경합 가드 유지)
        ReadingSession last = sessionRepository.save(open);
        for (int i = 1; i < segments.size(); i++) {
            ReadingSession piece = ReadingSession.start(open.getUser(), segments.get(i).start(), open.getBook());
            piece.end(segments.get(i).end());
            last = sessionRepository.save(piece);
        }
        return last;
    }

    /**
     * 진행 중 세션을 종료하고 저장한다. 부채 차감은 없다 — 종료된 세션이 그날 부채에 자동 반영된다.
     *
     * <p><b>상한 클램프</b>: 경과가 {@link #MAX_SESSION_DURATION}을 넘으면 {@code startedAt + cap}으로
     * 잘라 인정한다 — 끝내기를 깜빡한 21시간짜리 세션이 통계·잔디를 왜곡한 실측 사례 때문(2026-08-13).
     * 클램프는 <b>서비스 정책</b>이라 엔티티 불변식({@code durationSeconds = endedAt - startedAt})은 그대로다.
     *
     * <p><b>클램프가 분할보다 먼저다</b> — 상한을 원본 구간에 걸고 그 결과를 자정으로 자른다.
     * 순서를 뒤집으면 조각마다 6시간이 허용돼 하루를 넘겨 읽은 세션이 cap을 초과한다.
     *
     * @return 자정을 넘겼으면 <b>마지막 조각</b>(now가 속한 쪽), 아니면 그 세션 자신
     * @throws IllegalStateException 진행 중 세션이 없는 경우
     */
    public ReadingSession stop(User user, Instant now) {
        ReadingSession active = sessionRepository.findByUserAndEndedAtIsNull(user)
                .orElseThrow(() -> new IllegalStateException("no active session to stop"));
        return endSplitAndSave(active, clampToCap(active.getStartedAt(), now));
    }

    /**
     * <b>방치 세션 자동 종료</b> — cap을 넘겨 열려 있는 세션들을 정확히 cap 길이로 닫는다.
     * {@link StaleSessionSweeper}가 주기적으로 부른다(트랜잭션 경계는 여기).
     *
     * <p>경합 방어: 조회와 종료 사이에 사용자가 stop을 눌러 이미 닫힌 세션은 {@code end()}가
     * {@link IllegalStateException}을 던진다 — 한 건 실패가 나머지를 막지 않게 건별로 스킵한다.
     *
     * @return 실제로 닫은 <b>세션</b> 수(자정 분할로 행이 늘어도 원본 세션 단위로 센다)
     */
    public int closeStaleSessions(Instant now) {
        int closed = 0;
        for (ReadingSession session : sessionRepository.findByEndedAtIsNullAndStartedAtBefore(now.minus(MAX_SESSION_DURATION))) {
            try {
                endSplitAndSave(session, session.getStartedAt().plus(MAX_SESSION_DURATION));
            } catch (IllegalStateException alreadyEnded) {
                log.info("stale session {} already ended, skipping: {}", session.getId(), alreadyEnded.getMessage());
                continue;
            }
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
     * <p><b>자정 분할 조각까지 함께 태깅한다.</b> {@code stop}은 마지막 조각을 돌려주므로 그 하나만
     * 붙이면 자정 전 몫이 미태깅으로 남아 책 통계에서 샌다. 조각 링크 컬럼은 없고 <b>시각 인접성</b>이
     * 링크다 — 앞 조각의 {@code endedAt}은 뒤 조각의 {@code startedAt}과 같은 값이므로 뒤에서 앞으로
     * 체인을 걷는다. 미태깅·실시간 세션만 후보라(수동 기록은 책이 필수라 미태깅이 없다) 남의 독서나
     * 무관한 세션이 딸려올 길이 없다.
     *
     * @param sessionId 태깅할 세션 id
     * @param book      연결할 책(호출부에서 소유 검증 완료)
     * @return 책이 연결된 세션(넘겨받은 그 세션 — 앞 조각들도 함께 태깅되지만 반환은 이것)
     * @throws IllegalArgumentException 해당 사용자의 그 세션이 없는 경우(IDOR — 컨트롤러가 404로 마스킹)
     * @throws IllegalStateException    이미 책이 지정된 세션인 경우(컨트롤러가 409로)
     */
    public ReadingSession tagBook(User user, Long sessionId, Book book) {
        ReadingSession session = sessionRepository.findByIdAndUser(sessionId, user)
                .orElseThrow(() -> new IllegalArgumentException("session not found for user"));
        session.tagBook(book); // 이미 책 있으면 IllegalStateException
        ReadingSession saved = sessionRepository.save(session);
        // 인접한 앞 조각을 따라 올라가며 같은 책을 붙인다(분할이 없었으면 첫 조회가 바로 empty).
        ReadingSession earliest = session;
        for (Optional<ReadingSession> previous;
             (previous = sessionRepository.findByUserAndEndedAtAndBookIsNullAndManualEntryFalse(
                     user, earliest.getStartedAt())).isPresent(); ) {
            earliest = previous.get();
            earliest.tagBook(book);
            sessionRepository.save(earliest);
        }
        // 태깅한 책이 "읽고싶음"이었다면 "읽는중"으로 자동 전환(측정 시작과 동일).
        // 시작 시각은 태깅 시점(지금)이 아니라 실제로 읽기 시작한 때 — 분할됐으면 최초 조각의 시각이다.
        if (book.startReading(earliest.getStartedAt())) {
            bookRepository.save(book);
        }
        return saved;
    }

    /**
     * <b>진행 중</b> 세션의 측정 대상 교체 — 다른 탭에서 시작한 측정이 무슨 책인지 알고 그 자리에서
     * 바꾸는 경로(핸드오프 3f). 세션은 멈추지 않는다: 지금까지 잰 시간이 통째로 새 책에 붙는다.
     *
     * <p><b>세션 id를 받지 않는다.</b> {@code stop}과 같은 finder로 "내 진행 중 세션"을 서버가 찾으므로
     * 요청에 세션 좌표가 아예 없고, 그래서 세션 IDOR이 <b>구조적으로 성립하지 않는다</b>(그 대신 클라가
     * 든 id가 낡아 엉뚱한 세션을 건드릴 길도 없다). 책 소유 검증은 컨트롤러가 {@code findByIdAndUser}로
     * 마친 뒤 넘긴다 — {@link #tagBook}과 같은 분업이다.
     *
     * @param user 대상 사용자
     * @param book 새 대상(null = 책 없이)
     * @throws IllegalStateException 진행 중 세션이 없는 경우(컨트롤러가 409로 옮긴다 — stop과 같은 계약)
     */
    public ReadingSession changeActiveBook(User user, Book book) {
        ReadingSession active = sessionRepository.findByUserAndEndedAtIsNull(user)
                .orElseThrow(() -> new IllegalStateException("no active session"));
        active.changeBook(book);
        // 새 책이 "읽고싶음"이었다면 "읽는중"으로 자동 전환 — 시작 시각은 교체 시점(지금)이 아니라
        // 그 세션이 시작된 때다. tagBook과 같은 시맨틱이라 두 경로가 책 상태를 다르게 만들지 않는다.
        if (book != null && book.startReading(active.getStartedAt())) {
            bookRepository.save(book);
        }
        return sessionRepository.save(active);
    }
}
