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

import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 호출의 <b>전역</b> 하루 상한 — 사용자당 상한 위에 얹은 차단기.
 *
 * <p>왜 있나: 사용자당 상한만으로는 <b>총액에 천장이 없다</b>. 계정 수에 비례해 비용이 늘고, 계정 수는
 * (가입 레이트리밋이 없어) 공격자가 정한다. 어드민이 뚫려 전원이 승인되는 시나리오에서 마지막으로
 * 남는 방어선이 이것이다(보안 리뷰 2026-09-08 S-1).
 *
 * <p>{@code StudyAiUsageServiceTest}와 같은 규율로 {@code @Transactional}을 <b>일부러 안 붙였다</b> —
 * 붙이면 「읽고 판단한 뒤 쓴다」(TOCTOU) 구현도 초록으로 통과해 경합 테스트가 무의미해진다.
 * 대신 그쪽이 <b>테스트마다 다른 사용자</b>로 격리하는 자리에서, 전역 카운터는 사용자가 없으므로
 * <b>테스트마다 다른 날짜</b>로 격리한다.
 */
@SpringBootTest(properties = "booktimer.study.ai.daily-cap=2")
class StudyAiGlobalCapTest {

    @Autowired StudyAiUsageService usageService;
    @Autowired StudyAiDailyTotalRepository totalRepository;
    @Autowired UserRegistrationService registrationService;
    @Autowired UserRepository userRepository;

    private User register(String loginId) {
        registrationService.register(loginId + "@booktimer.com", "pw1234qwer!!", loginId,
                "닉네임_" + loginId, "Asia/Seoul", Role.USER, LocalDate.of(2026, 1, 1));
        return userRepository.findByLoginId(loginId).orElseThrow();
    }

    /** 테스트마다 다른 날 — 전역 카운터는 사용자로 못 가르므로 날짜가 격리 단위다. */
    private static Instant utcNoonOf(int day) {
        return Instant.parse("2026-1%d-%02dT12:00:00Z".formatted(day / 100, day % 100));
    }

    @Test
    @DisplayName("상한을 넘으면 거절한다 — 사용자가 달라도 전역 몫은 하나다")
    void rejectsBeyondCap() {
        Instant now = utcNoonOf(101);

        assertThat(usageService.tryConsumeGlobal(now)).isTrue();
        assertThat(usageService.tryConsumeGlobal(now)).isTrue();
        assertThat(usageService.tryConsumeGlobal(now)).isFalse();
    }

    @Test
    @DisplayName("날이 바뀌면 몫이 되살아난다")
    void resetsNextDay() {
        assertThat(usageService.tryConsumeGlobal(utcNoonOf(102))).isTrue();
        assertThat(usageService.tryConsumeGlobal(utcNoonOf(102))).isTrue();
        assertThat(usageService.tryConsumeGlobal(utcNoonOf(102))).isFalse();

        assertThat(usageService.tryConsumeGlobal(utcNoonOf(103))).isTrue();
    }

    // ⚠️ 이 테스트가 이 파일의 존재 이유 절반이다. 사용자당 상한은 StudyDates.today(유저 타임존)를
    // 쓰는데, 타임존은 사용자가 제한 없이 바꿀 수 있어 같은 순간에 로컬 날짜가 둘이 된다(리뷰 S-3).
    // 전역 상한이 같은 키를 쓰면 **막으려는 것에 같은 구멍이 뚫린다** — 자정 근처에 타임존만 바꿔
    // 전역 몫을 두 배로 쓸 수 있다. 아래 두 시각은 UTC로는 같은 날, 서울(UTC+9)로는 다른 날이다.
    @Test
    @DisplayName("날짜 키는 UTC다 — 서울 기준으로 날이 갈리는 두 시각이 같은 몫을 나눠 쓴다")
    void dateKeyIsUtcNotUserTimezone() {
        Instant beforeSeoulMidnight = Instant.parse("2026-10-04T14:00:00Z"); // 서울 10-04 23:00
        Instant afterSeoulMidnight = Instant.parse("2026-10-04T16:00:00Z");  // 서울 10-05 01:00

        assertThat(usageService.tryConsumeGlobal(beforeSeoulMidnight)).isTrue();
        assertThat(usageService.tryConsumeGlobal(afterSeoulMidnight)).isTrue();
        // 서울 날짜로 키를 잡았다면 각자 새 카운터라 여기서도 true가 된다.
        assertThat(usageService.tryConsumeGlobal(afterSeoulMidnight)).isFalse();
    }

