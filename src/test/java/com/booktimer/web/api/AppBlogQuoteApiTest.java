package com.booktimer.web.api;

import com.booktimer.auth.ApiTokenService;
import com.booktimer.blog.BlogSentence;
import com.booktimer.blog.BlogSentenceRepository;
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
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import com.booktimer.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 책생글 B1b — 글의 인용 박스 API(계획 2026-11-11-b1-blog-core §5.2 · §6.3 5 · 9 · §6.5~6.7 · §7) — 실 H2 + Bearer.
 *
 * <p>박스를 독자에게 보이려면 글쓴이가 초안에 표시 줄 {@code ⟦인용 N⟧}을 적고 올린다({@link #place}). 주인 혼자 볼 판정은
 * 초안의 {@code draft.quotes}로 잰다. 쪽지가 가진 박스는 저장소로 직접 만든다(쪽지 문은 AppMarginLinkApiTest).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AppBlogQuoteApiTest {

    private static final String SEOUL = "Asia/Seoul";
    private static final Instant OLD = Instant.parse("2020-01-01T00:00:00Z");

    @Autowired MockMvc mvc;
    @Autowired UserRegistrationService registrationService;
    @Autowired UserRepository userRepository;
    @Autowired ApiTokenService apiTokenService;
    @Autowired BlogSentenceRepository sentenceRepository;
    @Autowired QuoteBoxRepository quoteBoxRepository;
    @Autowired StoryRepository storyRepository;
    @Autowired BookRepository bookRepository;
    @Autowired BlockRepository blockRepository;
    @Autowired RateLimitService rateLimitService;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired Clock clock;

    @BeforeEach
    void clearRateLimits() {
        rateLimitService.clearForTest();
    }

    // ── 도우미 ──────────────────────────────────────────────────────────────

    /** 첫 설정을 마친 사용자의 토큰. */
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
        return s == null ? "null" : "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private long create(String token, String title, String body) throws Exception {
        String res = json(post("/api/app/blog/posts"), token,
                "{\"draftTitle\":" + str(title) + ",\"draftBody\":" + str(body) + "}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(res, "$.id")).longValue();
    }

    private String read(String token, long id, String s) throws Exception {
        return call(get("/api/app/blog/posts/" + id + (s == null ? "" : "?s=" + s)), token)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private int rev(String token, long id) throws Exception {
        return JsonPath.read(read(token, id, null), "$.draft.revision");
    }

    /** 초안을 그 글자로 저장하고 올린다 — 올린 글 보기. */
    private String republish(String token, long id, String title, String body) throws Exception {
        int r = rev(token, id);
        json(put("/api/app/blog/posts/" + id), token,
                "{\"draftTitle\":" + str(title) + ",\"draftBody\":" + str(body) + ",\"revision\":" + r + "}")
                .andExpect(status().isOk());
        return json(post("/api/app/blog/posts/" + id + "/publish"), token, "{\"revision\":" + (r + 1) + "}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private long published(String token, String title, String body) throws Exception {
        long id = create(token, title, body);
        json(post("/api/app/blog/posts/" + id + "/publish"), token, "{\"revision\":0}").andExpect(status().isOk());
        return id;
    }

    private static List<String> sids(String view) {
        return JsonPath.read(view, "$.blocks[*].sentences[*].sid");
    }

    private List<String> sidsOf(String token, long id) throws Exception {
        return sids(read(token, id, null));
    }

    private static String blog(long postId, String from, String to) {
        return "{\"kind\":\"BLOG\",\"postId\":" + postId + ",\"fromSid\":" + str(from) + ",\"toSid\":" + str(to) + "}";
    }

    private static String margin(long storyId, int revision, int from, int to) {
        return "{\"kind\":\"MARGIN\",\"storyId\":" + storyId + ",\"revision\":" + revision + ",\"from\":" + from
                + ",\"to\":" + to + "}";
    }

    private static String book(long storyId) {
        return "{\"kind\":\"BOOK\",\"storyId\":" + storyId + "}";
    }

    private ResultActions quote(String token, long postId, String body) throws Exception {
        return json(post("/api/app/blog/posts/" + postId + "/quotes"), token, body);
    }

    /** 박스를 만들고 그 id(201 단언). */
    private long box(String token, long postId, String body) throws Exception {
        String res = quote(token, postId, body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(res, "$.id")).longValue();
    }

    /** 내 글에 박스 표시 줄을 적어 올린다(독자에게 보이게). */
    private String place(String token, long postId, String body) throws Exception {
        return republish(token, postId, "내 글", body);
    }

    private ResultActions refresh(String token, long quoteId) throws Exception {
        return json(post("/api/app/blog/quotes/" + quoteId + "/refresh"), token, "{}");
    }

    /** 독자가 본 그 글의 첫 박스. */
    private Map<String, Object> firstQuote(String view) {
        List<Map<String, Object>> quotes = JsonPath.read(view, "$.blocks[?(@.type == 'Q')].quote");
        assertThat(quotes).as("Q 블록이 있다").isNotEmpty();
        return quotes.get(0);
    }

    private Map<String, Object> draftQuote(String token, long postId, int index) throws Exception {
        List<Map<String, Object>> quotes = JsonPath.read(read(token, postId, null), "$.draft.quotes");
        return quotes.get(index);
    }

    /** DB의 박스 행 — 같은 트랜잭션의 바뀐 엔티티를 먼저 내보낸 뒤 문맥을 비우고 읽는다. */
    private QuoteBox boxRow(long id) {
        em.flush();
        em.clear();
        return quoteBoxRepository.findById(id).orElseThrow();
    }

    private Book bookOf(String loginId, String title, String author, String isbn) {
        return bookRepository.saveAndFlush(Book.register(user(loginId), title, author, isbn, null, null, null, BookStatus.READING));
    }

    private Story note(String loginId, Book book, String quote, String text) {
        return storyRepository.saveAndFlush(Story.of(user(loginId), text, book, null, quote));
    }

    private void sql(String statement, Object... args) {
        em.flush();
        jdbc.update(statement, args);
        em.clear();
    }

    private void hide(long postId) {
        sql("update blog_post set hidden_at = ? where id = ?", Timestamp.from(Instant.now()), postId);
    }

    private void ageBox(long quoteId) {
        sql("update quote_box set quoted_at = ? where id = ?", Timestamp.from(OLD), quoteId);
    }

    private static String text(String res) {
        return JsonPath.read(res, "$.text");
    }

    // ── REQ-237 박스 만들기 · 출처 ──────────────────────────────────────────

    @Test
    @DisplayName("REQ-237 · 책 문장 박스에 『제목』 · 저자 · 날짜")
    void bookBoxHasSource() throws Exception {
        String me = ready("qbook");
        long mine = create(me, "내 글", "내 문장.");
        Story n = note("qbook", bookOf("qbook", "데미안", "헤르만 헤세", null), "새는 알에서 나오려고 투쟁한다", "생각");

        quote(me, mine, book(n.getId())).andExpect(status().isCreated())
                .andExpect(jsonPath("$.label").value(1)).andExpect(jsonPath("$.kind").value("BOOK"))
                .andExpect(jsonPath("$.text").value("새는 알에서 나오려고 투쟁한다"))
                .andExpect(jsonPath("$.attribution").value("『데미안』 · 헤르만 헤세"))
                .andExpect(jsonPath("$.quotedAt").exists());
    }

    @Test
    @DisplayName("REQ-237 · 책을 지운 쪽지의 책 문장 박스도 quote_source로")
    void bookBoxAfterBookDeleted() throws Exception {
        String me = ready("qbookdel");
        long mine = create(me, "내 글", "내 문장.");
        Book b = bookOf("qbookdel", "싯다르타", "헤르만 헤세", "9788937460449");
        Story n = note("qbookdel", b, "강물은 흐른다", "생각");
        storyRepository.unlinkBook(b, Story.sourceLine(b));

        long q = box(me, mine, book(n.getId()));
        assertThat(boxRow(q).getAttribution()).isEqualTo("『싯다르타』 · 헤르만 헤세");
        assertThat(boxRow(q).getSourceIsbn13()).isNull();
    }

    @Test
    @DisplayName("REQ-237 · 제목 300자 · 저자 200자 책의 책 문장 박스 201(attribution 505자)")
    void longestAttribution() throws Exception {
        String me = ready("qlong");
        long mine = create(me, "내 글", "내 문장.");
        Story n = note("qlong", bookOf("qlong", "가".repeat(300), "나".repeat(200), null), "옮긴 문장", "생각");

        long q = box(me, mine, book(n.getId()));
        assertThat(boxRow(q).getAttribution()).hasSize(505);
    }

    @Test
    @DisplayName("REQ-237 · 블로그 박스에 원 글쓴이 이름 · 원 글 제목")
    void blogBoxAttribution() throws Exception {
        String a = ready("qattra");
        String b = ready("qattrb");
        long src = published(a, "봄 이야기", "봄에는 꽃이 핀다. 여름에는 비가 온다.");
        List<String> s = sidsOf(a, src);
        long mine = create(b, "내 글", "내 문장.");

        quote(b, mine, blog(src, s.get(0), s.get(1))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("BLOG"))
                .andExpect(jsonPath("$.text").value("봄에는 꽃이 핀다. 여름에는 비가 온다."))
                .andExpect(jsonPath("$.attribution").value("닉_qattra · 「봄 이야기」"))
                .andExpect(jsonPath("$.sourceLink").value("/p/" + src + "/s/" + s.get(0) + "-" + s.get(1)))
                .andExpect(jsonPath("$.sourceDeleted").value(false))
                .andExpect(jsonPath("$.updatable").value(false));
    }

    @Test
    @DisplayName("REQ-237 · 남의 쪽지 storyId로 MARGIN · BOOK 박스 404(박스 행 0 — 양성 대조: 같은 픽스처의 내 쪽지는 201)")
    void othersNoteIs404() throws Exception {
        String me = ready("qidor1");
        ready("qidor2");
        long mine = create(me, "내 글", "내 문장.");
        Story others = note("qidor2", bookOf("qidor2", "데미안", "헤르만 헤세", null), "남의 옮긴 문장", "남의 비공개 생각.");
        Story own = note("qidor1", bookOf("qidor1", "데미안", "헤르만 헤세", null), "내 옮긴 문장", "내 생각.");

        quote(me, mine, margin(others.getId(), 0, 0, 0)).andExpect(status().isNotFound())
                .andExpect(content().string("쪽지를 찾을 수 없어요"));
        quote(me, mine, book(others.getId())).andExpect(status().isNotFound());
        assertThat(quoteBoxRepository.existsByOwnerTypeAndOwnerId(QuoteBox.Owner.POST, mine)).isFalse();

        quote(me, mine, margin(own.getId(), 0, 0, 0)).andExpect(status().isCreated()).andExpect(jsonPath("$.text").value("내 생각."));
        quote(me, mine, book(own.getId())).andExpect(status().isCreated()).andExpect(jsonPath("$.label").value(2));
    }

    @Test
    @DisplayName("REQ-237 · MARGIN — 쪽지 판이 다르면 409(박스 행 0)")
    void marginRevisionMismatch() throws Exception {
        String me = ready("qmrev");
        long mine = create(me, "내 글", "내 문장.");
        Story n = note("qmrev", null, null, "첫 생각이다. 둘째 생각이다.");

        quote(me, mine, margin(n.getId(), 5, 0, 1)).andExpect(status().isConflict())
                .andExpect(content().string("쪽지가 바뀌었어요 — 다시 골라 주세요"));
        assertThat(quoteBoxRepository.existsByOwnerTypeAndOwnerId(QuoteBox.Owner.POST, mine)).isFalse();
        quote(me, mine, margin(n.getId(), 0, 0, 1)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.text").value("첫 생각이다. 둘째 생각이다."));
    }

    @Test
    @DisplayName("REQ-237 · 101번째 박스 400")
    void hundredBoxCap() throws Exception {
        String me = ready("qcap");
        long mine = create(me, "내 글", "내 문장.");
        Story n = note("qcap", bookOf("qcap", "데미안", "헤르만 헤세", null), "옮긴 문장", "생각");
        List<QuoteBox> rows = new ArrayList<>();
        for (int i = 1; i <= 99; i++) rows.add(QuoteBox.book(mine, i, n.getId(), "옮긴 문장", null, null, Instant.now()));
        quoteBoxRepository.saveAllAndFlush(rows);

        quote(me, mine, book(n.getId())).andExpect(status().isCreated()).andExpect(jsonPath("$.label").value(100));
        quote(me, mine, book(n.getId())).andExpect(status().isBadRequest())
                .andExpect(content().string("한 글에 인용은 100개까지예요"));
        assertThat(quoteBoxRepository.countByOwnerTypeAndOwnerId(QuoteBox.Owner.POST, mine)).isEqualTo(100);
    }

    @Test
    @DisplayName("REQ-237 · 다음 label은 초안에 남은 표시 번호를 비켜 간다")
    void nextLabelSkipsDraftMarkers() throws Exception {
        String me = ready("qlabel");
        long mine = create(me, "내 글", "내 문장. 지난 박스 자리 ⟦인용 7⟧ 그대로.");
        Story n = note("qlabel", bookOf("qlabel", "데미안", "헤르만 헤세", null), "옮긴 문장", "생각");

        quote(me, mine, book(n.getId())).andExpect(status().isCreated()).andExpect(jsonPath("$.label").value(8));
        quote(me, mine, book(n.getId())).andExpect(status().isCreated()).andExpect(jsonPath("$.label").value(9));
    }

    @Test
    @DisplayName("REQ-237 · 초안의 표시 번호가 9,999면 다음 박스 400(박스 행 0) · 9,998이면 label 9,999로 201")
    void labelCeiling() throws Exception {
        String me = ready("qlabmax");
        long full = create(me, "내 글", "내 문장. ⟦인용 9999⟧");
        long last = create(me, "내 글", "내 문장. ⟦인용 9998⟧");
        Story n = note("qlabmax", bookOf("qlabmax", "데미안", "헤르만 헤세", null), "옮긴 문장", "생각");

        quote(me, full, book(n.getId())).andExpect(status().isBadRequest())
                .andExpect(content().string("인용은 더 만들 수 없어요"));
        assertThat(quoteBoxRepository.existsByOwnerTypeAndOwnerId(QuoteBox.Owner.POST, full)).isFalse();
        quote(me, last, book(n.getId())).andExpect(status().isCreated()).andExpect(jsonPath("$.label").value(9999));
    }

    @Test
    @DisplayName("REQ-237 · MARGIN — 고른 생각이 1,001자 넘으면 400(박스 행 0) · 다시 고르기도 400(박스 글 그대로)")
    void marginOverThousand() throws Exception {
        String me = ready("qmlong");
        long mine = create(me, "내 글", "내 문장.");
        String first = "가".repeat(600) + ".";
        Story n = note("qmlong", null, null, first + " " + "나".repeat(600) + ".");

        quote(me, mine, margin(n.getId(), 0, 0, 1)).andExpect(status().isBadRequest())
                .andExpect(content().string("인용은 1,000자까지예요"));
        assertThat(quoteBoxRepository.existsByOwnerTypeAndOwnerId(QuoteBox.Owner.POST, mine)).isFalse();
        long q = box(me, mine, margin(n.getId(), 0, 0, 0)); // 양성 대조 — 한 문장(601자)은 201
        repick(me, q, 0, 0, 1).andExpect(status().isBadRequest()).andExpect(content().string("인용은 1,000자까지예요"));
        assertThat(boxRow(q).getText()).isEqualTo(first);
    }

    @Test
    @DisplayName("엣지 · 남의 글에 박스 만들기 404(박스 행 0) — 내 글은 201")
    void othersPostBox404() throws Exception {
        String me = ready("qpidor1");
        String other = ready("qpidor2");
        long theirs = create(other, "남의 글", "남의 문장.");
        long mine = create(me, "내 글", "내 문장.");
        Story n = note("qpidor1", bookOf("qpidor1", "데미안", "헤르만 헤세", null), "옮긴 문장", "생각");

        quote(me, theirs, book(n.getId())).andExpect(status().isNotFound()).andExpect(content().string("글을 찾을 수 없어요"));
        assertThat(quoteBoxRepository.existsByOwnerTypeAndOwnerId(QuoteBox.Owner.POST, theirs)).isFalse();
        quote(me, mine, book(n.getId())).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("엣지 · QuoteRequest 빠진 필드는 그 갈래의 문구로 · 종류 밖은 400")
    void missingFields() throws Exception {
        String me = ready("qmiss");
        long mine = create(me, "내 글", "내 문장.");
        long src = published(me, "원 글", "원 문장이다.");
        Story n = note("qmiss", null, null, "생각이다.");
        Story bare = note("qmiss", null, null, "옮긴 문장 없는 쪽지.");

        quote(me, mine, "{\"kind\":\"BLOG\",\"fromSid\":\"AAAAAAAA\",\"toSid\":\"AAAAAAAA\"}").andExpect(status().isNotFound())
                .andExpect(content().string("글을 찾을 수 없어요"));
        quote(me, mine, "{\"kind\":\"BLOG\",\"postId\":" + src + "}").andExpect(status().isBadRequest())
                .andExpect(content().string("고른 문장을 찾을 수 없어요 — 글을 다시 불러와 주세요"));
        quote(me, mine, "{\"kind\":\"MARGIN\"}").andExpect(status().isNotFound()).andExpect(content().string("쪽지를 찾을 수 없어요"));
        quote(me, mine, "{\"kind\":\"MARGIN\",\"storyId\":" + n.getId() + "}").andExpect(status().isConflict())
                .andExpect(content().string("쪽지가 바뀌었어요 — 다시 골라 주세요"));
        quote(me, mine, "{\"kind\":\"MARGIN\",\"storyId\":" + n.getId() + ",\"revision\":0}").andExpect(status().isBadRequest())
                .andExpect(content().string("고른 범위가 쪽지의 생각 밖이에요"));
        quote(me, mine, "{\"kind\":\"BOOK\"}").andExpect(status().isNotFound()).andExpect(content().string("쪽지를 찾을 수 없어요"));
        quote(me, mine, book(bare.getId())).andExpect(status().isBadRequest()).andExpect(content().string("옮긴 문장이 없는 쪽지예요"));
        quote(me, mine, "{}").andExpect(status().isBadRequest()).andExpect(content().string("아직 쓸 수 없는 인용이에요"));
        quote(me, mine, "{\"kind\":\"AI\"}").andExpect(status().isBadRequest()).andExpect(content().string("아직 쓸 수 없는 인용이에요"));
        assertThat(quoteBoxRepository.existsByOwnerTypeAndOwnerId(QuoteBox.Owner.POST, mine)).isFalse();
    }

    // ── REQ-238 업데이트 판정 ───────────────────────────────────────────────

    @Test
    @DisplayName("REQ-238 · 원문 고침 → 남이 읽어도 updatable")
    void editedSourceIsUpdatableForReaders() throws Exception {
        String a = ready("q238a");
        String b = ready("q238b");
        String c = ready("q238c");
        long src = published(a, "원 글", "봄에는 꽃이 핀다. 여름에는 비가 온다.");
        List<String> s = sidsOf(a, src);
        long mine = create(b, "내 글", "내 문장.");
        box(b, mine, blog(src, s.get(0), s.get(1)));
        place(b, mine, "내 문장.\n⟦인용 1⟧");
        assertThat(firstQuote(read(c, mine, null)).get("updatable")).isEqualTo(false);

        republish(a, src, "원 글", "봄에는 꽃이 핀다. 여름에는 비가 많이 온다.");
        Map<String, Object> seen = firstQuote(read(c, mine, null));
        assertThat(seen.get("updatable")).isEqualTo(true);
        assertThat(seen.get("text")).isEqualTo("봄에는 꽃이 핀다. 여름에는 비가 온다.");
        assertThat(seen.get("sourceLink")).isEqualTo("/p/" + src + "/s/" + s.get(0) + "-" + s.get(1));
    }

    @Test
    @DisplayName("REQ-238 · 여백 박스는 남에게 updatable 없음")
    void marginBoxHidesUpdatableFromOthers() throws Exception {
        String b = ready("q238m");
        String c = ready("q238mc");
        Story n = note("q238m", null, null, "첫 생각이다. 둘째 생각이다.");
        long mine = create(b, "내 글", "내 문장.");
        box(b, mine, margin(n.getId(), 0, 0, 0));
        place(b, mine, "내 문장.\n⟦인용 1⟧");
        n = storyRepository.findById(n.getId()).orElseThrow();
        n.edit(null, "바뀐 생각이다.", n.getRevision());
        storyRepository.saveAndFlush(n);

        call(get("/api/app/blog/posts/" + mine), c).andExpect(jsonPath("$.blocks[1].type").value("Q"))
                .andExpect(jsonPath("$.blocks[1].quote.updatable").doesNotExist())
                .andExpect(jsonPath("$.blocks[1].quote.sourceDeleted").doesNotExist())
                .andExpect(jsonPath("$.blocks[1].quote.text").value("첫 생각이다."));
        call(get("/api/app/blog/posts/" + mine), b).andExpect(jsonPath("$.blocks[1].quote.updatable").value(true))
                .andExpect(jsonPath("$.blocks[1].quote.sourceDeleted").value(false));
    }

    @Test
    @DisplayName("REQ-238 · s1 · s3 지움(s2만 남음) → 박스 원문 링크 = /p/{id}/s/s2")
    void sourceLinkNarrowsToLive() throws Exception {
        String a = ready("q238l");
        String b = ready("q238lb");
        long src = published(a, "원 글", "봄에는 꽃이 핀다. 여름에는 비가 온다. 가을에는 낙엽이 진다.");
        List<String> s = sidsOf(a, src);
        long mine = create(b, "내 글", "내 문장.");
        box(b, mine, blog(src, s.get(0), s.get(2)));

        republish(a, src, "원 글", "여름에는 비가 온다.");
        Map<String, Object> q = draftQuote(b, mine, 0);
        assertThat(q.get("sourceLink")).isEqualTo("/p/" + src + "/s/" + s.get(1));
        assertThat(q.get("updatable")).isEqualTo(true);
    }

    @Test
    @DisplayName("REQ-238 · 가린 원 글을 인용한 박스는 sourceLink · updatable 없음 · 원 글쓴이와 차단 관계인 독자에게도 없음")
    void hiddenOrBlockedSourceShowsNoVerdict() throws Exception {
        String a = ready("q238h");
        String b = ready("q238hb");
        String c = ready("q238hc");
        String d = ready("q238hd");
        long src = published(a, "원 글", "봄에는 꽃이 핀다.");
        List<String> s = sidsOf(a, src);
        long mine = create(b, "내 글", "내 문장.");
        box(b, mine, blog(src, s.get(0), s.get(0)));
        place(b, mine, "내 문장.\n⟦인용 1⟧");
        republish(a, src, "원 글", "봄에는 꽃이 활짝 핀다.");

        blockRepository.saveAndFlush(Block.of(user("q238h"), user("q238hc")));
        call(get("/api/app/blog/posts/" + mine), c).andExpect(jsonPath("$.blocks[1].quote.sourceLink").doesNotExist())
                .andExpect(jsonPath("$.blocks[1].quote.updatable").doesNotExist())
                .andExpect(jsonPath("$.blocks[1].quote.sourceDeleted").doesNotExist())
                .andExpect(jsonPath("$.blocks[1].quote.text").value("봄에는 꽃이 핀다."));
        call(get("/api/app/blog/posts/" + mine), d).andExpect(jsonPath("$.blocks[1].quote.sourceLink").value("/p/" + src + "/s/" + s.get(0)))
                .andExpect(jsonPath("$.blocks[1].quote.updatable").value(true)); // 양성 대조

        hide(src);
        call(get("/api/app/blog/posts/" + mine), d).andExpect(jsonPath("$.blocks[1].quote.sourceLink").doesNotExist())
                .andExpect(jsonPath("$.blocks[1].quote.updatable").doesNotExist())
                .andExpect(jsonPath("$.blocks[1].quote.sourceDeleted").value(false))
                .andExpect(jsonPath("$.blocks[1].quote.attribution").value("닉_q238h · 「원 글」"));
    }

    // ── REQ-239 최신으로 반영 ───────────────────────────────────────────────

    @Test
    @DisplayName("REQ-239 · 반영 뒤 quotedAt = 오늘 · 글 = 지금 구간")
    void refreshTakesCurrentRange() throws Exception {
        String a = ready("q239a");
        String b = ready("q239b");
        long src = published(a, "원 글", "봄에는 꽃이 핀다. 여름에는 비가 온다.");
        List<String> s = sidsOf(a, src);
        long mine = create(b, "내 글", "내 문장.");
        long q = box(b, mine, blog(src, s.get(0), s.get(1)));
        republish(a, src, "고친 원 글", "봄에는 꽃이 핀다. 장마가 길었다. 여름에는 비가 많이 온다.");
        ageBox(q);

        String res = refresh(b, q).andExpect(status().isOk()).andExpect(jsonPath("$.updatable").value(false))
                .andExpect(jsonPath("$.label").value(1)).andReturn().getResponse().getContentAsString();
        assertThat(text(res)).isEqualTo("봄에는 꽃이 핀다. 장마가 길었다. 여름에는 비가 많이 온다.");
        assertThat(Instant.parse(JsonPath.read(res, "$.quotedAt"))).isAfter(OLD.plusSeconds(86_400));
        assertThat(boxRow(q).members()).hasSize(3);
        assertThat(boxRow(q).getAttribution()).isEqualTo("고친 원 글");
    }

    @Test
    @DisplayName("REQ-239 · 반영하면 1,001자 → 409")
    void refreshOverThousand() throws Exception {
        String a = ready("q239k");
        String b = ready("q239kb");
        String one = "첫" + "가나다라마바사아자차".repeat(39) + ".";
        String two = "둘" + "카타파하거너더러머버".repeat(39) + ".";
        long src = published(a, "원 글", one + " " + two);
        List<String> s = sidsOf(a, src);
        long mine = create(b, "내 글", "내 문장.");
        long q = box(b, mine, blog(src, s.get(0), s.get(1)));
        String longer = "둘" + "카타파하거너더러머버".repeat(39) + "서어저처커터퍼허".repeat(30) + ".";
        assertThat(sidsOf(a, src)).isEqualTo(s);
        republish(a, src, "원 글", one + " " + longer);
        assertThat(sidsOf(a, src)).as("고친 문장이 주소를 이었다(판 비교 전제)").isEqualTo(s);

        refresh(b, q).andExpect(status().isConflict())
                .andExpect(content().string("원문이 바뀌어 박스에 다 담을 수 없어요 — 인용을 다시 골라 주세요"));
        assertThat(boxRow(q).getText()).isEqualTo(one + " " + two);
    }

    @Test
    @DisplayName("REQ-239 · 반영 구간이 인용 박스를 걸치면 409")
    void refreshAcrossBox() throws Exception {
        String a = ready("q239x");
        String b = ready("q239xb");
        long src = published(a, "원 글", "봄에는 꽃이 핀다. 여름에는 비가 온다.");
        List<String> s = sidsOf(a, src);
        long mine = create(b, "내 글", "내 문장.");
        long q = box(b, mine, blog(src, s.get(0), s.get(1)));
        Story n = note("q239x", bookOf("q239x", "데미안", "헤르만 헤세", null), "옮긴 문장", "생각");
        box(a, src, book(n.getId()));
        republish(a, src, "원 글", "봄에는 꽃이 핀다.\n⟦인용 1⟧\n여름에는 비가 온다.");

        refresh(b, q).andExpect(status().isConflict())
                .andExpect(content().string("원문이 바뀌어 박스에 다 담을 수 없어요 — 인용을 다시 골라 주세요"));
        assertThat(boxRow(q).getText()).isEqualTo("봄에는 꽃이 핀다. 여름에는 비가 온다.");
    }

    @Test
    @DisplayName("REQ-239 · 남의 글 박스 · 남의 쪽지 박스 반영 404(박스 글 그대로)")
    void refreshOthersBox404() throws Exception {
        String a = ready("q239i");
        String b = ready("q239ib");
        String c = ready("q239ic");
        long src = published(a, "원 글", "봄에는 꽃이 핀다.");
        List<String> s = sidsOf(a, src);
        long mine = create(b, "내 글", "내 문장.");
        long postBox = box(b, mine, blog(src, s.get(0), s.get(0)));
        Story bNote = note("q239ib", null, null, "생각.");
        long noteBox = quoteBoxRepository.saveAndFlush(QuoteBox.blog(QuoteBox.Owner.NOTE, bNote.getId(), 1, src,
                user("q239i").getId(), List.of(s.get(0)), 0, "봄에는 꽃이 핀다.", "원 글", Instant.now())).getId();
        // 원 글이 없는 박스 — 소유 404가 원문 지움 409보다 먼저인지 잰다(주인에겐 409 — 이 픽스처가 그 검사에 닿는다는 양성 대조)
        long goneBox = quoteBoxRepository.saveAndFlush(QuoteBox.blog(QuoteBox.Owner.POST, mine, 2, 987_654_321L,
                user("q239i").getId(), List.of(s.get(0)), 0, "봄에는 꽃이 핀다.", "지운 글", Instant.now())).getId();
        republish(a, src, "원 글", "봄에는 꽃이 활짝 핀다.");

        refresh(c, postBox).andExpect(status().isNotFound());
        refresh(c, noteBox).andExpect(status().isNotFound());
        refresh(c, 987_654_321L).andExpect(status().isNotFound());
        refresh(c, goneBox).andExpect(status().isNotFound()).andExpect(content().string("인용을 찾을 수 없어요"));
        refresh(b, goneBox).andExpect(status().isConflict()).andExpect(content().string("원문이 지워졌어요"));
        // 원 글쓴이와 차단 관계인 남 — 소유 404가 차단 403보다 먼저(돌연변이 M41: 차단 검사를 소유 판정 앞으로 옮기면 403)
        blockRepository.saveAndFlush(Block.of(user("q239i"), user("q239ic")));
        refresh(c, postBox).andExpect(status().isNotFound());
        assertThat(boxRow(postBox).getText()).isEqualTo("봄에는 꽃이 핀다.");
        assertThat(boxRow(noteBox).getText()).isEqualTo("봄에는 꽃이 핀다.");
        refresh(b, postBox).andExpect(status().isOk()).andExpect(jsonPath("$.text").value("봄에는 꽃이 활짝 핀다."));
        refresh(b, noteBox).andExpect(status().isOk()).andExpect(jsonPath("$.text").value("봄에는 꽃이 활짝 핀다."));
    }

    @Test
    @DisplayName("REQ-239 · 구성원이 먼 자리로 옮겨져 구간이 박스를 걸침 → 409")
    void memberMovedAcrossBox() throws Exception {
        String a = ready("q239m");
        String b = ready("q239mb");
        Story n = note("q239m", bookOf("q239m", "데미안", "헤르만 헤세", null), "옮긴 문장", "생각");
        long src = create(a, "원 글", "봄에는 꽃이 핀다. 여름에는 비가 온다.");
        box(a, src, book(n.getId()));
        republish(a, src, "원 글", "봄에는 꽃이 핀다. 여름에는 비가 온다.\n⟦인용 1⟧\n겨울에는 눈이 내린다.");
        List<String> s = sidsOf(a, src);
        long mine = create(b, "내 글", "내 문장.");
        long q = box(b, mine, blog(src, s.get(0), s.get(1)));

        republish(a, src, "원 글", "봄에는 꽃이 핀다.\n⟦인용 1⟧\n겨울에는 눈이 내린다. 여름에는 비가 온다.");
        assertThat(sidsOf(a, src)).containsExactly(s.get(0), s.get(2), s.get(1));
        refresh(b, q).andExpect(status().isConflict());
        assertThat(boxRow(q).getText()).isEqualTo("봄에는 꽃이 핀다. 여름에는 비가 온다.");
    }

    @Test
    @DisplayName("REQ-239 · 원 글쓴이와 차단 관계가 된 뒤 반영 403 · 가린 원 글 반영 409(둘 다 박스 글 그대로)")
    void refreshBlockedOrHidden() throws Exception {
        String a = ready("q239bl");
        String b = ready("q239blb");
        long src = published(a, "원 글", "봄에는 꽃이 핀다.");
        List<String> s = sidsOf(a, src);
        long mine = create(b, "내 글", "내 문장.");
        long q = box(b, mine, blog(src, s.get(0), s.get(0)));
        republish(a, src, "원 글", "봄에는 꽃이 활짝 핀다.");

        Block block = blockRepository.saveAndFlush(Block.of(user("q239bl"), user("q239blb")));
        refresh(b, q).andExpect(status().isForbidden()).andExpect(content().string("이 글의 문장은 인용할 수 없어요"));
        blockRepository.delete(block);
        blockRepository.flush();
        hide(src);
        refresh(b, q).andExpect(status().isConflict()).andExpect(content().string("원문을 지금 볼 수 없어요"));
        assertThat(boxRow(q).getText()).isEqualTo("봄에는 꽃이 핀다.");
    }

    // ── REQ-240 · 241 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("REQ-240 · 남이 읽으면 sourceNote 없음 · 주인은 쪽지 id")
    void sourceNoteOnlyForOwner() throws Exception {
        String b = ready("q240");
        String c = ready("q240c");
        Book bk = bookOf("q240", "데미안", "헤르만 헤세", null);
        Story n = note("q240", bk, "옮긴 문장", "내 생각이다.");
        long mine = create(b, "내 글", "내 문장.");
        box(b, mine, margin(n.getId(), 0, 0, 0));
        box(b, mine, book(n.getId()));
        place(b, mine, "내 문장.\n⟦인용 1⟧\n⟦인용 2⟧");

        call(get("/api/app/blog/posts/" + mine), c).andExpect(jsonPath("$.blocks[1].quote.sourceNote").doesNotExist())
                .andExpect(jsonPath("$.blocks[2].quote.sourceNote").doesNotExist())
                .andExpect(jsonPath("$.blocks[1].quote.sourceLink").doesNotExist())
                .andExpect(jsonPath("$.blocks[2].quote.attribution").value("『데미안』 · 헤르만 헤세"));
        call(get("/api/app/blog/posts/" + mine), b).andExpect(jsonPath("$.blocks[1].quote.sourceNote.id").value(n.getId()))
                .andExpect(jsonPath("$.blocks[1].quote.sourceNote.bookId").value(bk.getId()))
                .andExpect(jsonPath("$.blocks[2].quote.sourceNote.id").value(n.getId()))
                .andExpect(jsonPath("$.blocks[1].quote.sourceLink").doesNotExist());
    }

    @Test
    @DisplayName("REQ-241 · ⟦인용 2⟧ → 박스 · ⟦인용 9⟧ → 글")
    void markerLinesBecomeBoxes() throws Exception {
        String b = ready("q241");
        Story n = note("q241", bookOf("q241", "데미안", "헤르만 헤세", null), "옮긴 문장", "생각");
        long mine = create(b, "내 글", "내 문장.");
        box(b, mine, book(n.getId()));
        box(b, mine, book(n.getId()));

        String view = place(b, mine, "내 문장.\n⟦인용 2⟧\n⟦인용 9⟧\n\n문장 안의 ⟦인용 1⟧ 표시는 글이다.");
        assertThat(JsonPath.<List<String>>read(view, "$.blocks[*].type")).containsExactly("P", "Q", "P", "P");
        assertThat(JsonPath.<Integer>read(view, "$.blocks[1].quote.label")).isEqualTo(2);
        assertThat(JsonPath.<List<Object>>read(view, "$.blocks[1].sentences")).isEmpty();
        assertThat(JsonPath.<String>read(view, "$.blocks[2].sentences[0].text")).isEqualTo("⟦인용 9⟧");
        assertThat(JsonPath.<String>read(view, "$.blocks[3].sentences[0].text")).isEqualTo("문장 안의 ⟦인용 1⟧ 표시는 글이다.");
    }

    @Test
    @DisplayName("REQ-241 · 같은 번호 둘째 표시는 글")
    void secondSameMarkerIsText() throws Exception {
        String b = ready("q241d");
        Story n = note("q241d", bookOf("q241d", "데미안", "헤르만 헤세", null), "옮긴 문장", "생각");
        long mine = create(b, "내 글", "내 문장.");
        box(b, mine, book(n.getId()));

        String view = place(b, mine, "⟦인용 1⟧\n⟦인용 1⟧");
        assertThat(JsonPath.<List<String>>read(view, "$.blocks[*].type")).containsExactly("Q", "P");
        assertThat(JsonPath.<String>read(view, "$.blocks[1].sentences[0].text")).isEqualTo("⟦인용 1⟧");
    }

    @Test
    @DisplayName("REQ-241 · 표시 줄을 지우고 올리면 그 박스 행 0")
    void orphanBoxRemoved() throws Exception {
        String b = ready("q241o");
        Story n = note("q241o", bookOf("q241o", "데미안", "헤르만 헤세", null), "옮긴 문장", "생각");
        long mine = create(b, "내 글", "내 문장.");
        long kept = box(b, mine, book(n.getId()));
        long gone = box(b, mine, book(n.getId()));
        place(b, mine, "내 문장.\n⟦인용 1⟧\n⟦인용 2⟧");
        assertThat(quoteBoxRepository.countByOwnerTypeAndOwnerId(QuoteBox.Owner.POST, mine)).isEqualTo(2);

        place(b, mine, "내 문장.\n⟦인용 1⟧");
        em.clear();
        assertThat(quoteBoxRepository.findById(gone)).isEmpty();
        assertThat(quoteBoxRepository.findById(kept)).isPresent();
    }

    @Test
    @DisplayName("REQ-245 · 문장 1,499개 + 박스 2개 = 1,501개 400(박스도 센다) · 1,498 + 2는 200")
    void layoutCapCountsBoxes() throws Exception {
        String b = ready("q245");
        Story n = note("q245", bookOf("q245", "데미안", "헤르만 헤세", null), "옮긴 문장", "생각");
        long over = create(b, "넘침", "가.");
        box(b, over, book(n.getId()));
        box(b, over, book(n.getId()));
        int r = rev(b, over);
        String body = String.join("\n", Collections.nCopies(1499, "가.")) + "\n⟦인용 1⟧\n⟦인용 2⟧";
        json(put("/api/app/blog/posts/" + over), b, "{\"draftTitle\":\"넘침\",\"draftBody\":" + str(body) + ",\"revision\":" + r + "}")
                .andExpect(status().isOk());
        json(post("/api/app/blog/posts/" + over + "/publish"), b, "{\"revision\":" + (r + 1) + "}")
                .andExpect(status().isBadRequest()).andExpect(content().string("문장이 너무 많아요 — 1,500개까지 올릴 수 있어요"));
        assertThat(sentenceRepository.findByPostId(over)).isEmpty();

        String fits = String.join("\n", Collections.nCopies(1498, "가.")) + "\n⟦인용 1⟧\n⟦인용 2⟧";
        String view = republish(b, over, "넘침", fits);
        assertThat(JsonPath.<List<String>>read(view, "$.blocks[?(@.type == 'Q')].type")).hasSize(2);
    }

    @Test
    @DisplayName("엣지 · 표시 줄만 있는 글(문장 0)은 400 · 박스는 그대로")
    void markerOnlyPost() throws Exception {
        String b = ready("q241z");
        Story n = note("q241z", bookOf("q241z", "데미안", "헤르만 헤세", null), "옮긴 문장", "생각");
        long mine = create(b, "내 글", "내 문장.");
        long q = box(b, mine, book(n.getId()));
        json(put("/api/app/blog/posts/" + mine), b, "{\"draftTitle\":\"내 글\",\"draftBody\":\"⟦인용 1⟧\",\"revision\":0}")
                .andExpect(status().isOk());

        json(post("/api/app/blog/posts/" + mine + "/publish"), b, "{\"revision\":1}").andExpect(status().isBadRequest())
                .andExpect(content().string("본문을 한 문장 이상 적어 주세요"));
        assertThat(quoteBoxRepository.findById(q)).isPresent();
    }

    // ── REQ-243 글 지우기 · REQ-209 출처 이름 ───────────────────────────────

    @Test
    @DisplayName("REQ-243 · 지운 글을 인용한 남의 박스: 글 그대로 · sourceDeleted · 내 글이 가진 박스 행 0")
    void deletedSourceKeepsOthersBox() throws Exception {
        String a = ready("q243a");
        String b = ready("q243b");
        long src = published(a, "원 글", "봄에는 꽃이 핀다.");
        List<String> s = sidsOf(a, src);
        Story n = note("q243a", bookOf("q243a", "데미안", "헤르만 헤세", null), "옮긴 문장", "생각");
        long own = box(a, src, book(n.getId()));
        long mine = create(b, "내 글", "내 문장.");
        long q = box(b, mine, blog(src, s.get(0), s.get(0)));

        call(delete("/api/app/blog/posts/" + src), a).andExpect(status().isNoContent());
        em.clear();
        assertThat(quoteBoxRepository.findById(own)).isEmpty();
        assertThat(quoteBoxRepository.existsByOwnerTypeAndOwnerId(QuoteBox.Owner.POST, src)).isFalse();
        Map<String, Object> view = draftQuote(b, mine, 0);
        assertThat(view.get("text")).isEqualTo("봄에는 꽃이 핀다.");
        assertThat(view.get("sourceDeleted")).isEqualTo(true);
        assertThat(view.get("sourceLink")).isNull();
        assertThat(view.get("updatable")).isNull();
        refresh(b, q).andExpect(status().isConflict()).andExpect(content().string("원문이 지워졌어요"));
    }

    @Test
    @DisplayName("REQ-209 · 원 글쓴이가 닉네임을 바꾸면 남의 박스 출처 이름도 바뀐다")
    void attributionFollowsNickname() throws Exception {
        String a = ready("q209n");
        String b = ready("q209nb");
        long src = published(a, "원 글", "봄에는 꽃이 핀다.");
        List<String> s = sidsOf(a, src);
        long mine = create(b, "내 글", "내 문장.");
        box(b, mine, blog(src, s.get(0), s.get(0)));
        assertThat(draftQuote(b, mine, 0).get("attribution")).isEqualTo("닉_q209n · 「원 글」");

        User author = user("q209n");
        author.updateProfile("바꾼 이름", author.getTimezone());
        userRepository.saveAndFlush(author);
        em.clear();
        assertThat(draftQuote(b, mine, 0).get("attribution")).isEqualTo("바꾼 이름 · 「원 글」");
    }

    @Test
    @DisplayName("REQ-209 · 원 글을 지우면 제목은 인용 때 것 · 이름은 지금 것")
    void deletedSourceUsesSnapshotTitle() throws Exception {
        String a = ready("q209t");
        String b = ready("q209tb");
        long src = published(a, "처음 제목", "봄에는 꽃이 핀다.");
        List<String> s = sidsOf(a, src);
        long mine = create(b, "내 글", "내 문장.");
        box(b, mine, blog(src, s.get(0), s.get(0)));
        republish(a, src, "바뀐 제목", "봄에는 꽃이 핀다.");
        assertThat(draftQuote(b, mine, 0).get("attribution")).isEqualTo("닉_q209t · 「바뀐 제목」");

        call(delete("/api/app/blog/posts/" + src), a).andExpect(status().isNoContent());
        assertThat(draftQuote(b, mine, 0).get("attribution")).isEqualTo("닉_q209t · 「처음 제목」");
    }

    @Test
    @DisplayName("REQ-209 · 원 글쓴이와 차단 관계인 독자 · 가린 원 글엔 출처 제목이 인용 때 것 · 이름은 지금 것(양성 대조: 그 밖의 독자는 지금 제목)")
    void blockedReaderAndHiddenSourceGetSnapshotTitle() throws Exception {
        String a = ready("q209h");
        String b = ready("q209hb");
        String c = ready("q209hc");
        String d = ready("q209hd");
        long src = published(a, "처음 제목", "봄에는 꽃이 핀다.");
        List<String> s = sidsOf(a, src);
        long mine = create(b, "내 글", "내 문장.");
        box(b, mine, blog(src, s.get(0), s.get(0)));
        place(b, mine, "내 문장.\n⟦인용 1⟧");
        republish(a, src, "바뀐 제목", "봄에는 꽃이 핀다.");
        blockRepository.saveAndFlush(Block.of(user("q209h"), user("q209hc")));
        call(get("/api/app/blog/posts/" + mine), d)
                .andExpect(jsonPath("$.blocks[1].quote.attribution").value("닉_q209h · 「바뀐 제목」")); // 양성 대조
        call(get("/api/app/blog/posts/" + mine), c)
                .andExpect(jsonPath("$.blocks[1].quote.attribution").value("닉_q209h · 「처음 제목」")); // 차단 → 인용 때 제목 · 이름은 지금 것

        hide(src);
        republish(a, src, "가린 뒤 바꾼 제목", "봄에는 꽃이 핀다."); // 가린 글도 글쓴이는 다시 올릴 수 있다
        call(get("/api/app/blog/posts/" + mine), d)
                .andExpect(jsonPath("$.blocks[1].quote.attribution").value("닉_q209h · 「처음 제목」")); // deploy-F6
    }

    // ── REQ-247 구간 만들기 ─────────────────────────────────────────────────

    @Test
    @DisplayName("REQ-247 · 고른 문장이 산 문장이 아니면 400 · 1,001자 400 · 두 문단에 걸친 구간 201(줄바꿈)")
    void rangeRules() throws Exception {
        String a = ready("q247a");
        String b = ready("q247b");
        long src = published(a, "원 글", "봄에는 꽃이 핀다. 지울 문장이다.\n\n여름에는 비가 온다.");
        List<String> s = sidsOf(a, src);
        republish(a, src, "원 글", "봄에는 꽃이 핀다.\n\n여름에는 비가 온다.");
        long mine = create(b, "내 글", "내 문장.");
        String bad = "고른 문장을 찾을 수 없어요 — 글을 다시 불러와 주세요";

        quote(b, mine, blog(src, "ZZZZZZZZ", s.get(0))).andExpect(status().isBadRequest()).andExpect(content().string(bad));
        quote(b, mine, blog(src, s.get(1), s.get(2))).andExpect(status().isBadRequest()).andExpect(content().string(bad));
        quote(b, mine, blog(src, s.get(2), s.get(0))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.text").value("봄에는 꽃이 핀다.\n여름에는 비가 온다."));

        long big = published(a, "긴 글", "가".repeat(499) + ". " + "나".repeat(500) + ".");
        List<String> t = sidsOf(a, big);
        quote(b, mine, blog(big, t.get(0), t.get(1))).andExpect(status().isBadRequest())
                .andExpect(content().string("인용은 1,000자까지예요"));
        long draftOnly = create(a, "초안", "아직 안 올린 문장.");
        quote(b, mine, blog(draftOnly, "AAAAAAAA", "AAAAAAAA")).andExpect(status().isNotFound())
                .andExpect(content().string("글을 찾을 수 없어요"));
        assertThat(quoteBoxRepository.countByOwnerTypeAndOwnerId(QuoteBox.Owner.POST, mine)).isEqualTo(1);
    }

    @Test
    @DisplayName("REQ-247 · 인용 박스를 사이에 둔 구간 400")
    void rangeAcrossBox400() throws Exception {
        String a = ready("q247x");
        String b = ready("q247xb");
        Story n = note("q247x", bookOf("q247x", "데미안", "헤르만 헤세", null), "옮긴 문장", "생각");
        long src = create(a, "원 글", "봄에는 꽃이 핀다.");
        box(a, src, book(n.getId()));
        republish(a, src, "원 글", "봄에는 꽃이 핀다.\n⟦인용 1⟧\n여름에는 비가 온다.");
        List<String> s = sidsOf(a, src);
        long mine = create(b, "내 글", "내 문장.");

        quote(b, mine, blog(src, s.get(0), s.get(1))).andExpect(status().isBadRequest())
                .andExpect(content().string("인용 박스를 사이에 둔 문장은 한 박스로 고를 수 없어요"));
        quote(b, mine, blog(src, s.get(1), s.get(1))).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("REQ-247 · 자기 문장 인용 201")
    void selfQuote() throws Exception {
        String a = ready("q247s");
        long src = published(a, "내 글", "봄에는 꽃이 핀다.");
        List<String> s = sidsOf(a, src);

        quote(a, src, blog(src, s.get(0), s.get(0))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.attribution").value("닉_q247s · 「내 글」"));
        String view = place(a, src, "봄에는 꽃이 핀다.\n⟦인용 1⟧");
        assertThat(JsonPath.<List<String>>read(view, "$.blocks[*].type")).containsExactly("P", "Q");
    }

    // ── REQ-249 나뉜 조각 ───────────────────────────────────────────────────

    @Test
    @DisplayName("REQ-249 · 인용 뒤 나뉨 → 박스 지금 구간에 두 조각")
    void splitAfterQuote() throws Exception {
        String a = ready("q249a");
        String b = ready("q249b");
        long src = published(a, "원 글", "나는 책을 읽었고 그 책은 재미있었다.");
        String s1 = sidsOf(a, src).get(0);
        long mine = create(b, "내 글", "내 문장.");
        long q = box(b, mine, blog(src, s1, s1));
        republish(a, src, "원 글", "나는 책을 읽었다. 그 책은 재미있었다.");

        assertThat(draftQuote(b, mine, 0).get("updatable")).isEqualTo(true);
        refresh(b, q).andExpect(status().isOk()).andExpect(jsonPath("$.text").value("나는 책을 읽었다. 그 책은 재미있었다."));
        assertThat(boxRow(q).members()).hasSize(2).contains(s1); // 원래 sid가 어느 조각에 남는지는 판 비교 몫 — 둘 다 든다
    }

    /** 나뉜 뒤의 새 조각(원래 sid가 아닌 쪽 — split_from = 원래 sid). */
    private String fragmentOf(List<String> now, String original) {
        String f = now.stream().filter(x -> !x.equals(original)).findFirst().orElseThrow();
        assertThat(sentenceRepository.findBySid(f).orElseThrow().getSplitFrom()).isEqualTo(original);
        return f;
    }

    private String textOf(String sid) {
        return sentenceRepository.findBySid(sid).orElseThrow().getText();
    }

    @Test
    @DisplayName("REQ-249 · 인용 뒤 두 번 나뉜 조각까지")
    void splitTwiceAfterQuote() throws Exception {
        String a = ready("q249t");
        String b = ready("q249tb");
        long src = published(a, "원 글", "나는 책을 읽었고 그 책은 재미있었다.");
        String s1 = sidsOf(a, src).get(0);
        long mine = create(b, "내 글", "내 문장.");
        long q = box(b, mine, blog(src, s1, s1));
        String f1 = fragmentOf(sids(republish(a, src, "원 글", "나는 책을 읽었다. 그 책은 재미있었다.")), s1);
        // 둘째 나뉨(조각의 조각)은 행으로 직접 — 판 비교 문턱과 무관하게 사슬만 잰다(규칙 사례는 QuoteRangeTest R16)
        BlogSentence f2 = sentenceRepository.saveAndFlush(BlogSentence.create(
                sentenceRepository.findBySid(f1).orElseThrow().getPost(), "F2F2F2F2", "결말도 좋았다.", f1));
        sql("update blog_post set layout_json = ? where id = ?",
                "[{\"p\":[[\"" + f1 + "\",\"" + s1 + "\",\"" + f2.getSid() + "\"]],\"q\":null}]", src);

        refresh(b, q).andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value(textOf(f1) + " " + textOf(s1) + " 결말도 좋았다."));
        assertThat(boxRow(q).members()).containsExactly(f1, s1, "F2F2F2F2");
    }

    @Test
    @DisplayName("REQ-249 · 인용 전 나뉨 조각은 더하지 않는다(seen_sentence_id)")
    void splitBeforeQuoteIsNotAdded() throws Exception {
        String a = ready("q249p");
        String b = ready("q249pb");
        long src = published(a, "원 글", "나는 책을 읽었고 그 책은 재미있었다.");
        String s1 = sidsOf(a, src).get(0);
        fragmentOf(sids(republish(a, src, "원 글", "나는 책을 읽었다. 그 책은 재미있었다.")), s1);
        long mine = create(b, "내 글", "내 문장.");
        long q = box(b, mine, blog(src, s1, s1));

        assertThat(draftQuote(b, mine, 0).get("updatable")).isEqualTo(false);
        refresh(b, q).andExpect(status().isOk()).andExpect(jsonPath("$.text").value(textOf(s1)));
        assertThat(boxRow(q).members()).containsExactly(s1);
    }

    // ── REQ-297 여백 박스 다시 고르기 ──────────────────────────────────────

    private ResultActions repick(String token, long quoteId, int revision, int from, int to) throws Exception {
        return json(put("/api/app/blog/quotes/" + quoteId + "/source"), token,
                "{\"revision\":" + revision + ",\"from\":" + from + ",\"to\":" + to + "}");
    }

    @Test
    @DisplayName("REQ-297 · 다시 고르기 뒤 text · quotedAt 오늘 · label 그대로")
    void repickTakesNewRange() throws Exception {
        String b = ready("q297");
        Story n = note("q297", null, null, "첫 생각이다. 둘째 생각이다.\n셋째 줄이다.");
        long mine = create(b, "내 글", "내 문장.");
        long q = box(b, mine, margin(n.getId(), 0, 0, 0));
        ageBox(q);

        String res = repick(b, q, 0, 1, 2).andExpect(status().isOk()).andExpect(jsonPath("$.label").value(1))
                .andReturn().getResponse().getContentAsString();
        assertThat(text(res)).isEqualTo("둘째 생각이다.\n셋째 줄이다.");
        assertThat(Instant.parse(JsonPath.read(res, "$.quotedAt"))).isAfter(OLD.plusSeconds(86_400));
        refresh(b, q).andExpect(status().isBadRequest()).andExpect(content().string("최신으로 반영은 블로그 문장 인용만 돼요"));
    }

    @Test
    @DisplayName("REQ-297 · 범위가 생각 밖이면 400 · 남의 박스 404 · 원 쪽지 지움 409")
    void repickErrors() throws Exception {
        String b = ready("q297e");
        String c = ready("q297ec");
        Story n = note("q297e", bookOf("q297e", "데미안", "헤르만 헤세", null), "옮긴 문장", "첫 생각이다. 둘째 생각이다.");
        long mine = create(b, "내 글", "내 문장.");
        long q = box(b, mine, margin(n.getId(), 0, 0, 0));
        long bookBox = box(b, mine, book(n.getId()));

        repick(b, q, 0, 0, 2).andExpect(status().isBadRequest()).andExpect(content().string("고른 범위가 쪽지의 생각 밖이에요"));
        repick(b, q, 0, -1, 0).andExpect(status().isBadRequest());
        repick(c, q, 0, 0, 1).andExpect(status().isNotFound());
        // 소유 404가 종류 400보다 먼저 — 남은 박스 id로 종류를 알아낼 수 없다(§6.6 머리)
        repick(c, bookBox, 0, 0, 0).andExpect(status().isNotFound()).andExpect(content().string("인용을 찾을 수 없어요"));
        refresh(c, q).andExpect(status().isNotFound()).andExpect(content().string("인용을 찾을 수 없어요"));
        repick(b, bookBox, 0, 0, 0).andExpect(status().isBadRequest()).andExpect(content().string("여백 인용만 다시 고를 수 있어요"));
        refresh(b, q).andExpect(status().isBadRequest()).andExpect(content().string("최신으로 반영은 블로그 문장 인용만 돼요"));
        repick(b, q, 0, 0, 1).andExpect(status().isOk()); // 양성 대조

        storyRepository.delete(storyRepository.findById(n.getId()).orElseThrow());
        storyRepository.flush();
        repick(b, q, 0, 0, 0).andExpect(status().isConflict()).andExpect(content().string("원문(여백)이 지워졌어요"));
        assertThat(boxRow(q).getText()).isEqualTo("첫 생각이다. 둘째 생각이다.");
    }

    @Test
    @DisplayName("REQ-297 · 원 쪽지 판이 다르면 409(박스 글 그대로)")
    void repickRevisionMismatch() throws Exception {
        String b = ready("q297r");
        Story n = note("q297r", null, null, "첫 생각이다. 둘째 생각이다.");
        long mine = create(b, "내 글", "내 문장.");
        long q = box(b, mine, margin(n.getId(), 0, 0, 0));

        repick(b, q, 3, 0, 1).andExpect(status().isConflict()).andExpect(content().string("쪽지가 바뀌었어요 — 다시 골라 주세요"));
        assertThat(boxRow(q).getText()).isEqualTo("첫 생각이다.");
    }

    // ── REQ-236 구간 주소 ───────────────────────────────────────────────────

    private Map<String, Object> highlight(String token, long id, String s) throws Exception {
        return JsonPath.read(read(token, id, s), "$.highlight");
    }

    @Test
    @DisplayName("REQ-236 · 구간 주소 a-b → 지금 구간 문장들")
    void rangeAddress() throws Exception {
        String a = ready("q236");
        long src = published(a, "원 글", "봄에는 꽃이 핀다. 여름에는 비가 온다. 가을에는 낙엽이 진다.");
        List<String> s = sidsOf(a, src);

        assertThat(highlight(a, src, s.get(0) + "-" + s.get(2)))
                .containsEntry("status", "LIVE").containsEntry("sids", s);
        republish(a, src, "원 글", "봄에는 꽃이 핀다. 장마가 길었다. 여름에는 비가 온다. 가을에는 낙엽이 진다.");
        List<String> now = sidsOf(a, src);
        assertThat(highlight(a, src, s.get(0) + "-" + s.get(1))).containsEntry("sids", now.subList(0, 3));
        assertThat(read(a, src, s.get(0) + "-ZZZZZZZZ")).contains("\"highlight\":null");
        assertThat(read(a, src, "abc-def")).contains("\"highlight\":null");
    }

    @Test
    @DisplayName("REQ-236 · 끝 문장 b가 지워진 a-b → a만")
    void rangeEndDeleted() throws Exception {
        String a = ready("q236e");
        long src = published(a, "원 글", "봄에는 꽃이 핀다. 여름에는 비가 온다.");
        List<String> s = sidsOf(a, src);
        republish(a, src, "원 글", "봄에는 꽃이 핀다.");

        assertThat(highlight(a, src, s.get(0) + "-" + s.get(1))).containsEntry("status", "LIVE")
                .containsEntry("sids", List.of(s.get(0)));
    }

    @Test
    @DisplayName("REQ-236 · a · b 둘 다 지움 → DELETED")
    void rangeAllDeleted() throws Exception {
        String a = ready("q236d");
        long src = published(a, "원 글", "봄에는 꽃이 핀다. 여름에는 비가 온다.");
        List<String> s = sidsOf(a, src);
        republish(a, src, "원 글", "전혀 다른 이야기를 새로 적는다.");

        assertThat(highlight(a, src, s.get(0) + "-" + s.get(1))).containsEntry("status", "DELETED")
                .containsEntry("sids", List.of());
    }

    @Test
    @DisplayName("REQ-236 · 구간 주소 — 합쳐짐 MERGED · 고쳐짐 EDITED")
    void rangeMergedAndEdited() throws Exception {
        String a = ready("q236m");
        long src = published(a, "원 글", "나는 책을 읽었다. 그 책은 재미있었다.");
        List<String> s = sidsOf(a, src);
        republish(a, src, "원 글", "나는 책을 읽었고 그 책은 재미있었다.");
        assertThat(highlight(a, src, s.get(0) + "-" + s.get(1))).containsEntry("status", "MERGED")
                .containsEntry("sids", List.of(s.get(0)));

        long other = published(a, "다른 글", "봄에는 꽃이 핀다. 여름에는 비가 온다.");
        List<String> t = sidsOf(a, other);
        republish(a, other, "다른 글", "봄에는 꽃이 핀다. 여름에는 비가 많이 온다.");
        assertThat(highlight(a, other, t.get(0) + "-" + t.get(1))).containsEntry("status", "EDITED")
                .containsEntry("sids", t);
    }

    // ── REQ-214 hangBook ────────────────────────────────────────────────────

    @Test
    @DisplayName("REQ-214 · hangBook — 글이 인용한 책이 내 서재에 있으면 그 책 · 없으면 null")
    void hangBook() throws Exception {
        String a = ready("q214a");
        String b = ready("q214b");
        String c = ready("q214c");
        Story n = note("q214a", bookOf("q214a", "데미안", "헤르만 헤세", "9788937460449"), "새는 알에서 나오려고 투쟁한다", "생각");
        long src = create(a, "원 글", "봄에는 꽃이 핀다.");
        box(a, src, book(n.getId()));
        place(a, src, "봄에는 꽃이 핀다.\n⟦인용 1⟧");
        Book mineBook = bookOf("q214b", "데미안(내 책)", "헤르만 헤세", "9788937460449");
        bookOf("q214c", "싯다르타", "헤르만 헤세", "9788937460000");

        call(get("/api/app/blog/posts/" + src), b).andExpect(jsonPath("$.hangBook.bookId").value(mineBook.getId()))
                .andExpect(jsonPath("$.hangBook.title").value("데미안(내 책)"));
        call(get("/api/app/blog/posts/" + src), c).andExpect(jsonPath("$.hangBook").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("엣지 · 주인의 draft.quotes = 그 글 박스 전부(배치에 아직 없는 것 포함 · label 순)")
    void draftQuotesListsAll() throws Exception {
        String b = ready("qdraft");
        Story n = note("qdraft", bookOf("qdraft", "데미안", "헤르만 헤세", null), "옮긴 문장", "내 생각이다.");
        long mine = create(b, "내 글", "내 문장.");
        box(b, mine, book(n.getId()));
        box(b, mine, margin(n.getId(), 0, 0, 0));

        call(get("/api/app/blog/posts/" + mine), b).andExpect(jsonPath("$.draft.quotes", hasSize(2)))
                .andExpect(jsonPath("$.draft.quotes[0].label").value(1)).andExpect(jsonPath("$.draft.quotes[1].kind").value("MARGIN"));
    }
}
