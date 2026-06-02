package com.booktimer.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 일회성 스키마 보정 — {@code users.password_hash} 를 NULL 허용으로 변경.
 *
 * <p>소셜(OAuth) 계정 도입으로 {@code passwordHash} 가 nullable이 됐지만, 기존 운영 DB는
 * {@code ddl-auto=update}로 만들어져 있어 이 변경이 반영되지 않았다 — Hibernate의 {@code update}는
 * 새 컬럼은 추가해도 <b>기존 컬럼의 NOT NULL 제약은 완화하지 않기</b> 때문이다. 그 결과 소셜 신규
 * 사용자 INSERT가 {@code Column 'password_hash' cannot be null}로 실패했다(500).
 *
 * <p>앱은 이미 ALTER 권한을 가지므로(같은 배포에서 {@code auth_provider} 컬럼이 자동 추가됨), 시작 시
 * 한 번 멱등하게 보정한다: 컬럼이 이미 NULL 허용이면 건너뛰고, NOT NULL이면 ALTER 한다. prod 전용
 * (테스트 H2는 MySQL의 {@code MODIFY} 구문과 달라 실행 대상이 아니다). 실패해도 앱 기동은 막지 않는다.
 *
 * <p><b>임시 방편</b>이다 — 스키마 변경의 근본 관리는 Flyway 마이그레이션으로 전환한다(plan.md). 그때
 * 이 보정 클래스는 제거한다.
 */
@Component
@Profile("prod")
public class PasswordHashNullableSchemaFix implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PasswordHashNullableSchemaFix.class);

    private final JdbcTemplate jdbcTemplate;

    public PasswordHashNullableSchemaFix(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            String nullable = jdbcTemplate.queryForObject(
                    "SELECT IS_NULLABLE FROM information_schema.COLUMNS "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' "
                            + "AND COLUMN_NAME = 'password_hash'",
                    String.class);

            if ("NO".equalsIgnoreCase(nullable)) {
                jdbcTemplate.execute("ALTER TABLE users MODIFY password_hash VARCHAR(255) NULL");
                log.info("[schema-fix] users.password_hash → NULL 허용으로 변경 완료 (소셜 계정 지원)");
            } else {
                log.info("[schema-fix] users.password_hash 이미 NULL 허용 — 건너뜀 (nullable={})", nullable);
            }
        } catch (Exception e) {
            // 기동을 막지 않는다 — 실패 시 로그로 남기고 수동 조치.
            log.error("[schema-fix] users.password_hash NULL 허용 변경 실패 — 수동 확인 필요", e);
        }
    }
}
