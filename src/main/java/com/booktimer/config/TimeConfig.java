package com.booktimer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 시간 의존성 설정.
 *
 * <p>{@link Clock} 을 빈으로 노출해 "지금"을 주입 가능하게 만든다. 운영은 시스템 UTC 시계를
 * 쓰고(절대 시점), "오늘"은 각 유저 타임존으로 변환해 계산한다. 테스트는 이 빈 대신
 * {@code Clock.fixed(...)} 를 직접 넣어 시간 의존을 결정적으로 만든다.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
