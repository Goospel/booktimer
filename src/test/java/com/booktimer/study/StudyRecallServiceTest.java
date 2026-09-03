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
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 백지복습의 <b>연쇄 삭제</b>만 재는 통합 테스트 (실 H2).
 *
 * <p>{@code StudyPlanServiceTest#deletingStudyBook_unlinksPlanItems_keepingRows}의 복습 판이다.
 * 이 자리에 계측기가 없으면 {@code StudyBookService.delete}의 {@code studyRecallRepository.unlinkBook}
 * 한 줄을 지워도 아무 테스트도 죽지 않는다 — 그런데 그 줄이 없으면 <b>책에 글을 걸어 둔 사람만</b>
 * 책 삭제가 FK 제약 위반으로 실패한다. mock은 FK를 아예 모른다(T-023·T-029, CLAUDE.md 「구체 예 ②」).
 *
 * <p>나머지(저장 upsert·상한·게이트·분석 실패)는 {@code StudyRecallApiControllerTest}가 문을 통해 잰다 —
 * 같은 규칙을 두 층에서 두 번 재지 않는다.
 */
@SpringBootTest
@Transactional
class StudyRecallServiceTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired StudyRecallService recallService;
    @Autowired StudyRecallRepository recallRepository;
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

    @Test
    @DisplayName("공부 책을 지워도 백지복습 글은 남고 book_id만 풀린다 — FK 위반 없이(mock으로는 못 잡는 자리)")
    void deletingStudyBook_unlinksRecalls_keepingRows() {
        User user = register("recallunlink");
        StudyBook book = studyBookRepository.save(
                StudyBook.register(user, "정보처리기사 실기", "저자", null, null, null, null));
        StudyRecall recall = recallService.save(user, today(), book, "정보처리기사 실기", "3장 함수",
                "함수는 입력을 받아 출력을 낸다", StudyRecall.Source.TEXT);

        studyBookService.delete(user, book.getId());

        StudyRecall reloaded = recallRepository.findById(recall.getId()).orElseThrow();
        assertThat(reloaded.getBook()).isNull();
        assertThat(reloaded.getSubject()).isEqualTo("정보처리기사 실기"); // 제목 스냅샷은 남는다
        assertThat(reloaded.getBody()).isEqualTo("함수는 입력을 받아 출력을 낸다");
        assertThat(studyBookRepository.findById(book.getId())).isEmpty();
    }
}
