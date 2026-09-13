package com.booktimer.study;

import com.booktimer.study.StudyAiUsage.Kind;
import com.booktimer.study.StudyAiUsageService.Grant;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 하루 상한 카운터 — <b>경합에서도 정확히 max번만</b> 통과시키는지가 이 파일의 요점이다.
 *
 * <p>{@code @Transactional}을 <b>일부러 안 붙였다</b>: 붙이면 모든 호출이 테스트 트랜잭션 하나에 묶여
 * 「COUNT 후 INSERT」 같은 TOCTOU 구현도 초록으로 통과한다. 실제 배치(각 요청이 제 트랜잭션)를 재려면
 * 커밋이 진짜로 일어나야 한다. 대신 테스트마다 다른 사용자를 써서 서로 간섭하지 않게 한다.
 */
@SpringBootTest
class StudyAiUsageServiceTest {

    private static final String SEOUL = "Asia/Seoul";
    private static final LocalDate DAY = LocalDate.of(2026, 9, 3);

    @Autowired StudyAiUsageService usageService;
    @Autowired UserRegistrationService registrationService;
    @Autowired UserRepository userRepository;

    private User register(String loginId) {
        registrationService.register(loginId + "@booktimer.com", "pw1234qwer!!", loginId,
                "닉네임_" + loginId, SEOUL, Role.USER, LocalDate.of(2026, 1, 1));
        return userRepository.findByLoginId(loginId).orElseThrow();
    }

    @Test
    @DisplayName("ANALYZE는 하루 1번 — 두 번째는 거절된다")
    void analyze_isOncePerDay() {
        User user = register("usage1");

        assertThat(usageService.tryConsume(user, DAY, Kind.ANALYZE)).isTrue();
        assertThat(usageService.tryConsume(user, DAY, Kind.ANALYZE)).isFalse();
        assertThat(usageService.remaining(user, DAY, Kind.ANALYZE)).isZero();
    }

    @Test
    @DisplayName("종류가 다르면 몫도 다르다 — 분석을 다 써도 일정 생성은 남는다")
    void kinds_areIndependent() {
        User user = register("usage2");

        assertThat(usageService.tryConsume(user, DAY, Kind.ANALYZE)).isTrue();

        assertThat(usageService.remaining(user, DAY, Kind.PLAN)).isEqualTo(Kind.PLAN.max());
        assertThat(usageService.tryConsume(user, DAY, Kind.PLAN)).isTrue();
    }

    @Test
    @DisplayName("날짜가 다르면 몫도 다르다 — 자정이 지나면 다시 쓸 수 있다")
    void days_areIndependent() {
        User user = register("usage3");

        assertThat(usageService.tryConsume(user, DAY, Kind.ANALYZE)).isTrue();
        assertThat(usageService.tryConsume(user, DAY.plusDays(1), Kind.ANALYZE)).isTrue();
    }

    @Test
    @DisplayName("환불하면 다시 쓸 수 있다 — 외부 호출이 실패한 몫은 사용자 것이 아니다")
    void refund_returnsTheShare() {
        User user = register("usage4");

        assertThat(usageService.tryConsume(user, DAY, Kind.ANALYZE)).isTrue();
        assertThat(usageService.tryConsume(user, DAY, Kind.ANALYZE)).isFalse();

        usageService.refund(user, DAY, Kind.ANALYZE);

        assertThat(usageService.remaining(user, DAY, Kind.ANALYZE)).isEqualTo(1);
        assertThat(usageService.tryConsume(user, DAY, Kind.ANALYZE)).isTrue();
    }

    @Test
    @DisplayName("환불이 0 밑으로 내려가지 않는다 — 안 쓴 몫을 환불해도 내일 몫이 늘지 않는다")
    void refund_neverGoesBelowZero() {
        User user = register("usage5");

        usageService.refund(user, DAY, Kind.ANALYZE);
        usageService.refund(user, DAY, Kind.ANALYZE);

        assertThat(usageService.remaining(user, DAY, Kind.ANALYZE)).isEqualTo(Kind.ANALYZE.max());
        assertThat(usageService.tryConsume(user, DAY, Kind.ANALYZE)).isTrue();
        assertThat(usageService.tryConsume(user, DAY, Kind.ANALYZE)).isFalse();
    }

