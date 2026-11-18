package com.booktimer.blog;

import com.booktimer.user.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface BlogCommentRepository extends JpaRepository<BlogComment, Long> {

    /**
     * 댓글 시트 — 그 글의 sid 묶음 · 보는 사람과 차단 관계(양방향)인 사람의 댓글과 보는 사람이 신고한 댓글은 뺀다
     * ({@link BlogPostRepository#VISIBLE_TO_VIEWER}와 같은 꼴) · 가린 댓글은 남는다(자리 표시 — 부르는 쪽이 그린다) · 최근 것부터
     * (부르는 쪽이 오래된 순으로 뒤집는다 — 최근 N개를 남기려고).
     */
    @Query("""
            select c from BlogComment c join fetch c.user u
            where c.post.id = :postId and c.sid in :sids
              and not exists (select 1 from com.booktimer.block.Block bl
                              where (bl.blocker.id = :viewerId and bl.blocked.id = u.id)
                                 or (bl.blocker.id = u.id and bl.blocked.id = :viewerId))
              and not exists (select 1 from com.booktimer.blog.BlogReport r
                              where r.reporter.id = :viewerId and r.targetType = com.booktimer.blog.BlogReport.Target.COMMENT
                                and r.targetId = c.id)
            order by c.createdAt desc, c.id desc
            """)
    List<BlogComment> findForSheet(@Param("postId") Long postId, @Param("sids") Collection<String> sids,
                                   @Param("viewerId") Long viewerId, Pageable page);

    /** 문장 뒤 수(REQ-266) — sid마다 [sid, 수]. 보는 사람과 무관(J39) · 가린 댓글만 뺀다(B2b). */
    @Query("select c.sid, count(c) from BlogComment c where c.post.id = :postId and c.hiddenAt is null group by c.sid")
    List<Object[]> countBySid(@Param("postId") Long postId);

    /** 「내 글」 새 댓글 수(REQ-298) — 글마다 [postId, 수]: 확인 시각 뒤 남이 단 댓글(글쓴이 자신 · 가린 댓글 제외). */
    @Query("""
            select p.id, count(c) from BlogComment c join c.post p
            where p.user = :user and c.user <> :user and c.hiddenAt is null
              and (p.authorSeenCommentsAt is null or c.createdAt > p.authorSeenCommentsAt)
            group by p.id
            """)
    List<Object[]> newCounts(@Param("user") User user);

    long countByHideKindAndHiddenAtLessThanEqual(Moderation.Kind kind, java.time.Instant cutoff);

    List<BlogComment> findTop100ByHideKindAndHiddenAtLessThanEqualOrderByHiddenAt(Moderation.Kind kind, java.time.Instant cutoff);

    /** 글 지우기 — blog_comment.post_id FK라 글보다 앞. 일괄 DELETE · 문맥 비움({@link BlogSentenceRepository#deleteByPost}와 같은 꼴). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from BlogComment c where c.post = :post")
    void deleteByPost(@Param("post") BlogPost post);

    /** 탈퇴 정리 — 내가 단 댓글(남의 글에 단 것 포함 · blog_comment.user_id FK). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from BlogComment c where c.user = :user")
    void deleteByUser(@Param("user") User user);

    /** 탈퇴 정리 — 내 글에 달린 남의 댓글(blog_comment.post_id FK라 글보다 앞). 하위 질의 하나(OR로 묶지 않는다). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from BlogComment c where c.post.id in (select p.id from BlogPost p where p.user = :user)")
    void deleteByPostUser(@Param("user") User user);
}
