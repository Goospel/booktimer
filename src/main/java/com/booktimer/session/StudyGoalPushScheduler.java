package com.booktimer.session;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 공부 회당 시간 도달 감지 배치 트리거 — 얇은 어댑터, 로직은 {@link StudyGoalPushService}.
 *
 * <p><b>점등 게이트</b>: {@code booktimer.toss.messenger.study-goal-enabled=true}일 때만 빈이 된다(기본 OFF).
 * 독서 목표 달성({@link GoalMetPushScheduler})과 별개 토글이고, 실발송엔 {@code messenger.enabled}도 true여야 한다.
 */
@Component
@ConditionalOnProperty(name = "booktimer.toss.messenger.study-goal-enabled", havingValue = "true")
public class StudyGoalPushScheduler {

    private final StudyGoalPushService pushService;

    public StudyGoalPushScheduler(StudyGoalPushService pushService) {
        this.pushService = pushService;
    }

    @Scheduled(fixedDelay = 60_000)
    public void runStudyGoalDetection() {
        pushService.detectAndPush();
    }
}
