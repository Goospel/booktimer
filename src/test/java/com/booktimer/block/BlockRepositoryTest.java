package com.booktimer.block;

import com.booktimer.config.JpaConfig;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BlockRepository 슬라이스 테스트 (@DataJpaTest, H2).
 *
 * <p>{@code findByBlockerOrderByCreatedAtDesc}의 {@code @EntityGraph}로 차단 행 조회 시 blocked User가
 * 즉시 초기화되는지 검증한다(차단 목록 조립의 lazy User ×N 제거). 1차 캐시가 가짜 GREEN을 내지 않도록
 * {@code em.flush()+clear()} 후 조회한다.
 */
@DataJpaTest
@Import(JpaConfig.class)
class BlockRepositoryTest {

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private BlockRepository blockRepository;

    @Autowired
    private UserRepository userRepository;

    private User persistedUser(String email) {
        return userRepository.save(
                User.of(email, "$2a$10$abcdefghijklmnopqrstuv", "책벌레", "Asia/Seoul", Role.USER));
    }

    @Test
    @DisplayName("findByBlockerOrderByCreatedAtDesc: blocked가 한 쿼리로 즉시 초기화된다 (N+1 없음)")
    void blockedUsersInitializeBlocked() {
        User blocker = persistedUser("blocker@booktimer.com");
        User target = persistedUser("target@booktimer.com");
        blockRepository.save(Block.of(blocker, target));

        em.flush();
        em.clear();

        List<Block> rows = blockRepository.findByBlockerOrderByCreatedAtDesc(blocker);

        assertThat(rows).hasSize(1);
        assertThat(Hibernate.isInitialized(rows.get(0).getBlocked())).isTrue();
    }
}
