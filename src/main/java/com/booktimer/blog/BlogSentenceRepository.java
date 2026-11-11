package com.booktimer.blog;

import com.booktimer.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BlogSentenceRepository extends JpaRepository<BlogSentence, Long> {

    /** 한 글의 문장 행 전부(산 것 · 죽은 것) — 순서는 배치가 정한다. */
    List<BlogSentence> findByPostId(Long postId);

    Optional<BlogSentence> findBySid(String sid);

    boolean existsBySid(String sid);

    /** 글 지우기 — blog_sentence.post_id FK라 글보다 앞 · 판 행보다 뒤. */
    void deleteByPost(BlogPost post);

    /** 탈퇴 정리 — 내 글들의 문장(판 행보다 뒤 · 글보다 앞). */
    void deleteByPostUser(User user);
}
