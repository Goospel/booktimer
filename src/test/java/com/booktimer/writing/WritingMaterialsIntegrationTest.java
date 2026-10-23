package com.booktimer.writing;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.book.StudyBook;
import com.booktimer.book.StudyBookRepository;
import com.booktimer.story.Story;
import com.booktimer.story.StoryRepository;
import com.booktimer.study.StudyNote;
import com.booktimer.study.StudyNoteRepository;
import com.booktimer.study.StudyRecall;
import com.booktimer.study.StudyRecallRepository;
import com.booktimer.testsupport.MockedBoundaryTest;
import com.booktimer.user.AppleUserProvisioningService;
import com.booktimer.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 재료는 그 책의 여백뿐이다 — 같은 ISBN의 공부 필기·백지복습이 있어도(v1 REQ-48 · 결정 5 · §3.3-18), 실 H2.
 *
 * <p>통과하면 확정: 같은 사람·같은 ISBN의 공부 서재 데이터가 모델 문자열에 한 글자도 없고, 그 책 여백은 실린다(양성 대조 —
 * 「재료가 아예 비는」 구현은 여기서 떨어진다). 실패하면 배제: ISBN으로 두 서재를 잇는 조회가 재료 경로에 끼어든 것.
 */
@MockedBoundaryTest
@Transactional
class WritingMaterialsIntegrationTest {

    private static final String ISBN = "9788937460449";

    @Autowired AppleUserProvisioningService appleUsers;
    @Autowired BookRepository bookRepository;
    @Autowired StudyBookRepository studyBookRepository;
    @Autowired StoryRepository storyRepository;
    @Autowired StudyNoteRepository studyNoteRepository;
    @Autowired StudyRecallRepository studyRecallRepository;
    @Autowired WritingService writingService;

    @Test
    @DisplayName("REQ-48 · 같은 ISBN의 공부 필기·백지복습이 있어도 재료 블록에는 그 글자가 없고 그 책 여백 글자는 있다")
    void studyDataNeverReachesMaterial() {
        User me = appleUsers.register("000777.writing.0048");
        Book reading = bookRepository.saveAndFlush(
                Book.register(me, "데미안", "헤르만 헤세", ISBN, null, null, null, BookStatus.READING));
        storyRepository.saveAndFlush(Story.of(me, "여백에만 있는 생각", reading, "paper", "알은 세계다"));
        StudyBook study = studyBookRepository.saveAndFlush(
                StudyBook.register(me, "데미안", "헤르만 헤세", ISBN, null, null, null));
        studyNoteRepository.saveAndFlush(StudyNote.of(me, study, "필기 제목", "공부 필기에만 있는 글자"));
        studyRecallRepository.saveAndFlush(StudyRecall.of(me, LocalDate.of(2026, 10, 20), study, "과목", "범위",
                "백지복습에만 있는 글자", StudyRecall.Source.TEXT));
        assertThat(reading.getIsbn13()).as("전제 — 두 서재가 같은 ISBN").isEqualTo(study.getIsbn13());

        Book owned = writingService.ownedBook(me, reading.getId());
        String text = writingService.material(me, writingService.margins(me, owned)).text(writingService.bookLine(me, owned));

        assertThat(text).contains("여백에만 있는 생각", "알은 세계다");
        assertThat(text).doesNotContain("공부 필기에만 있는 글자", "백지복습에만 있는 글자", "필기 제목", "범위");
    }
}
