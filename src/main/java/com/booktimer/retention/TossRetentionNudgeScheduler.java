package com.booktimer.retention;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 토스 재참여 넛지 배치 트리거. 얇은 스케줄 어댑터 — 실제 로직은 {@link TossRetentionNudgeService}.
 *
 * <p><b>매일 KST 저녁 7시</b>에 1회 실행한다. 이메일 넛지(10:00)와 시각을 가르는 이유는 둘이다: ① 푸시는 즉시성
 * 매체라 독서를 실제로 시작할 수 있는 저녁이 낫다 ② 두 채널이 공유하는 멱등 컬럼({@code lastNudgeSentAt})의
 * 경합이 원천 차단된다(같은 날 이메일이 먼저 마킹하면 저녁 배치는 그 사람을 자동 제외). 야간 발송 제한
 * (21:00~익일 08:00) 앞이라 자연히 회피하는 것도 이메일 스케줄러와 같은 논리다.
 *
 * <p><b>점등 게이트</b>: {@code booktimer.toss.messenger.retention-enabled=true}일 때만 빈으로 등록된다(기본 OFF).
 * 완독 축하({@code messenger.enabled})·목표 달성({@code goal-met-enabled})과 별개 토글이라 독립적으로 점등한다 —
 * 콘솔 문구 검수가 캠페인별로 이뤄지기 때문이다.
 */
@Component
@ConditionalOnProperty(name = "booktimer.toss.messenger.retention-enabled", havingValue = "true")
public class TossRetentionNudgeScheduler {

    private final TossRetentionNudgeService nudgeService;

    public TossRetentionNudgeScheduler(TossRetentionNudgeService nudgeService) {
        this.nudgeService = nudgeService;
    }

    /** 매일 KST 19:00 — 초 분 시 일 월 요일. */
    @Scheduled(cron = "0 0 19 * * *", zone = "Asia/Seoul")
    public void runDailyNudge() {
        nudgeService.sendNudges();
    }
}
