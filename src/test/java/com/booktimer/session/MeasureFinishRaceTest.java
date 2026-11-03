package com.booktimer.session;

import com.booktimer.user.AccountService;
import com.booktimer.user.Role;
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
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 앱 마감의 <b>경합</b> — 같은 마감이 동시에 여러 번 와도(응답 유실 뒤 재전송 · 두 경로의 보내기) 행이 한 벌인지 잰다.
 *
 * <p>비트랜잭션이라 요청마다 제 트랜잭션이 실제로 커밋된다({@code StudyAiApprovalRaceTest}와 같은 규율 —
 * {@code @Transactional} 테스트였다면 모든 호출이 한 트랜잭션에 묶여 잠금 없는 구현도 초록이다).
 *
 * <p>⚠️ <b>음성 판정 전용(MySQL 스냅숏)</b>: H2는 READ COMMITTED라 「잠금 앞에 조회가 있는」 회귀를 못 잡는다 —
 * 그 순서는 서비스 단위의 구조 테스트(첫 저장소 호출 = 잠금)가 지킨다. 이 테스트가 재는 것은 「잠금이 아예 없다」뿐이다.
 *
 * <p>⚠️ 등록한 사용자가 커밋되므로 {@link #cleanUp()}이 운영 탈퇴 경로(purge)로 반드시 지운다.
 */
@SpringBootTest
class MeasureFinishRaceTest {

    private static final Instant NOW = Instant.parse("2026-11-02T03:00:00Z");
    private static final int RACERS = 8;
    private static final String LOGIN_ID = "finishrace";
    private static final String PASSWORD = "pw1234qwer!!";

    @Autowired ReadingSessionService readingService;
    @Autowired ReadingSessionRepository sessionRepository;
    @Autowired UserRegistrationService registrationService;
    @Autowired UserRepository userRepository;
    @Autowired AccountService accountService;

    @AfterEach
    void cleanUp() {
        try {
            accountService.deleteAccount(LOGIN_ID + "@booktimer.com", PASSWORD);
        } catch (Exception e) {
            // 등록에 실패했거나 이미 지워진 경우 — 정리가 테스트 결과를 가리면 안 된다.
        }
    }

    @Test
    @DisplayName("REQ-355 · 같은 마감을 동시에 8번 보내도 행은 한 벌")
    void concurrentFinishesStoreOnce() throws Exception {
        registrationService.register(LOGIN_ID + "@booktimer.com", PASSWORD, LOGIN_ID, "닉네임_" + LOGIN_ID,
                "Asia/Seoul", Role.USER, LocalDate.of(2026, 1, 1));
        User user = userRepository.findByLoginId(LOGIN_ID).orElseThrow();
        Instant s = NOW.minusSeconds(2 * 3600);
        Instant e = NOW.minusSeconds(3600);

        ExecutorService pool = Executors.newFixedThreadPool(RACERS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(RACERS);
        Queue<Throwable> errors = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < RACERS; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    readingService.finish(user, s, e, null, NOW);
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(errors).as("잠금 대기 중 실패한 요청이 있다 — 그러면 「한 벌」이 경합을 재지 못한 채 초록이다").isEmpty();
        List<ReadingSession> rows = sessionRepository.findByUser(user);
        assertThat(rows)
                .as("같은 마감이 두 벌 이상 적혔다 — 사용자 잠금이 없으면 동시 요청이 같은 빈 바쁜 구간을 읽는다")
                .singleElement()
                .satisfies(r -> assertThat(r.getDurationSeconds()).isEqualTo(3600L));
    }
}
