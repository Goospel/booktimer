package com.booktimer.writing;

import com.booktimer.book.Book;
import com.booktimer.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WritingDraftRepository extends JpaRepository<WritingDraft, Long> {

    /** 최근 고친 글 20편 — 책이 풀린 글도 함께(REQ-10). */
    List<WritingDraft> findTop20ByUserOrderByUpdatedAtDesc(User user);

    /** 본인 글만 — 남의 id는 빈 값(→ 404, 존재 비노출). */
    Optional<WritingDraft> findByIdAndUser(Long id, User user);

    /** 본인 글 + 책을 한 번에 — AI 단계는 트랜잭션 밖에서 돌아(외부 호출) 지연 로딩할 수 없다(WritingAiService). */
    @Query("select d from WritingDraft d left join fetch d.book where d.id = :id and d.user = :user")
    Optional<WritingDraft> findWithBook(@Param("id") Long id, @Param("user") User user);

    /** 한 책의 글 — 재료 화면의 「이 책으로 쓴 글」. */
    List<WritingDraft> findByUserAndBookOrderByUpdatedAtDesc(User user, Book book);

    /**
     * 책 삭제 때 글의 연결만 푼다(REQ-13 — 글은 사용자 것이다). {@code writing_draft.book_id} FK 때문에 이 정리 없이는
     * 책 삭제가 제약 위반으로 실패한다. 벌크 갱신이라 {@code ReadingSessionRepository.unlinkBook}처럼 앞뒤로 flush·clear한다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update WritingDraft d set d.book = null where d.book = :book")
    int unlinkBook(@Param("book") Book book);

    /**
     * 탈퇴 정리 — {@code writing_draft.book_id}·{@code user_id} FK 때문에 {@code bookRepository.deleteByUser}와 users 삭제보다
     * 앞이어야 한다({@code AccountService.purge} 순서 · {@code AccountServiceTest} inOrder가 잠근다).
     */
    void deleteByUser(User user);
}
