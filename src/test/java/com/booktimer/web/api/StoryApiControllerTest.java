package com.booktimer.web.api;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.security.RateLimitService;
import com.booktimer.story.Story;
import com.booktimer.story.StoryRepository;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StoryApiControllerTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRegistrationService registrationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StoryRepository storyRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private Clock clock;

    @BeforeEach
    void clearRateLimits() {
        rateLimitService.clearForTest();
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    private User register(String email, String loginId, String nickname) {
        registrationService.register(email, "pw1234qwer!!", loginId, nickname, SEOUL, Role.USER, today());
        return userRepository.findByEmail(email).orElseThrow();
    }

    private Book publicBookOf(User owner, String title) {
        Book book = Book.register(owner, title, null, null, null, null, null, BookStatus.READING);
        book.makePublic();
        return bookRepository.save(book);
    }

    @Test
    @DisplayName("POST /api/stories 미인증 → 302 로그인 리다이렉트 (기본 잠김)")
    void create_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(post("/api/stories").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"문장\",\"bookId\":1}"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("POST /api/stories CSRF 없으면 403")
    void create_withoutCsrf_returns403() throws Exception {
        register("story-csrf@booktimer.com", "storycsrf", "작성자");

        mockMvc.perform(post("/api/stories")
                        .with(user("story-csrf@booktimer.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"문장\",\"bookId\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/stories 인증+csrf → 200, 남긴 글 반환")
    void create_authenticated_returnsEntry() throws Exception {
        User me = register("story-author@booktimer.com", "storyauthor", "작성자");
        Book book = publicBookOf(me, "여백이 열린 책");

        mockMvc.perform(post("/api/stories")
                        .with(user("story-author@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"인상 깊은 문장\",\"bookId\":" + book.getId() + ",\"bgCode\":\"night\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("인상 깊은 문장"))
                .andExpect(jsonPath("$.bgCode").value("night"))
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    @DisplayName("POST /api/stories bookId 없음 → 400 (여백은 책에 귀속)")
    void create_withoutBookId_returns400() throws Exception {
        register("story-nobook@booktimer.com", "storynobook", "작성자");

        mockMvc.perform(post("/api/stories")
                        .with(user("story-nobook@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"책 없는 문장\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/stories 남의 책에 글 남기기 → 404 (IDOR — 존재 누설 금지)")
    void create_othersBook_returns404() throws Exception {
        User owner = register("story-owner@booktimer.com", "storyowner", "주인");
        register("story-intruder@booktimer.com", "storyintruder", "침입자");
        Book book = publicBookOf(owner, "남의 책");

        mockMvc.perform(post("/api/stories")
                        .with(user("story-intruder@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"남의 여백에 낙서\",\"bookId\":" + book.getId() + "}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/stories 도메인 검증 실패(팔레트 밖 bgCode) → 400")
    void create_invalidBgCode_returns400() throws Exception {
        User me = register("story-bad@booktimer.com", "storybad", "작성자");
        Book book = publicBookOf(me, "책");

        mockMvc.perform(post("/api/stories")
                        .with(user("story-bad@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"문장\",\"bookId\":" + book.getId() + ",\"bgCode\":\"#ff0000\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /api/stories/{id} 타인 글 → 404 (IDOR)")
    void delete_othersStory_returns404() throws Exception {
        User author = register("del-author@booktimer.com", "delauthor", "작성자");
        register("del-actor@booktimer.com", "delactor", "삭제시도자");
        Story story = storyRepository.save(Story.of(author, "남의 문장", publicBookOf(author, "책"), null));

        mockMvc.perform(delete("/api/stories/" + story.getId())
                        .with(user("del-actor@booktimer.com")).with(csrf()))
                .andExpect(status().isNotFound());
    }
}
