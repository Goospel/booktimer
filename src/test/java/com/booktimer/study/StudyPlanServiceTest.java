package com.booktimer.study;

import com.booktimer.book.StudyBook;
import com.booktimer.book.StudyBookRepository;
import com.booktimer.book.StudyBookService;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 공부 일정 원장 서비스 통합 테스트 (H2).
 *
 * <p>여기서 재는 것은 세 가지다: ① 「오늘 이후 전부 교체」 규칙이 <b>과거를 건드리지 않는가</b>,
 * ② 남의 항목을 지울 수 없는가(IDOR), ③ 공부 책을 지워도 일정 행이 <b>FK 위반 없이</b> 살아남는가.
 *
 * <p>③이 mock이 아니라 실 H2인 이유: {@code study_plan_item.book_id → study_book} FK는 mock이
 * 검증하지 못한다(CLAUDE.md 「구체 예 ②」 — T-023·T-029 계열).
 */
@SpringBootTest
@Transactional
class StudyPlanServiceTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired StudyPlanService planService;
    @Autowired StudyPlanItemRepository planItemRepository;
    @Autowired StudyBookService studyBookService;
    @Autowired StudyBookRepository studyBookRepository;
    @Autowired UserRegistrationService registrationService;
    @Autowired UserRepository userRepository;
    @Autowired Clock clock;

    private User register(String loginId) {
        registrationService.register(loginId + "@booktimer.com", "pw1234qwer!!", loginId,
                "닉네임_" + loginId, SEOUL, Role.USER, today());
        return userRepository.findByLoginId(loginId).orElseThrow();
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    private StudyBook studyBook(User user, String title) {
        return studyBookRepository.save(StudyBook.register(user, title, "저자", null, null, null, null));
    }

    // ── 오늘 이후 전부 교체 (§2.2 A안) ──────────────────────────────────────

    @Test
    @DisplayName("applyReplacingFuture: 오늘·미래 항목만 지우고 새 일정을 넣는다 — 과거는 남는다")
    void applyReplacingFuture_replacesTodayAndFuture_keepsPast() {
        User user = register("planowner");
        LocalDate today = today();
        planService.add(user, today.minusDays(3), null, "정보처리기사", "지난 일정");
        planService.add(user, today, null, "정보처리기사", "오늘 옛 일정");
        planService.add(user, today.plusDays(1), null, "정보처리기사", "내일 옛 일정");
        planService.add(user, today.plusDays(2), null, "정보처리기사", "모레 옛 일정");

        StudyPlanService.ApplyResult result = planService.applyReplacingFuture(user, today, "정보처리기사", null,
                List.of(new StudyPlanService.PlanDay(today, "1장 p.1-20"),
                        new StudyPlanService.PlanDay(today.plusDays(1), "2장 p.21-40")));

        assertThat(result.removed()).isEqualTo(3);
        assertThat(result.applied()).isEqualTo(2);
        assertThat(planItemRepository.findByUserAndPlanDateBetweenOrderByPlanDateAsc(
                        user, today.minusDays(10), today.plusDays(10)))
                .extracting(StudyPlanItem::getTask)
                .containsExactly("지난 일정", "1장 p.1-20", "2장 p.21-40");
    }

    @Test
    @DisplayName("applyReplacingFuture: 빈 일정은 거부한다 — 「적용」이 조용한 전체 삭제가 되면 안 된다")
    void applyReplacingFuture_emptyDays_rejected() {
        User user = register("planempty");
        assertThatThrownBy(() -> planService.applyReplacingFuture(user, today(), "제목", null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("applyReplacingFuture: 오늘보다 이른 날짜는 거부한다 — 과거는 교체 대상이 아니다")
    void applyReplacingFuture_pastDate_rejected() {
        User user = register("planpast");
        LocalDate today = today();
        assertThatThrownBy(() -> planService.applyReplacingFuture(user, today, "제목", null,
                List.of(new StudyPlanService.PlanDay(today.minusDays(1), "어제 할 일"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("applyReplacingFuture: 같은 날짜가 두 번 오면 거부한다")
    void applyReplacingFuture_duplicateDate_rejected() {
        User user = register("plandup");
        LocalDate today = today();
        assertThatThrownBy(() -> planService.applyReplacingFuture(user, today, "제목", null,
                List.of(new StudyPlanService.PlanDay(today, "가"), new StudyPlanService.PlanDay(today, "나"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("applyReplacingFuture: 367개는 거부한다(366일 상한)")
    void applyReplacingFuture_tooManyDays_rejected() {
        User user = register("planmany");
        LocalDate today = today();
        List<StudyPlanService.PlanDay> days = new java.util.ArrayList<>();
        for (int i = 0; i < 367; i++) {
            days.add(new StudyPlanService.PlanDay(today.plusDays(i), "할 일 " + i));
        }
        assertThatThrownBy(() -> planService.applyReplacingFuture(user, today, "제목", null, days))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("applyReplacingFuture: 남의 일정은 건드리지 않는다")
    void applyReplacingFuture_leavesOtherUsersItems() {
        User me = register("planme");
        User other = register("planother");
        LocalDate today = today();
        planService.add(other, today.plusDays(1), null, "남의 과목", "남의 일정");

        planService.applyReplacingFuture(me, today, "내 과목", null,
                List.of(new StudyPlanService.PlanDay(today, "내 일정")));

        assertThat(planItemRepository.findByUserAndPlanDateBetweenOrderByPlanDateAsc(
                        other, today, today.plusDays(3)))
                .hasSize(1);
    }

    // ── 추가·삭제 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("delete: 남의 항목은 없는 것으로 취급하고(IDOR) 행도 그대로 둔다")
    void delete_othersItem_isNotFoundAndKeepsRow() {
        User me = register("delme");
        User other = register("delother");
        StudyPlanItem othersItem = planService.add(other, today(), null, "남의 과목", "남의 일정");

        assertThat(planService.delete(me, othersItem.getId())).isFalse();
        assertThat(planItemRepository.findById(othersItem.getId())).isPresent();
    }

    @Test
    @DisplayName("delete: 내 항목은 지워진다")
    void delete_ownItem_removesRow() {
        User user = register("delown");
        StudyPlanItem item = planService.add(user, today(), null, "과목", "할 일");

        assertThat(planService.delete(user, item.getId())).isTrue();
        assertThat(planItemRepository.findById(item.getId())).isEmpty();
    }

    @Test
    @DisplayName("add: 빈 할 일·빈 과목은 거부한다")
    void add_blankFields_rejected() {
        User user = register("addblank");
        assertThatThrownBy(() -> planService.add(user, today(), null, "과목", "  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> planService.add(user, today(), null, "  ", "할 일"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("add: 500자를 넘는 할 일은 거부한다")
    void add_tooLongTask_rejected() {
        User user = register("addlong");
        assertThatThrownBy(() -> planService.add(user, today(), null, "과목", "가".repeat(501)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── 책 삭제 연쇄 (실 H2 FK) ────────────────────────────────────────────

    @Test
    @DisplayName("공부 책을 지워도 일정 행은 남고 book_id만 풀린다 — FK 위반 없이(mock으로는 못 잡는 자리)")
    void deletingStudyBook_unlinksPlanItems_keepingRows() {
        User user = register("unlinkuser");
        StudyBook book = studyBook(user, "정보처리기사 실기");
        StudyPlanItem item = planService.add(user, today(), book, "정보처리기사 실기", "1장 훑기");

        studyBookService.delete(user, book.getId());

        StudyPlanItem reloaded = planItemRepository.findById(item.getId()).orElseThrow();
        assertThat(reloaded.getBook()).isNull();
        assertThat(reloaded.getSubject()).isEqualTo("정보처리기사 실기"); // 제목 스냅샷은 남는다
        assertThat(studyBookRepository.findById(book.getId())).isEmpty();
    }

    // ── 달 조회 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("month: 그 달의 항목만 날짜 오름차순으로 준다")
    void month_returnsOnlyThatMonthAscending() {
        User user = register("monthuser");
        planService.add(user, LocalDate.of(2026, 8, 31), null, "과목", "지난달");
        planService.add(user, LocalDate.of(2026, 9, 15), null, "과목", "중순");
        planService.add(user, LocalDate.of(2026, 9, 1), null, "과목", "초하루");
        planService.add(user, LocalDate.of(2026, 10, 1), null, "과목", "다음달");

        assertThat(planService.month(user, YearMonth.of(2026, 9)))
                .extracting(StudyPlanItem::getTask)
                .containsExactly("초하루", "중순");
    }
}
