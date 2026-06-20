package com.booktimer.push;

import com.booktimer.config.JpaConfig;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PushSubscriptionRepository 슬라이스 테스트 (@DataJpaTest, H2).
 *
 * <p>검증 항목:
 * <ul>
 *   <li>동일 endpoint 재구독 → upsert(행 1개)</li>
 *   <li>다른 endpoint 재구독 → 신규(한 사용자 멀티 디바이스 허용)</li>
 *   <li>endpoint unique — 다른 사용자가 같은 endpoint 구독 불가</li>
 *   <li>IDOR 차단 — findByUserAndEndpoint 는 본인 것만</li>
 *   <li>리마인더 대상 쿼리 — dailyReminderEnabled + 구독 보유 + cutoff</li>
 * </ul>
 */
@DataJpaTest
@Import(JpaConfig.class)
class PushSubscriptionRepositoryTest {

    @Autowired PushSubscriptionRepository subRepo;
    @Autowired UserRepository userRepo;

    private static final String EP1 = "https://fcm.googleapis.com/ep1";
    private static final String EP2 = "https://fcm.googleapis.com/ep2";
    private static final String P256 = "BNokZ9hFp5Q1blNxyz";
    private static final String AUTH = "secretauthABC";

    private User savedUser(String email) {
        return userRepo.save(
                User.of(email, "$2a$10$abcdefghijklmnopqrstuv", "테스터", "Asia/Seoul", Role.USER));
    }

    // --- subscribe upsert ---

    @Test
    @DisplayName("같은 endpoint 로 재구독하면 행이 1개만 존재한다(upsert)")
    void subscribe_sameEndpoint_upsert() {
        User user = savedUser("u1@test.com");
        subRepo.save(PushSubscription.of(user, EP1, P256, AUTH));

        // 같은 endpoint 기존 행 업데이트(updateKeys)
        PushSubscription existing = subRepo.findByEndpoint(EP1).orElseThrow();
        existing.updateKeys("newP256", "newAuth");
        subRepo.saveAndFlush(existing);

        assertThat(subRepo.findAll()).hasSize(1);
        assertThat(subRepo.findByEndpoint(EP1).orElseThrow().getP256dh()).isEqualTo("newP256");
    }

    @Test
    @DisplayName("같은 사용자가 다른 endpoint 로 구독하면 멀티 디바이스 허용(2행)")
    void subscribe_differentEndpoints_sameUser_allowed() {
        User user = savedUser("u2@test.com");
        subRepo.save(PushSubscription.of(user, EP1, P256, AUTH));
        subRepo.save(PushSubscription.of(user, EP2, P256, AUTH));

        assertThat(subRepo.findByUser(user)).hasSize(2);
    }

    // --- endpoint unique 제약 ---

    @Test
    @DisplayName("다른 사용자가 이미 등록된 endpoint 를 구독하면 unique 위반")
    void subscribe_duplicateEndpoint_crossUser_violation() {
        User u1 = savedUser("u3@test.com");
        User u2 = savedUser("u4@test.com");
        subRepo.save(PushSubscription.of(u1, EP1, P256, AUTH));
        subRepo.flush();

        assertThatThrownBy(() -> {
            subRepo.save(PushSubscription.of(u2, EP1, P256, AUTH));
            subRepo.flush();
        }).isInstanceOf(Exception.class); // unique constraint violation
    }

    // --- IDOR 차단 ---

    @Test
    @DisplayName("findByUserAndEndpoint 는 본인 구독만 반환한다(다른 사용자 endpoint 는 empty)")
    void findByUserAndEndpoint_idor() {
        User owner = savedUser("u5@test.com");
        User attacker = savedUser("u6@test.com");
        subRepo.save(PushSubscription.of(owner, EP1, P256, AUTH));

        Optional<PushSubscription> result = subRepo.findByUserAndEndpoint(attacker, EP1);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByUserAndEndpoint 는 본인 구독이면 반환한다")
    void findByUserAndEndpoint_ownSub() {
        User user = savedUser("u7@test.com");
        subRepo.save(PushSubscription.of(user, EP1, P256, AUTH));

        Optional<PushSubscription> result = subRepo.findByUserAndEndpoint(user, EP1);

        assertThat(result).isPresent();
    }

    // --- 리마인더 대상 쿼리 ---

    @Test
    @DisplayName("dailyReminderEnabled=true & 구독 있는 사용자가 cutoff 전에 발송 안 했으면 대상에 포함된다")
    void findReminderTargets_includesEligible() {
        User user = savedUser("u8@test.com");
        user.enableDailyReminder();
        userRepo.save(user);
        subRepo.save(PushSubscription.of(user, EP1, P256, AUTH));

        Instant cutoff = Instant.now().minusSeconds(100);
        List<User> targets = subRepo.findReminderTargetUsers(cutoff);

        assertThat(targets).extracting(User::getId).contains(user.getId());
    }

    @Test
    @DisplayName("오늘 이미 발송한 사용자(lastReminderSentAt > cutoff)는 대상에서 제외된다")
    void findReminderTargets_excludesAlreadySent() {
        User user = savedUser("u9@test.com");
        user.enableDailyReminder();
        user.recordReminderSent(Instant.now()); // 방금 발송
        userRepo.save(user);
        subRepo.save(PushSubscription.of(user, EP1, P256, AUTH));

        Instant cutoff = Instant.now().minusSeconds(3600); // 1시간 전 cutoff → 방금 발송 → 제외
        List<User> targets = subRepo.findReminderTargetUsers(cutoff);

        assertThat(targets).extracting(User::getId).doesNotContain(user.getId());
    }

    @Test
    @DisplayName("dailyReminderEnabled=false 인 사용자는 구독이 있어도 대상에서 제외된다")
    void findReminderTargets_excludesDisabledUsers() {
        User user = savedUser("u10@test.com");
        // dailyReminderEnabled 기본 false
        userRepo.save(user);
        subRepo.save(PushSubscription.of(user, EP1, P256, AUTH));

        Instant cutoff = Instant.now().minusSeconds(100);
        List<User> targets = subRepo.findReminderTargetUsers(cutoff);

        assertThat(targets).extracting(User::getId).doesNotContain(user.getId());
    }

    @Test
    @DisplayName("구독이 없는 사용자는 dailyReminderEnabled=true 여도 대상에서 제외된다")
    void findReminderTargets_excludesUsersWithNoSubscription() {
        User user = savedUser("u11@test.com");
        user.enableDailyReminder();
        userRepo.save(user);
        // 구독 없음

        Instant cutoff = Instant.now().minusSeconds(100);
        List<User> targets = subRepo.findReminderTargetUsers(cutoff);

        assertThat(targets).extracting(User::getId).doesNotContain(user.getId());
    }
}
