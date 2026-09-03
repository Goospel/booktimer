package com.booktimer.study;

import com.booktimer.study.StudyAiUsage.Kind;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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
