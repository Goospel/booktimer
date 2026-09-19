package com.booktimer.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/** 매일 04:10 KST — 닫힌 지 30일 지난 대화방을 지운다({@link ChatRetentionService}). 스위치와 무관하게 돈다(방이 없으면 쿼리 1개). */
@Component
public class ChatHousekeepingScheduler {

    private static final Logger log = LoggerFactory.getLogger(ChatHousekeepingScheduler.class);

    private final ChatRetentionService retention;
    private final Clock clock;

    public ChatHousekeepingScheduler(ChatRetentionService retention, Clock clock) {
        this.retention = retention;
        this.clock = clock;
    }

    @Scheduled(cron = "0 10 4 * * *", zone = "Asia/Seoul")
    public void run() {
        int deleted = retention.purgeExpiredClosedRooms(clock.instant());
        if (deleted > 0) {
            log.info("대화방 보존 삭제: {}개", deleted);
        }
    }
}
