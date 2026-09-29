package com.booktimer.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * 2026-06-17 09:00Z(= 18:00 KST = 05:00 EDT)로 멈춘 시계 — 서울·뉴욕이 같은 날짜(06-17)라 타임존 테스트도 결정적이다.
 * 통합 테스트가 운영 {@code Clock.systemUTC()}(실시간)를 쓰면 「오늘」 판정이 자정 경계에서 플레이키하게 깨진다.
 * <p>{@code @Import(FixedClockConfig.class)}로만 쓴다. 이것을 가져오는 테스트끼리는 컨텍스트를 공유한다 —
 * 같은 내용이라도 테스트마다 중첩 클래스로 두면 캐시 키가 갈라져 컨텍스트가 하나씩 더 뜬다(T-235).
 * <p>{@code @TestConfiguration}이라 컴포넌트 스캔에서 빠진다(TestTypeExcludeFilter) — 다른 테스트로 새지 않는다.
 */
@TestConfiguration
public class FixedClockConfig {

    public static final Instant NOW = Instant.parse("2026-06-17T09:00:00Z");

    @Bean
    @Primary
    Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }
}
