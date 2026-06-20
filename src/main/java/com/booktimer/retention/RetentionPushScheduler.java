package com.booktimer.retention;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 비활동 복귀 nudge 푸시 배치 트리거 (PWA L3b).
 *
 * <p><b>매일 KST 19시</b>에 1회 실행 — 야간(21~08시, 정보통신망법 §50) 회피 + L3a 20시와 분리.
 * 멱등은 {@link RetentionPushService}의 INACTIVITY_WINDOW + marketingPushNudgeSentAt이 보장.
 *
 * <p><b>점등 게이트</b>: {@code booktimer.nudge-push.enabled=true}일 때만 빈으로 등록된다(기본 OFF).
 * 마케팅 §50 법무 충족 후 {@code BOOKTIMER_NUDGE_PUSH_ENABLED=true} 환경변수로 별도 점등.
 * 이메일 nudge({@code booktimer.nudge.enabled}) · L3a push({@code booktimer.push.enabled})와 독립.
 */
@Component
@ConditionalOnProperty(name = "booktimer.nudge-push.enabled", havingValue = "true")
public class RetentionPushScheduler {

    private final RetentionPushService retentionPushService;

    public RetentionPushScheduler(RetentionPushService retentionPushService) {
        this.retentionPushService = retentionPushService;
    }

    /** 매일 KST 19:00 — 초 분 시 일 월 요일. */
    @Scheduled(cron = "0 0 19 * * *", zone = "Asia/Seoul")
    public void run() {
        retentionPushService.sendNudges();
    }
}
