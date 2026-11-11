package com.booktimer.blog;

import com.booktimer.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BlogSentenceRepository extends JpaRepository<BlogSentence, Long> {

    /** 한 글의 문장 행 전부(산 것 · 죽은 것) — 순서는 배치가 정한다. */
    List<BlogSentence> findByPostId(Long postId);

    Optional<BlogSentence> findBySid(String sid);

    boolean existsBySid(String sid);

    /**
     * 글 지우기 — blog_sentence.post_id FK라 글보다 앞 · 판 행보다 뒤. 일괄 DELETE(1,500문장도 한 문장 · §5.1).
     * 문맥을 비운다 — 남은 문장 · 판 엔티티가 지운 글을 가리킨 채 flush되면 TransientPropertyValueException
     * ({@code ChatRoomRepository.deleteByMember}와 같은 꼴 · 지운 뒤 글은 {@code delete}가 다시 찾아 지운다).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from BlogSentence s where s.post = :post")
    void deleteByPost(@Param("post") BlogPost post);

    /** 탈퇴 정리 — 내 글들의 문장(판 행보다 뒤 · 글보다 앞). 일괄 DELETE · 문맥 비움(위와 같은 까닭). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from BlogSentence s where s.post.id in (select p.id from BlogPost p where p.user = :user)")
    void deleteByPostUser(@Param("user") User user);
}
