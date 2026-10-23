package com.booktimer.user;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 구글 신원 고정(REQ-115 · 116)의 웹 {@code provision} 경로 — 실제 빈 + H2. 열 값은 JDBC로 직접 읽는다
 * ({@code pinGoogleSubIfEmpty}가 영속성 컨텍스트를 비우므로 엔티티 인스턴스는 낡을 수 있다).
 *
 * <p>테스트 세계는 {@link OAuthPreHijackingIntegrationTest}와 같다({@code @SpringBootTest} + {@code @Transactional}) — 새 컨텍스트 0.
 */
@SpringBootTest
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class GoogleSubPinningIntegrationTest {

    private static final String PINNED_LOG = "google sub pinned";

    @Autowired
    private OAuthUserProvisioningService provisioningService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BookRepository bookRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("REQ-115 · 기존 계정을 처음 열면 google_sub만 채우고 두 번째엔 쓰지 않는다")
    void existingAccount_firstOpenPinsOnlyGoogleSub_secondWritesNothing(CapturedOutput output) {
        User google = googleUser("first@booktimer.com", "firstopen");
        Map<String, Object> before = rowWithoutGoogleSub(google.getId());

        User result = provisioningService.provision("sub-first-1", "first@booktimer.com", null, "다른 이름", true);

        assertThat(result.getId()).isEqualTo(google.getId());
        assertThat(googleSubOf(google.getId())).isEqualTo("sub-first-1");
        assertThat(rowWithoutGoogleSub(google.getId())).isEqualTo(before); // 다른 열 그대로
        assertThat(output).contains(PINNED_LOG + " userId=" + google.getId());

        User again = provisioningService.provision("sub-first-1", "first@booktimer.com", null, "다른 이름", true);

        assertThat(again.getId()).isEqualTo(google.getId());
        assertThat(googleSubOf(google.getId())).isEqualTo("sub-first-1");
        assertThat(rowWithoutGoogleSub(google.getId())).isEqualTo(before);
        assertThat(countOccurrences(output.getOut(), PINNED_LOG)).isEqualTo(1); // 두 번째는 sub로 열려 쓰기 0
    }

    @Test
    @DisplayName("REQ-115 · 새 구글 계정은 만들 때 고정된다")
    void newGoogleAccount_isPinnedOnCreation() {
        User created = provisioningService.provision("sub-new-1", "brandnew@booktimer.com", null, "새사람", true);

        assertThat(created.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(googleSubOf(created.getId())).isEqualTo("sub-new-1");

        // 정책 ①(미검증 LOCAL 폐기)로 새로 만든 계정도 고정된다.
        User squatter = userRepository.saveAndFlush(User.of("squat@booktimer.com", "hash", "선점자", "Asia/Seoul", Role.USER));
        User replaced = provisioningService.provision("sub-new-2", "squat@booktimer.com", null, "진짜주인", true);

        assertThat(replaced.getId()).isNotEqualTo(squatter.getId());
        assertThat(userRepository.findById(squatter.getId())).isEmpty();
        assertThat(googleSubOf(replaced.getId())).isEqualTo("sub-new-2");
    }

    @Test
    @DisplayName("REQ-115 · 불일치면 계정·기록·고정·행 수가 그대로다(같은 sub의 양성 대조는 연다)")
    void mismatch_changesNothing_sameSubOpens() {
        User owner = googleUser("owner@booktimer.com", "pinnedowner");
        bookRepository.saveAndFlush(Book.register(owner, "읽던 책", null, null, null, null, null, BookStatus.READING));
        pinByJdbc(owner.getId(), "sub-owner");

        // 양성 대조 — 고정된 sub로는 연다.
        assertThat(provisioningService.provision("sub-owner", "owner@booktimer.com", null, "주인", true).getId())
                .isEqualTo(owner.getId());

        Map<String, Object> before = rowWithoutGoogleSub(owner.getId());
        long users = userRepository.count();

        assertThatThrownBy(() -> provisioningService.provision("sub-intruder", "owner@booktimer.com", null, "침입자", true))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                        e -> assertThat(e.getError().getErrorCode()).isEqualTo("google_account_mismatch"));

        assertThat(googleSubOf(owner.getId())).isEqualTo("sub-owner");
        assertThat(rowWithoutGoogleSub(owner.getId())).isEqualTo(before);
        assertThat(userRepository.count()).isEqualTo(users);
        assertThat(bookRepository.countByUser(userRepository.findById(owner.getId()).orElseThrow())).isEqualTo(1);
    }

    @Test
    @DisplayName("REQ-115 · 고정된 sub로 다른 이메일이 오면 그 계정을 열고 새 이메일의 미검증 계정은 건드리지 않는다")
    void pinnedSubWithOtherEmail_opensPinnedAccount_leavesOtherEmailAlone() {
        User pinned = googleUser("old-address@booktimer.com", "movedmail");
        pinByJdbc(pinned.getId(), "sub-moved");
        User unverifiedLocal = userRepository.saveAndFlush(
                User.of("new-address@booktimer.com", "hash", "선점자", "Asia/Seoul", Role.USER));
        long users = userRepository.count();

        User result = provisioningService.provision("sub-moved", "new-address@booktimer.com", null, "주인", true);

        assertThat(result.getId()).isEqualTo(pinned.getId());
        assertThat(userRepository.findById(unverifiedLocal.getId())).isPresent(); // 정책 ① 0
        assertThat(userRepository.count()).isEqualTo(users);
        assertThat(jdbcTemplate.queryForObject("select email from users where id = ?", String.class, pinned.getId()))
                .isEqualTo("old-address@booktimer.com"); // 저장 이메일은 바꾸지 않는다
    }

    @Test
    @DisplayName("REQ-115 · 고정은 비어 있을 때만 쓴다 — 값이 이미 있으면 0행이고 값이 그대로다")
    void pinGoogleSubIfEmpty_writesOnlyWhenEmpty() {
        User taken = googleUser("taken@booktimer.com", "takenpin");
        pinByJdbc(taken.getId(), "sub-other");
        User empty = googleUser("empty@booktimer.com", "emptypin");

        assertThat(userRepository.pinGoogleSubIfEmpty(taken.getId(), "sub-mine")).isZero();
        assertThat(googleSubOf(taken.getId())).isEqualTo("sub-other");

        assertThat(userRepository.pinGoogleSubIfEmpty(empty.getId(), "sub-mine")).isEqualTo(1); // 양성 대조
        assertThat(googleSubOf(empty.getId())).isEqualTo("sub-mine");
    }

    @Test
    @DisplayName("REQ-116 · 합성 도메인 이메일은 미검증과 같이 거부하고 그 주소의 계정을 열지도 만들지도 않는다")
    void syntheticDomainEmail_rejected_accountNeitherOpenedNorCreated() {
        // 양성 대조 — 같은 세계에서 보통 주소는 연다.
        User normal = googleUser("normal@booktimer.com", "normaluser");
        assertThat(provisioningService.provision("sub-normal", "normal@booktimer.com", null, "보통", true).getId())
                .isEqualTo(normal.getId());

        // 실제 Apple 계정의 주소(합성 도메인 — 미검증). 지금은 그 도메인이 DNS에 없을 뿐이다.
        User apple = userRepository.saveAndFlush(User.ofOAuth(
                "apple-001.abc@noreply.booktimer.app", "독자", "Asia/Seoul", Role.USER, AuthProvider.APPLE));
        Map<String, Object> before = rowWithoutGoogleSub(apple.getId());
        long users = userRepository.count();

        for (String email : new String[]{"apple-001.abc@noreply.booktimer.app", "APPLE-001.ABC@NOREPLY.BOOKTIMER.APP"}) {
            assertThatThrownBy(() -> provisioningService.provision("sub-attacker", email, null, "공격자", true))
                    .isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                            e -> assertThat(e.getError().getErrorCode()).isEqualTo("synthetic_email"));
        }

        assertThat(userRepository.count()).isEqualTo(users);
        assertThat(googleSubOf(apple.getId())).isNull();
        assertThat(rowWithoutGoogleSub(apple.getId())).isEqualTo(before);
    }

    @Test
    @DisplayName("REQ-116 · 구글이 보증하지 않는 주소의 이메일 가입 계정은 웹 구글 로그인으로 열리고 고정되지 않는다(hd 토큰이면 고정된다)")
    void unauthorizedAddressLocal_opensWithoutPin_hdTokenPins(CapturedOutput output) {
        User local = User.of("me@example.com", "hash", "주인", "Asia/Seoul", Role.USER);
        local.assignLoginId("examplelocal");
        local.verifyEmail();
        local = userRepository.saveAndFlush(local);

        User opened = provisioningService.provision("sub-ex-1", "me@example.com", null, "주인", true);

        assertThat(opened.getId()).isEqualTo(local.getId()); // 웹 회귀 0 — 그 계정으로 로그인된다
        assertThat(googleSubOf(local.getId())).isNull();
        assertThat(output).doesNotContain(PINNED_LOG);

        // 양성 대조 — 같은 계정에 hd(워크스페이스)가 실린 토큰이면 고정된다.
        User pinned = provisioningService.provision("sub-ex-1", "me@example.com", "example.com", "주인", true);

        assertThat(pinned.getId()).isEqualTo(local.getId());
        assertThat(googleSubOf(local.getId())).isEqualTo("sub-ex-1");
        assertThat(output).contains(PINNED_LOG + " userId=" + local.getId());
    }

    private User googleUser(String email, String loginId) {
        User u = User.ofOAuth(email, "구글러", "Asia/Seoul", Role.USER, AuthProvider.GOOGLE);
        u.verifyEmail();
        u.assignLoginId(loginId);
        return userRepository.saveAndFlush(u);
    }

    /** 픽스처 고정 — JDBC로 쓰므로 1차 캐시의 낡은 엔티티가 그 값을 가리지 않게 영속성 컨텍스트를 비운다. */
    private void pinByJdbc(Long id, String sub) {
        jdbcTemplate.update("update users set google_sub = ? where id = ?", sub, id);
        entityManager.clear();
    }

    private String googleSubOf(Long id) {
        return jdbcTemplate.queryForObject("select google_sub from users where id = ?", String.class, id);
    }

    private Map<String, Object> rowWithoutGoogleSub(Long id) {
        Map<String, Object> row = new HashMap<>(jdbcTemplate.queryForMap("select * from users where id = ?", id));
        row.keySet().removeIf(k -> k.equalsIgnoreCase("google_sub"));
        return row;
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) {
            count++;
        }
        return count;
    }
}
