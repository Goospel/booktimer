package com.booktimer.session;

import com.booktimer.book.Book;
import com.booktimer.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * ReadingSession 영속성. User와 N:1.
 */
public interface ReadingSessionRepository extends JpaRepository<ReadingSession, Long> {

    List<ReadingSession> findByUser(User user);

    /** 한 책에 연결된(그 책으로 측정한) 세션들 — 책별 상세(잔디·기록) 집계에 쓰인다. */
    List<ReadingSession> findByUserAndBook(User user, Book book);

    /** 진행 중(endedAt == null) 세션. 서비스의 중복 start 방지에 쓰인다. */
    Optional<ReadingSession> findByUserAndEndedAtIsNull(User user);

    /** 진행 중 세션을 책과 함께 즉시 로딩 — 트랜잭션 밖(뷰)에서 책 제목 접근 시 lazy 예외 방지. */
    @Query("select s from ReadingSession s left join fetch s.book where s.user = :user and s.endedAt is null")
    Optional<ReadingSession> findActiveWithBook(@Param("user") User user);

    /**
     * 한 책의 누적 독서 시간(초) — 그 책에 연결된 세션들의 측정 길이 합.
     *
     * <p>진행 중(미종료) 세션은 {@code durationSeconds=0}이라 합계에 영향을 주지 않는다(종료 시 채워짐).
     * 측정 기록이 없으면 0을 돌려준다(coalesce).
     */
    @Query("select coalesce(sum(s.durationSeconds), 0) from ReadingSession s where s.user = :user and s.book = :book")
    long sumDurationByUserAndBook(@Param("user") User user, @Param("book") Book book);

    /** 회원 탈퇴 시 해당 유저의 모든 측정 기록을 제거한다. */
    void deleteByUser(User user);

    /**
     * 책 삭제 시, 그 책을 가리키던 측정 세션을 "책 미지정"으로 푼다(book_id = null).
     *
     * <p>세션 자체는 지우지 않는다 — 책을 책장에서 빼도 그날 읽은 기록(잔디·누적 시간)은 보존돼야 한다.
     * {@code reading_session.book_id} FK 때문에 이 정리 없이 책을 지우면 제약 위반으로 실패한다.
     *
     * <p>벌크 갱신이라 영속성 컨텍스트를 우회하므로, 호출 전후 일관성을 위해 flush/clear를 자동 수행한다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ReadingSession s set s.book = null where s.book = :book")
    void unlinkBook(@Param("book") Book book);
}
