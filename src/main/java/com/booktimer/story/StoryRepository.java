package com.booktimer.story;

import com.booktimer.book.Book;
import com.booktimer.user.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoryRepository extends JpaRepository<Story, Long> {

    /**
     * 한 책의 여백에 쌓인 글 — <b>최신순</b>이라 {@code pageable} 상한이 최근 것을 남긴다.
     * 동시각 tie는 id로 갈라 상한 경계가 호출마다 흔들리지 않게 한다.
     *
     * <p>{@code user}까지 조건에 두는 이유: 책 소유자와 글 작성자가 어긋난 행이 생기면(불변식 파손)
     * 조용히 남의 글이 실리는 대신 안 보이는 쪽으로 실패한다.
     *
     * <p>book은 fetch하지 않는다 — 호출부가 이미 책을 손에 쥐고 있고({@code marginOf}의 게이트가
     * 조회한 그 책) 카드에는 책 라벨이 없다(헤더에 한 번만 실린다).
     */
    List<Story> findByUserAndBookOrderByCreatedAtDescIdDesc(User user, Book book, Pageable pageable);

    /**
     * 책 삭제 시 그 책 여백의 글을 함께 지운다 — 여백은 책에 딸린 자리라, 책이 사라지면 자리도 사라진다.
     * {@code story.book_id}가 NOT NULL이 된 뒤로 「첨부만 풀기」(옛 {@code unlinkBook})는 불가능하다.
     */
    void deleteByBook(Book book);

    /** 회원 탈퇴 정리용 — {@code story.book_id} 때문에 책 삭제보다 앞서야 한다(AccountService.purge). */
    void deleteByUser(User user);
}
