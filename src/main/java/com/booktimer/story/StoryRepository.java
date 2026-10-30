package com.booktimer.story;

import com.booktimer.book.Book;
import com.booktimer.user.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 여백 쪽지 저장소.
 *
 * <p><b>남이 부르는 읽기 쿼리는 없다</b>(2026-10-30 책생글 M1 — C3). 옛 {@code feedRecent}(홈 소식) · {@code sharedRecent}(「여백」 탭) ·
 * {@code sharedByIsbn}·{@code countSharedByIsbn}(책축) · {@code sharedCountsByIsbn}(검색 배지)는 여백이 본인만의 공간이 되면서
 * 지웠다 — 쿼리가 남아 있으면 누군가 다시 잇는다. 아래 목록은 전부 본인 것만 묻는 데 쓴다(호출부가 본인 판정을 진다).
 * 웹 · 미니앱 목록 쿼리는 {@code join s.book}(inner)이라 책 없는 쪽지가 실리지 않는다(J64).
 */
public interface StoryRepository extends JpaRepository<Story, Long> {

    /** 책 한 권 → 그 여백의 최근 글 시각. {@link #recencyByBook} 투영. */
    interface BookStoryRecency {
        Long getBookId();

        Instant getLastAt();
    }

    /**
     * 책방 격자 발광용 배치 집계 — 책 여럿의 「마지막 글 시각」을 한 쿼리로 (N+1 금지). <b>본인 프로필에서만</b> 부른다
     * ({@code ProfileApiController.recencyOf} — 남에게는 빈 맵, C3).
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
     * 내 서재 목록용 배치 집계 — 책 여럿의 쪽지 수를 한 쿼리에. 글이 없는 책은 행이 없다(호출부가 0으로 채운다).
     * 빈 목록이면 호출하지 않는다.
     */
    @Query("""
            select s.book.id as bookId, count(s) as count
            from Story s
            where s.book.id in :bookIds
            group by s.book.id
            """)
    List<BookStoryCount> countByBook(@Param("bookIds") Collection<Long> bookIds);

    /** 책 한 권의 쪽지 — 최근순(같은 시각이면 id 내림차순). 본인 책에만 쓴다. */
    List<Story> findByUserAndBookOrderByCreatedAtDescIdDesc(User user, Book book, Pageable pageable);

    /** 책 없는 쪽지 — 최근순(앱 {@code GET /api/app/margins?book=none} — C9). */
    List<Story> findByUserAndBookIsNullOrderByCreatedAtDescIdDesc(User user, Pageable pageable);

    /**
     * 한 사람의 여백 전체 — 책 구분 없이 최신순(웹 책방 「여백」 탭 · 동결 미니앱 「내 여백 전체」). <b>본인만</b> 부른다.
     * {@code join fetch s.book}(inner)이라 책 없는 쪽지는 빠진다 — 옛 화면은 책 라벨 없는 카드를 그릴 줄 모른다(J64).
     */
    @Query("""
            select s from Story s join fetch s.book b
            where s.user = :target
            order by s.createdAt desc, s.id desc
            """)
    List<Story> recentByUser(@Param("target") User target, Pageable pageable);

    /** 본인 쪽지 하나 — 책을 함께 읽는다(AI 단계는 트랜잭션 밖이라 지연 로딩이 안 된다 — {@code WritingAiService}). */
    @Query("select s from Story s left join fetch s.book where s.id = :id and s.user = :user")
    Optional<Story> findWithBook(@Param("id") Long id, @Param("user") User user);

    /**
     * 책을 지울 때 쪽지는 남기고 연결만 푼다(C9 — v1 REQ-13을 여백까지). 옮긴 문장이 있는데 출처가 비어 있는 쪽지는 먼저 그 책의
     * 출처 줄({@code Story.sourceLine})로 채운다 — 책이 사라져도 『제목』 · 저자가 남는다(J84). 이미 있는 출처는 그대로다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Story s
            set s.quoteSource = case when s.quote is not null and s.quoteSource is null then :line else s.quoteSource end,
                s.book = null
            where s.book = :book
            """)
    int unlinkBook(@Param("book") Book book, @Param("line") String sourceLine);

    /** 회원 탈퇴 정리용 — {@code story.book_id} 때문에 책 삭제보다 앞서야 한다(AccountService.purge). */
    void deleteByUser(User user);
}
