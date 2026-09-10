package com.booktimer.study;

import com.booktimer.book.StudyBook;
import com.booktimer.book.StudyBookRepository;
import com.booktimer.user.AccountService;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 필기의 <b>탈퇴 연쇄</b>만 재는 통합 테스트 (실 H2 — mock 금지, T-023·T-029).
 *
 * <p>{@code study_note}는 users와 study_book을 <b>둘 다</b> FK 참조한다. 그래서 purge에서 순서가 하나만
 * 틀려도(필기를 책보다 늦게 지우면) 필기를 가진 사람만 탈퇴가 제약 위반으로 실패한다 — 필기 없는
 * 사용자로 도는 다른 테스트는 전부 초록인 채로. mock은 FK를 아예 모른다.
 *
 * <p>{@code FlywayMigrationTest#everyTableWithForeignKeyToUsersIsClearedByPurge}가 목록 누락은 잡지만,
 * <b>순서</b>는 잡지 못한다(그쪽은 집합만 본다). 그 사각이 이 테스트의 몫이다.
 */
@SpringBootTest
@Transactional
class StudyNoteServiceTest {

    @Autowired AccountService accountService;
    @Autowired StudyNoteService noteService;
    @Autowired StudyNoteRepository noteRepository;
    @Autowired StudyBookRepository studyBookRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("필기를 가진 회원이 탈퇴해도 FK 위반 없이 정리된다 (필기 → 책 순서)")
    void withdrawal_purgesNotes() {
        User user = User.of("note-purge@booktimer.com", passwordEncoder.encode("rawpw1234"),
                "책벌레", "Asia/Seoul", Role.USER);
        user.assignLoginId("notepurge");
        userRepository.saveAndFlush(user);
        StudyBook book = studyBookRepository.saveAndFlush(
                StudyBook.register(user, "정보처리기사 실기", "저자", null, null, null, null));
        noteService.create(user, book, "3장 함수", "함수는 입력을 받아 출력을 낸다");

        assertThatCode(() -> {
            accountService.deleteAccount("note-purge@booktimer.com", "rawpw1234");
            assertThat(userRepository.findByEmail("note-purge@booktimer.com")).isEmpty();
        }).doesNotThrowAnyException();

        assertThat(noteRepository.count()).isZero();
    }
}
