package com.booktimer.user;

import com.booktimer.block.Block;
import com.booktimer.block.BlockRepository;
import com.booktimer.config.JpaConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UserRepository 슬라이스 테스트 (@DataJpaTest, H2 인메모리).
 *
 * <p>저장/조회, email 기반 조회, email 유니크 제약(uk_users_email)을 검증한다.
 * 운영은 MySQL이지만 슬라이스 테스트는 H2로 도커 없이 독립 실행.
 */
@DataJpaTest
@Import(JpaConfig.class) // BaseTimeEntity auditing(created_at/updated_at) 활성화 — 없으면 INSERT 시 NOT NULL 위반
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BlockRepository blockRepository;

    private User sampleUser(String email) {
        return User.of(email, "$2a$10$abcdefghijklmnopqrstuv", "책벌레", "Asia/Seoul", Role.USER);
    }

    private User sampleUser(String email, String nickname) {
        return User.of(email, "$2a$10$abcdefghijklmnopqrstuv", nickname, "Asia/Seoul", Role.USER);
    }

    /** 적격 후보 픽스처 — loginId 확정, Role.USER */
    private User candidate(String email, String loginId) {
        User u = User.of(email, "$2a$10$abcdefghijklmnopqrstuv", "닉네임", "Asia/Seoul", Role.USER);
        u.assignLoginId(loginId);
        return userRepository.save(u);
    }

    /** 운영자 픽스처 */
    private User adminUser(String email, String loginId) {
        User u = User.of(email, "$2a$10$abcdefghijklmnopqrstuv", "운영자", "Asia/Seoul", Role.ADMIN);
        u.assignLoginId(loginId);
        return userRepository.save(u);
    }

    /** OAuth 온보딩 전 픽스처 — loginId=null */
    private User oauthPending(String email) {
        return userRepository.save(
                User.ofOAuth(email, "구글이름", "Asia/Seoul", Role.USER, AuthProvider.GOOGLE));
    }

    @Test
    @DisplayName("저장 후 email로 조회된다 (id 부여 확인)")
    void save_thenFindByEmail() {
        userRepository.save(sampleUser("reader@booktimer.com"));

        Optional<User> found = userRepository.findByEmail("reader@booktimer.com");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isNotNull();
        assertThat(found.get().getNickname()).isEqualTo("책벌레");
        assertThat(found.get().getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("없는 email 조회는 Optional.empty")
    void findByEmail_absent_empty() {
        Optional<User> found = userRepository.findByEmail("none@booktimer.com");

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("같은 email 중복 저장은 유니크 제약 위반")
    void duplicateEmail_violatesUnique() {
        userRepository.saveAndFlush(sampleUser("dup@booktimer.com"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(sampleUser("dup@booktimer.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 nickname 중복 저장은 허용된다 (nickname은 더 이상 유니크가 아님 — 공개 핸들은 login_id)")
    void duplicateNickname_allowed() {
        userRepository.saveAndFlush(sampleUser("a@booktimer.com", "같은닉"));

        // V14에서 uk_users_nickname 제거 → 표시 이름 중복은 정상. 식별/검색은 login_id가 담당.
        User second = userRepository.saveAndFlush(sampleUser("b@booktimer.com", "같은닉"));

        assertThat(second.getId()).isNotNull();
        assertThat(userRepository.findByEmail("a@booktimer.com")).isPresent();
        assertThat(userRepository.findByEmail("b@booktimer.com")).isPresent();
    }

    // --- findRecommendCandidates: DB 필터 절 단위 검증 (N+1 제거 — PR-C) ---
    // 랜덤 순서라 순서 단언 금지 — 집합 멤버십(contains/doesNotContain)과 size로만 단언.

    @Test
    @DisplayName("findRecommendCandidates: ADMIN 사용자는 결과에 없다")
    void findRecommendCandidates_excludesAdmin() {
        User viewer = candidate("viewer@booktimer.com", "viewer1");
        candidate("normal@booktimer.com", "normal1");
        adminUser("admin@booktimer.com", "admin1");

        List<User> result = userRepository.findRecommendCandidates(viewer.getId(), PageRequest.of(0, 50));

        assertThat(result).extracting(User::getLoginId)
                .contains("normal1")
                .doesNotContain("admin1");
    }

    @Test
    @DisplayName("findRecommendCandidates: viewer 본인은 결과에 없다")
    void findRecommendCandidates_excludesSelf() {
        User viewer = candidate("viewer@booktimer.com", "viewer1");
        candidate("other@booktimer.com", "other1");

        List<User> result = userRepository.findRecommendCandidates(viewer.getId(), PageRequest.of(0, 50));

        assertThat(result).extracting(User::getLoginId)
                .contains("other1")
                .doesNotContain("viewer1");
    }

    @Test
    @DisplayName("findRecommendCandidates: login_id=null(OAuth 온보딩 전) 사용자는 결과에 없다")
    void findRecommendCandidates_excludesNullLoginId() {
        User viewer = candidate("viewer@booktimer.com", "viewer1");
        candidate("normal@booktimer.com", "normal1");
        oauthPending("pending@booktimer.com");

        List<User> result = userRepository.findRecommendCandidates(viewer.getId(), PageRequest.of(0, 50));

        assertThat(result).extracting(User::getLoginId)
                .contains("normal1")
                .doesNotContainNull();
    }

    @Test
    @DisplayName("findRecommendCandidates: viewer가 차단한 사용자는 결과에 없다")
    void findRecommendCandidates_excludesBlocked_viewerBlocked() {
        User viewer = candidate("viewer@booktimer.com", "viewer1");
        candidate("eligible@booktimer.com", "eligible1");
        User blocked = candidate("blocked@booktimer.com", "blocked1");
        blockRepository.save(Block.of(viewer, blocked));

        List<User> result = userRepository.findRecommendCandidates(viewer.getId(), PageRequest.of(0, 50));

        assertThat(result).extracting(User::getLoginId)
                .contains("eligible1")
                .doesNotContain("blocked1");
    }

    @Test
    @DisplayName("findRecommendCandidates: viewer를 차단한 사용자도 결과에 없다(역방향 차단)")
    void findRecommendCandidates_excludesBlocked_blockedViewer() {
        User viewer = candidate("viewer@booktimer.com", "viewer1");
        candidate("eligible@booktimer.com", "eligible1");
        User blocker = candidate("blocker@booktimer.com", "blocker1");
        blockRepository.save(Block.of(blocker, viewer));

        List<User> result = userRepository.findRecommendCandidates(viewer.getId(), PageRequest.of(0, 50));

        assertThat(result).extracting(User::getLoginId)
                .contains("eligible1")
                .doesNotContain("blocker1");
    }

    @Test
    @DisplayName("findRecommendCandidates: 적격 사용자(일반·loginId 있음·차단 없음·본인 아님)는 포함된다")
    void findRecommendCandidates_includesEligible() {
        User viewer = candidate("viewer@booktimer.com", "viewer1");
        candidate("eligible@booktimer.com", "eligible1");

        List<User> result = userRepository.findRecommendCandidates(viewer.getId(), PageRequest.of(0, 50));

        assertThat(result).extracting(User::getLoginId)
                .containsExactlyInAnyOrder("eligible1");
    }

    @Test
    @DisplayName("findRecommendCandidates: Pageable 상한만큼만 반환된다")
    void findRecommendCandidates_respectsLimit() {
        User viewer = candidate("viewer@booktimer.com", "viewer1");
        candidate("e1@booktimer.com", "eligible1");
        candidate("e2@booktimer.com", "eligible2");
        candidate("e3@booktimer.com", "eligible3");
        candidate("e4@booktimer.com", "eligible4");

        List<User> result = userRepository.findRecommendCandidates(viewer.getId(), PageRequest.of(0, 2));

        assertThat(result).hasSize(2);
    }
}
