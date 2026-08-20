package com.booktimer.web.api;

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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * GET /api/explore 컨트롤러 통합 테스트 — 미니앱 「둘러보기」 전용 경로.
 *
 * <p>{@code /api/search}와 <b>따로 낸 이유</b>: 그 응답은 웹 프론트(UserSearchPanel.vue)가 함께 쓰고 있어,
 * 셔플·책 4권·공개책 0권 제외 같은 미니앱 전용 규칙을 얹으면 웹 동작까지 바뀐다. 여기는 미니앱만 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ExploreApiControllerTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRegistrationService registrationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private Clock clock;

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    private User register(String email, String loginId, String nickname) {
        registrationService.register(email, "pw1234qwer!!", loginId, nickname, SEOUL, Role.USER, today());
        return userRepository.findByLoginId(loginId).orElseThrow();
    }

    private void publicBook(User owner, String title, String coverUrl) {
        Book b = Book.register(owner, title, null, null, coverUrl, null, null, BookStatus.READING);
        b.makePublic();
        bookRepository.save(b);
    }

    @Test
    @DisplayName("GET /api/explore 미인증 → 302 로그인 리다이렉트 (default-deny)")
    void getExplore_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/api/explore"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("GET /api/explore 인증 → 200 JSON (users 배열 + rateLimited 플래그)")
    void getExplore_authenticated_returnsJsonStructure() throws Exception {
        register("explore-me@booktimer.com", "expmeid", "나");

        mockMvc.perform(get("/api/explore")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("explore-me@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.users").isArray())
                .andExpect(jsonPath("$.rateLimited").isBoolean());
    }

    @Test
    @DisplayName("카드 한 장에 사람 한 줄 + 그 사람의 공개 책이 표지 주소와 함께 실린다")
    void getExplore_carriesUserRowAndBooks() throws Exception {
        register("explore-me2@booktimer.com", "expmeid2", "나");
        User other = register("explore-o2@booktimer.com", "expotherid", "이웃");
        publicBook(other, "이웃의책", "https://cover/other.jpg");

        mockMvc.perform(get("/api/explore")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("explore-me2@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users", hasSize(1)))
                .andExpect(jsonPath("$.users[0].loginId").value("expotherid"))
                .andExpect(jsonPath("$.users[0].nickname").value("이웃"))
                .andExpect(jsonPath("$.users[0].publicBookCount").value(1))
                .andExpect(jsonPath("$.users[0].following").value(false))
                .andExpect(jsonPath("$.users[0].self").value(false))
                .andExpect(jsonPath("$.users[0].books", hasSize(1)))
                .andExpect(jsonPath("$.users[0].books[0].title").value("이웃의책"))
                .andExpect(jsonPath("$.users[0].books[0].coverUrl").value("https://cover/other.jpg"));
    }

    @Test
    @DisplayName("공개 책이 0권인 사람은 둘러보기에 뜨지 않는다(빈 카드 방지)")
    void getExplore_excludesUsersWithoutPublicBooks() throws Exception {
        register("explore-me3@booktimer.com", "expmeid3", "나");
        User bookless = register("explore-nb@booktimer.com", "expnobookid", "책없음");
        bookRepository.save(Book.register(bookless, "비공개책", null, null, null, null, null, BookStatus.READING));

        var result = mockMvc.perform(get("/api/explore")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("explore-me3@booktimer.com")))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("expnobookid");
    }

    @Test
    @DisplayName("시점 정보를 응답에 담지 않는다 — 「언제 읽었는가」는 둘러보기에서 노출하지 않는다")
    void getExplore_doesNotLeakReadingTimes() throws Exception {
        register("explore-me4@booktimer.com", "expmeid4", "나");
        User other = register("explore-o4@booktimer.com", "expotherid4", "이웃");
        publicBook(other, "책", null);

        var result = mockMvc.perform(get("/api/explore")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("explore-me4@booktimer.com")))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("seconds").doesNotContain("lastReadAt").doesNotContain("readingSince");
    }

    @Test
    @DisplayName("N-055: login_id=null 사용자(온보딩 전)는 책이 있어도 둘러보기에 새지 않는다")
    void getExplore_nullLoginIdUser_notLeaked() throws Exception {
        register("explore-me5@booktimer.com", "expmeid5", "나");
        registrationService.register("explore-null@booktimer.com", "pw1234qwer!!", "널상태유저", SEOUL, Role.USER, today());
        User pending = userRepository.findByEmail("explore-null@booktimer.com").orElseThrow();
        publicBook(pending, "핸들없는이의책", null);

        var result = mockMvc.perform(get("/api/explore")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("explore-me5@booktimer.com")))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("\"loginId\":null").doesNotContain("핸들없는이의책");
    }
}
