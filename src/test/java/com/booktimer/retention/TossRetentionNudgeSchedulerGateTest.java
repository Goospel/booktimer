package com.booktimer.retention;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 토스 재참여 넛지 점등 게이트({@code booktimer.toss.messenger.retention-enabled}) 검증.
 *
 * <p>이 배치는 <b>떠난 사용자에게 푸시를 보낸다</b> — 기본값이 ON으로 뒤집히면 콘솔 검수 전에 운영에서 배치가 돌고
 * 미승인 템플릿으로 발송을 시도한다. 이 테스트가 단독으로 잡는 실패는 "다크런치 기본 OFF가 깨지는 것"과
 * "다른 토스 토글(완독 축하 {@code messenger.enabled})에 얹혀 함께 켜지는 것"이다.
 */
class TossRetentionNudgeSchedulerGateTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(TossRetentionNudgeService.class, () -> mock(TossRetentionNudgeService.class))
            .withConfiguration(UserConfigurations.of(TossRetentionNudgeScheduler.class));

    @Test
    void schedulerAbsent_whenPropertyMissing() {
        runner.run(context -> assertThat(context).doesNotHaveBean(TossRetentionNudgeScheduler.class));
    }

    @Test
    void schedulerAbsent_whenDisabled() {
        runner.withPropertyValues("booktimer.toss.messenger.retention-enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(TossRetentionNudgeScheduler.class));
    }

    @Test
    void schedulerAbsent_whenOnlyMessengerEnabled() {
        runner.withPropertyValues("booktimer.toss.messenger.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(TossRetentionNudgeScheduler.class));
    }

    @Test
    void schedulerPresent_whenEnabled() {
        runner.withPropertyValues("booktimer.toss.messenger.retention-enabled=true")
                .run(context -> assertThat(context).hasSingleBean(TossRetentionNudgeScheduler.class));
    }
}
