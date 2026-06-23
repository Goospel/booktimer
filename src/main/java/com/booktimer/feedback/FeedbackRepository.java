package com.booktimer.feedback;

import com.booktimer.user.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Feedback 영속성. 작성자 본인 조회(스코핑)·관리자 전체 조회·탈퇴 정리 메서드를 둔다.
 */
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    /** 작성자 본인 글만 — 최신순. 노출 스코핑의 1차 방어(쿼리 자체가 남의 글을 안 가져옴). */
    List<Feedback> findByAuthorOrderByCreatedAtDesc(User author);

    /** 관리자 전체 조회 — 최신순. */
    @EntityGraph(attributePaths = {"author"})
    List<Feedback> findAllByOrderByCreatedAtDesc();

    /** 관리자 유형 필터 — 해당 유형만, 최신순. */
    @EntityGraph(attributePaths = {"author"})
    List<Feedback> findByTypeOrderByCreatedAtDesc(FeedbackType type);

    /** 회원 탈퇴 정리 — 내가 쓴 문의를 모두 제거(author_id FK). */
    void deleteByAuthor(User author);
}