    @Test
    @DisplayName("환불하면 그 몫이 돌아온다 — 외부 장애로 그 날 예산을 잃지 않는다")
    void refundReturnsTheSlot() {
        Instant now = utcNoonOf(105);
        assertThat(usageService.tryConsumeGlobal(now)).isTrue();
        assertThat(usageService.tryConsumeGlobal(now)).isTrue();
        assertThat(usageService.tryConsumeGlobal(now)).isFalse();

        usageService.refundGlobal(now);

        assertThat(usageService.tryConsumeGlobal(now)).isTrue();
    }

    // 안 쓴 몫을 환불해 음수가 되면 그 날 상한이 조용히 늘어난다(공짜 호출).
    @Test
    @DisplayName("안 쓴 몫을 환불해도 음수가 되지 않는다")
    void refundDoesNotGoNegative() {
        Instant now = utcNoonOf(106);
        usageService.refundGlobal(now);
        usageService.refundGlobal(now);

        assertThat(usageService.tryConsumeGlobal(now)).isTrue();
        assertThat(usageService.tryConsumeGlobal(now)).isTrue();
        assertThat(usageService.tryConsumeGlobal(now)).isFalse();
    }

    // 사고 중에 배포 없이 전면 차단하는 킬 스위치 — 상한을 0으로 내리면 첫 요청부터 막힌다.
    // 서비스는 프로퍼티로 상한을 고정 주입받으므로 여기선 리포지터리에 직접 0을 준다.
    @Test
    @DisplayName("킬 스위치: 상한 0이면 첫 요청부터 막힌다")
    void killSwitchAtZero() {
        LocalDate day = LocalDate.of(2026, 10, 7);

        assertThat(totalRepository.consume(day, 0)).isZero();
        assertThat(totalRepository.consume(day, 0)).isZero();
    }

    // 순서가 「사용자 몫 → 전역 몫」이라는 것이 이 카운터의 **의미**를 정한다. 전역을 먼저 두면
    // 「내 하루 N+1번째 요청」 같은 흔한 거절이 매번 전역을 올렸다 되돌리고, 되돌리기를 빠뜨리면
    // 남의 몫까지 먹는다. 뒤에 두면 이 수가 「실제로 호출까지 갔을 요청」을 뜻한다 — 사고 분석 때
    // 보고 싶은 값이 그것이다.
    // ⚠️ 이 테스트<b>만</b> 트랜잭션이다. 클래스가 비트랜잭션인 것은 경합에서 TOCTOU 구현이 초록으로
    // 통과하지 않게 하려는 것인데, 여기는 경합을 재지 않는다. 반면 사용자를 커밋하면 그 행이 다른
    // 테스트 컨텍스트의 <b>사용자 집계</b>에 샌다 — 실제로 AdminStatsServiceTest의 「사용자당 평균」이
    // 1000 → 666(=2000/3)으로 깨졌다. 롤백으로 그 누수를 막는다.
    @Test
    @org.springframework.transaction.annotation.Transactional
    @DisplayName("사용자 몫이 소진되면 전역 카운터는 움직이지 않는다")
    void userExhaustedDoesNotTouchGlobal() {
        User user = register("globalorder");
        Instant now = utcNoonOf(109);

        // ANALYZE는 하루 1번 — 첫 번째만 통과하고 전역도 1만 쓴다.
        assertThat(usageService.tryConsumeBoth(user, now, Kind.ANALYZE)).isEqualTo(Grant.OK);
        assertThat(usageService.tryConsumeBoth(user, now, Kind.ANALYZE)).isEqualTo(Grant.USER_EXHAUSTED);

        // 상한이 2이므로 전역엔 정확히 한 자리가 남아 있어야 한다.
        assertThat(usageService.tryConsumeGlobal(now)).isTrue();
        assertThat(usageService.tryConsumeGlobal(now)).isFalse();
    }

    @Test
    @DisplayName("경합: 10스레드가 동시에 집어도 정확히 상한(2)만 통과한다")
    void concurrentConsumeLetsExactlyCapThrough() throws Exception {
        Instant now = utcNoonOf(108);
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger passed = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    if (usageService.tryConsumeGlobal(now)) {
                        passed.incrementAndGet();
                    }
                } catch (Exception ignored) {
                    // 경합에 진 INSERT는 서비스가 삼키고 재시도한다 — 여기선 통과 수만 센다.
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(passed.get()).isEqualTo(2);
    }
}
