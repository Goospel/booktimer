package com.booktimer.web.api;

import com.booktimer.auth.ApiTokenService;
import com.booktimer.blog.BlogAccess;
import com.booktimer.blog.QuoteBox;
import com.booktimer.blog.QuoteBoxRepository;
import com.booktimer.block.Block;
import com.booktimer.block.BlockRepository;
import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.security.RateLimitService;
import com.booktimer.story.Story;
import com.booktimer.story.StoryRepository;
import com.booktimer.user.AppleUserProvisioningService;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import com.booktimer.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 책생글 B1b — 쪽지에 블로그 문장 걸기 · 붙이기 · 빼기 · 문장 나누기 API · 옛 JS 삭제 가드 · 쪽지 응답의 박스
 * (계획 2026-11-11-b1-blog-core §5.2 · §6.6 · §4.7 · §4.8 · §7 REQ-206 · 207 · 214 · 244 · 289 · 292) — 실 H2 + Bearer.
 *
 * <p>허용 목록 닫힌 쪽은 REQ-244 시험만 {@code ReflectionTestUtils}로 바꾸고 finally로 되돌린다(새 컨텍스트 금지 — T-235).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AppMarginLinkApiTest {

    private static final String SEOUL = "Asia/Seoul";
    private static final String CLOSED = "블로그 기능은 12월 1일부터 열려요";

    @Autowired MockMvc mvc;
    @Autowired UserRegistrationService registrationService;
    @Autowired AppleUserProvisioningService appleProvisioning;
    @Autowired UserRepository userRepository;
    @Autowired ApiTokenService apiTokenService;
    @Autowired QuoteBoxRepository quoteBoxRepository;
    @Autowired StoryRepository storyRepository;
    @Autowired BookRepository bookRepository;
    @Autowired BlockRepository blockRepository;
    @Autowired BlogAccess blogAccess;
    @Autowired RateLimitService rateLimitService;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired Clock clock;

    @BeforeEach
    void clearRateLimits() {
        rateLimitService.clearForTest();
    }

    // ── 도우미 ──────────────────────────────────────────────────────────────

    private String ready(String loginId) {
        registrationService.register(loginId + "@booktimer.com", "pw1234qwer!!", loginId, "닉_" + loginId, SEOUL, Role.USER,
                LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL)));
        User u = user(loginId);
        u.confirmBlogIdentity(Instant.now());
        userRepository.saveAndFlush(u);
        return apiTokenService.issue(u);
    }

    private User user(String loginId) {
        return userRepository.findByLoginId(loginId).orElseThrow();
    }

    private ResultActions json(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private static String str(String s) {
        return s == null ? "null" : "\"" + s.replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private static long id(String res) {
        return ((Number) JsonPath.read(res, "$.id")).longValue();
    }

    /** 올린 글 하나(문장 셋) · 그 sid. */
    private record Src(long id, List<String> sids) {
    }

    private Src source(String token, String body) throws Exception {
        String res = json(post("/api/app/blog/posts"), token, "{\"draftTitle\":\"원 글\",\"draftBody\":" + str(body) + "}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long post = id(res);
        String view = json(post("/api/app/blog/posts/" + post + "/publish"), token, "{\"revision\":0}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new Src(post, JsonPath.read(view, "$.blocks[*].sentences[*].sid"));
    }

    private static String link(long postId, String from, String to) {
        return "{\"postId\":" + postId + ",\"fromSid\":" + str(from) + ",\"toSid\":" + str(to) + "}";
    }

    private static String hangBody(Long bookId, String text, String link) {
        return "{\"bookId\":" + bookId + ",\"quote\":null,\"text\":" + str(text) + ",\"link\":" + link + "}";
    }

    private ResultActions hang(String token, Long bookId, String text, String link) throws Exception {
        return json(post("/api/app/margins"), token, hangBody(bookId, text, link));
    }

    /** 문장을 걸어 새 쪽지를 만들고 그 쪽지 id(201 단언). */
    private long hung(String token, String text, Src src, int from, int to) throws Exception {
        return id(hang(token, null, text, link(src.id(), src.sids().get(from), src.sids().get(to)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private ResultActions attach(String token, long noteId, Src src, int from, int to) throws Exception {
        return json(post("/api/app/margins/" + noteId + "/quotes"), token, link(src.id(), src.sids().get(from), src.sids().get(to)));
    }

    private List<Long> boxIds(String token, long noteId) throws Exception {
        List<Number> ids = JsonPath.read(call(get("/api/app/margins/" + noteId), token).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.quotes[*].id");
        return ids.stream().map(Number::longValue).toList();
    }

    private Story note(String loginId, String text) {
        return storyRepository.saveAndFlush(Story.of(user(loginId), text, null, null));
    }

    private Book book(String loginId) {
        return bookRepository.saveAndFlush(Book.register(user(loginId), "데미안", "헤르만 헤세", null, null, null, null, BookStatus.READING));
    }

    private long storiesOf(String loginId) {
        em.flush();
        return jdbc.queryForObject("select count(*) from story where user_id = ?", Long.class, user(loginId).getId());
    }

    private boolean boxExists(long quoteId) {
        em.flush();
        return jdbc.queryForObject("select count(*) from quote_box where id = ?", Long.class, quoteId) > 0;
    }

    private long noteBoxes(long noteId) {
        return quoteBoxRepository.countByOwnerTypeAndOwnerId(QuoteBox.Owner.NOTE, noteId);
    }

    // ── REQ-214 문장 걸기 ───────────────────────────────────────────────────

    @Test
    @DisplayName("REQ-214 · 건 문장만 있는 쪽지 201")
    void linkedOnlyNote() throws Exception {
        String a = ready("lk214a");
        String b = ready("lk214b");
        Src src = source(a, "봄에는 꽃이 핀다. 여름에는 비가 온다. 가을에는 낙엽이 진다.");

        String res = hang(b, null, null, link(src.id(), src.sids().get(0), src.sids().get(1))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.text").value("")).andExpect(jsonPath("$.quote").doesNotExist())
                .andExpect(jsonPath("$.quotes", hasSize(1))).andExpect(jsonPath("$.quotes[0].label").value(1))
                .andExpect(jsonPath("$.quotes[0].kind").value("BLOG"))
                .andExpect(jsonPath("$.quotes[0].text").value("봄에는 꽃이 핀다. 여름에는 비가 온다."))
                .andExpect(jsonPath("$.quotes[0].attribution").value("닉_lk214a · 「원 글」"))
                .andReturn().getResponse().getContentAsString();
        assertThat(noteBoxes(id(res))).isEqualTo(1);
    }

    @Test
    @DisplayName("REQ-214 · 남의 책 id로 걸기 404(쪽지 0)")
    void othersBook404() throws Exception {
        String a = ready("lk214o");
        String b = ready("lk214ob");
        ready("lk214oc");
        Src src = source(a, "봄에는 꽃이 핀다.");
        String l = link(src.id(), src.sids().get(0), src.sids().get(0));

        hang(b, book("lk214oc").getId(), "생각", l).andExpect(status().isNotFound());
        assertThat(storiesOf("lk214ob")).isZero();
        hang(b, book("lk214ob").getId(), "생각", l).andExpect(status().isCreated()); // 양성 대조 — 내 책
    }

    @Test
    @DisplayName("REQ-214 · 초안 문장 404 · 차단 관계 403")
    void draftAndBlocked() throws Exception {
        String a = ready("lk214d");
        String b = ready("lk214db");
        String draft = json(post("/api/app/blog/posts"), a, "{\"draftTitle\":\"초안\",\"draftBody\":\"아직이다.\"}")
                .andReturn().getResponse().getContentAsString();
        hang(b, null, "생각", link(id(draft), "AAAAAAAA", "AAAAAAAA")).andExpect(status().isNotFound())
                .andExpect(content().string("글을 찾을 수 없어요"));

        Src src = source(a, "봄에는 꽃이 핀다.");
        blockRepository.saveAndFlush(Block.of(user("lk214db"), user("lk214d")));
        hang(b, null, "생각", link(src.id(), src.sids().get(0), src.sids().get(0))).andExpect(status().isForbidden())
                .andExpect(content().string("이 글의 문장은 인용할 수 없어요"));
        assertThat(storiesOf("lk214db")).isZero();
    }

    @Test
    @DisplayName("REQ-214 · 내 가린 글 문장 인용 · 걸기 404")
    void ownHiddenPost404() throws Exception {
        String b = ready("lk214h");
        Src src = source(b, "봄에는 꽃이 핀다.");
        String l = link(src.id(), src.sids().get(0), src.sids().get(0));
        String quote = "{\"kind\":\"BLOG\",\"postId\":" + src.id() + ",\"fromSid\":\"" + src.sids().get(0) + "\",\"toSid\":\""
                + src.sids().get(0) + "\"}";
        hang(b, null, "생각", l).andExpect(status().isCreated()); // 양성 대조 — 가리기 전엔 201
        json(post("/api/app/blog/posts/" + src.id() + "/quotes"), b, quote).andExpect(status().isCreated());

        em.flush();
        jdbc.update("update blog_post set hidden_at = ? where id = ?", Timestamp.from(Instant.now()), src.id());
        em.clear();
        hang(b, null, "생각", l).andExpect(status().isNotFound());
        json(post("/api/app/blog/posts/" + src.id() + "/quotes"), b, quote).andExpect(status().isNotFound())
                .andExpect(content().string("글을 찾을 수 없어요"));
    }

    @Test
    @DisplayName("REQ-214 · 쪽지를 지우면(withQuotes) 건 박스 행 0")
    void deleteWithQuotesRemovesBoxes() throws Exception {
        String a = ready("lk214w");
        String b = ready("lk214wb");
        Src src = source(a, "봄에는 꽃이 핀다. 여름에는 비가 온다.");
        long n = hung(b, "생각이다.", src, 0, 0);
        attach(b, n, src, 1, 1).andExpect(status().isCreated());
        assertThat(noteBoxes(n)).isEqualTo(2);

        call(delete("/api/app/margins/" + n + "?withQuotes=true"), b).andExpect(status().isNoContent());
        assertThat(noteBoxes(n)).isZero();
        assertThat(storiesOf("lk214wb")).isZero();
    }

    @Test
    @DisplayName("엣지 · 걸기 몸의 문장이 틀리면 400 · 쪽지 0")
    void hangBadSids() throws Exception {
        String a = ready("lk214s");
        String b = ready("lk214sb");
        Src src = source(a, "봄에는 꽃이 핀다.");
        hang(b, null, "생각", link(src.id(), "ZZZZZZZZ", src.sids().get(0))).andExpect(status().isBadRequest())
                .andExpect(content().string("고른 문장을 찾을 수 없어요 — 글을 다시 불러와 주세요"));
        hang(b, null, "생각", "{\"fromSid\":\"a\"}").andExpect(status().isNotFound());
        assertThat(storiesOf("lk214sb")).isZero();
    }

    // ── REQ-206 옛 JS 삭제 가드 ─────────────────────────────────────────────

    @Test
    @DisplayName("REQ-206 · 박스가 있는 쪽지 DELETE(플래그 없음) 409 · 쪽지 · 박스 그대로")
    void deleteWithoutFlag409() throws Exception {
        String a = ready("lk206a");
        String b = ready("lk206b");
        Src src = source(a, "봄에는 꽃이 핀다.");
        long n = hung(b, "생각이다.", src, 0, 0);

        call(delete("/api/app/margins/" + n), b).andExpect(status().isConflict())
                .andExpect(content().string("건 문장이 있는 쪽지예요 — 앱을 껐다 다시 켠 뒤 지워 주세요"));
        call(delete("/api/app/margins/" + n + "?withQuotes=false"), b).andExpect(status().isConflict());
        assertThat(storyRepository.findById(n)).isPresent();
        assertThat(noteBoxes(n)).isEqualTo(1);
        call(delete("/api/app/margins/" + n + "?withQuotes=true"), b).andExpect(status().isNoContent()); // 양성 대조
        assertThat(noteBoxes(n)).isZero();
    }

    @Test
    @DisplayName("REQ-206 · 박스 없는 쪽지는 플래그 없이 204")
    void deletePlainNote() throws Exception {
        String b = ready("lk206p");
        Story n = note("lk206p", "그냥 생각.");
        call(delete("/api/app/margins/" + n.getId()), b).andExpect(status().isNoContent());
        assertThat(storyRepository.findById(n.getId())).isEmpty();
    }

    @Test
    @DisplayName("REQ-206 · 남의 쪽지는 플래그와 무관하게 404")
    void deleteOthersNote404() throws Exception {
        String a = ready("lk206o");
        String b = ready("lk206ob");
        String c = ready("lk206oc");
        Src src = source(a, "봄에는 꽃이 핀다.");
        long n = hung(b, "생각이다.", src, 0, 0);

        call(delete("/api/app/margins/" + n), c).andExpect(status().isNotFound());
        call(delete("/api/app/margins/" + n + "?withQuotes=true"), c).andExpect(status().isNotFound());
        assertThat(noteBoxes(n)).isEqualTo(1);
    }

    @Test
    @DisplayName("REQ-206 · 웹 DELETE /api/stories/{id}는 박스째 지운다")
    void webDeleteTakesBoxes() throws Exception {
        String a = ready("lk206w");
        String b = ready("lk206wb");
        Src src = source(a, "봄에는 꽃이 핀다.");
        long n = hung(b, "생각이다.", src, 0, 0);

        mvc.perform(delete("/api/stories/" + n).with(SecurityMockMvcRequestPostProcessors.user("lk206wb@booktimer.com")).with(csrf()))
                .andExpect(status().isOk());
        assertThat(noteBoxes(n)).isZero();
        assertThat(storyRepository.findById(n)).isEmpty();
    }

    // ── REQ-207 쪽지 응답의 박스 ────────────────────────────────────────────

    @Test
    @DisplayName("REQ-207 · 쪽지 GET에 quotes(label 순) · 목록에 quoteCount")
    void quotesInViews() throws Exception {
        String a = ready("lk207a");
        String b = ready("lk207b");
        Src src = source(a, "봄에는 꽃이 핀다. 여름에는 비가 온다.");
        long n = hung(b, "생각이다.", src, 1, 1);
        attach(b, n, src, 0, 0).andExpect(status().isCreated());
        Story plain = note("lk207b", "박스 없는 쪽지.");
        Book bk = book("lk207b");
        long onBook = id(hang(b, bk.getId(), "책 쪽지.", link(src.id(), src.sids().get(0), src.sids().get(0)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        call(get("/api/app/margins/" + n), b).andExpect(jsonPath("$.quotes", hasSize(2)))
                .andExpect(jsonPath("$.quotes[0].label").value(1)).andExpect(jsonPath("$.quotes[0].text").value("여름에는 비가 온다."))
                .andExpect(jsonPath("$.quotes[1].label").value(2)).andExpect(jsonPath("$.quotes[1].text").value("봄에는 꽃이 핀다."));
        String list = call(get("/api/app/margins?book=none"), b).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(list, "$[?(@.id == " + n + ")].quoteCount")).containsExactly(2);
        assertThat(JsonPath.<List<Integer>>read(list, "$[?(@.id == " + plain.getId() + ")].quoteCount")).containsExactly(0);
        call(get("/api/app/books/" + bk.getId() + "/margins"), b).andExpect(jsonPath("$[0].id").value(onBook))
                .andExpect(jsonPath("$[0].quoteCount").value(1));
    }

    @Test
    @DisplayName("REQ-207 · 박스가 있으면 옮긴 문장 · 생각을 다 비운 PUT 200(양성 대조: 박스 없으면 400)")
    void emptyPutWithBoxes() throws Exception {
        String a = ready("lk207p");
        String b = ready("lk207pb");
        Src src = source(a, "봄에는 꽃이 핀다.");
        long n = hung(b, "생각이다.", src, 0, 0);
        Story plain = note("lk207pb", "박스 없는 쪽지.");
        String empty = "{\"quote\":null,\"text\":\"\",\"revision\":0}";

        json(put("/api/app/margins/" + n), b, empty).andExpect(status().isOk()).andExpect(jsonPath("$.text").value(""))
                .andExpect(jsonPath("$.quotes", hasSize(1)));
        json(put("/api/app/margins/" + plain.getId()), b, empty).andExpect(status().isBadRequest())
                .andExpect(content().string("옮긴 문장이나 생각을 하나는 적어 주세요"));
    }

    // ── REQ-289 붙이기 · 빼기 ───────────────────────────────────────────────

    @Test
    @DisplayName("REQ-289 · 기존 쪽지에 박스 더하기 201 · 둘째 박스 label 2")
    void attachToExisting() throws Exception {
        String a = ready("lk289a");
        String b = ready("lk289b");
        Src src = source(a, "봄에는 꽃이 핀다. 여름에는 비가 온다.");
        Story n = note("lk289b", "내 생각이다.");

        attach(b, n.getId(), src, 0, 0).andExpect(status().isCreated()).andExpect(jsonPath("$.quotes", hasSize(1)))
                .andExpect(jsonPath("$.quotes[0].label").value(1));
        attach(b, n.getId(), src, 1, 1).andExpect(status().isCreated()).andExpect(jsonPath("$.quotes[1].label").value(2))
                .andExpect(jsonPath("$.text").value("내 생각이다."));
    }

    @Test
    @DisplayName("REQ-289 · 11번째 박스 400")
    void tenBoxCap() throws Exception {
        String a = ready("lk289c");
        String b = ready("lk289cb");
        Src src = source(a, "봄에는 꽃이 핀다.");
        Story n = note("lk289cb", "생각.");
        List<QuoteBox> rows = new ArrayList<>();
        for (int i = 1; i <= 9; i++) {
            rows.add(QuoteBox.blog(QuoteBox.Owner.NOTE, n.getId(), i, src.id(), user("lk289c").getId(), src.sids(), 0,
                    "봄에는 꽃이 핀다.", "원 글", Instant.now()));
        }
        quoteBoxRepository.saveAllAndFlush(rows);

        attach(b, n.getId(), src, 0, 0).andExpect(status().isCreated()).andExpect(jsonPath("$.quotes[9].label").value(10));
        attach(b, n.getId(), src, 0, 0).andExpect(status().isBadRequest()).andExpect(content().string("한 쪽지에 인용은 10개까지예요"));
        assertThat(noteBoxes(n.getId())).isEqualTo(10);
    }

    @Test
    @DisplayName("REQ-289 · 남의 쪽지에 더하기 404")
    void attachToOthers404() throws Exception {
        String a = ready("lk289o");
        String b = ready("lk289ob");
        Src src = source(a, "봄에는 꽃이 핀다.");
        Story theirs = note("lk289o", "남의 생각.");

        attach(b, theirs.getId(), src, 0, 0).andExpect(status().isNotFound());
        assertThat(noteBoxes(theirs.getId())).isZero();
        attach(a, theirs.getId(), src, 0, 0).andExpect(status().isCreated()); // 양성 대조 — 주인
    }

    @Test
    @DisplayName("REQ-289 · 박스 빼기 뒤 박스 행 0")
    void removeBox() throws Exception {
        String a = ready("lk289r");
        String b = ready("lk289rb");
        Src src = source(a, "봄에는 꽃이 핀다.");
        long n = hung(b, "생각이다.", src, 0, 0);
        long q = boxIds(b, n).get(0);

        call(delete("/api/app/margins/" + n + "/quotes/" + q), b).andExpect(status().isOk())
                .andExpect(jsonPath("$.quotes", hasSize(0))).andExpect(jsonPath("$.text").value("생각이다."));
        assertThat(boxExists(q)).isFalse();
    }

    @Test
    @DisplayName("REQ-289 · 내 쪽지 경로로 남의 글 박스 id 빼기 404(그 박스 행 그대로)")
    void removeOthersPostBox404() throws Exception {
        String a = ready("lk289p");
        String b = ready("lk289pb");
        Src src = source(a, "봄에는 꽃이 핀다.");
        String created = json(post("/api/app/blog/posts/" + src.id() + "/quotes"), a,
                "{\"kind\":\"BLOG\",\"postId\":" + src.id() + ",\"fromSid\":\"" + src.sids().get(0) + "\",\"toSid\":\""
                        + src.sids().get(0) + "\"}").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long postBox = id(created);
        long n = hung(b, "생각이다.", src, 0, 0);

        call(delete("/api/app/margins/" + n + "/quotes/" + postBox), b).andExpect(status().isNotFound())
                .andExpect(content().string("인용을 찾을 수 없어요"));
        assertThat(boxExists(postBox)).isTrue();
    }

    @Test
    @DisplayName("REQ-289 · 내 쪽지 경로로 내 다른 쪽지의 박스 id 빼기 404")
    void removeOtherNotesBox404() throws Exception {
        String a = ready("lk289n");
        String b = ready("lk289nb");
        Src src = source(a, "봄에는 꽃이 핀다.");
        long n1 = hung(b, "첫 쪽지.", src, 0, 0);
        long n2 = hung(b, "둘째 쪽지.", src, 0, 0);
        long other = boxIds(b, n2).get(0);

        call(delete("/api/app/margins/" + n1 + "/quotes/" + other), b).andExpect(status().isNotFound());
        assertThat(boxExists(other)).isTrue();
        call(delete("/api/app/margins/" + n2 + "/quotes/" + other), b).andExpect(status().isOk()); // 양성 대조
    }

    @Test
    @DisplayName("REQ-289 · 박스 하나뿐인 빈 쪽지에서 빼기 400")
    void removeLastFromEmptyNote400() throws Exception {
        String a = ready("lk289e");
        String b = ready("lk289eb");
        Src src = source(a, "봄에는 꽃이 핀다.");
        long n = hung(b, null, src, 0, 0);
        long q = boxIds(b, n).get(0);

        call(delete("/api/app/margins/" + n + "/quotes/" + q), b).andExpect(status().isBadRequest())
                .andExpect(content().string("쪽지가 비어요 — 쪽지를 지워 주세요"));
        assertThat(boxExists(q)).isTrue();
    }

    @Test
    @DisplayName("REQ-289 · 허용 목록 밖 403")
    void attachClosed403() throws Exception {
        String a = ready("lk289x");
        String b = ready("lk289xb");
        Src src = source(a, "봄에는 꽃이 핀다.");
        Story n = note("lk289xb", "생각.");
        ReflectionTestUtils.setField(blogAccess, "allowed", "none");
        try {
            attach(b, n.getId(), src, 0, 0).andExpect(status().isForbidden()).andExpect(content().string(CLOSED));
        } finally {
            ReflectionTestUtils.setField(blogAccess, "allowed", "*");
        }
        assertThat(noteBoxes(n.getId())).isZero();
    }

    // ── REQ-292 쪽지 문장 나누기 ────────────────────────────────────────────

    @Test
    @DisplayName("REQ-292 · 쪽지 문장 나누기 API(본인만 · 남은 404 · revision · n · br · paraEnd)")
    void noteSentences() throws Exception {
        String b = ready("lk292");
        String c = ready("lk292c");
        Story n = note("lk292", "첫 문장이다. 둘째 문장이다.\n셋째 줄이다.\n\n새 문단이다.");

        call(get("/api/app/margins/" + n.getId() + "/sentences"), b).andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(n.getRevision())).andExpect(jsonPath("$.sentences", hasSize(4)))
                .andExpect(jsonPath("$.sentences[0].n").value(0)).andExpect(jsonPath("$.sentences[0].text").value("첫 문장이다."))
                .andExpect(jsonPath("$.sentences[0].br").value(false)).andExpect(jsonPath("$.sentences[0].paraEnd").value(false))
                .andExpect(jsonPath("$.sentences[1].br").value(true)).andExpect(jsonPath("$.sentences[1].paraEnd").value(false))
                .andExpect(jsonPath("$.sentences[2].br").value(false)).andExpect(jsonPath("$.sentences[2].paraEnd").value(true))
                .andExpect(jsonPath("$.sentences[3].n").value(3)).andExpect(jsonPath("$.sentences[3].paraEnd").value(true));
        call(get("/api/app/margins/" + n.getId() + "/sentences"), c).andExpect(status().isNotFound());
    }

    // ── REQ-244 허용 목록(B1b 다섯) ─────────────────────────────────────────

    @ParameterizedTest(name = "REQ-244 · 목록 밖 403 — 쓰기 문 전부 · 같은 문구 「블로그 기능은 12월 1일부터 열려요」 · 요청자는 Apple 가입 계정 · {0} {1}")
    @CsvSource(delimiter = '|', value = {
            "POST | /api/app/blog/posts/{post}/quotes | {\"kind\":\"BOOK\",\"storyId\":{note}}",
            "POST | /api/app/blog/quotes/{quote}/refresh | {}",
            "PUT | /api/app/blog/quotes/{quote}/source | {\"revision\":0,\"from\":0,\"to\":0}",
            "POST | /api/app/margins | {\"text\":\"생각\",\"link\":{\"postId\":{src},\"fromSid\":\"{sid}\",\"toSid\":\"{sid}\"}}",
            "POST | /api/app/margins/{note}/quotes | {\"postId\":{src},\"fromSid\":\"{sid}\",\"toSid\":\"{sid}\"}"
    })
    void closedListRejectsQuoteWrites(String method, String path, String body) throws Exception {
        String a = ready("lk244src"); // 회차마다 롤백 — 이름이 겹치지 않는다
        Src src = source(a, "봄에는 꽃이 핀다.");
        User apple = appleProvisioning.register("001.quote.closed");
        String token = apiTokenService.issue(apple);
        String draft = json(post("/api/app/blog/posts"), token, "{\"draftTitle\":\"내 글\",\"draftBody\":\"문장.\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Story n = storyRepository.saveAndFlush(Story.of(apple, "내 생각이다.", null, null));
        long q = id(json(post("/api/app/blog/posts/" + id(draft) + "/quotes"), token,
                "{\"kind\":\"MARGIN\",\"storyId\":" + n.getId() + ",\"revision\":0,\"from\":0,\"to\":0}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()); // 열린 동안 만든 내 것
        long notesBefore = noteBoxes(n.getId());
        ReflectionTestUtils.setField(blogAccess, "allowed", "none");
        try {
            for (boolean real : new boolean[]{true, false}) {
                String missing = "987654321";
                String url = path.replace("{post}", real ? String.valueOf(id(draft)) : missing)
                        .replace("{quote}", real ? String.valueOf(q) : missing).replace("{note}", real ? String.valueOf(n.getId()) : missing);
                String b = body.replace("{note}", real ? String.valueOf(n.getId()) : missing)
                        .replace("{src}", real ? String.valueOf(src.id()) : missing).replace("{sid}", real ? src.sids().get(0) : "ZZZZZZZZ");
                json("POST".equals(method) ? post(url) : put(url), token, b).andExpect(status().isForbidden())
                        .andExpect(content().string(CLOSED));
            }
        } finally {
            ReflectionTestUtils.setField(blogAccess, "allowed", "*");
        }
        assertThat(noteBoxes(n.getId())).isEqualTo(notesBefore);
        assertThat(quoteBoxRepository.countByOwnerTypeAndOwnerId(QuoteBox.Owner.POST, id(draft))).isEqualTo(1);
        assertThat(storiesOf(apple)).isEqualTo(1);
    }

    private long storiesOf(User u) {
        em.flush();
        return jdbc.queryForObject("select count(*) from story where user_id = ?", Long.class, u.getId());
    }

    @Test
    @DisplayName("REQ-244 · 목록 밖이어도 link 없는 쪽지 만들기 201 · 박스 빼기 · 쪽지 지우기 · 쪽지 문장 API 200")
    void closedListKeepsNotesOpen() throws Exception {
        String a = ready("lk244open");
        Src src = source(a, "봄에는 꽃이 핀다.");
        User apple = appleProvisioning.register("001.quote.open");
        String token = apiTokenService.issue(apple);
        long n = hung(token, "생각이다.", src, 0, 0); // 열린 동안
        long q = boxIds(token, n).get(0);
        ReflectionTestUtils.setField(blogAccess, "allowed", "none");
        try {
            json(post("/api/app/margins"), token, "{\"bookId\":null,\"quote\":null,\"text\":\"link 없는 쪽지\"}")
                    .andExpect(status().isCreated());
            call(get("/api/app/margins/" + n + "/sentences"), token).andExpect(status().isOk());
            call(delete("/api/app/margins/" + n + "/quotes/" + q), token).andExpect(status().isOk());
            call(delete("/api/app/margins/" + n + "?withQuotes=true"), token).andExpect(status().isNoContent());
        } finally {
            ReflectionTestUtils.setField(blogAccess, "allowed", "*");
        }
        assertThat(boxExists(q)).isFalse();
        assertThat(storyRepository.findById(n)).isEmpty();
    }
}
