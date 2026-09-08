package com.booktimer.study;

import com.booktimer.user.Role;
import com.booktimer.user.StudyAiAccess;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 승인 정원의 <b>경합</b> — 「읽고 판단한 뒤 쓴다」가 아닌지 잰다.
 *
 * <p><b>왜 따로 있나</b>: {@code StudyAiApprovalCapTest}는 {@code @Transactional}이라 모든 호출이 테스트
 * 트랜잭션 하나에 묶여, <b>TOCTOU 구현도 초록으로 통과한다</b>. 실제 배치(각 요청이 제 트랜잭션)를
 * 재려면 커밋이 진짜로 일어나야 한다 — {@code StudyAiUsageServiceTest}가 카운터에 대해 세운 것과 같은 규율이다.
 *
 * <p><b>이 테스트가 있는 이유</b>: 정원은 「어드민 계정이 뚫렸을 때 사고의 모양을 작게 만든다」는 2차
 * 방어선이다. 그런데 <b>어드민을 쥔 공격자에게 동시 요청은 공짜다</b> — 경합에서 무너지면 그 방어선은
 * 자기가 지목한 바로 그 적 앞에서만 없는 것이 된다. 리뷰 실측(2026-09-08)에서 8스레드가 정원 1을
 * 전원 통과했다.
 *
 * <p>⚠️ 비트랜잭션이라 등록한 사용자가 <b>커밋된다</b>. 그대로 두면 다른 테스트 컨텍스트의 사용자
 * 집계에 새어 엉뚱한 곳이 깨진다(실제로 {@code AdminStatsServiceTest}의 「사용자당 평균」이 그렇게
 * 깨졌다) — 그래서 {@link #cleanUp()}이 반드시 지운다.
 */
@SpringBootTest
class StudyAiApprovalRaceTest {

    private static final Instant NOW = Instant.parse("2026-09-08T01:00:00Z");
    private static final int RACERS = 8;
    private static final String PASSWORD = "pw1234qwer!!";

    @Autowired StudyAiAccessService accessService;
    @Autowired UserRegistrationService registrationService;
    @Autowired UserRepository userRepository;
    @Autowired com.booktimer.user.AccountService accountService;

    private final List<String> registered = new ArrayList<>();

    /**
     * ⚠️ {@code userRepository.delete}로는 못 지운다 — {@code reading_timer} 등 자식 행의 FK가 막는다
     * (실제로 그 정리 실패 때문에 승인자가 남아 {@code StudyAiApprovalCapTest}가 「정원이 이미 찼다」로
     * 깨졌다). 운영 탈퇴가 쓰는 {@code purge} 경로를 그대로 탄다 — FK 순서를 두 번 짜지 않는다.
     */
    @AfterEach
    void cleanUp() {
        registered.forEach(loginId -> {
            try {
                accountService.deleteAccount(loginId + "@booktimer.com", PASSWORD);
            } catch (Exception e) {
                // 이미 지워졌거나 등록에 실패한 항목 — 정리가 테스트 결과를 가리면 안 된다.
            }
        });
        registered.clear();
    }

    private User registerAndRequest(String loginId) {
        registrationService.register(loginId + "@booktimer.com", PASSWORD, loginId,
                "닉네임_" + loginId, "Asia/Seoul", Role.USER, LocalDate.of(2026, 1, 1));
        registered.add(loginId);
        User user = userRepository.findByLoginId(loginId).orElseThrow();
        accessService.request(user, NOW);
        return user;
    }

    @Test
    @DisplayName("경합: 8명이 동시에 승인돼도 정원(1명)을 넘지 않는다")
    void concurrentApprovalsRespectCapacity() throws Exception {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < RACERS; i++) {
            ids.add("race" + i);
            registerAndRequest("race" + i);
        }

        ExecutorService pool = Executors.newFixedThreadPool(RACERS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(RACERS);
        for (String loginId : ids) {
            pool.submit(() -> {
                try {
                    start.await();
                    accessService.approve(loginId, NOW);
                } catch (Exception ignored) {
                    // 정원 초과·경합 실패는 정상 — 여기선 최종 APPROVED 수만 본다.
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(userRepository.countByStudyAiAccess(StudyAiAccess.APPROVED))
                .as("정원을 넘겨 승인됐다 — 읽고 판단한 뒤 쓰는(TOCTOU) 구현이면 전원이 통과한다")
                .isEqualTo(StudyAiAccessService.MAX_APPROVED);
    }
}
