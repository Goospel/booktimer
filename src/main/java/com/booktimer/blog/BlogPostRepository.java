package com.booktimer.blog;

import com.booktimer.user.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BlogPostRepository extends JpaRepository<BlogPost, Long> {

    /** 읽기 · 초안 저장 — 잠그지 않는다(§4.11). */
    Optional<BlogPost> findByIdAndUser(Long id, User user);

    /**
     * 올리기 · 지우기의 글 행 잠금(§4.11 — {@code UserRepository.lockById} 꼴). 그 트랜잭션의 <b>첫 DB 문장</b>이어야 한다
     * (MySQL 스냅숏 — 앞에 일반 조회가 있으면 잠금이 풀린 뒤 읽는 문장 행이 앞 올리기의 새 행을 못 본다).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from BlogPost p where p.id = :id and p.user = :user")
    Optional<BlogPost> findForUpdate(@Param("id") Long id, @Param("user") User user);

    /** 탈퇴 정리 — blog_post.user_id FK라 유저보다 앞 · 문장 · 판이 글보다 앞(AccountService.purge). */
    void deleteByUser(User user);
}
