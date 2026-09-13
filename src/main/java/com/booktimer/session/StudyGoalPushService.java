package com.booktimer.session;

import com.booktimer.config.TossProperties;
import com.booktimer.toss.TossMessengerClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

    /**
     * 닿은 세션에 발송하고 마킹한다(스케줄러가 분마다 호출).
     *
     * <p><b>배치 트랜잭션이 없다</b>: 후보 조회는 트랜잭션 밖(fetch join된 book·user의 단순 필드만 읽는다),
     * 발송(HTTP)도 밖, 마킹만 세션마다 자기 트랜잭션에서 즉시 커밋한다. 한 트랜잭션으로 묶으면 ① 마킹 UPDATE의
     * 행 락이 뒤 세션들의 발송 동안 잡혀 그 사용자의 stop·책 교체가 기다리고 ② 마킹 하나의 DB 예외가 롤백 전용
     * 표시를 남겨 이미 발송한 세션들의 마킹까지 롤백돼 다음 틱에 재발송된다.
     *
     * <p><b>알려진 한계</b>: {@code changeActiveBook}은 엔티티 save(전 컬럼 UPDATE)라, 그 요청이 마킹 커밋 <b>직전에</b>
     * 행을 읽고 직후에 커밋하면 {@code goal_notified_at}이 null로 돌아가 다음 틱(GRACE 안)에 한 통 더 갈 수 있다.
     * 창은 그 요청의 수 ms다. {@code @DynamicUpdate}는 파급이 넓어 도입하지 않았다(stop은 세션이 끝나 무해).
     */
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
