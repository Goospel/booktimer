package com.booktimer.blog;

import com.booktimer.block.BlockRepository;
import com.booktimer.user.AccountService;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import com.booktimer.user.UserRepository;
import com.booktimer.web.api.AppBlockApiController;
import com.booktimer.web.api.AppBlogApiController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 신고 · 차단 두 번 누름(계획 §4.4 · §4.7) — 비트랜잭션이라 요청마다 제 트랜잭션이 실제로 커밋된다({@code BlogPostRaceTest}와 같은
 * 컨텍스트 키 · MockMvc를 붙이면 새 컨텍스트라 컨트롤러 빈을 직접 부른다). 유일 위반은 컨트롤러가 서비스 트랜잭션 <b>밖</b>에서 잡아
 * 같은 200으로 바꾼다.
 *
 * <p>⚠️ <b>음성 판정 전용</b>: 스레드가 우연히 줄을 서면 충돌 없이 초록이다 — 충돌이 실제로 났는지는 세지 않는다. 행 1 단언은
 * 겹쳤을 때 엔티티의 유일 제약 누락을 잡는다. ⚠️ 등록한 사용자는 {@link #cleanUp()}이 운영 탈퇴 경로(purge)로 지운다.
 */
@SpringBootTest
class BlogSafetyRaceTest {

    private static final String PASSWORD = "pw1234qwer!!";
    private static final List<String> IDS = List.of("bsrauthor", "bsrreporter");
    private static final int RACERS = 8;

    @Autowired UserRegistrationService registrationService;
    @Autowired UserRepository userRepository;
    @Autowired AccountService accountService;
    @Autowired BlogPostService postService;
    @Autowired BlogPublishService publishService;
    @Autowired BlogReportRepository reportRepository;
    @Autowired BlockRepository blockRepository;
    @Autowired AppBlogApiController blogApi;
    @Autowired AppBlockApiController blockApi;

    @AfterEach
    void cleanUp() {
        for (String id : IDS) {
            try {
                accountService.deleteAccount(id + "@booktimer.com", PASSWORD);
            } catch (Exception e) {
                // 등록에 실패했거나 이미 지워진 경우 — 정리가 시험 결과를 가리면 안 된다.
            }
        }
    }

    private User readyUser(String loginId) {
        registrationService.register(loginId + "@booktimer.com", PASSWORD, loginId, "닉_" + loginId, "Asia/Seoul", Role.USER,
                LocalDate.of(2026, 1, 1));
        User user = userRepository.findByLoginId(loginId).orElseThrow();
        user.confirmBlogIdentity(Instant.now());
        return userRepository.save(user);
    }

    private long publishedPost(User author) {
        long id = postService.create(author, "제목", "첫 문장이다.").getId();
        publishService.publish(author, id, 0);
        return id;
    }

    private Queue<Object> race(Consumer<Integer> call) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(RACERS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(RACERS);
        Queue<Object> outcomes = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < RACERS; i++) {
            int n = i;
            pool.submit(() -> {
                try {
                    start.await();
                    call.accept(n);
                    outcomes.add("ok");
                } catch (Throwable t) {
                    outcomes.add(t);
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();
        return outcomes;
    }

    @Test
    @DisplayName("REQ-252 · 같은 (신고자, 대상)을 8스레드가 동시에 보내도 전부 200 · 행 1")
    void concurrentReportsAreIdempotent() throws Exception {
        User author = readyUser("bsrauthor");
        readyUser("bsrreporter");
        long post = publishedPost(author);
        AppBlogApiController.ReportRequest body = new AppBlogApiController.ReportRequest("POST", post, "SPAM", null);

        Queue<Object> outcomes = race(n -> {
            ResponseEntity<Void> res = blogApi.report(body, () -> "bsrreporter");
            assertThat(res.getStatusCode().value()).isEqualTo(200);
        });

        assertThat(outcomes).as("유일 위반이 500으로 샜다 — 컨트롤러가 서비스 밖에서 잡지 않는다").containsOnly("ok");
        assertThat(reportRepository.findAll()).filteredOn(r -> r.getTargetId().equals(post)).hasSize(1);
    }

    @Test
    @DisplayName("REQ-254 · 같은 글쓴이를 8스레드가 동시에 차단해도 전부 200 · 차단 행 1")
    void concurrentBlocksAreIdempotent() throws Exception {
        User author = readyUser("bsrauthor");
        User me = readyUser("bsrreporter");
        long post = publishedPost(author);
        AppBlockApiController.BlockRequest body = new AppBlockApiController.BlockRequest("POST", post);

        Queue<Object> outcomes = race(n -> assertThat(blockApi.block(body, () -> "bsrreporter").userId()).isEqualTo(author.getId()));

        assertThat(outcomes).as("유일 위반이 500으로 샜다").containsOnly("ok");
        assertThat(blockRepository.findByBlockerOrderByCreatedAtDesc(me)).hasSize(1);
    }
}
