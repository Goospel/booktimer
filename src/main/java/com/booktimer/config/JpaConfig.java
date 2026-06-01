package com.booktimer.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA 공통 설정.
 *
 * <p>{@code @EnableJpaAuditing}으로 {@link com.booktimer.common.BaseTimeEntity}의
 * createdAt/updatedAt 자동 채움을 활성화한다. 메인 앱은 컴포넌트 스캔으로 이 설정을 줍지만,
 * {@code @DataJpaTest} 슬라이스는 자동 로드하지 않으므로 테스트에서 {@code @Import(JpaConfig.class)}로
 * 끌어와야 한다.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
