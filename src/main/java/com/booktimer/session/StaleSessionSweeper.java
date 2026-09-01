package com.booktimer.session;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 방치된 독서 세션 자동 종료 배치 트리거. 얇은 스케줄 어댑터 —
 * 실제 로직·트랜잭션 경계는 {@link ReadingSessionService#closeStaleSessions(Instant)}.
 *
 * <p><b>왜</b>: 사용자가 「측정 끝내기」를 잊으면 {@code ended_at IS NULL} 세션이 무기한 남는다
 * (운영에서 21시간째 열린 세션 실측, 2026-08-13). 열린 세션은 통계·잔디를 왜곡하고
 * 새 측정 시작도 막는다("이미 진행 중" 거부).
 *
 * <p><b>정책</b>: cap({@link ReadingSessionService#MAX_SESSION_DURATION}, 6시간)을 넘긴 세션을
 * 정확히 cap 길이로 닫는다 — 방치분을 버리는 게 아니라 인정 상한까지는 인정한다.
 * 10분 주기라 방치 세션의 실제 수명 상한은 6시간 + 최대 10분.
 *
 * <p>점등 게이트 없이 무조건 등록된다 — 알림 발송 같은 외부 부수효과가 아니라 내부 데이터 위생이라
 * 환경별로 켜고 끌 이유가 없다({@code GoalMetPushScheduler}와 다른 점).
 */
@Component
public class StaleSessionSweeper {

    private final ReadingSessionService sessionService;
    private final StudySessionService studySessionService;

    public StaleSessionSweeper(ReadingSessionService sessionService,
                               StudySessionService studySessionService) {
        this.sessionService = sessionService;
        this.studySessionService = studySessionService;
    }

    @Scheduled(fixedDelay = 600_000)
    public void closeStaleSessions() {
        Instant now = Instant.now();
        sessionService.closeStaleSessions(now);
        // 공부 세션도 같은 정책·같은 주기로 닫는다 — 원장이 갈렸다고 위생까지 갈리면 방치 세션이 한쪽에만 쌓인다.
        studySessionService.closeStaleSessions(now);
    }
}
