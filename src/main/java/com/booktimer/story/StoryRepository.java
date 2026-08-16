package com.booktimer.story;

import com.booktimer.book.Book;
import com.booktimer.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoryRepository extends JpaRepository<Story, Long> {

    /**
     * 책 삭제 시 그 책 여백의 글을 함께 지운다 — 여백은 책에 딸린 자리라, 책이 사라지면 자리도 사라진다.
     * {@code story.book_id}가 NOT NULL이 된 뒤로 「첨부만 풀기」(옛 {@code unlinkBook})는 불가능하다.
     */
    void deleteByBook(Book book);

    /** 회원 탈퇴 정리용 — {@code story.book_id} 때문에 책 삭제보다 앞서야 한다(AccountService.purge). */
    void deleteByUser(User user);
}
