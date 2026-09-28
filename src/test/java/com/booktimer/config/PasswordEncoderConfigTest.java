package com.booktimer.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BCrypt 강도 설정({@code booktimer.security.bcrypt-strength}) — 운영 기본 10이 조용히 낮아지는 회귀와
 * 테스트용 강도 4가 기존 운영 해시를 못 읽는 회귀를 잡는다(T-235).
 */
class PasswordEncoderConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PasswordEncoderConfig.class);

    @Test
    @DisplayName("속성이 없으면 운영 기본 강도 10으로 해싱한다")
    void defaultsToStrength10() {
        runner.run(context -> assertThat(context.getBean(PasswordEncoder.class).encode("pw12345678"))
                .startsWith("$2a$10$"));
    }

    @Test
    @DisplayName("속성으로 강도 4를 주면 강도 4로 해싱한다")
    void honorsStrengthProperty() {
        runner.withPropertyValues("booktimer.security.bcrypt-strength=4")
                .run(context -> assertThat(context.getBean(PasswordEncoder.class).encode("pw12345678"))
                        .startsWith("$2a$04$"));
    }

    @Test
    @DisplayName("강도 4 인코더도 강도 10으로 만든 기존 해시를 검증한다")
    void strength4MatchesStrength10Hash() {
        String legacyHash = new BCryptPasswordEncoder(10).encode("pw12345678");
        runner.withPropertyValues("booktimer.security.bcrypt-strength=4")
                .run(context -> assertThat(context.getBean(PasswordEncoder.class).matches("pw12345678", legacyHash))
                        .isTrue());
    }
}
