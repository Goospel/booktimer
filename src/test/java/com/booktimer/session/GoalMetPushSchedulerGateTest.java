package com.booktimer.session;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 목표 달성 푸시 점등 게이트({@code booktimer.toss.messenger.goal-met-enabled}) 검증.
 *
 * <p>이 스케줄러는 <b>분마다</b> 전 사용자의 활성 세션을 훑는다 — 기본값이 ON으로 뒤집히면 콘솔 검수 전에
 * 운영에서 매분 배치가 돌고, 승인 안 된 템플릿으로 발송을 시도한다. 이 테스트가 단독으로 잡는 실패는
 * "다크런치 기본 OFF가 깨지는 것"이다. 완독 축하({@code messenger.enabled})와 <b>독립 토글</b>이라
 * 둘 중 하나만 켜도 다른 쪽은 안 도는 것도 여기서 고정된다.
 */
class GoalMetPushSchedulerGateTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(GoalMetPushService.class, () -> mock(GoalMetPushService.class))
            .withConfiguration(UserConfigurations.of(GoalMetPushScheduler.class));

    @Test
    void schedulerAbsent_whenPropertyMissing() {
        runner.run(context -> assertThat(context).doesNotHaveBean(GoalMetPushScheduler.class));
    }

    @Test
    void schedulerAbsent_whenDisabled() {
        runner.withPropertyValues("booktimer.toss.messenger.goal-met-enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(GoalMetPushScheduler.class));
    }

    @Test
    void schedulerAbsent_whenOnlyMessengerEnabled() {
        runner.withPropertyValues("booktimer.toss.messenger.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(GoalMetPushScheduler.class));
    }

    @Test
    void schedulerPresent_whenEnabled() {
        runner.withPropertyValues("booktimer.toss.messenger.goal-met-enabled=true")
                .run(context -> assertThat(context).hasSingleBean(GoalMetPushScheduler.class));
    }
}
