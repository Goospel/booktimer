package com.booktimer.web.api;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.book.BookVisibility;
import com.booktimer.follow.Follow;
import com.booktimer.follow.FollowRepository;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * GET /api/book-readers 컨트롤러 통합 테스트.
 *
 * <p>①인증 게이트(302), ②200 JSON 구조(reading·wanting), ③버킷 분류,
 * ④isbn 누락/blank→빈 버킷, ⑤스코프(팔로우 안 한 사람 제외), ⑥PRIVATE 제외,
 * ⑦N-055 login_id=null 사용자 제외.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BookReadersApiControllerTest {

    private static final String SEOUL = "Asia/Seoul";
    private static final String TEST_ISBN = "9781234567890";

    @Autowired MockMvc mockMvc;
    @Autowired UserRegistrationService registrationService;
    @Autowired UserRepository userRepository;
    @Autowired BookRepository bookRepository;
    @Autowired FollowRepository followRepository;
    @Autowired Clock clock;

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    private User reg(String email, String loginId, String nick) {
        return registrationService.register(email, "pw1234qwer!!", loginId, nick, SEOUL, Role.USER, today());
    }

    /** login_id=null 사용자(온보딩 전) */
    private User regNull(String email, String nick) {
        return registrationService.register(email, "pw1234qwer!!", nick, SEOUL, Role.USER, today());
    }

    private void book(User owner, String isbn, BookStatus status, BookVisibility visibility) {
        Book b = Book.register(owner, "책-" + isbn, null, isbn, null, null, null, status);
        b.changeVisibility(visibility);
        bookRepository.saveAndFlush(b);
    }

    private void follow(User follower, User followee) {
        followRepository.saveAndFlush(Follow.of(follower, followee));
    }

    @Test
    @DisplayName("GET /api/book-readers 미인증 → 302 /login (default-deny)")
    void getBookReaders_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/api/book-readers?isbn=" + TEST_ISBN))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("isbn 인증, 팔로우 대상이 PUBLIC·READING 보유 → 200 JSON, reading 버킷에 포함")
    void getBookReaders_followedUserHasPublicReadingBook_inReadingBucket() throws Exception {
        User me = reg("br-s1@booktimer.com", "brid1", "독서테스터1");
        User reader = reg("br-s1r@booktimer.com", "brreader1", "독자1");
        follow(me, reader);
        book(reader, TEST_ISBN, BookStatus.READING, BookVisibility.PUBLIC);

        mockMvc.perform(get("/api/book-readers?isbn=" + TEST_ISBN)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("br-s1@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.reading").isArray())
                .andExpect(jsonPath("$.wanting").isArray())
                .andExpect(jsonPath("$.reading[0].loginId").value("brreader1"));
    }

    @Test
    @DisplayName("버킷 분류: FINISHED → reading, WANT_TO_READ → wanting")
    void getBookReaders_bucketClassification() throws Exception {
        User me = reg("br-s2@booktimer.com", "brid2", "독서테스터2");
        User readerA = reg("br-s2a@booktimer.com", "bra2", "독자A");
        User readerB = reg("br-s2b@booktimer.com", "brb2", "독자B");
        follow(me, readerA);
        follow(me, readerB);
        book(readerA, TEST_ISBN, BookStatus.FINISHED, BookVisibility.PUBLIC);
        book(readerB, TEST_ISBN, BookStatus.WANT_TO_READ, BookVisibility.PUBLIC);

        mockMvc.perform(get("/api/book-readers?isbn=" + TEST_ISBN)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("br-s2@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reading[0].loginId").value("bra2"))
                .andExpect(jsonPath("$.wanting[0].loginId").value("brb2"));
    }

    @Test
    @DisplayName("isbn 누락 → reading·wanting 모두 빈 배열")
    void getBookReaders_noIsbn_emptyBuckets() throws Exception {
        reg("br-s3@booktimer.com", "brid3", "독서테스터3");

        mockMvc.perform(get("/api/book-readers")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("br-s3@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reading").isEmpty())
                .andExpect(jsonPath("$.wanting").isEmpty());
    }

    @Test
    @DisplayName("isbn='' (blank) → reading·wanting 모두 빈 배열")
    void getBookReaders_blankIsbn_emptyBuckets() throws Exception {
        reg("br-s4@booktimer.com", "brid4", "독서테스터4");

        mockMvc.perform(get("/api/book-readers?isbn=")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("br-s4@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reading").isEmpty())
                .andExpect(jsonPath("$.wanting").isEmpty());
    }

    @Test
    @DisplayName("스코프(IDOR-safe): 팔로우 안 한 사용자는 결과에 없음")
    void getBookReaders_unfollowedUserWithBook_notInResult() throws Exception {
        User me = reg("br-s5@booktimer.com", "brid5", "독서테스터5");
        User stranger = reg("br-s5s@booktimer.com", "brstranger5", "낯선이5");
        book(stranger, TEST_ISBN, BookStatus.READING, BookVisibility.PUBLIC);

        mockMvc.perform(get("/api/book-readers?isbn=" + TEST_ISBN)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("br-s5@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reading").isEmpty())
                .andExpect(jsonPath("$.wanting").isEmpty());
    }

    @Test
    @DisplayName("PRIVATE 책은 결과에서 제외")
    void getBookReaders_privateBook_notInResult() throws Exception {
        User me = reg("br-s6@booktimer.com", "brid6", "독서테스터6");
        User reader = reg("br-s6r@booktimer.com", "brreader6", "독자6");
        follow(me, reader);
        book(reader, TEST_ISBN, BookStatus.READING, BookVisibility.PRIVATE);

        mockMvc.perform(get("/api/book-readers?isbn=" + TEST_ISBN)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("br-s6@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reading").isEmpty())
                .andExpect(jsonPath("$.wanting").isEmpty());
    }

    @Test
    @DisplayName("N-055: login_id=null 팔로우 대상은 결과에서 제외")
    void getBookReaders_nullLoginIdTarget_notInResult() throws Exception {
        User me = reg("br-n055@booktimer.com", "brn055", "N055테스터");
        User nullStateUser = regNull("br-n055null@booktimer.com", "N055널상태");
        follow(me, nullStateUser);
        book(nullStateUser, TEST_ISBN, BookStatus.READING, BookVisibility.PUBLIC);

        var result = mockMvc.perform(get("/api/book-readers?isbn=" + TEST_ISBN)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("br-n055@booktimer.com")))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("\"loginId\":null");
    }
}
