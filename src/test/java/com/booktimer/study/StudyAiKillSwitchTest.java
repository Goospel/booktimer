package com.booktimer.study;

import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 킬 스위치 — 전역 상한이 <b>실제로 발동했을 때</b> 무슨 일이 일어나는가.
 *
 * <p>이 파일이 없으면 이 PR의 핵심 기능에 end-to-end 계측기가 하나도 없다. 기존 테스트는 카운터의
 * 산술만 재고, <b>상한에 걸린 요청이 어떻게 끝나는지</b>는 아무도 안 봤다 — 리뷰 실측(2026-09-08)에서
 * {@code case GLOBAL_EXHAUSTED} 줄을 통째로 지워도 전 스위트가 초록이었다.
 *
 * <p>재는 것은 둘이고 <b>둘째가 본질</b>이다: ① 503으로 끝나는가 ② <b>어댑터를 안 부르는가(=돈이
 * 안 나가는가)</b>. ①만 재면 「503을 돌려주면서 호출은 했다」가 초록이 된다 — 그건 상한이 아니다.
 *
 * <p>{@code daily-cap=0}은 운영에서 <b>사고 중 전면 차단</b>에 쓰는 값 그대로다(배포 없이 SSM으로 내린다).
 */
@SpringBootTest(properties = "booktimer.study.ai.daily-cap=0")
@Transactional
class StudyAiKillSwitchTest {

    @Autowired StudyPlanService planService;
    @Autowired StudyAiAccessService accessService;
    @Autowired StudyAiUsageService usageService;
    @Autowired UserRegistrationService registrationService;
    @Autowired UserRepository userRepository;

    /** 어댑터는 목이다 — 「부르지 않았다」를 재려면 진짜를 쓸 수 없다(키가 없어 DISABLED로 새 버린다). */
    @MockitoBean GeminiStudyPlanner planner;

    private User approvedUser(String loginId) {
        registrationService.register(loginId + "@booktimer.com", "pw1234qwer!!", loginId,
                "닉네임_" + loginId, "Asia/Seoul", Role.USER, LocalDate.of(2026, 1, 1));
        User user = userRepository.findByLoginId(loginId).orElseThrow();
        Instant now = Instant.parse("2026-09-08T01:00:00Z");
        accessService.request(user, now);
        accessService.approve(loginId, now);
        return userRepository.findByLoginId(loginId).orElseThrow();
    }

    @Test
    @DisplayName("전역 상한 0이면 503으로 막고 — 어댑터를 아예 부르지 않는다")
    void whenGlobalCapExhausted_blocksBeforeCallingAdapter() {
        User user = approvedUser("killswitch");
        org.mockito.BDDMockito.given(planner.isEnabled()).willReturn(true);

        assertThatThrownBy(() -> planService.generate(user, new StudyPlanService.GenerateCommand(
                "정보보안기사", "1장 접근통제", LocalDate.now().plusDays(30), 120, 5)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

        // 본질은 이쪽이다 — 503을 돌려주면서 호출은 했다면 상한이 아니다.
        org.mockito.BDDMockito.then(planner).should(org.mockito.Mockito.never()).generatePlan(
                org.mockito.ArgumentMatchers.any());
    }

    // 전역에서 막혔다고 사용자가 자기 몫을 잃으면 안 된다 — 서비스 사정으로 개인이 손해 보는 자리다.
    @Test
    @DisplayName("전역에서 막히면 사용자 몫은 되돌아온다")
    void globalRejectionRefundsUserQuota() {
        User user = approvedUser("killrefund");
        org.mockito.BDDMockito.given(planner.isEnabled()).willReturn(true);
        LocalDate today = StudyDates.today(user, Instant.parse("2026-09-08T01:00:00Z"));

        assertThatThrownBy(() -> planService.generate(user, new StudyPlanService.GenerateCommand(
                "정보보안기사", "1장", LocalDate.now().plusDays(30), 120, 5)))
                .isInstanceOf(ResponseStatusException.class);

        assertThat(usageService.remaining(user, today, StudyAiUsage.Kind.PLAN))
                .as("전역 거절이 사용자 몫을 먹었다")
                .isEqualTo(StudyAiUsage.Kind.PLAN.max());
    }

    @Test
    @DisplayName("상한 0은 전역 카운터 자체가 첫 요청부터 막는다")
    void globalCounterRejectsFromTheFirstCall() {
        assertThat(usageService.tryConsumeGlobal(Instant.parse("2026-09-08T01:00:00Z"))).isFalse();
    }
}
