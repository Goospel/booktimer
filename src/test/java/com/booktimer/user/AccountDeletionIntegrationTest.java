package com.booktimer.user;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 회원 탈퇴 통합 테스트 (실제 빈 + H2) — 책을 가진 사용자도 FK 위반 없이 탈퇴되는지 본다.
 *
 * <p>book은 user_id로 users를 FK 참조한다(cascade 없음). purge가 book을 안 지우면 유저 삭제 시
 * 제약 위반이 난다 — mock 단위테스트는 못 잡으므로 실제 스키마로 검증한다.
 */
@SpringBootTest
@Transactional
class AccountDeletionIntegrationTest {

    @Autowired
    private AccountService accountService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BookRepository bookRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("책을 가진 사용자가 탈퇴해도 FK 위반 없이 계정·책이 삭제된다")
    void deleteAccount_withBooks_succeeds() {
        String email = "owner@booktimer.com";
        User user = userRepository.saveAndFlush(
                User.of(email, passwordEncoder.encode("rawpw1234"), "책주인", "Asia/Seoul", Role.USER));
        bookRepository.saveAndFlush(
                Book.register(user, "내 책", null, null, null, null, null, BookStatus.READING));

        // book FK가 정리되지 않으면 여기서 제약 위반 예외가 난다(쿼리 시 flush 강제).
        assertThatCode(() -> {
            accountService.deleteAccount(email, "rawpw1234");
            assertThat(userRepository.findByEmail(email)).isEmpty();
        }).doesNotThrowAnyException();
    }
}
