package com.booktimer.session;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * "오늘 목표 달성" 감지 배치 트리거. 얇은 스케줄 어댑터 — 실제 로직은 {@link GoalMetPushService}.
 *
 * <p><b>분마다</b>(fixedDelay 60초) 돈다. 달성 순간은 요청이 없는 시점이라 훅을 걸 곳이 없고 토스 API엔
 * 예약 발송이 없어, 폴링이 유일한 감지 수단이다 — 지연 상한 ≈ 1분(사용자가 합의한 "분 단위 오차").
 * 이전 실행이 끝난 뒤 60초를 세는 {@code fixedDelay}라 배치가 느려져도 겹쳐 돌지 않는다.
 *
 * <p><b>점등 게이트</b>: {@code booktimer.toss.messenger.goal-met-enabled=true}일 때만 빈으로 등록된다(기본 OFF).
 * 완독 축하({@code booktimer.toss.messenger.enabled})와 토글이 분리돼 있어 따로 점등·소등할 수 있다 —
 * 다만 실발송에는 둘 다 true여야 한다(발송 클라이언트가 후자에 걸려 있다).
 */
@Component
@ConditionalOnProperty(name = "booktimer.toss.messenger.goal-met-enabled", havingValue = "true")
public class GoalMetPushScheduler {

    private final GoalMetPushService pushService;

    public GoalMetPushScheduler(GoalMetPushService pushService) {
        this.pushService = pushService;
    }

    @Scheduled(fixedDelay = 60_000)
    public void runGoalMetDetection() {
        pushService.detectAndPush();
    }
}
