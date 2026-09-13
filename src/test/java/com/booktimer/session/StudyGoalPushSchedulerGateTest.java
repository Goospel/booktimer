package com.booktimer.session;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 공부 회당 시간 푸시 점등 게이트({@code booktimer.toss.messenger.study-goal-enabled}) 검증.
 *
 * <p>단독으로 잡는 실패: 다크런치 기본 OFF가 깨져 콘솔 승인 전 운영에서 분당 배치가 도는 것, 그리고 독서
 * 목표 달성 토글({@code goal-met-enabled})만 켜도 이 스케줄러가 딸려 뜨는 것.
 */
class StudyGoalPushSchedulerGateTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(StudyGoalPushService.class, () -> mock(StudyGoalPushService.class))
            .withConfiguration(UserConfigurations.of(StudyGoalPushScheduler.class));

    @Test
    void schedulerAbsent_whenPropertyMissing() {
        runner.run(context -> assertThat(context).doesNotHaveBean(StudyGoalPushScheduler.class));
    }

    @Test
    void schedulerAbsent_whenDisabled() {
        runner.withPropertyValues("booktimer.toss.messenger.study-goal-enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(StudyGoalPushScheduler.class));
    }

    @Test
    void schedulerAbsent_whenOnlyReadingGoalMetEnabled() {
        runner.withPropertyValues("booktimer.toss.messenger.goal-met-enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(StudyGoalPushScheduler.class));
    }

    @Test
    void schedulerPresent_whenEnabled() {
        runner.withPropertyValues("booktimer.toss.messenger.study-goal-enabled=true")
                .run(context -> assertThat(context).hasSingleBean(StudyGoalPushScheduler.class));
    }
}
