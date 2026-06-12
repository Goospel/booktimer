package com.booktimer.retention;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 재참여 넛지 배치 트리거(이메일 인프라 2단계 PR-2). 얇은 스케줄 어댑터 — 실제 로직은 {@link RetentionNudgeService}.
 *
 * <p><b>매일 KST 오전 10시</b>에 1회 실행한다. 단일 KST 발송으로 정보통신망법의 야간 발송 제한(21:00~익일 08:00)을
 * 자연히 회피한다(현 타깃 한국 가정 — 다국적 확장 시 타임존별 분할은 추후). 멱등은 서비스의 {@code lastNudgeSentAt}이
 * 보장하므로 같은 날 두 번 돌아도 중복 발송되지 않는다.
 *
 * <p><b>점등 게이트</b>: {@code booktimer.nudge.enabled=true}일 때만 빈으로 등록된다(기본 OFF). transactional 발송
 * ({@code booktimer.email.enabled})과 토글이 분리돼 있어, 이메일 점등 시 가입 인증·비번 재설정만 켜고 마케팅 넛지는
 * 법무 9박스(정보통신망법 §50) 완료 후 {@code BOOKTIMER_NUDGE_ENABLED=true}로 별도 점등할 수 있다.
 */
@Component
@ConditionalOnProperty(name = "booktimer.nudge.enabled", havingValue = "true")
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
