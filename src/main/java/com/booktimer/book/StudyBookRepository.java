package com.booktimer.book;

import com.booktimer.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * StudyBook 영속성. User와 N:1.
 *
 * <p>{@link BookRepository}와 같은 규율의 반대편이다 — 독서 표면(책방·홈 피드·추천·책BTI·popularity)은
 * 이 인터페이스를 아예 모르고, 공부 서재만 여기를 본다.
 */
public interface StudyBookRepository extends JpaRepository<StudyBook, Long> {

    /** 내 공부 서재(최신 등록 먼저) — 독서 {@code myBooks}와 같은 정렬. */
    List<StudyBook> findByUserOrderByCreatedAtDesc(User user);

    /** 소유권 확인용 — 내 책일 때만 조회된다(IDOR 방지). */
    Optional<StudyBook> findByIdAndUser(Long id, User user);

    /**
     * 같은 사용자가 같은 isbn13으로 이미 담은 공부 책 — 재추가 시 중복 행 방지.
     * isbn13은 적재 시 정규화돼 저장되므로({@link Isbn#normalize}) 조회 키도 정규화한 값을 넘긴다.
     */
    Optional<StudyBook> findFirstByUserAndIsbn13(User user, String isbn13);

    /** 회원 탈퇴 시 정리(FK: study_book.user_id → users). */
    void deleteByUser(User user);
}
