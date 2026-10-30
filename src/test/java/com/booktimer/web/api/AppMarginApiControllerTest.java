package com.booktimer.web.api;

import com.booktimer.auth.ApiTokenService;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 책생글 M1 — 앱 여백(쪽지) API {@code /api/app/margins/**}(설계 §5.1 · REQ-210 · 211 · 218 · 219 · 227) — 실 H2 + Bearer.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AppMarginApiControllerTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired MockMvc mvc;
    @Autowired UserRegistrationService registrationService;
    @Autowired UserRepository userRepository;
    @Autowired ApiTokenService apiTokenService;
    @Autowired BookRepository bookRepository;
    @Autowired StoryRepository storyRepository;
    @Autowired RateLimitService rateLimitService;
    @Autowired Clock clock;

    @BeforeEach
    void clearRateLimits() {
        rateLimitService.clearForTest();
    }

    private User register(String loginId) {
        registrationService.register(loginId + "@booktimer.com", "pw1234qwer!!", loginId, "닉_" + loginId, SEOUL, Role.USER,
                LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL)));
        return userRepository.findByLoginId(loginId).orElseThrow();
    }

    private Book book(User owner) {
        return bookRepository.saveAndFlush(Book.register(owner, "데미안", "헤르만 헤세", null, null, null, null, BookStatus.READING));
    }

    private ResultActions json(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private static String note(Long bookId, String quote, String text) {
        return "{\"bookId\":" + bookId + ",\"quote\":" + str(quote) + ",\"text\":" + str(text) + "}";
    }

    private static String edit(String quote, String text, int revision, Boolean exported) {
        return "{\"quote\":" + str(quote) + ",\"text\":" + str(text) + ",\"revision\":" + revision
                + ",\"exported\":" + exported + "}";
    }

    private static String str(String s) {
        return s == null ? "null" : "\"" + s + "\"";
    }

    private Long create(String token, String body) throws Exception {
        String res = json(post("/api/app/margins"), token, body).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(res.replaceAll("(?s)^\\{\"id\":(\\d+),.*", "$1"));
    }

    @Test
    @DisplayName("REQ-210 · 생각 10,200자 저장 · 10,201자 400 · 둘 다 비면 400 · bookId 없이 200")
    void lengthAndPresenceRules() throws Exception {
        User me = register("mlen");
        String token = apiTokenService.issue(me);
        Long bookId = book(me).getId();

        Long id = create(token, note(bookId, null, "가".repeat(10_200)));
        assertThat(storyRepository.findById(id).orElseThrow().getText()).hasSize(10_200);
        json(post("/api/app/margins"), token, note(bookId, null, "가".repeat(10_201)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN));
        json(post("/api/app/margins"), token, note(bookId, " ", "  ")).andExpect(status().isBadRequest());
        Long bookless = create(token, note(null, null, "책 없이 끄적인 생각"));
        assertThat(storyRepository.findById(bookless).orElseThrow().getBook()).isNull();
    }

    @Test
    @DisplayName("REQ-210 · bookId 없이 quote를 보내면 400 — 출처 없는 책 문장이 생기지 않는다(J84)")
    void booklessQuoteIs400() throws Exception {
        User me = register("mbq");
        String token = apiTokenService.issue(me);
        long before = storyRepository.count();

        json(post("/api/app/margins"), token, note(null, "새는 알에서 나오려고 투쟁한다", "생각"))
                .andExpect(status().isBadRequest());
        assertThat(storyRepository.count()).isEqualTo(before);
        // 양성 대조 — 책이 있으면 같은 몸이 된다 · 출처 스냅샷이 붙는다
        Long id = create(token, note(book(me).getId(), "새는 알에서 나오려고 투쟁한다", "생각"));
        assertThat(storyRepository.findById(id).orElseThrow().getQuoteSource()).isEqualTo("『데미안』 · 헤르만 헤세");
    }

    @Test
    @DisplayName("REQ-210 · AI 기록만 있는 옮긴 쪽지는 빈 글로 저장 200(양성 대조: AI 기록 없는 쪽지는 400)")
    void aiOnlyNoteMayBeEmptied() throws Exception {
        User me = register("mai");
        String token = apiTokenService.issue(me);
        Long withAi = create(token, note(null, null, "옮겨 온 글"));
        Story s = storyRepository.findById(withAi).orElseThrow();
        s.recordRound("{\"rounds\":[]}", 10, Instant.parse("2026-11-23T00:00:00Z"));
        storyRepository.saveAndFlush(s);
        Long plain = create(token, note(null, null, "AI 기록 없는 쪽지"));

        json(put("/api/app/margins/" + withAi), token, edit(null, "", 0, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value(""))
                .andExpect(jsonPath("$.revision").value(1));
        json(put("/api/app/margins/" + plain), token, edit(null, "", 0, null)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("REQ-211 · 낡은 판으로 고치면 409, 생각 그대로")
    void staleRevisionIs409() throws Exception {
        User me = register("mstale");
        String token = apiTokenService.issue(me);
        Long id = create(token, note(null, null, "처음"));
        json(put("/api/app/margins/" + id), token, edit(null, "두 번째", 0, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(1));

        json(put("/api/app/margins/" + id), token, edit(null, "낡은 판의 덮어쓰기", 0, null))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN));
        Story after = storyRepository.findById(id).orElseThrow();
        assertThat(after.getText()).isEqualTo("두 번째");
        assertThat(after.getRevision()).isEqualTo(1);
    }

    @Test
    @DisplayName("REQ-218 · 쪽지 만들기 31번째 429(30번째까지 201)")
    void thirtyFirstCreateIs429() throws Exception {
        User me = register("mrate");
        String token = apiTokenService.issue(me);
        for (int i = 1; i <= 30; i++) {
            json(post("/api/app/margins"), token, note(null, null, "쪽지 " + i)).andExpect(status().isCreated());
        }
        json(post("/api/app/margins"), token, note(null, null, "쪽지 31")).andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("REQ-219 · 책 없는 쪽지가 ?book=none에 — 책 있는 쪽지 · 남의 책 없는 쪽지는 없다")
    void booklessListing() throws Exception {
        User me = register("mnone");
        User other = register("mnoneother");
        String token = apiTokenService.issue(me);
        Long bookless = create(token, note(null, null, "책 없는 쪽지"));
        create(token, note(book(me).getId(), null, "책 있는 쪽지"));
        create(apiTokenService.issue(other), note(null, null, "남의 책 없는 쪽지"));

        call(get("/api/app/margins").param("book", "none"), token).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(bookless));
    }

    @Test
    @DisplayName("M1 · 목록 미리보기 80자 자르기가 이모지(서로게이트 쌍)를 반으로 가르지 않는다")
    void previewDoesNotSplitSurrogatePair() throws Exception {
        String token = apiTokenService.issue(register("mprev"));
        create(token, note(null, null, "가".repeat(79) + "😀" + "나"));

        call(get("/api/app/margins").param("book", "none"), token).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].preview").value("가".repeat(79)));
    }

    @Test
    @DisplayName("REQ-219 · 남의 쪽지는 조회 · 고치기 · 지우기 모두 404(AI는 동의 문턱이 먼저 — WritingApiControllerAiTest status404)")
    void othersNoteIs404() throws Exception {
        User owner = register("mowner");
        String ownerToken = apiTokenService.issue(owner);
        String thief = apiTokenService.issue(register("mthief"));
        Long id = create(ownerToken, note(book(owner).getId(), null, "주인의 생각"));

        call(get("/api/app/margins/" + id), thief).andExpect(status().isNotFound());
        json(put("/api/app/margins/" + id), thief, edit(null, "덮어쓰기", 0, null)).andExpect(status().isNotFound());
        call(delete("/api/app/margins/" + id), thief).andExpect(status().isNotFound());
        assertThat(storyRepository.findById(id).orElseThrow().getText()).isEqualTo("주인의 생각");
        // 책 없는 쪽지도 같은 판정 — 책을 거쳐 주인을 고르지 않는다(옛 좋아요 경로 포함)
        Long bookless = create(ownerToken, note(null, null, "주인의 책 없는 쪽지"));
        call(get("/api/app/margins/" + bookless), thief).andExpect(status().isNotFound());
        json(put("/api/app/margins/" + bookless), thief, edit(null, "덮어쓰기", 0, null)).andExpect(status().isNotFound());
        call(delete("/api/app/margins/" + bookless), thief).andExpect(status().isNotFound());
        mvc.perform(post("/api/stories/" + bookless + "/like").with(user("mthief@booktimer.com")).with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(storyRepository.findById(bookless).orElseThrow().getText()).isEqualTo("주인의 책 없는 쪽지");
        mvc.perform(post("/api/stories/" + bookless + "/like").with(user("mowner@booktimer.com")).with(csrf()))
                .andExpect(status().isOk()); // 양성 대조 — 주인은 누를 수 있다
        // 양성 대조 — 주인은 읽고 지울 수 있다
        call(get("/api/app/margins/" + id), ownerToken).andExpect(status().isOk()).andExpect(jsonPath("$.text").value("주인의 생각"));
        call(delete("/api/app/margins/" + id), ownerToken).andExpect(status().isNoContent());
        assertThat(storyRepository.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("REQ-227 · exported:true면 exported_at — 빠지면 그대로 null")
    void exportedMarksOnce() throws Exception {
        User me = register("mexp");
        String token = apiTokenService.issue(me);
        Long id = create(token, note(null, null, "내보낼 생각"));

        json(put("/api/app/margins/" + id), token, edit(null, "내보낼 생각", 0, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.exported").value(false));
        assertThat(storyRepository.findById(id).orElseThrow().getExportedAt()).isNull();
        json(put("/api/app/margins/" + id), token, edit(null, "내보낼 생각", 1, true)).andExpect(status().isOk())
                .andExpect(jsonPath("$.exported").value(true));
        assertThat(storyRepository.findById(id).orElseThrow().getExportedAt()).isNotNull();
    }

    @Test
    @DisplayName("M1 · 책 쪽지 목록은 그 책 쪽지만 최근순 — 남의 책 id는 404")
    void bookListing() throws Exception {
        User me = register("mbook");
        String token = apiTokenService.issue(me);
        Book mine = book(me);
        Long first = create(token, note(mine.getId(), null, "첫 쪽지"));
        Long second = create(token, note(mine.getId(), null, "둘째 쪽지"));
        create(token, note(null, null, "책 없는 쪽지"));
        Book others = book(register("mbookother"));

        call(get("/api/app/books/" + mine.getId() + "/margins"), token).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(second))
                .andExpect(jsonPath("$[1].id").value(first));
        call(get("/api/app/books/" + others.getId() + "/margins"), token).andExpect(status().isNotFound());
    }
}
