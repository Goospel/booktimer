package com.booktimer.migration;

import com.booktimer.user.AuthProvider;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Flyway 마이그레이션이 스키마를 관리함을 검증한다.
 *
 * <p>이전에는 {@code ddl-auto=update} + 기동 시 보정 코드(PasswordHashNullableSchemaFix)로
 * 스키마를 맞췄다. Flyway 도입 후엔 V1 마이그레이션이 스키마의 단일 소스다 — 이 테스트는
 * (1) V1이 적용됐고, (2) 소셜 계정(password_hash=null) INSERT가 되며, (3) 이메일 유니크가
 * 강제됨을 확인한다. H2(MySQL 모드)에서 마이그레이션이 실제로 실행되므로 SQL 자체도 검증된다.
 */
@SpringBootTest
@TestPropertySource(properties = {
        // 메인 스위트는 Flyway를 끄고 Hibernate로 스키마를 만들지만, 이 테스트만 Flyway를 켜서
        // 실제 마이그레이션을 전용 격리 DB(MySQL 모드)에 적용한다. ddl-auto=validate로 두어,
        // V1 스키마가 엔티티 매핑과 어긋나면(드리프트) 컨텍스트 로딩이 실패하도록 한다.
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.datasource.url=jdbc:h2:mem:flyway_migration_test;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
class FlywayMigrationTest {

    @Autowired
    Flyway flyway;

    @Autowired
    UserRepository userRepository;

    @Test
    void v1_baseline_migration_is_applied() {
        boolean v1Applied = Arrays.stream(flyway.info().applied())
                .map(MigrationInfo::getVersion)
                .anyMatch(v -> v != null && v.getVersion().equals("1"));

        assertThat(v1Applied)
                .as("V1 baseline 마이그레이션이 적용되어야 한다")
                .isTrue();
    }

    @Test
    void social_account_with_null_password_can_be_persisted() {
        // password_hash 가 nullable이 아니면 여기서 제약 위반으로 실패한다.
        User social = User.ofOAuth("social@example.com", "소셜유저", "Asia/Seoul",
                Role.USER, AuthProvider.GOOGLE);

        User saved = userRepository.saveAndFlush(social);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getPasswordHash()).isNull();
    }

    @Test
    void email_unique_constraint_is_enforced() {
        userRepository.saveAndFlush(
                User.of("dup@example.com", "hash", "닉1", "Asia/Seoul", Role.USER));

        assertThatThrownBy(() -> userRepository.saveAndFlush(
                User.of("dup@example.com", "hash", "닉2", "Asia/Seoul", Role.USER)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
