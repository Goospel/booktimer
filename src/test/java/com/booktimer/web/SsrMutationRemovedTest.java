package com.booktimer.web;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 제거된 SSR 뮤테이션 엔드포인트 불존재 검증 (선별 SPA 백로그 정리).
 *
 * <p>인증 사용자 + CSRF로 POST해도 404여야 한다 — 미인증/노CSRF면 302/403으로 제거 증명 안 됨.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SsrMutationRemovedTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired MockMvc mockMvc;
    @Autowired UserRegistrationService registrationService;
    @Autowired UserRepository userRepository;
    @Autowired BookRepository bookRepository;
    @Autowired Clock clock;

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    private User register(String email, String loginId, String nick) {
        registrationService.register(email, "pw1234qwer!!", loginId, nick, SEOUL, Role.USER, today());
        return userRepository.findByLoginId(loginId).orElseThrow();
    }

    private Book book(User owner) {
        return bookRepository.save(
                Book.register(owner, "테스트책", null, null, null, null, null, BookStatus.READING));
    }

    // ── follow / unfollow ───────────────────────────────────────────────────

    @Test
    @DisplayName("POST /follow → 404 (SSR 핸들러 제거됨)")
    void follow_removed_returns404() throws Exception {
        register("rm-fol@bt.com", "rmfolid", "rm팔로워");
        mockMvc.perform(post("/follow").param("loginId", "someone")
                        .with(user("rm-fol@bt.com")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /unfollow → 404 (SSR 핸들러 제거됨)")
    void unfollow_removed_returns404() throws Exception {
        register("rm-unfol@bt.com", "rmunfolid", "rm언팔러");
        mockMvc.perform(post("/unfollow").param("loginId", "someone")
                        .with(user("rm-unfol@bt.com")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    // ── report ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /report → 404 (SSR 핸들러 제거됨)")
    void report_removed_returns404() throws Exception {
        register("rm-rpt@bt.com", "rmrptid", "rm신고자");
        mockMvc.perform(post("/report").param("loginId", "someone").param("reason", "SPAM")
                        .with(user("rm-rpt@bt.com")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    // ── block / unblock ──────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /block → 404 (SSR 핸들러 제거됨)")
    void block_removed_returns404() throws Exception {
        register("rm-blk@bt.com", "rmblkid", "rm차단자");
        mockMvc.perform(post("/block").param("loginId", "someone")
                        .with(user("rm-blk@bt.com")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /unblock → 404 (SSR 핸들러 제거됨)")
    void unblock_removed_returns404() throws Exception {
        register("rm-ublk@bt.com", "rmublkid", "rm차단해제");
        mockMvc.perform(post("/unblock").param("loginId", "someone")
                        .with(user("rm-ublk@bt.com")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    // ── books ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /books/add → 404 (SSR 핸들러 제거됨; /books/{id:\\d+} 숫자 제한으로 경로 충돌 해소)")
    void booksAdd_removed_returns404() throws Exception {
        // book-detail이 /books/{id:\d+}로 숫자만 받게 바뀌어 /books/add는 어떤 매핑에도 안 걸린다.
        // (이전엔 미제한 /books/{id}가 경로를 매칭해 메서드 불일치로 405였음 — Vue 번들 충돌의 같은 뿌리)
        register("rm-badd@bt.com", "rmaddid", "rm추가자");
        mockMvc.perform(post("/books/add").param("title", "책")
                        .with(user("rm-badd@bt.com")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /books/{id}/status → 404 (SSR 핸들러 제거됨)")
    void booksStatus_removed_returns404() throws Exception {
        User u = register("rm-bst@bt.com", "rmbstid", "rm상태자");
        Book b = book(u);
        mockMvc.perform(post("/books/{id}/status", b.getId()).param("status", "FINISHED")
                        .with(user("rm-bst@bt.com")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /books/{id}/visibility → 404 (SSR 핸들러 제거됨)")
    void booksVisibility_removed_returns404() throws Exception {
        User u = register("rm-bvis@bt.com", "rmbvisid", "rm공개자");
        Book b = book(u);
        mockMvc.perform(post("/books/{id}/visibility", b.getId()).param("visibility", "PUBLIC")
                        .with(user("rm-bvis@bt.com")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /books/{id}/delete → 404 (SSR 핸들러 제거됨)")
    void booksDelete_removed_returns404() throws Exception {
        User u = register("rm-bdel@bt.com", "rmdelid", "rm삭제자");
        Book b = book(u);
        mockMvc.perform(post("/books/{id}/delete", b.getId())
                        .with(user("rm-bdel@bt.com")).with(csrf()))
                .andExpect(status().isNotFound());
    }
}
