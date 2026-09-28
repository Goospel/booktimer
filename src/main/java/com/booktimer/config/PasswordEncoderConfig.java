package com.booktimer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 비밀번호 해싱용 BCrypt {@link PasswordEncoder}.
 *
 * <p>강도(cost)는 {@code booktimer.security.bcrypt-strength}로 정하고 운영 기본은 10이다. 테스트만
 * {@code src/test/resources/application.properties}에서 4로 낮춘다 — 픽스처 가입마다 도는 강도 10 해싱이
 * 스위트 시간의 약 1/4을 먹어 커밋 테스트 게이트를 넘겼기 때문이다(T-235).
 *
 * <p>BCrypt 해시는 자기 강도를 문자열 안에 담고 있어({@code $2a$10$...}) {@code matches()}는 인코더 강도와
 * 무관하게 동작한다 — 강도를 바꿔도 기존 해시는 그대로 검증된다.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder(@Value("${booktimer.security.bcrypt-strength:10}") int strength) {
        return new BCryptPasswordEncoder(strength);
    }
}
