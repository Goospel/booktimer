package com.booktimer.study;

import com.booktimer.user.Role;
import com.booktimer.user.StudyAiAccess;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 승인 정원 — 어드민이 뚫려도 <b>한 번에 열 수 있는 문의 수</b>를 묶는다(보안 리뷰 2026-09-08 S-1 보강).
 *
 * <p>전역 일일 상한({@link StudyAiGlobalCapTest})이 총액을 묶는 1차 방어선이고, 이건 그 위의 2차다 —
 * 둘은 서로 독립이다. 정원이 있으면 「전원 승인」 자체가 불가능해져 사고의 <b>모양</b>이 작아지고,
 * 전역 상한이 그래도 새는 총액을 묶는다.
 *
 * <p>{@code @Transactional}이라 각 테스트가 롤백된다 — APPROVED 수는 DB 전역이라 커밋하면 테스트끼리
 * 서로의 정원을 잡아먹는다.
 */
@SpringBootTest
@Transactional
class StudyAiApprovalCapTest {

    private static final Instant NOW = Instant.parse("2026-09-08T01:00:00Z");

    @Autowired StudyAiAccessService accessService;
    @Autowired UserRegistrationService registrationService;
    @Autowired UserRepository userRepository;

    private User registerAndRequest(String loginId) {
        User user = registerUnverified(loginId);
        // 신청 게이트가 이메일 검증을 요구한다(S-4) — 로컬 가입은 미검증으로 시작하므로 여기서 채운다.
        // 정원 테스트의 관심사가 아니지만, 이 한 줄이 아래 S-4 테스트들의 <b>양성 대조군</b>이기도 하다.
        user.verifyEmail();
        userRepository.saveAndFlush(user);
        accessService.request(user, NOW);
        return user;
    }

    /** 이메일 검증 없이 등록한다 — 로컬 가입의 실제 초기 상태이자 S-4 게이트의 대상. */
    private User registerUnverified(String loginId) {
        registrationService.register(loginId + "@booktimer.com", "pw1234qwer!!", loginId,
                "닉네임_" + loginId, "Asia/Seoul", Role.USER, LocalDate.of(2026, 1, 1));
        return userRepository.findByLoginId(loginId).orElseThrow();
    }

    private long approvedCount() {
        return userRepository.countByStudyAiAccess(StudyAiAccess.APPROVED);
    }

    @Test
    @DisplayName("정원(1명)이 차면 다음 승인은 거절된다 — APPROVED 수가 늘지 않는다")
    void approveAtCapacityIsRejected() {
        registerAndRequest("capa1");
        registerAndRequest("capa2");

        accessService.approve("capa1", NOW);
        assertThat(approvedCount()).isEqualTo(1);

        assertThatThrownBy(() -> accessService.approve("capa2", NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("정원");

        // 거절이 상태를 흔들지 않는다 — 두 번째 사람은 여전히 대기다.
        assertThat(approvedCount()).isEqualTo(1);
        assertThat(userRepository.findByLoginId("capa2").orElseThrow().getStudyAiAccess())
                .isEqualTo(StudyAiAccess.PENDING);
    }

    @Test
    @DisplayName("회수하면 자리가 하나 난다 — 정원은 잠그는 게 아니라 세는 것이다")
    void revokeFreesASlot() {
        registerAndRequest("capb1");
        registerAndRequest("capb2");
        accessService.approve("capb1", NOW);

        accessService.revoke("capb1", NOW);
        assertThat(approvedCount()).isZero();

        accessService.approve("capb2", NOW);
        assertThat(approvedCount()).isEqualTo(1);
    }

    // 거절은 승인이 아니므로 정원과 무관하다 — 정원이 찼다고 대기열을 정리하지 못하면 곤란하다.
    @Test
    @DisplayName("정원이 차 있어도 거절은 된다")
    void rejectWorksAtCapacity() {
        registerAndRequest("capc1");
        registerAndRequest("capc2");
        accessService.approve("capc1", NOW);

        accessService.reject("capc2", NOW);

        assertThat(userRepository.findByLoginId("capc2").orElseThrow().getStudyAiAccess())
                .isEqualTo(StudyAiAccess.REJECTED);
    }

    // AI 신청은 「돈이 나가는 문」의 첫 단계인데 이메일 검증을 안 봤다(리뷰 S-4) — 아무 주소로 만든
    // 계정이 대기 큐에 줄을 설 수 있었다. 게이트를 전이보다 앞에 둬 상태를 흔들지 않고 막는다.
    @Test
    @DisplayName("이메일 미검증이면 신청이 403 — 상태는 NONE 그대로다(S-4)")
    void requestRejectsUnverifiedEmail() {
        registerUnverified("capd1");
        User user = userRepository.findByLoginId("capd1").orElseThrow();

        assertThatThrownBy(() -> accessService.request(user, NOW))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("이메일 인증");

        assertThat(userRepository.findByLoginId("capd1").orElseThrow().getStudyAiAccess())
                .isEqualTo(StudyAiAccess.NONE);
    }

    // 승인 쪽에도 같은 검사가 필요한 이유: 신청 게이트만 두면 <b>이 변경이 배포되는 순간 이미 대기 중인</b>
    // 미검증 PENDING이 그대로 승인된다. 게이트는 마지막 문에도 서 있어야 한다.
    @Test
    @DisplayName("이메일 미검증 PENDING은 승인되지 않는다 — 검증을 채우면 승인된다(양성 대조군)")
    void approveRejectsUnverifiedEmail() {
        User user = registerUnverified("capd2");
        user.requestStudyAi(NOW); // 신청 게이트를 우회해 대기로 만든다 = 배포 전에 쌓여 있던 줄의 재현
        userRepository.saveAndFlush(user);

        assertThatThrownBy(() -> accessService.approve("capd2", NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("미검증");
        assertThat(approvedCount()).isZero();

        // 양성 대조군 — 검증만 채우면 같은 호출이 통과한다. 이게 없으면 「늘 거절」 구현도 초록이다.
        user.verifyEmail();
        userRepository.saveAndFlush(user);
        accessService.approve("capd2", NOW);
        assertThat(approvedCount()).isEqualTo(1);
    }
}
