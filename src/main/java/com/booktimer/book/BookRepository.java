package com.booktimer.book;

import com.booktimer.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Book 영속성. User와 N:1.
 */
public interface BookRepository extends JpaRepository<Book, Long> {

    /** 내 책장(최신 등록 먼저). */
    List<Book> findByUserOrderByCreatedAtDesc(User user);

    /** 공개 범위로 거른 책장 — 프로필 페이지에서 PUBLIC 책만 노출(최신 등록 먼저). */
    List<Book> findByUserAndVisibilityOrderByCreatedAtDesc(User user, BookVisibility visibility);

    /** 소유권 확인용 — 내 책일 때만 조회된다(IDOR 방지). */
    Optional<Book> findByIdAndUser(Long id, User user);

    /** 회원 탈퇴 시 해당 유저의 모든 책을 제거한다. */
    void deleteByUser(User user);
}
