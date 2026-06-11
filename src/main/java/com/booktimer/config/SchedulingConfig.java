package com.booktimer.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄 배치 설정 — {@link EnableScheduling}로 {@code @Scheduled} 메서드를 활성화한다(이메일 인프라 2단계).
 *
 * <p>현재 유일한 스케줄 작업은 재참여 넛지 배치({@link com.booktimer.retention.RetentionNudgeScheduler}) —
 * 매일 KST 오전 10시에 7일 비활동 사용자에게 넛지 메일을 보낸다. 실발송 여부는 {@code booktimer.email.enabled}
 * 토글이 가르므로(꺼짐이면 로그만), 이 설정을 켜도 인프라/법무 점등 전엔 실제 메일이 나가지 않는다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
