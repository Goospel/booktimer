package com.booktimer.blog;

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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 같은 글 행을 함께 쓰는 문들(계획 2026-11-11-b1-blog-core §4.11) — 비트랜잭션이라 요청마다 제 트랜잭션이 실제로 커밋된다
 * ({@code MeasureFinishRaceTest}와 같은 규율 · 같은 컨텍스트 키).
 *
 * <p>⚠️ 등록한 사용자가 커밋되므로 {@link #cleanUp()}이 운영 탈퇴 경로(purge)로 반드시 지운다.
 */
@SpringBootTest
class BlogPostRaceTest {

    private static final String LOGIN_ID = "blograce";
    private static final String PASSWORD = "pw1234qwer!!";
    private static final int RACERS = 8;

    @Autowired UserRegistrationService registrationService;
    @Autowired UserRepository userRepository;
    @Autowired AccountService accountService;
    @Autowired BlogPostService postService;
    @Autowired BlogPublishService publishService;
    @Autowired BlogPostRepository postRepository;
    @Autowired BlogSentenceRepository sentenceRepository;
    @Autowired PlatformTransactionManager txManager;

    @AfterEach
    void cleanUp() {
        try {
            accountService.deleteAccount(LOGIN_ID + "@booktimer.com", PASSWORD);
        } catch (Exception e) {
            // 등록에 실패했거나 이미 지워진 경우 — 정리가 테스트 결과를 가리면 안 된다.
        }
    }

    private User readyUser() {
        registrationService.register(LOGIN_ID + "@booktimer.com", PASSWORD, LOGIN_ID, "닉_" + LOGIN_ID, "Asia/Seoul",
                Role.USER, LocalDate.of(2026, 1, 1));
        User user = userRepository.findByLoginId(LOGIN_ID).orElseThrow();
        user.confirmBlogIdentity(Instant.now());
        return userRepository.save(user);
    }

    /**
     * 결정적 재현: 바깥 트랜잭션이 글 행을 먼저 읽고(자동저장 PUT) → 그 안 {@code REQUIRES_NEW}에서 올리기가 커밋 → 바깥이
     * 초안을 저장해 커밋. 전 열 UPDATE면 올리기가 쓴 제목 · 배치 · 올린 시각이 옛 스냅샷(null)으로 <b>오류 없이</b> 덮인다.
     */
    @Test
    @DisplayName("REQ-230 · 낡은 스냅샷의 초안 저장이 그사이 올린 제목 · 배치 · 올린 시각을 덮지 않는다")
    void staleDraftSaveDoesNotOverwritePublish() {
        User user = readyUser();
        long id = postService.create(user, "올린 제목", "첫 문장이다. 둘째 문장이다.").getId();

        TransactionTemplate outer = new TransactionTemplate(txManager);
        TransactionTemplate inner = new TransactionTemplate(txManager);
        inner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        outer.executeWithoutResult(status -> {
            postRepository.findByIdAndUser(id, user).orElseThrow(); // 자동저장이 먼저 읽은 스냅샷
            inner.executeWithoutResult(s -> publishService.publish(user, id, 0));
            postService.save(user, id, "고친 초안 제목", "첫 문장이다. 둘째 문장이다. 셋째.", 0);
        });

        BlogPost after = postRepository.findById(id).orElseThrow();
        assertThat(after.getTitle()).as("올린 제목").isEqualTo("올린 제목");
        assertThat(after.getLayoutJson()).as("배치").isNotNull();
        assertThat(after.getPublishedAt()).as("올린 시각").isNotNull();
        assertThat(after.getDraftTitle()).as("초안 저장도 들어갔다").isEqualTo("고친 초안 제목");
        assertThat(after.getDraftRevision()).isEqualTo(1);
    }

    /**
     * ⚠️ <b>음성 판정 전용</b>: H2는 READ COMMITTED라 통과가 잠금을 증명하지 않고, 실패는 잠금이 없음을 뜻한다
     * (「잠금 앞에 조회가 있는」 회귀 — MySQL 스냅숏 — 는 원리상 못 본다. 그 순서는 리뷰가 본다 · §4.11).
     */
    @Test
    @DisplayName("REQ-232 · 같은 글을 동시에 8번 올려도 문장 행은 한 벌")
    void concurrentPublishesStoreOnce() throws Exception {
        User user = readyUser();
        long id = postService.create(user, "제목", "첫 문장이다. 둘째 문장이다.").getId();

        ExecutorService pool = Executors.newFixedThreadPool(RACERS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(RACERS);
        Queue<Throwable> errors = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < RACERS; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    publishService.publish(user, id, 0);
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
        assertThat(sentenceRepository.findByPostId(id)).as("같은 올리기가 문장 행을 두 벌 이상 썼다 — 글 행 잠금이 없다").hasSize(2);
    }
}
