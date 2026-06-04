package com.booktimer.admin;

import com.booktimer.user.Role;
import com.booktimer.user.UserRegistrationService;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADMIN 시드 승격 서비스 통합 테스트 (실제 빈 + H2).
 *
 * <p>운영자 계정은 가입으로 만들 수 없다(가입은 Role.USER 고정 — 권한 상승 벡터 차단).
 * 대신 환경변수로 지정한 이메일을 앱 시작 시 ADMIN으로 승격한다. 이 테스트는 승격 <b>로직</b>
 * (멱등·미존재 무시·빈 값 무시)을 직접 검증한다. 환경변수 바인딩·기동 배선은 별도(시드 러너).
 */
@SpringBootTest
@Transactional
class AdminAccountServiceTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired
    private AdminAccountService adminAccountService;

    @Autowired
    private UserRegistrationService registrationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private Clock clock;

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    @Test
    @DisplayName("seedAdmins: 목록의 이메일을 가진 USER를 ADMIN으로 승격한다")
    void seedAdmins_promotesListedUser() {
        registrationService.register("boss@booktimer.com", "rawpw1234", "사장", SEOUL, Role.USER, today());

        int promoted = adminAccountService.seedAdmins(List.of("boss@booktimer.com"));

        assertThat(promoted).isEqualTo(1);
        assertThat(userRepository.findByEmail("boss@booktimer.com").orElseThrow().getRole())
                .isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("seedAdmins: 멱등 — 이미 ADMIN이면 다시 승격하지 않는다(0건)")
    void seedAdmins_idempotent() {
        registrationService.register("boss@booktimer.com", "rawpw1234", "사장", SEOUL, Role.USER, today());

        adminAccountService.seedAdmins(List.of("boss@booktimer.com"));
        int again = adminAccountService.seedAdmins(List.of("boss@booktimer.com"));

        assertThat(again).isZero();
        assertThat(userRepository.findByEmail("boss@booktimer.com").orElseThrow().getRole())
                .isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("seedAdmins: 존재하지 않는 이메일/공백/null은 조용히 무시(예외 없음, 0건)")
    void seedAdmins_unknownOrBlank_noop() {
        int promoted = adminAccountService.seedAdmins(Arrays.asList("ghost@booktimer.com", "  ", null));

        assertThat(promoted).isZero();
    }
}
