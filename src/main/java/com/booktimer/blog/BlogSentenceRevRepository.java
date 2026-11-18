package com.booktimer.blog;

import com.booktimer.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BlogSentenceRevRepository extends JpaRepository<BlogSentenceRev, Long> {

    /** 「예전 문장 보기」 — 지금 판보다 작은 판, 최근 판 먼저(J38). */
    List<BlogSentenceRev> findBySentenceAndRevLessThanOrderByRevDesc(BlogSentence sentence, int rev);

    /** 예전 판 지우기(B2a REQ-258 · Q10) — 지금 판 행은 남는다(「고쳐짐」 = 문장 행의 rev). 한 문장 DELETE. */
    @Modifying(flushAutomatically = true)
    @Query("delete from BlogSentenceRev r where r.sentence = :s and r.rev < :rev")
    void deleteOlder(@Param("s") BlogSentence sentence, @Param("rev") int rev);

    /** 글 지우기 — blog_sentence_rev.sentence_id FK라 문장보다 앞. 일괄 DELETE(하위 질의 꼴 · §5.1). */
    @Modifying(flushAutomatically = true)
    @Query("delete from BlogSentenceRev r where r.sentence.id in (select s.id from BlogSentence s where s.post = :post)")
    void deleteBySentencePost(@Param("post") BlogPost post);

    /** 탈퇴 정리 — 내 글들의 판 행(문장보다 앞). 일괄 DELETE. */
    @Modifying(flushAutomatically = true)
    @Query("delete from BlogSentenceRev r where r.sentence.id in "
            + "(select s.id from BlogSentence s join s.post p where p.user = :user)")
    void deleteBySentencePostUser(@Param("user") User user);
}
