package com.booktimer.session;

import com.booktimer.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 공부 세션 start/stop 유스케이스 오케스트레이션 — {@link ReadingSessionService}의 공부판이다.
 *
 * <p>상한(6시간)·방치 스윕·클램프 정책은 독서에서 <b>값을 그대로 재사용</b>한다
 * ({@link ReadingSessionService#MAX_SESSION_DURATION}) — 두 모드가 다른 상한을 갖는다면 그건 결정이 아니라
 * 표류다.
 *
 * <p>독서에 없는 규칙 하나: <b>진행 중 독서 세션이 있으면 공부 시작을 거부</b>한다. 두 원장이 같은 시간을
 * 이중으로 세지 않게 하는 자리다. ⚠️ <b>역방향은 넣지 않는다</b> — {@code ReadingSessionService.start}에
 * 공부 검사를 달면 웹의 독서 시작이 화면에 보이지도 않는 공부 세션에 막혀 웹 회귀가 된다. 미니앱은 클라
 * 토글 잠금이 먼저 막고, 남는 극단 조합(웹에서 독서 시작)은 원장이 분리라 데이터 오염이 없어 수용한다.
 */
@Service
@Transactional
public class StudySessionService {

    private static final Logger log = LoggerFactory.getLogger(StudySessionService.class);

    private final StudySessionRepository studyRepository;
    private final ReadingSessionRepository readingRepository;

    public StudySessionService(StudySessionRepository studyRepository,
                               ReadingSessionRepository readingRepository) {
        this.studyRepository = studyRepository;
        this.readingRepository = readingRepository;
    }

    /**
     * 새 공부 세션을 시작한다.
     *
     * @throws IllegalStateException 진행 중인 공부 <b>또는 독서</b> 세션이 있는 경우
     */
    public StudySession start(User user, Instant now) {
        studyRepository.findByUserAndEndedAtIsNull(user).ifPresent(s -> {
            throw new IllegalStateException("an active study session already exists");
        });
        readingRepository.findByUserAndEndedAtIsNull(user).ifPresent(s -> {
            throw new IllegalStateException("an active reading session already exists");
        });
        return studyRepository.save(StudySession.start(user, now));
    }

    /**
     * 진행 중 공부 세션을 종료하고 저장한다. 경과가 상한을 넘으면 {@code startedAt + cap}으로 잘라 인정한다
     * (독서와 같은 정책 — 끝내기를 깜빡한 세션이 통계를 왜곡한 실측 때문).
     *
     * @throws IllegalStateException 진행 중 세션이 없는 경우
     */
    public StudySession stop(User user, Instant now) {
        StudySession active = studyRepository.findByUserAndEndedAtIsNull(user)
                .orElseThrow(() -> new IllegalStateException("no active study session to stop"));
        active.end(clampToCap(active.getStartedAt(), now));
        return studyRepository.save(active);
    }

    /**
     * <b>방치 세션 자동 종료</b> — cap을 넘겨 열려 있는 공부 세션들을 정확히 cap 길이로 닫는다
     * ({@link StaleSessionSweeper}가 독서와 같은 주기로 부른다).
     *
     * @return 실제로 닫은 세션 수
     */
    public int closeStaleSessions(Instant now) {
        int closed = 0;
        for (StudySession session : studyRepository.findByEndedAtIsNullAndStartedAtBefore(
                now.minus(ReadingSessionService.MAX_SESSION_DURATION))) {
            try {
                session.end(session.getStartedAt().plus(ReadingSessionService.MAX_SESSION_DURATION));
            } catch (IllegalStateException alreadyEnded) {
                log.info("stale study session {} already ended, skipping: {}", session.getId(), alreadyEnded.getMessage());
                continue;
            }
            studyRepository.save(session);
            closed++;
        }
        return closed;
    }

    /**
     * 오늘 공부한 시간(초) — <b>유저 타임존</b>의 하루 경계로 완료 세션을 합친다
     * ({@code ReadingDebtService.today(user)}와 같은 당일 판정).
     *
     * <p>진행 중 세션 몫은 여기 없다 — 클라이언트가 elapsed를 더해 매초 올린다(독서 히어로와 같은 분업).
     */
    @Transactional(readOnly = true)
    public long todaySeconds(User user, Instant now) {
        ZoneId zone = ZoneId.of(user.getTimezone());
        LocalDate today = LocalDate.ofInstant(now, zone);
        return studyRepository.sumCompletedSeconds(
                user,
                today.atStartOfDay(zone).toInstant(),
                today.plusDays(1).atStartOfDay(zone).toInstant());
    }

    /** 진행 중 공부 세션(없으면 null) — 화면 상태(hasActiveSession·activeStartedAt)의 출처. */
    @Transactional(readOnly = true)
    public StudySession activeSession(User user) {
        return studyRepository.findByUserAndEndedAtIsNull(user).orElse(null);
    }

    /** 경과가 cap을 초과하면 {@code startedAt + cap}, 아니면 {@code now} 그대로. */
    private static Instant clampToCap(Instant startedAt, Instant now) {
        Instant cap = startedAt.plus(ReadingSessionService.MAX_SESSION_DURATION);
        return now.isAfter(cap) ? cap : now;
    }
}
