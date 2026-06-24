package com.booktimer.garden;

import com.booktimer.config.JpaConfig;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AuthorAffectionRepository 슬라이스 테스트 (@DataJpaTest, H2 인메모리).
 *
 * <p>upsert·unique(user,code)·sumFeedCountByUser(없으면 0) 검증.
 */
@DataJpaTest
@Import(JpaConfig.class)
class AuthorAffectionRepositoryTest {

    @Autowired
    private AuthorAffectionRepository affectionRepository;

    @Autowired
    private UserRepository userRepository;

    private User persistedUser(String email) {
        return userRepository.save(
                User.of(email, "$2a$10$abcdefghijklmnopqrstuv", "독자", "Asia/Seoul", Role.USER));
    }

    @Test
    @DisplayName("저장 후 findByUser로 조회된다")
    void save_thenFindByUser() {
        User user = persistedUser("affection-test@booktimer.com");
        affectionRepository.save(AuthorAffection.create(user, "han_gang"));

        List<AuthorAffection> found = affectionRepository.findByUser(user);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).getCharacterCode()).isEqualTo("han_gang");
        assertThat(found.get(0).getFeedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("findByUserAndCharacterCode — 있으면 반환, 없으면 empty")
    void findByUserAndCode_presenceAndAbsence() {
        User user = persistedUser("affection-code@booktimer.com");
        affectionRepository.save(AuthorAffection.create(user, "han_gang"));

        Optional<AuthorAffection> found = affectionRepository.findByUserAndCharacterCode(user, "han_gang");
        Optional<AuthorAffection> missing = affectionRepository.findByUserAndCharacterCode(user, "unknown");

        assertThat(found).isPresent();
        assertThat(missing).isEmpty();
    }

    @Test
    @DisplayName("같은 (user, code) 중복 저장 시 unique 위반")
    void duplicateUserCode_violatesUnique() {
        User user = persistedUser("affection-dup@booktimer.com");
        affectionRepository.saveAndFlush(AuthorAffection.create(user, "han_gang"));

        assertThatThrownBy(() ->
                affectionRepository.saveAndFlush(AuthorAffection.create(user, "han_gang")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("기록 없는 user의 sumFeedCountByUser = 0")
    void sumFeedCount_noRows_returnsZero() {
        User user = persistedUser("affection-zero@booktimer.com");

        long sum = affectionRepository.sumFeedCountByUser(user);

        assertThat(sum).isEqualTo(0L);
    }

    @Test
    @DisplayName("여러 캐릭터 먹인 합계 반환")
    void sumFeedCount_multipleRows_returnsSum() {
        User user = persistedUser("affection-sum@booktimer.com");
        AuthorAffection a1 = AuthorAffection.create(user, "han_gang");
        a1.incrementFeedCount(); // feedCount=2
        affectionRepository.save(a1);

        AuthorAffection a2 = AuthorAffection.create(user, "kim_young_ha"); // feedCount=1
        affectionRepository.save(a2);

        long sum = affectionRepository.sumFeedCountByUser(user);

        assertThat(sum).isEqualTo(3L); // 2 + 1
    }
}
