package com.booktimer.push;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 독서 리마인더 배치 트리거 (PWA L3a).
 *
 * <p><b>매일 KST 20시</b>에 1회 실행 — 주간(20시)이라 야간 발송 제한(21~08시, 정보통신망법 §50)을 회피한다.
 * 멱등은 {@link PushReminderService}의 DEDUP_WINDOW(23h)가 보장.
 *
 * <p><b>점등 게이트</b>: {@code booktimer.push.enabled=true} 일 때만 빈으로 등록된다(기본 OFF).
 * transactional 성격(본인 opt-in)이지만 점등 분리는 VAPID 키 설정 완료 후 켜기 위함.
 */
@Component
@ConditionalOnProperty(name = "booktimer.push.enabled", havingValue = "true")
public class PushReminderScheduler {

    private final PushReminderService reminderService;

    public PushReminderScheduler(PushReminderService reminderService) {
        this.reminderService = reminderService;
    }

    /** 매일 KST 20:00 — 초 분 시 일 월 요일. */
    @Scheduled(cron = "0 0 20 * * *", zone = "Asia/Seoul")
    public void runDailyReminder() {
        reminderService.sendReminders();
    }
}
