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

    /** 책 한 권 → 그 여백에 쌓인 글 수. {@link #countByBook} 투영. */
    interface BookStoryCount {
        Long getBookId();

        long getCount();
    }

    /**
     * 내 서재 목록용 배치 집계 — {@link #recencyByBook}과 같은 모양으로 책 여럿의 글 수를 한 쿼리에.
     *
     * <p>쓰임은 하나: 서재 관리 시트가 「공개로 바꾸기」 전에 <b>「여백에 남긴 글 N개가 팔로워에게
     * 보여요」</b>를 고지하는 것(2026-08-16 결정 B). 비공개 책에도 글을 쓸 수 있게 된 뒤로 공개 전환은
     * "안 보이던 메모가 한꺼번에 노출되는 순간"이 됐다.
     *
     * <p>글이 없는 책은 <b>행이 아예 없다</b>(group by) — 호출부가 0으로 채운다. 빈 목록이면 호출하지
     * 않는다(recencyByBook과 동일 가드 — {@code in ()}은 DB마다 취급이 다르다).
     */
    @Query("""
            select s.book.id as bookId, count(s) as count
            from Story s
            where s.book.id in :bookIds
            group by s.book.id
            """)
    List<BookStoryCount> countByBook(@Param("bookIds") Collection<Long> bookIds);

    /**
     * 홈 소식용 — viewer가 팔로우한 사람들이 <b>최근에</b> 여백에 남긴 글 (최신순).
     *
     * <p>Follow와는 매핑된 연관이 없어 theta 조인({@code f.followee = u})으로 묶는다
     * ({@code BookRepository.feedStarted} 미러). 차단 필터는 쿼리에 불필요 — "팔로우 존재 → 차단 없음"
     * write-시점 불변식(차단 시 팔로우 양방향 해제)이 보장하고, 행동 회귀 테스트가 못 박는다.
     * ADMIN·공개핸들(login_id) 미설정 작성자는 노출 불변식대로 제외(N-055).
     *
     * <p><b>책 공개 여부는 여기가 주 방어다</b>({@code b.visibility = PUBLIC}). 2026-08-16 결정 2로
     * PRIVATE 책에도 글을 남길 수 있게 된 뒤로, 이 한 줄이 「비공개 책 여백 = 소유자 전용 메모」를
     * 소식 피드에서 지키는 유일한 장치가 됐다(예전엔 {@code Story.of} 불변식이 앞을 막아 이건 전환 창
     * 백업이었다). 지우면 남의 비공개 메모가 팔로워 홈에 실린다.
     *
     * <p>작성자·책은 fetch로 즉시 초기화 — 소식 한 줄이 닉네임·책 제목을 읽으므로 홈 진입 핫패스의
     * N+1을 막는다. ToOne fetch만 쓴다.
     */
    @Query("""
            select s from Story s join fetch s.user u join fetch s.book b, com.booktimer.follow.Follow f
            where f.followee = u and f.follower = :viewer
              and s.createdAt > :cutoff
              and b.visibility = com.booktimer.book.BookVisibility.PUBLIC
              and u.role <> com.booktimer.user.Role.ADMIN
              and u.loginId is not null
            order by s.createdAt desc, s.id desc
            """)
    List<Story> feedRecent(@Param("viewer") User viewer, @Param("cutoff") Instant cutoff);

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
