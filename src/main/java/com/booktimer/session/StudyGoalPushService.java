package com.booktimer.session;

import com.booktimer.config.TossProperties;
import com.booktimer.toss.TossMessengerClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * 공부 「회당 시간」 도달 푸시 — 측정이 그 책의 회당 시간에 닿으면 토스 앱으로 <b>세션당 한 통</b>.
 *
 * <p>{@link GoalMetPushService}와 같은 뼈대다(분당 폴링 · 다크런치 2중 게이트 · per-session 격리 · 성공만 마킹).
 * 템플릿은 변수가 없다(콘솔 문구: 제목 「공부 시간 달성」, 착지 홈) — 그래서 컨텍스트는 빈 맵이다.
 *
 * <p><b>닿음</b>은 클라이언트 「달성」 표시와 같은 부등식 {@code startedAt + goal ≤ now}. 닿은 지
 * {@link #REACH_GRACE}가 지난 세션은 보내지도 마킹하지도 않는다 — 서버 공백·다크런치 동안 닿은 세션이
 * 점등 순간 한꺼번에 쏟아지지 않게(늦은 알림은 엉뚱한 알림이다).
 *
 * <p><b>멱등</b>은 {@code study_session.goal_notified_at}이고, 쓰기는 컬럼 단독 UPDATE다
 * ({@link StudySessionRepository#markGoalNotified}) — 엔티티 save는 {@code stop}과 경합해 {@code endedAt}을 되살린다.
 */
@Service
public class StudyGoalPushService {

    private static final Logger log = LoggerFactory.getLogger(StudyGoalPushService.class);

    static final Duration REACH_GRACE = Duration.ofMinutes(10);

    private final StudySessionRepository repository;
    /** 게이트 OFF(다크런치)면 빈이 없다 — Optional 주입(완독 축하·목표 달성과 동일). */
    private final Optional<TossMessengerClient> client;
    private final TossProperties properties;
    private final Clock clock;

    public StudyGoalPushService(StudySessionRepository repository,
                                Optional<TossMessengerClient> client,
                                TossProperties properties,
                                Clock clock) {
        this.repository = repository;
        this.client = client;
        this.properties = properties;
        this.clock = clock;
    }

    /** ponytail: 배치 전체가 한 트랜잭션 — 활성 세션이 두 자리 규모라 충분하다. 커지면 per-session 트랜잭션으로. */
    @Transactional
    public void detectAndPush() {
        String code = properties.getMessenger().getStudyGoalTemplateCode();
        if (code == null || code.isBlank() || client.isEmpty()) {
            return; // 다크런치 — 마킹도 하지 않는다
        }
        Instant now = clock.instant();
        for (StudySession s : repository.findGoalPushCandidates()) {
            try {
                pushIfReached(s, now, code.strip());
            } catch (RuntimeException e) {
                log.warn("회당 시간 푸시 실패(나머지는 계속) — sessionId={}: {}", s.getId(), e.toString());
            }
        }
    }

    private void pushIfReached(StudySession s, Instant now, String code) {
        String userKey = s.getUser().getTossUserKey();
        if (userKey == null) {
            return; // 토스 미연결 — 대상 아님
        }
        Instant reachedAt = s.getStartedAt().plusSeconds(s.getBook().getSessionGoalSeconds());
        if (reachedAt.isAfter(now) || reachedAt.isBefore(now.minus(REACH_GRACE))) {
            return; // 아직 / 너무 늦음
        }
        if (client.get().sendMessage(userKey, code, Map.of())) {
            repository.markGoalNotified(s.getId(), now); // 성공만 마킹 — 실패는 GRACE 안에서 다음 틱 재시도
        }
    }
}
