package com.booktimer.retention;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 넛지 스케줄러 점등 게이트({@code booktimer.nudge.enabled}) 검증.
 *
 * <p>이메일 점등 시 transactional({@code booktimer.email.enabled})만 켜고 마케팅 재참여 넛지는
 * <b>법무 9박스(정보통신망법 §50) 완료 전까지 분리 차단</b>해야 한다. 발송 토글이 하나면 둘이 함께 켜지므로
 * 넛지 스케줄러에 독립 게이트를 둔다. 이 테스트가 단독으로 잡는 실패: "게이트 기본 OFF가 깨져
 * transactional 점등과 동시에 마케팅 넛지 배치가 살아나는 것"(법무 미충족 발송 = 과태료·평판 리스크).
 *
 * <p>{@link ApplicationContextRunner}로 스케줄러 빈만 격리 평가한다(서비스는 mock — 조건부 등록만 관심).
 */
class RetentionNudgeSchedulerGateTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(RetentionNudgeService.class, () -> mock(RetentionNudgeService.class))
            .withConfiguration(UserConfigurations.of(RetentionNudgeScheduler.class));

    @Test
    void schedulerAbsent_whenNudgeDisabled() {
        runner.withPropertyValues("booktimer.nudge.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(RetentionNudgeScheduler.class));
    }

    @Test
    void schedulerAbsent_whenPropertyMissing() {
        runner.run(context -> assertThat(context).doesNotHaveBean(RetentionNudgeScheduler.class));
    }

    @Test
    void schedulerPresent_whenNudgeEnabled() {
        runner.withPropertyValues("booktimer.nudge.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(RetentionNudgeScheduler.class));
    }
}
