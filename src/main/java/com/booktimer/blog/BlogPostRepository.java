package com.booktimer.blog;

import com.booktimer.user.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
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

    String VISIBLE_TO_VIEWER = """
            select p from BlogPost p join fetch p.user u
            where p.publishedAt is not null and p.hiddenAt is null
              and not exists (select 1 from com.booktimer.block.Block bl
                              where (bl.blocker.id = :viewerId and bl.blocked.id = u.id)
                                 or (bl.blocker.id = u.id and bl.blocked.id = :viewerId))
            """;

    /** 전체 목록 첫 쪽(§6.4) — 올린 · 가리지 않은 · 차단 관계 아닌 글, 최신순(같은 시각은 id). 내 글도 실린다. */
    @Query(VISIBLE_TO_VIEWER + " order by p.publishedAt desc, p.id desc")
    List<BlogPost> findFeed(@Param("viewerId") Long viewerId, Pageable page);

    /** 전체 목록 다음 쪽 — 키셋 커서(올린 시각 · id). */
    @Query(VISIBLE_TO_VIEWER + """
              and (p.publishedAt < :at or (p.publishedAt = :at and p.id < :id))
            order by p.publishedAt desc, p.id desc
            """)
    List<BlogPost> findFeedAfter(@Param("viewerId") Long viewerId, @Param("at") Instant at, @Param("id") Long id,
                                 Pageable page);

    /** 내 글 목록 — 최근 고친 순 200개(ponytail: 넘으면 페이지를 붙인다). */
    List<BlogPost> findTop200ByUserOrderByUpdatedAtDescIdDesc(User user);

    /** 탈퇴 정리 — blog_post.user_id FK라 유저보다 앞 · 문장 · 판이 글보다 앞(AccountService.purge). */
    void deleteByUser(User user);
}
