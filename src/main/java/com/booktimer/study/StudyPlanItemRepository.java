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
 * StudyPlanItem 영속성. User와 N:1.
 *
 * <p>{@code StudyDailyCheckRepository}와 같은 규율이다 — 독서 표면은 이 인터페이스를 아예 모른다.
 */
public interface StudyPlanItemRepository extends JpaRepository<StudyPlanItem, Long> {

    /** 달력 한 달치(양끝 포함) — 화면이 날짜순으로 그대로 그린다. */
    List<StudyPlanItem> findByUserAndPlanDateBetweenOrderByPlanDateAsc(User user, LocalDate from, LocalDate to);

    /** 소유권 확인용 — 내 항목일 때만 조회된다(IDOR 방지). */
    Optional<StudyPlanItem> findByIdAndUser(Long id, User user);

    /**
     * 「오늘 이후 전부 교체」의 삭제 절반 — 지운 개수를 돌려준다(화면의 「N개가 바뀝니다」 확인 문구).
     *
     * <p>파생 삭제라 엔티티를 로드해 하나씩 지운다(벌크 DELETE가 아니다) — 영속성 컨텍스트가 같은 행을
     * 들고 있어도 어긋나지 않는다. 한 사람의 남은 일정은 아무리 많아도 366행이라 비용이 문제되지 않는다.
     */
    int deleteByUserAndPlanDateGreaterThanEqual(User user, LocalDate from);

    /**
     * 교체가 지울 항목 수 — 미리보기의 「오늘 이후 N개가 바뀝니다」.
     *
     * <p>세는 시점과 지우는 시점이 다르다(생성 → 사람이 미리보기를 읽는 동안 → 적용). 그 사이 사용자가
     * 일정을 더하면 실제로 지워지는 수는 이 값보다 많아진다 — 화면 문구는 그래서 「지금 기준」이라고
     * 말한다({@code PlanForm.vue}).
     */
    int countByUserAndPlanDateGreaterThanEqual(User user, LocalDate from);

    /**
     * 공부 책 삭제 시, 그 책을 가리키던 일정을 "책 미지정"으로 푼다(book_id = null).
     *
     * <p>일정 행 자체는 지우지 않는다 — 책을 서재에서 빼도 「그날 뭘 하기로 했었나」는 남아야 하고,
     * subject 스냅샷이 제목을 대신 든다. 벌크 갱신이라 영속성 컨텍스트를 우회하므로 flush/clear를
     * 자동 수행한다({@code StudySessionRepository.unlinkBook}과 같다).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update StudyPlanItem i set i.book = null where i.book = :book")
    void unlinkBook(@Param("book") StudyBook book);

    /** 회원 탈퇴 시 정리(FK: study_plan_item.user_id → users). */
    void deleteByUser(User user);
}
