package com.booktimer.retention;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 재참여 넛지 배치 트리거(이메일 인프라 2단계 PR-2). 얇은 스케줄 어댑터 — 실제 로직은 {@link RetentionNudgeService}.
 *
 * <p><b>매일 KST 오전 10시</b>에 1회 실행한다. 단일 KST 발송으로 정보통신망법의 야간 발송 제한(21:00~익일 08:00)을
 * 자연히 회피한다(현 타깃 한국 가정 — 다국적 확장 시 타임존별 분할은 추후). 멱등은 서비스의 {@code lastNudgeSentAt}이
 * 보장하므로 같은 날 두 번 돌아도 중복 발송되지 않는다.
 */
@Component
public class RetentionNudgeScheduler {

    private final RetentionNudgeService nudgeService;

    public RetentionNudgeScheduler(RetentionNudgeService nudgeService) {
        this.nudgeService = nudgeService;
    }

    /** 매일 KST 10:00 — 초 분 시 일 월 요일. */
    @Scheduled(cron = "0 0 10 * * *", zone = "Asia/Seoul")
    public void runDailyNudge() {
        nudgeService.sendNudges();
    }
}
