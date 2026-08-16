package com.booktimer.story;

import com.booktimer.book.Book;
import com.booktimer.user.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface StoryRepository extends JpaRepository<Story, Long> {

    /** 책 한 권 → 그 여백의 최근 글 시각. {@link #recencyByBook} 투영. */
    interface BookStoryRecency {
        Long getBookId();

        Instant getLastAt();
    }

    /**
     * 책방 격자 발광용 배치 집계 — 책 여럿의 「마지막 글 시각」을 한 쿼리로 (N+1 금지).
     *
     * <p>작성자가 아니라 <b>책 id 목록</b>으로 좁힌다: 화면이 그리는 책만 물어보게 되고, 호출부
     * ({@code ProfileApiController})가 손에 쥔 것도 {@code ProfileView.books()}뿐이라 그게 가장 곧다.
     * 책의 주인과 글 작성자는 언제나 같으므로({@code Story.of} 불변식) 작성자 기준 집계와 결과가 같다.
     *
     * <p>빈 목록이면 호출하지 않는다 — {@code in ()}은 DB마다 취급이 다르고, 물어볼 것도 없다.
     */
    @Query("""
            select s.book.id as bookId, max(s.createdAt) as lastAt
            from Story s
            where s.book.id in :bookIds
            group by s.book.id
            """)
    List<BookStoryRecency> recencyByBook(@Param("bookIds") Collection<Long> bookIds);

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
