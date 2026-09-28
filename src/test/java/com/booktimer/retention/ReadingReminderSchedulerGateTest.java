package com.booktimer.retention;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 독서 알림 매시 배치 점등 게이트({@code booktimer.toss.messenger.reading-reminder-enabled}) — 다크런치 기본 OFF가
 * 깨지거나 다른 토스 토글(완독 축하 {@code messenger.enabled})에 얹혀 함께 켜지는 회귀를 잡는다.
 */
class ReadingReminderSchedulerGateTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(ReadingReminderService.class, () -> mock(ReadingReminderService.class))
            .withConfiguration(UserConfigurations.of(ReadingReminderScheduler.class));

    @Test
    @DisplayName("REQ-08 · 토글이 없거나 false면 배치 빈이 없다")
    void absent_whenMissingOrFalse() {
        runner.run(context -> assertThat(context).doesNotHaveBean(ReadingReminderScheduler.class));
        runner.withPropertyValues("booktimer.toss.messenger.reading-reminder-enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ReadingReminderScheduler.class));
    }

    @Test
    @DisplayName("REQ-08 · 메신저 토글만 켜져도 배치 빈은 없다")
    void absent_whenOnlyMessengerEnabled() {
        runner.withPropertyValues("booktimer.toss.messenger.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(ReadingReminderScheduler.class));
    }

    @Test
    @DisplayName("REQ-08 · 토글이 true면 배치 빈이 있다")
    void present_whenEnabled() {
        runner.withPropertyValues("booktimer.toss.messenger.reading-reminder-enabled=true")
                .run(context -> assertThat(context).hasSingleBean(ReadingReminderScheduler.class));
    }
}
