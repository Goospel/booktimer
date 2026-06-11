package com.booktimer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 비동기 실행 설정 — {@link EnableAsync}로 {@code @Async} 메서드를 활성화한다.
 *
 * <p>이메일 발송({@link com.booktimer.email.EmailDispatcher})을 요청 스레드 밖으로 빼기 위한 전용 풀을 둔다.
 * 기본 {@code SimpleAsyncTaskExecutor}(요청마다 새 스레드 무한 생성)를 쓰지 않고 <b>크기를 제한한 풀</b>을 둬,
 * 발송 폭주 시에도 스레드가 무제한으로 늘지 않게 한다(작은 서비스 규모에 맞춘 보수적 값).
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "emailTaskExecutor")
    public Executor emailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("email-");
        executor.initialize();
        return executor;
    }
}
