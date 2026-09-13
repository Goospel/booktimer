package com.booktimer.study;

import com.booktimer.book.StudyBook;
import com.booktimer.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 백지복습 영속성. 조회 키가 늘 <b>(나, 날짜)</b>인 것이 요점이다 — id로 찾는 문이 없어 IDOR이 성립할
 * 자리가 애초에 없다.
 */
public interface StudyRecallRepository extends JpaRepository<StudyRecall, Long> {

    Optional<StudyRecall> findByUserAndRecallDate(User user, LocalDate date);

    /**
     * 달력이 쓰는 구간 조회. 호출부가 <b>전달 말일</b>부터 당기는 이유는 복습문제 표식이 「쓴 날」이 아니라
     * 「푸는 날(다음날)」에 서기 때문이다 — 달 첫날의 문제는 전달 말일의 글에서 나온다.
     */
    List<StudyRecall> findByUserAndRecallDateBetweenOrderByRecallDateAsc(User user, LocalDate from, LocalDate to);

    /**
     * 공부 책 삭제 시 참조를 푼다(book_id = null) — 글 자체는 지우지 않는다.
     * {@code StudyPlanItemRepository.unlinkBook}과 같은 규칙이다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update StudyRecall r set r.book = null where r.book = :book")
    void unlinkBook(@Param("book") StudyBook book);

    /** 회원 탈퇴 시 정리(FK: study_recall.user_id → users). */
    void deleteByUser(User user);
}
