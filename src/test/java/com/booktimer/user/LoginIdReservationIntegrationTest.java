package com.booktimer.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 놓아준 옛 핸들의 <b>예약</b>이 login_id를 정하는 모든 경로에서 지켜지는지 (실제 빈 + H2).
 *
 * <p>왜 필요한가: {@code login_id} UNIQUE는 변경 순간 옛 값을 비워 준다 — 그래서 <b>DB는 이걸 막지 못한다</b>.
 * 예약이 한 경로(아이디 변경)에만 걸려 있으면, 제3자가 <b>가입·온보딩</b>으로 그 핸들을 주워 갈 수 있고,
 * 아이디를 바꾼 사용자의 열린 세션(principal = 옛 핸들)이 브리지 조회로 <b>남의 계정</b>에 붙는다(계정 탈취).
 *
 * <p>그래서 판정을 {@link UserRepository#isLoginIdTaken}에 모으고, 여기서 <b>경로별로</b> 못 박는다 —
 * 새 경로가 생겨도 이 테스트들이 같은 규칙을 요구한다.
 */
@SpringBootTest
@Transactional
class LoginIdReservationIntegrationTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired
    private UserRegistrationService registrationService;
    @Autowired
    private OnboardingService onboardingService;
    @Autowired
    private AccountService accountService;

    /** 한 사용자가 {@code oldHandle}을 실제로 놓아준 상태를 만든다 — 그 핸들은 previous_login_id로 예약돼 남는다. */
    private void abandon(String email, String oldHandle, String newHandle) {
        User owner = registrationService.register(
                email, "rawpw1234", oldHandle, "독자", SEOUL, Role.USER, LocalDate.now());
        accountService.changeLoginId(owner, newHandle);
    }

    /** login_id가 아직 없는 계정(소셜/미니앱 유입) — 온보딩에서 핸들을 정한다. */
    private User handleless(String email) {
        return registrationService.register(email, "rawpw1234", "독자", SEOUL, Role.USER, LocalDate.now());
    }

    @Test
    @DisplayName("가입: 남이 놓아준 옛 핸들로는 새로 가입할 수 없다 — 이게 뚫리면 옛 세션이 남의 계정에 붙는다")
    void registerRejectsAbandonedHandle() {
        abandon("res-reg@booktimer.com", "resprobeold", "resprobenew");

        assertThatThrownBy(() -> registrationService.register(
                "res-thief@booktimer.com", "rawpw1234", "resprobeold", "침입자", SEOUL, Role.USER, LocalDate.now()))
                .isInstanceOf(LoginIdAlreadyExistsException.class);
    }

    @Test
    @DisplayName("온보딩 완료: 남이 놓아준 옛 핸들은 온보딩에서도 고를 수 없다")
    void onboardingCompleteRejectsAbandonedHandle() {
        abandon("res-onb@booktimer.com", "resonbold", "resonbnew");
        User newcomer = handleless("res-onb-new@booktimer.com");

        assertThatThrownBy(() -> onboardingService.complete(newcomer, "resonbold", "새사람", 3600L))
                .isInstanceOf(LoginIdAlreadyExistsException.class);
    }

    @Test
    @DisplayName("핸들 생성(미니앱): 남이 놓아준 옛 핸들은 미니앱 핸들 만들기에서도 막힌다")
    void assignHandleRejectsAbandonedHandle() {
        abandon("res-mini@booktimer.com", "resminiold", "resmininew");
        User newcomer = handleless("res-mini-new@booktimer.com");

        assertThatThrownBy(() -> onboardingService.assignHandle(newcomer, "resminiold"))
                .isInstanceOf(LoginIdAlreadyExistsException.class);
    }
}
