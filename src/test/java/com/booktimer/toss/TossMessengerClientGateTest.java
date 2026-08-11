package com.booktimer.toss;

import com.booktimer.config.TossProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 메신저 발송 점등 게이트({@code booktimer.toss.messenger.enabled}) 검증.
 *
 * <p>앱인토스는 <b>콘솔 문구 검수 승인 전 발송을 거부</b>한다(에러 5004). 코드는 검수보다 먼저 머지·배포되므로
 * 기본 OFF가 깨지면 승인 전 발송 시도가 운영에서 매 완독마다 에러 로그를 찍는다. 이 테스트가 단독으로 잡는 실패:
 * "게이트 기본값이 ON으로 뒤집혀 다크런치가 아니게 되는 것".
 */
class TossMessengerClientGateTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(TossProperties.class)
            .withBean(SslBundles.class, () -> mock(SslBundles.class))
            .withConfiguration(UserConfigurations.of(TossMessengerClient.class));

    @Test
    void clientAbsent_whenPropertyMissing() {
        runner.run(context -> assertThat(context).doesNotHaveBean(TossMessengerClient.class));
    }

    @Test
    void clientAbsent_whenDisabled() {
        runner.withPropertyValues("booktimer.toss.messenger.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(TossMessengerClient.class));
    }

    @Test
    void clientPresent_whenEnabled() {
        runner.withPropertyValues("booktimer.toss.messenger.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(TossMessengerClient.class));
    }
}
