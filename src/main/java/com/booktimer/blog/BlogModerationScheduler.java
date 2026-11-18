package com.booktimer.blog;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * 매일 09:00 KST — 열린 블로그 신고 · 기간(30일) 끝난 임시조치 가운데 하나라도 있으면 운영자에게 요약 메일 1통(계획 §4.6 · 사용자 결정 2
 * — 서버가 저절로 지우지 않는다). 트랜잭션 밖에서 부르므로 {@link BlogNotifier#send}의 「곧바로」 갈래를 탄다.
 */
@Component
public class BlogModerationScheduler {

    private final BlogModerationService service;
    private final BlogNotifier notifier;
    private final Clock clock;

    public BlogModerationScheduler(BlogModerationService service, BlogNotifier notifier, Clock clock) {
        this.service = service;
        this.notifier = notifier;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    public void run() {
        long open = service.openCount();
        long expired = service.expiredCount(clock.instant());
        if (open > 0 || expired > 0) {
            notifier.daily(open, expired);
        }
    }
}
