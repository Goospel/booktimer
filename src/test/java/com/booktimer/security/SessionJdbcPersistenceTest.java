package com.booktimer.security;

import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;

/**
 * 세션 외부화(Spring Session JDBC) 통합 테스트.
 *
 * <p>기본 {@code HttpSession}은 JVM 메모리에 저장돼 태스크(컨테이너)가 교체되면 사라진다 →
 * 배포 때마다 재로그인. 세션을 공유 저장소(여기선 RDS)에 영속화하면 태스크가 바뀌어도 로그인이
 * 유지된다. 이 테스트는 폼 로그인으로 만들어진 세션이 실제로 {@code SPRING_SESSION} 테이블에
 * 행으로 남는지(=메모리가 아니라 DB에 저장) 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SessionJdbcPersistenceTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        // 공유 인메모리 H2라 다른 테스트에 누수되지 않도록 정리. 테이블이 아직 없을 수 있으니 방어적으로.
        try {
            jdbcTemplate.execute("DELETE FROM SPRING_SESSION_ATTRIBUTES");
            jdbcTemplate.execute("DELETE FROM SPRING_SESSION");
        } catch (Exception ignored) {
            // 세션 테이블 미존재(기능 미구현 단계) — 무시
        }
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("폼 로그인으로 만들어진 세션이 JDBC 저장소(SPRING_SESSION)에 영속화된다 — 인메모리 아님")
    void authenticatedSession_persistedToJdbcStore() throws Exception {
        userRepository.save(User.of(
                "reader@booktimer.com", passwordEncoder.encode("rawpw1234"), "책벌레", "Asia/Seoul", Role.USER));

        mockMvc.perform(formLogin("/login").user("reader@booktimer.com").password("rawpw1234"))
                .andExpect(authenticated().withUsername("reader@booktimer.com"));

        // 로그인 세션이 메모리가 아니라 SPRING_SESSION 테이블에 principal 인덱스와 함께 남아야 한다.
        Integer sessions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?",
                Integer.class, "reader@booktimer.com");

        assertThat(sessions)
                .as("폼 로그인 세션이 JDBC 저장소에 영속화되어야 한다")
                .isGreaterThan(0);
    }
}
