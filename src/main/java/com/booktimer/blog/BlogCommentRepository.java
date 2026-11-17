package com.booktimer.blog;

import com.booktimer.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlogCommentRepository extends JpaRepository<BlogComment, Long> {

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
