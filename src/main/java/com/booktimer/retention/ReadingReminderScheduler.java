package com.booktimer.retention;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 독서 알림 매시 배치 트리거. 얇은 스케줄 어댑터 — 실제 로직은 {@link ReadingReminderService}.
 *
 * <p><b>점등 게이트</b>: {@code booktimer.toss.messenger.reading-reminder-enabled=true}일 때만 빈으로 등록된다(기본 OFF).
 * 다른 토스 캠페인과 별개 토글이다 — 콘솔 검수·판정이 캠페인별이라서다.
 */
@Component
@ConditionalOnProperty(name = "booktimer.toss.messenger.reading-reminder-enabled", havingValue = "true")
public class ReadingReminderScheduler {

    private final ReadingReminderService service;

    public ReadingReminderScheduler(ReadingReminderService service) {
        this.service = service;
    }

    /**
     * 매시 정각 — 각자 고른 현지 시각은 서비스가 거른다. 초 분 시 일 월 요일.
     * ponytail: 정시 오프셋 zone만 현지 정각에 간다 — 반시간 오프셋 zone(인도 등)은 고른 시의 :30에 간다.
     * 토스 가입자는 Asia/Seoul 고정이라 지금은 무관, 분 단위 시각이 필요해지면 그때 배치 주기를 바꾼다.
     */
    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    public void runHourly() {
        service.sendDue();
    }
}