    /**
     * 사용자당 상한의 날짜 키가 <b>UTC</b>인지 잰다 — 유저 타임존을 키로 잡으면 자정 근처에 <b>설정에서
     * 존만 바꿔</b> 그 날 몫을 두 배로 쓸 수 있다(보안 리뷰 2026-09-08 S-3). 전역 상한은 같은 이유로
     * 처음부터 UTC였는데, 사용자당 상한만 {@code StudyDates.today}(유저 타임존)로 남아 있었다.
     *
     * <p>⚠️ <b>이 테스트만 트랜잭션이다.</b> 클래스가 비트랜잭션인 것은 경합에서 TOCTOU 구현이 초록으로
     * 통과하지 않게 하려는 것인데 여기는 경합을 재지 않는다. 반면 타임존을 바꾼 사용자를 커밋하면 그 행이
     * 다른 테스트 컨텍스트의 집계에 샌다({@code StudyAiApprovalRaceTest} javadoc의 실측) — 롤백으로 막는다.
     */
    @Test
    @Transactional
    @DisplayName("타임존을 바꿔도 몫이 되살아나지 않는다 — 날짜 키는 UTC다(UTC 자정을 넘기면 되살아남 = 양성 대조군)")
    void timezoneChangeDoesNotResetTheShare() {
        User user = register("usagetz");
        // 서울(UTC+9)로는 09-13 23:30 — UTC로는 아직 09-13이다.
        Instant seoulLateNight = Instant.parse("2026-09-13T14:30:00Z");

        for (int i = 0; i < Kind.PLAN.max(); i++) {
            assertThat(usageService.tryConsumeBoth(user, seoulLateNight, Kind.PLAN)).isEqualTo(Grant.OK);
        }
        assertThat(usageService.tryConsumeBoth(user, seoulLateNight, Kind.PLAN)).isEqualTo(Grant.USER_EXHAUSTED);

        // UTC+14 — 같은 순간이 이 사람의 달력으로는 이미 09-14다. 유저 타임존을 키로 잡았다면 새 행이
        // 생겨 여기서 OK가 난다(= 몫이 두 배가 된다).
        user.updateProfile(user.getNickname(), "Pacific/Kiritimati");
        userRepository.saveAndFlush(user);

        assertThat(usageService.tryConsumeBoth(user, seoulLateNight, Kind.PLAN)).isEqualTo(Grant.USER_EXHAUSTED);
        assertThat(usageService.remaining(user, seoulLateNight, Kind.PLAN)).isZero();

        // 양성 대조군 — UTC 자정을 진짜로 넘기면 몫은 되살아나야 한다. 이게 없으면 「늘 거절」 구현도
        // 위 단언을 초록으로 통과한다.
        Instant nextUtcDay = Instant.parse("2026-09-14T00:30:00Z");
        assertThat(usageService.tryConsumeBoth(user, nextUtcDay, Kind.PLAN)).isEqualTo(Grant.OK);
        assertThat(usageService.remaining(user, nextUtcDay, Kind.PLAN)).isEqualTo(Kind.PLAN.max() - 1);
    }

    /**
     * 환불의 날짜 키도 소진과 <b>같은 UTC</b>인지 — 둘이 갈리면 환불이 <b>없는 행</b>을 깎아 조용히
     * 아무 일도 안 한다(장애로 사용자가 오늘 몫을 잃는다). 소진 쪽만 고치면 이 반쪽이 남는 자리다.
     *
     * <p>존을 UTC+14로 두는 것이 계측기의 핵심이다 — 서울 사용자로는 이 시각의 두 날짜가 같아서
     * <b>키를 어느 쪽으로 잡아도 초록</b>이 된다.
     */
    @Test
    @Transactional
    @DisplayName("환불도 같은 UTC 키를 쓴다 — 존이 UTC보다 앞선 사용자도 실패한 호출의 몫을 돌려받는다")
    void refundBothUsesTheSameUtcKey() {
        User user = register("usagetzr");
        user.updateProfile(user.getNickname(), "Pacific/Kiritimati"); // UTC+14
        userRepository.saveAndFlush(user);
        // 이 사람의 달력으로는 09-14, UTC로는 09-13 — 두 키가 갈린다.
        Instant now = Instant.parse("2026-09-13T14:30:00Z");

        assertThat(usageService.tryConsumeBoth(user, now, Kind.ANALYZE)).isEqualTo(Grant.OK);
        assertThat(usageService.remaining(user, now, Kind.ANALYZE)).isZero();

        usageService.refundBoth(user, now, Kind.ANALYZE); // 외부 호출 실패 — 그 몫은 사용자 것이 아니다

        assertThat(usageService.remaining(user, now, Kind.ANALYZE)).isEqualTo(Kind.ANALYZE.max());
        assertThat(usageService.tryConsumeBoth(user, now, Kind.ANALYZE)).isEqualTo(Grant.OK);
    }

    @Test
    @DisplayName("경합: 10스레드가 동시에 PLAN(max 3)을 집어도 정확히 3번만 통과한다")
    void concurrentConsume_lettsExactlyMaxThrough() throws Exception {
        User user = register("usage6");
        int threads = 10;
        int max = Kind.PLAN.max();

        AtomicInteger granted = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        if (usageService.tryConsume(user, DAY, Kind.PLAN)) {
                            granted.incrementAndGet();
                        }
                    } catch (Exception ignored) {
                        // 경합 중 예외로 죽은 스레드는 「통과」로 세지 않는다 — 아래 단언이 그만큼 모자라 실패한다
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(granted.get()).as("exactly %d of %d succeeded", max, threads).isEqualTo(max);
        assertThat(usageService.remaining(user, DAY, Kind.PLAN)).isZero();
    }
}
