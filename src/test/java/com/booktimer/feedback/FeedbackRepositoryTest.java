package com.booktimer.feedback;

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
 * FeedbackRepository 슬라이스 테스트 (@DataJpaTest, H2).
 *
 * <p>flush()+clear()로 1차 캐시를 비운 뒤 조회해야 @EntityGraph 유무의 차이를 볼 수 있다.
 * 캐시가 살아 있으면 fetch 없이도 isInitialized가 true가 돼 가짜 GREEN이 된다.
 */
@DataJpaTest
@Import(JpaConfig.class)
class FeedbackRepositoryTest {

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private UserRepository userRepository;

    private User persistedUser(String email, String loginId) {
        User u = User.of(email, "$2a$10$abcdefghijklmnopqrstuv", "책벌레", "Asia/Seoul", Role.USER);
        u.assignLoginId(loginId);
        return userRepository.save(u);
    }

    @Test
    @DisplayName("findAllByOrderByCreatedAtDesc: author가 한 쿼리로 즉시 초기화된다 (N+1 없음)")
    void findAll_initializesAuthor() {
        User author = persistedUser("author1@booktimer.com", "authorone");
        feedbackRepository.save(Feedback.of(author, FeedbackType.BUG, "버그 제목", "버그 내용"));

        em.flush();
        em.clear();

        List<Feedback> rows = feedbackRepository.findAllByOrderByCreatedAtDesc();

        assertThat(rows).hasSize(1);
        assertThat(Hibernate.isInitialized(rows.get(0).getAuthor())).isTrue();
    }

    @Test
    @DisplayName("findByTypeOrderByCreatedAtDesc: author가 한 쿼리로 즉시 초기화된다 (N+1 없음)")
    void findByType_initializesAuthor() {
        User author = persistedUser("author2@booktimer.com", "authortwo");
        feedbackRepository.save(Feedback.of(author, FeedbackType.SUGGESTION, "제안 제목", "제안 내용"));

        em.flush();
        em.clear();

        List<Feedback> rows = feedbackRepository.findByTypeOrderByCreatedAtDesc(FeedbackType.SUGGESTION);

        assertThat(rows).hasSize(1);
        assertThat(Hibernate.isInitialized(rows.get(0).getAuthor())).isTrue();
    }
}
