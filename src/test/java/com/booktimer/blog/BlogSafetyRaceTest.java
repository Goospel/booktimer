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
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    @Autowired BlogReportService reportService;
    @Autowired BlogModerationService moderation;
    @Autowired BlogPostRepository postRepository;
    @Autowired BlogCommentRepository commentRepository;
    @Autowired BlogTargets targets;
    @Autowired PlatformTransactionManager txManager;
    @Autowired JdbcTemplate jdbc;

    /** 바깥 트랜잭션(운영자 처리) 안에서 그 사이 다른 요청이 커밋하는 것을 결정적으로 끼운다 — 스레드 경합 없이. */
    private void interleaved(Runnable read, Runnable meanwhile, Runnable then) {
        TransactionTemplate inner = new TransactionTemplate(txManager);
        inner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        new TransactionTemplate(txManager).executeWithoutResult(s -> {
            read.run();
            inner.executeWithoutResult(s2 -> meanwhile.run());
            then.run();
        });
    }

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

    @Test
    @DisplayName("처리방침 5절 · 신고가 대상 글쓴이를 읽은 뒤 그 사람의 탈퇴가 커밋되면 저장은 404 — 탈퇴한 사람 id를 지닌 신고 행 0")
    void reportAfterTargetPurgeLeavesNoTargetUser() {
        User author = readyUser("bsrauthor");
        User reporter = readyUser("bsrreporter");
        long post = publishedPost(author);
        long authorId = author.getId();
        Object[] outcome = new Object[1];

        try {
            interleaved(() -> assertThat(targets.publicAuthor(BlogReport.Target.POST, post)).isPresent(), // 양성 대조 — 읽을 땐 있다
                    () -> accountService.deleteAccount("bsrauthor@booktimer.com", PASSWORD),
                    () -> reportService.report(reporter, BlogReport.Target.POST, post, "SPAM", null));
        } catch (RuntimeException e) {
            outcome[0] = e;
        }

        assertThat(jdbc.queryForObject("select count(*) from blog_report where target_user_id = ?", Integer.class, authorId))
                .as("탈퇴한 사람 id가 신고 행에 남았다").isZero();
        assertThat(outcome[0]).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
    }

    @Test
    @DisplayName("처리방침 5절 · 운영자가 신고를 읽은 뒤 대상 글쓴이의 탈퇴 정리가 target_user_id를 비우면 resolve가 옛 값으로 되쓰지 않는다")
    void resolveKeepsClearedTargetUser() {
        User author = readyUser("bsrauthor");
        User reporter = readyUser("bsrreporter");
        long post = publishedPost(author);
        reportService.report(reporter, BlogReport.Target.POST, post, "SPAM", null);
        long reportId = jdbc.queryForObject("select id from blog_report where target_id = ? and reporter_id = ?", Long.class, post,
                reporter.getId());
        String targetUser = "select target_user_id from blog_report where id = ?";
        assertThat(jdbc.queryForObject(targetUser, Long.class, reportId)).isEqualTo(author.getId()); // 양성 대조

        interleaved(() -> reportRepository.findById(reportId).orElseThrow(),
                () -> reportRepository.clearTargetUser(author.getId()),
                () -> moderation.resolve(reportId, null, null, null, null));

        assertThat(jdbc.queryForObject("select status from blog_report where id = ?", String.class, reportId)).isEqualTo("RESOLVED");
        assertThat(jdbc.queryForObject(targetUser, Long.class, reportId)).as("탈퇴로 비운 글쓴이 id가 되살아났다").isNull();
    }

    @Test
    @DisplayName("REQ-338 · 운영자가 댓글을 읽은 뒤 글쓴이가 그 댓글을 지우면 처리 커밋은 OptimisticLockingFailureException(관리자 문이 flash로 바꾸는 종류)")
    void moderatingDeletedCommentFailsOptimistically() {
        User author = readyUser("bsrauthor");
        long post = publishedPost(author);
        long comment = commentRepository.saveAndFlush(BlogComment.of(postRepository.findById(post).orElseThrow(), "AAAAAAAA", author, "댓글"))
                .getId();

        assertThatThrownBy(() -> interleaved(() -> commentRepository.findById(comment).orElseThrow(),
                () -> jdbc.update("delete from blog_comment where id = ?", comment),
                () -> moderation.apply(BlogModerationService.Unit.COMMENT, comment, Moderation.Action.POLICY, null)))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }
}
