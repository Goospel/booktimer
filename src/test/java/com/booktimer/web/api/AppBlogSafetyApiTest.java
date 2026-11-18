package com.booktimer.web.api;

import com.booktimer.auth.ApiTokenService;
import com.booktimer.blog.BlogModerationService;
import com.booktimer.blog.BlogModerationService.Unit;
import com.booktimer.blog.BlogReport;
import com.booktimer.blog.BlogReportRepository;
import com.booktimer.blog.Moderation.Action;
import com.booktimer.block.Block;
import com.booktimer.block.BlockRepository;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 책생글 B2b — 신고 · 가리기 · 차단 · 글쓰기 막기(계획 2026-11-16-blog-b2 §8.2 3 · §7) — 실 H2 + Bearer.
 *
 * <p>{@code AppBlogApiControllerTest}와 같은 세 줄 세계(새 컨텍스트 0 — T-235). 가리기는 운영자 서비스를 바로 부른다(관리자 문 ·
 * CSRF는 {@code AdminBlogControllerTest}가 잰다). 시험마다 같은 픽스처의 <b>내 것 · 남의 것 2xx</b>를 양성 대조로 둔다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AppBlogSafetyApiTest {

    private static final String SEOUL = "Asia/Seoul";
    private static final String HIDDEN_POST = "운영 정책으로 가려진 글이에요";
    private static final String NOT_FOUND = "글을 찾을 수 없어요";
    private static final String RESTRICTED = "블로그 글쓰기와 댓글이 운영 정책으로 막혔어요 — 이의는 설정의 [문의하기]로 보내 주세요";

    @Autowired MockMvc mvc;
    @Autowired UserRegistrationService registrationService;
    @Autowired AppleUserProvisioningService appleProvisioning;
    @Autowired UserRepository userRepository;
    @Autowired ApiTokenService apiTokenService;
    @Autowired BlogModerationService moderation;
    @Autowired BlogReportRepository reportRepository;
    @Autowired BlockRepository blockRepository;
    @Autowired StoryRepository storyRepository;
    @Autowired RateLimitService rateLimitService;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired Clock clock;

    @BeforeEach
    void clearRateLimits() {
        rateLimitService.clearForTest();
    }

    // ── 도우미 ──────────────────────────────────────────────────────────────

    private User register(String loginId, Role role) {
        registrationService.register(loginId + "@booktimer.com", "pw1234qwer!!", loginId, "닉_" + loginId, SEOUL, role,
                LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL)));
        return user(loginId);
    }

    private User user(String loginId) {
        return userRepository.findByLoginId(loginId).orElseThrow();
    }

    private String ready(String loginId) {
        return ready(loginId, Role.USER);
    }

    private String ready(String loginId, Role role) {
        User u = register(loginId, role);
        u.confirmBlogIdentity(Instant.now());
        userRepository.saveAndFlush(u);
        return apiTokenService.issue(u);
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

    private static long id(String res) {
        return ((Number) JsonPath.read(res, "$.id")).longValue();
    }

    private long create(String token, String title, String body) throws Exception {
        return id(json(post("/api/app/blog/posts"), token, "{\"draftTitle\":" + str(title) + ",\"draftBody\":" + str(body) + "}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private long published(String token, String title, String body) throws Exception {
        long id = create(token, title, body);
        json(post("/api/app/blog/posts/" + id + "/publish"), token, "{\"revision\":0}").andExpect(status().isOk());
        return id;
    }

    private String read(String token, long id) throws Exception {
        return call(get("/api/app/blog/posts/" + id), token).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private List<String> sidsOf(String token, long id) throws Exception {
        return JsonPath.read(read(token, id), "$.blocks[*].sentences[*].sid");
    }

    private static String commentsUrl(long postId, String sid) {
        return "/api/app/blog/posts/" + postId + "/sentences/" + sid + "/comments";
    }

    private long commented(String token, long postId, String sid, String body) throws Exception {
        return id(json(post(commentsUrl(postId, sid)), token, "{\"body\":" + str(body) + "}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private List<Map<String, Object>> sheet(String token, long postId, String sid) throws Exception {
        return JsonPath.read(call(get(commentsUrl(postId, sid)), token).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$");
    }

    private ResultActions report(String token, String type, long targetId, String reason, String detail) throws Exception {
        return json(post("/api/app/blog/reports"), token, "{\"targetType\":" + str(type) + ",\"targetId\":" + targetId
                + ",\"reason\":" + str(reason) + ",\"detail\":" + str(detail) + "}");
    }

    private ResultActions blockBy(String token, String type, long targetId) throws Exception {
        return json(post("/api/app/blog/blocks"), token, "{\"targetType\":" + str(type) + ",\"targetId\":" + targetId + "}");
    }

    private List<Long> feedIds(String token) throws Exception {
        List<Number> ids = JsonPath.read(call(get("/api/app/blog/posts"), token).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.posts[*].id");
        return ids.stream().map(Number::longValue).toList();
    }

    /** 글에 BLOG 박스(원 글 = source의 첫 문장)를 만들고 표시 줄을 적어 올린다 — 박스 id. */
    private long placedBox(String token, long postId, long sourceId, String sid) throws Exception {
        long box = id(json(post("/api/app/blog/posts/" + postId + "/quotes"), token,
                "{\"kind\":\"BLOG\",\"postId\":" + sourceId + ",\"fromSid\":" + str(sid) + ",\"toSid\":" + str(sid) + "}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        int rev = JsonPath.read(read(token, postId), "$.draft.revision");
        json(put("/api/app/blog/posts/" + postId), token, "{\"draftTitle\":\"박스 글\",\"draftBody\":\"앞 문장이다.\\n⟦인용 "
                + JsonPath.read(read(token, postId), "$.draft.quotes[0].label") + "⟧\",\"revision\":" + rev + "}").andExpect(status().isOk());
        json(post("/api/app/blog/posts/" + postId + "/publish"), token, "{\"revision\":" + (rev + 1) + "}").andExpect(status().isOk());
        return box;
    }

    private void flushClear() {
        em.flush();
        em.clear();
    }

    // ── REQ-242 · 252 신고 ──────────────────────────────────────────────────

    @Test
    @DisplayName("REQ-242 · 신고한 글은 신고자 목록에서 빠짐 · 남의 목록엔 그대로")
    void reportedPostLeavesReporterFeed() throws Exception {
        String author = ready("rf242a");
        String reporter = ready("rf242r");
        String other = ready("rf242o");
        long post = published(author, "신고될 글", "첫 문장이다.");
        long kept = published(author, "남는 글", "다른 문장이다.");
        assertThat(feedIds(reporter)).contains(post, kept); // 양성 대조

        report(reporter, "POST", post, "SPAM", null).andExpect(status().isOk());

        assertThat(feedIds(reporter)).contains(kept).doesNotContain(post);
        assertThat(feedIds(other)).contains(post, kept);
    }

    @Test
    @DisplayName("REQ-242 · 신고한 글 GET은 신고자에게 404(「글을 찾을 수 없어요」) · 남은 200")
    void reportedPostIs404ForReporter() throws Exception {
        String author = ready("rg242a");
        String reporter = ready("rg242r");
        String other = ready("rg242o");
        long post = published(author, "신고될 글", "첫 문장이다.");
        String sid = sidsOf(author, post).get(0);

        report(reporter, "POST", post, "HARASSMENT", null).andExpect(status().isOk());

        call(get("/api/app/blog/posts/" + post), reporter).andExpect(status().isNotFound()).andExpect(content().string(NOT_FOUND));
        call(get(commentsUrl(post, sid)), reporter).andExpect(status().isNotFound());
        call(get("/api/app/blog/sentences/" + sid + "/revs"), reporter).andExpect(status().isNotFound());
        json(post(commentsUrl(post, sid)), reporter, "{\"body\":\"댓글\"}").andExpect(status().isNotFound());
        call(get("/api/app/blog/posts/" + post), other).andExpect(status().isOk());
        call(get("/api/app/blog/posts/" + post), author).andExpect(status().isOk());
    }

    @Test
    @DisplayName("REQ-252 · 같은 대상 두 번째 신고 200(멱등) · 행 1")
    void reportIsIdempotent() throws Exception {
        String author = ready("ri252a");
        String reporter = ready("ri252r");
        long post = published(author, "글", "첫 문장이다.");
        report(reporter, "POST", post, "SPAM", null).andExpect(status().isOk());
        report(reporter, "POST", post, "SPAM", "두 번째").andExpect(status().isOk());
        flushClear();
        assertThat(reportRepository.findAll()).filteredOn(r -> r.getTargetId().equals(post)).singleElement()
                .satisfies(r -> {
                    assertThat(r.getTargetUserId()).isEqualTo(user("ri252a").getId());
                    assertThat(r.getDetail()).isNull();
                });
    }

    @Test
    @DisplayName("REQ-252 · 신고 뒤 신고자 댓글 목록에서 빠짐 · 남에겐 그대로")
    void reportedCommentLeavesReporterSheet() throws Exception {
        String author = ready("rc252a");
        String writer = ready("rc252w");
        String reporter = ready("rc252r");
        long post = published(author, "글", "첫 문장이다.");
        String sid = sidsOf(author, post).get(0);
        long c = commented(writer, post, sid, "신고될 댓글");

        report(reporter, "COMMENT", c, "HARASSMENT", null).andExpect(status().isOk());

        assertThat(sheet(reporter, post, sid)).isEmpty();
        assertThat(sheet(author, post, sid)).extracting(m -> ((Number) m.get("id")).longValue()).containsExactly(c);
    }

    @Test
    @DisplayName("REQ-252 · 신고 뒤 같은 사람의 다른 댓글은 그대로(대상 단위)")
    void reportIsPerTarget() throws Exception {
        String author = ready("rt252a");
        String writer = ready("rt252w");
        String reporter = ready("rt252r");
        long post = published(author, "글", "첫 문장이다.");
        String sid = sidsOf(author, post).get(0);
        long bad = commented(writer, post, sid, "신고될 댓글");
        long fine = commented(writer, post, sid, "남는 댓글");

        report(reporter, "COMMENT", bad, "SPAM", null).andExpect(status().isOk());

        assertThat(sheet(reporter, post, sid)).extracting(m -> ((Number) m.get("id")).longValue()).containsExactly(fine);
        assertThat(feedIds(reporter)).contains(post);
    }

    @Test
    @DisplayName("REQ-252 · 문장 · 박스 신고 = 글 대상 + detail sid: · quote: → 그 글이 신고자에게서 빠짐")
    void sentenceAndBoxReportsHideThePost() throws Exception {
        String author = ready("rs252a");
        String bySid = ready("rs252s");
        String byBox = ready("rs252b");
        long source = published(author, "원 글", "인용될 문장이다.");
        long post = create(author, "박스 글", "앞 문장이다.");
        long box = placedBox(author, post, source, sidsOf(author, source).get(0));
        String sid = sidsOf(author, post).get(0);

        report(bySid, "POST", post, "INAPPROPRIATE", "sid:" + sid + "\n이 문장이 문제예요").andExpect(status().isOk());
        report(byBox, "POST", post, "OTHER", "quote:" + box).andExpect(status().isOk());

        call(get("/api/app/blog/posts/" + post), bySid).andExpect(status().isNotFound());
        call(get("/api/app/blog/posts/" + post), byBox).andExpect(status().isNotFound());
        call(get("/api/app/blog/posts/" + source), byBox).andExpect(status().isOk()); // 원 글은 대상이 아니다
        flushClear();
        assertThat(reportRepository.findAll()).filteredOn(r -> r.getTargetId().equals(post))
                .extracting(BlogReport::getDetail).containsExactlyInAnyOrder("sid:" + sid + "\n이 문장이 문제예요", "quote:" + box);
    }

    @Test
    @DisplayName("REQ-252 · 내 글 · 내 댓글 신고 400 · 공개 대상 아닌 것(초안 · 가린 글 · 가린 댓글 · 없는 id) 404 · detail 501자 400(접두 포함 500자는 200)")
    void reportTargetRules() throws Exception {
        String author = ready("rr252a");
        String reporter = ready("rr252r");
        long post = published(author, "글", "첫 문장이다.");
        String sid = sidsOf(author, post).get(0);
        long mine = commented(reporter, post, sid, "내 댓글");
        long hiddenComment = commented(author, post, sid, "가려질 댓글");
        long draft = create(author, "초안", "아직 안 올린 글.");
        long hiddenPost = published(author, "가려질 글", "문장이다.");
        moderation.apply(Unit.POST, hiddenPost, Action.POLICY, null);
        moderation.apply(Unit.COMMENT, hiddenComment, Action.TEMP, null);

        report(author, "POST", post, "SPAM", null).andExpect(status().isBadRequest())
                .andExpect(content().string("내 글이나 댓글은 신고할 수 없어요"));
        report(reporter, "COMMENT", mine, "SPAM", null).andExpect(status().isBadRequest());
        for (long[] t : new long[][]{{0, draft}, {0, hiddenPost}, {1, hiddenComment}, {0, 987_654_321L}, {1, 987_654_321L}}) {
            report(reporter, t[0] == 0 ? "POST" : "COMMENT", t[1], "SPAM", null).andExpect(status().isNotFound())
                    .andExpect(content().string("신고할 대상을 찾을 수 없어요"));
        }
        report(reporter, "SENTENCE", post, "SPAM", null).andExpect(status().isBadRequest())
                .andExpect(content().string("신고할 대상을 골라 주세요"));
        String head = "sid:" + sid + "\n";
        report(reporter, "POST", post, "SPAM", "가".repeat(501)).andExpect(status().isBadRequest())
                .andExpect(content().string("부가 설명은 500자까지 쓸 수 있어요"));
        report(reporter, "POST", post, "SPAM", head + "가".repeat(500 - head.length() + 1)).andExpect(status().isBadRequest());
        report(reporter, "POST", post, "SPAM", head + "가".repeat(500 - head.length())).andExpect(status().isOk()); // 양성 대조
    }

    @Test
    @DisplayName("REQ-252 · 나를 차단한 사람의 글 · 내가 차단한 사람이 내 글에 단 댓글도 신고 200")
    void victimCanReportAcrossBlocks() throws Exception {
        String victim = ready("rv252v");
        String abuser = ready("rv252x");
        long victimPost = published(victim, "내 글", "첫 문장이다.");
        String sid = sidsOf(victim, victimPost).get(0);
        long abusive = commented(abuser, victimPost, sid, "욕설 댓글");
        long abuserPost = published(abuser, "저쪽 글", "문장이다.");
        blockRepository.saveAndFlush(Block.of(user("rv252x"), user("rv252v"))); // 저쪽이 나를 막았다
        blockRepository.saveAndFlush(Block.of(user("rv252v"), user("rv252x"))); // 나도 저쪽을 막았다

        report(victim, "COMMENT", abusive, "HARASSMENT", null).andExpect(status().isOk());
        report(victim, "POST", abuserPost, "HARASSMENT", null).andExpect(status().isOk());
        flushClear();
        assertThat(reportRepository.findAll()).filteredOn(r -> r.getReporter().getId().equals(user("rv252v").getId()))
                .hasSize(2);
    }

    @Test
    @DisplayName("REQ-252 · detail 접두가 다른 글의 문장 · 다른 글의 박스 · 쪽지 박스면 400 · 그 글의 것이면 200 · 꼴이 틀린 접두 400 · 댓글 대상에 접두 400")
    void prefixMustBelongToThePost() throws Exception {
        String author = ready("rp252a");
        String reporter = ready("rp252r");
        long source = published(author, "원 글", "인용될 문장이다.");
        String sourceSid = sidsOf(author, source).get(0);
        long post = create(author, "박스 글", "앞 문장이다.");
        long box = placedBox(author, post, source, sourceSid);
        long otherPost = create(author, "다른 박스 글", "앞 문장이다.");
        long otherBox = placedBox(author, otherPost, source, sourceSid);
        Story note = storyRepository.saveAndFlush(Story.of(user("rp252a"), "내 생각.", null, null));
        json(post("/api/app/margins/" + note.getId() + "/quotes"), author,
                "{\"postId\":" + source + ",\"fromSid\":" + str(sourceSid) + ",\"toSid\":" + str(sourceSid) + "}")
                .andExpect(status().is2xxSuccessful());
        long noteBox = jdbc.queryForObject("select max(id) from quote_box where owner_type = 'NOTE' and owner_id = ?",
                Long.class, note.getId());
        String sid = sidsOf(author, post).get(0);
        long c = commented(author, post, sid, "댓글");
        String bad = "신고할 문장이나 인용을 찾을 수 없어요 — 글을 다시 불러와 주세요";

        report(reporter, "POST", post, "SPAM", "sid:" + sourceSid).andExpect(status().isBadRequest()).andExpect(content().string(bad));
        report(reporter, "POST", post, "SPAM", "quote:" + otherBox).andExpect(status().isBadRequest());
        report(reporter, "POST", post, "SPAM", "quote:" + noteBox).andExpect(status().isBadRequest());
        report(reporter, "POST", post, "SPAM", "quote:abc").andExpect(status().isBadRequest());
        report(reporter, "COMMENT", c, "SPAM", "sid:" + sid).andExpect(status().isBadRequest());
        assertThat(reportRepository.count()).isZero();
        report(reporter, "POST", post, "SPAM", "quote:" + box).andExpect(status().isOk()); // 양성 대조
    }

    @Test
    @DisplayName("REQ-252 · 사유가 틀린 값이면 OTHER로 저장")
    void badReasonFallsBackToOther() throws Exception {
        String author = ready("ro252a");
        String reporter = ready("ro252r");
        long post = published(author, "글", "첫 문장이다.");
        report(reporter, "POST", post, "NOT_A_REASON", null).andExpect(status().isOk());
        flushClear();
        assertThat(reportRepository.findAll()).singleElement().satisfies(r -> assertThat(r.getReason().name()).isEqualTo("OTHER"));
    }

    @Test
    @DisplayName("REQ-252 · 11번째 신고(시간) 429")
    void reportRateLimit() throws Exception {
        List<String> authors = List.of(ready("rl252a"), ready("rl252b")); // 올리기 하루 상한(10)에 걸리지 않게 둘이 쓴다
        String reporter = ready("rl252r");
        for (int i = 0; i < 10; i++) {
            report(reporter, "POST", published(authors.get(i % 2), "글" + i, "문장이다."), "SPAM", null).andExpect(status().isOk());
        }
        report(reporter, "POST", published(authors.get(1), "열한째", "문장이다."), "SPAM", null).andExpect(status().isTooManyRequests());
    }

    // ── REQ-253 · 332 · 333 · 334 가리기 ────────────────────────────────────

    @Test
    @DisplayName("REQ-253 · 가린 글 남 404 「운영 정책으로 가려진 글이에요」 · 지운 글 · 초안은 「글을 찾을 수 없어요」 · 글쓴이 200(moderation)")
    void hiddenPostMessages() throws Exception {
        String author = ready("rh253a");
        String reader = ready("rh253r");
        long hidden = published(author, "가린 글", "첫 문장이다.");
        long draft = create(author, "초안", "아직.");
        long gone = published(author, "지울 글", "문장이다.");
        call(delete("/api/app/blog/posts/" + gone), author).andExpect(status().isNoContent());
        moderation.apply(Unit.POST, hidden, Action.TEMP, "게시자만 보는 글");

        call(get("/api/app/blog/posts/" + hidden), reader).andExpect(status().isNotFound()).andExpect(content().string(HIDDEN_POST));
        call(get("/api/app/blog/posts/" + draft), reader).andExpect(status().isNotFound()).andExpect(content().string(NOT_FOUND));
        call(get("/api/app/blog/posts/" + gone), reader).andExpect(status().isNotFound()).andExpect(content().string(NOT_FOUND));
        assertThat(feedIds(reader)).doesNotContain(hidden);
        call(get("/api/app/blog/posts/" + hidden), author).andExpect(status().isOk())
                .andExpect(jsonPath("$.hidden").value(true))
                .andExpect(jsonPath("$.moderation.kind").value("TEMP"))
                .andExpect(jsonPath("$.moderation.until").isNotEmpty())
                .andExpect(jsonPath("$.moderation.note").value("게시자만 보는 글"));
        call(get("/api/app/blog/posts/mine"), author)
                .andExpect(jsonPath("$[?(@.id == " + hidden + ")].moderation.kind").value(org.hamcrest.Matchers.contains("TEMP")));
    }

    @Test
    @DisplayName("REQ-253 · 가린 박스는 text 없이 hidden · 반영 · 다시 고르기 409 · updatable 없음")
    void hiddenBoxPlaceholder() throws Exception {
        String author = ready("rb253a");
        String reader = ready("rb253r");
        long source = published(author, "원 글", "인용될 문장이다.");
        long post = create(author, "박스 글", "앞 문장이다.");
        long box = placedBox(author, post, source, sidsOf(author, source).get(0));
        moderation.apply(Unit.QUOTE, box, Action.POLICY, "박스 까닭");

        for (String who : List.of(reader, author)) {
            String view = read(who, post);
            Map<String, Object> q = ((List<Map<String, Object>>) JsonPath.read(view, "$.blocks[?(@.type == 'Q')].quote")).get(0);
            assertThat(q.get("hidden")).isEqualTo(true);
            assertThat(q.get("text")).isNull();
            assertThat(q.get("attribution")).isNull();
            assertThat(q.get("updatable")).isNull();
            assertThat(q.get("sourceLink")).isNull();
            assertThat(view).doesNotContain("인용될 문장이다");
        }
        Map<String, Object> mineQ = ((List<Map<String, Object>>) JsonPath.read(read(author, post), "$.draft.quotes")).get(0);
        assertThat(((Map<String, Object>) mineQ.get("moderation")).get("note")).isEqualTo("박스 까닭");
        assertThat(read(reader, post)).doesNotContain("박스 까닭");
        json(post("/api/app/blog/quotes/" + box + "/refresh"), author, "{}").andExpect(status().isConflict())
                .andExpect(content().string("운영 정책으로 가려진 인용이에요"));
    }

    @Test
    @DisplayName("REQ-253 · 가린 여백 박스 다시 고르기 409")
    void hiddenMarginBoxRepick409() throws Exception {
        String author = ready("rm253a");
        Story note = storyRepository.saveAndFlush(Story.of(user("rm253a"), "내 생각이다. 둘째 생각이다.", null, null));
        long post = create(author, "글", "앞 문장이다.");
        long box = id(json(post("/api/app/blog/posts/" + post + "/quotes"), author, "{\"kind\":\"MARGIN\",\"storyId\":" + note.getId()
                + ",\"revision\":" + note.getRevision() + ",\"from\":0,\"to\":0}").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        String repick = "{\"revision\":" + note.getRevision() + ",\"from\":1,\"to\":1}";
        moderation.apply(Unit.QUOTE, box, Action.TEMP, null);
        json(put("/api/app/blog/quotes/" + box + "/source"), author, repick).andExpect(status().isConflict())
                .andExpect(content().string("운영 정책으로 가려진 인용이에요"));
        moderation.apply(Unit.QUOTE, box, Action.RESTORE, null);
        json(put("/api/app/blog/quotes/" + box + "/source"), author, repick).andExpect(status().isOk()); // 양성 대조
    }

    /** 막힌 계정 — 공개 글 · 댓글을 만들거나 바꾸는 문(약관 ⑧ 「글쓰기와 댓글」). */
    @ParameterizedTest(name = "REQ-253 · 막힌 계정은 공개 글 · 댓글을 만드는 문 전부 403(같은 문구) · {0}")
    @ValueSource(strings = {"draft-create", "draft-save", "publish", "quote-create", "post-box-refresh", "repick", "comment"})
    void restrictedWritesAre403(String door) throws Exception {
        String me = ready("rw253" + door.replace("-", "").substring(0, 4));
        User u = userRepository.findByLoginId("rw253" + door.replace("-", "").substring(0, 4)).orElseThrow();
        long post = published(me, "내 글", "첫 문장이다.");
        String sid = sidsOf(me, post).get(0);
        Story note = storyRepository.saveAndFlush(Story.of(u, "내 생각이다.", null, null));
        long blogBox = id(json(post("/api/app/blog/posts/" + post + "/quotes"), me,
                "{\"kind\":\"BLOG\",\"postId\":" + post + ",\"fromSid\":" + str(sid) + ",\"toSid\":" + str(sid) + "}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long marginBox = id(json(post("/api/app/blog/posts/" + post + "/quotes"), me, "{\"kind\":\"MARGIN\",\"storyId\":"
                + note.getId() + ",\"revision\":" + note.getRevision() + ",\"from\":0,\"to\":0}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        int rev = JsonPath.read(read(me, post), "$.draft.revision");
        moderation.restrict(u.getId(), true);

        ResultActions r = switch (door) {
            case "draft-create" -> json(post("/api/app/blog/posts"), me, "{\"draftTitle\":\"t\",\"draftBody\":\"b\"}");
            case "draft-save" -> json(put("/api/app/blog/posts/" + post), me,
                    "{\"draftTitle\":\"t\",\"draftBody\":\"b\",\"revision\":" + rev + "}");
            case "publish" -> json(post("/api/app/blog/posts/" + post + "/publish"), me, "{\"revision\":" + rev + "}");
            case "quote-create" -> json(post("/api/app/blog/posts/" + post + "/quotes"), me,
                    "{\"kind\":\"BLOG\",\"postId\":" + post + ",\"fromSid\":" + str(sid) + ",\"toSid\":" + str(sid) + "}");
            case "post-box-refresh" -> json(post("/api/app/blog/quotes/" + blogBox + "/refresh"), me, "{}");
            case "repick" -> json(put("/api/app/blog/quotes/" + marginBox + "/source"), me,
                    "{\"revision\":" + note.getRevision() + ",\"from\":0,\"to\":0}");
            default -> json(post(commentsUrl(post, sid)), me, "{\"body\":\"댓글\"}");
        };
        r.andExpect(status().isForbidden()).andExpect(content().string(RESTRICTED));
        moderation.restrict(u.getId(), false);
        call(get("/api/app/blog/posts/" + post), me).andExpect(status().isOk());
        json(post(commentsUrl(post, sid)), me, "{\"body\":\"풀린 뒤 댓글\"}").andExpect(status().isCreated()); // 양성 대조
    }

    @Test
    @DisplayName("REQ-253 · 막힌 계정도 blog/me · 걸기 · 붙이기 · 쪽지 박스 반영 · 신고 · 차단 · 지우기 · seen은 된다")
    void restrictedKeepsPrivateDoors() throws Exception {
        String me = ready("ro253m");
        String other = ready("ro253o");
        User u = user("ro253m");
        long mine = published(me, "내 글", "첫 문장이다.");
        long theirs = published(other, "남의 글", "인용될 문장이다. 둘째 문장이다.");
        List<String> sids = sidsOf(other, theirs);
        long myComment = commented(me, theirs, sids.get(0), "내 댓글");
        long otherComment = commented(other, theirs, sids.get(0), "남의 댓글");
        Story note = storyRepository.saveAndFlush(Story.of(u, "붙일 쪽지.", null, null));
        moderation.restrict(u.getId(), true);
        String link = "{\"postId\":" + theirs + ",\"fromSid\":" + str(sids.get(0)) + ",\"toSid\":" + str(sids.get(0)) + "}";

        json(put("/api/app/blog/me"), me, "{\"name\":\"새이름이다\",\"agreeTerms\":true}").andExpect(status().isOk());
        json(post("/api/app/margins"), me, "{\"bookId\":null,\"quote\":null,\"text\":\"걸기\",\"link\":" + link + "}")
                .andExpect(status().isCreated());
        json(post("/api/app/margins/" + note.getId() + "/quotes"), me, link).andExpect(status().is2xxSuccessful());
        Long noteBox = jdbc.queryForObject("select max(id) from quote_box where owner_type = 'NOTE' and owner_id = ?", Long.class,
                note.getId());
        json(post("/api/app/blog/quotes/" + noteBox + "/refresh"), me, "{}").andExpect(status().isOk());
        report(me, "COMMENT", otherComment, "SPAM", null).andExpect(status().isOk());
        blockBy(me, "POST", theirs).andExpect(status().isOk());
        call(post("/api/app/blog/posts/" + mine + "/seen"), me).andExpect(status().isNoContent());
        call(delete("/api/app/blog/comments/" + myComment), me).andExpect(status().isNoContent());
        call(delete("/api/app/blog/posts/" + mine), me).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("REQ-332 · 가린 댓글 자리: 남에겐 이름 · 본문 없이 hidden · 쓴 사람에겐 본문 + moderation")
    void hiddenCommentPlaceholder() throws Exception {
        String author = ready("rk332a");
        String writer = ready("rk332w");
        String reader = ready("rk332r");
        long post = published(author, "글", "첫 문장이다.");
        String sid = sidsOf(author, post).get(0);
        long c = commented(writer, post, sid, "가려질 댓글 본문");
        long shown = commented(reader, post, sid, "보이는 댓글"); // 양성 대조
        moderation.apply(Unit.COMMENT, c, Action.POLICY, "댓글 까닭");

        String others = call(get(commentsUrl(post, sid)), reader).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(c)).andExpect(jsonPath("$[0].hidden").value(true))
                .andExpect(jsonPath("$[0].body").value(nullValue())).andExpect(jsonPath("$[0].authorName").value(nullValue()))
                .andExpect(jsonPath("$[0].isAuthor").value(nullValue())).andExpect(jsonPath("$[0].moderation").value(nullValue()))
                .andExpect(jsonPath("$[1].id").value(shown)).andExpect(jsonPath("$[1].hidden").value(false))
                .andReturn().getResponse().getContentAsString();
        assertThat(others).doesNotContain("가려질 댓글 본문", "닉_rk332w", "댓글 까닭");
        call(get(commentsUrl(post, sid)), writer)
                .andExpect(jsonPath("$[0].body").value("가려질 댓글 본문")).andExpect(jsonPath("$[0].hidden").value(true))
                .andExpect(jsonPath("$[0].moderation.kind").value("POLICY")).andExpect(jsonPath("$[0].moderation.note").value("댓글 까닭"));
    }

    @Test
    @DisplayName("REQ-332 · 쪽지의 가린 박스: 쪽지 주인에게 text 없이 hidden + moderation")
    void hiddenNoteBox() throws Exception {
        String me = ready("rn332m");
        String other = ready("rn332o");
        long theirs = published(other, "남의 글", "인용될 문장이다.");
        String sid = sidsOf(other, theirs).get(0);
        Story note = storyRepository.saveAndFlush(Story.of(user("rn332m"), "내 생각.", null, null));
        json(post("/api/app/margins/" + note.getId() + "/quotes"), me,
                "{\"postId\":" + theirs + ",\"fromSid\":" + str(sid) + ",\"toSid\":" + str(sid) + "}").andExpect(status().is2xxSuccessful());
        long box = jdbc.queryForObject("select max(id) from quote_box where owner_type = 'NOTE' and owner_id = ?", Long.class, note.getId());
        moderation.apply(Unit.QUOTE, box, Action.COPYRIGHT, "중단 요청서 사본");

        call(get("/api/app/margins/" + note.getId()), me).andExpect(status().isOk())
                .andExpect(jsonPath("$.quotes[0].hidden").value(true)).andExpect(jsonPath("$.quotes[0].text").value(nullValue()))
                .andExpect(jsonPath("$.quotes[0].moderation.kind").value("COPYRIGHT"))
                .andExpect(jsonPath("$.quotes[0].moderation.note").value("중단 요청서 사본"));
    }

    @Test
    @DisplayName("REQ-332 · 운영자가 지운(REMOVED) 댓글 · 박스 · 글도 자리 표시는 남는다(내용 0)")
    void removedLeavesPlaceholders() throws Exception {
        String author = ready("rx332a");
        String writer = ready("rx332w");
        String reader = ready("rx332r");
        long source = published(author, "원 글", "인용될 문장이다.");
        long post = create(author, "박스 글", "앞 문장이다.");
        long box = placedBox(author, post, source, sidsOf(author, source).get(0));
        String sid = sidsOf(author, post).get(0);
        long c = commented(writer, post, sid, "지울 댓글");
        moderation.apply(Unit.COMMENT, c, Action.REMOVE, null);
        moderation.apply(Unit.QUOTE, box, Action.REMOVE, null);

        call(get(commentsUrl(post, sid)), reader).andExpect(jsonPath("$[0].id").value(c)).andExpect(jsonPath("$[0].hidden").value(true));
        call(get(commentsUrl(post, sid)), writer).andExpect(jsonPath("$[0].body").value(nullValue()))
                .andExpect(jsonPath("$[0].moderation.kind").value("REMOVED"));
        String view = read(reader, post);
        assertThat(view).doesNotContain("지울 댓글", "인용될 문장이다");
        assertThat((List<Object>) JsonPath.read(view, "$.blocks[?(@.type == 'Q')].quote.hidden")).containsExactly(true);

        moderation.apply(Unit.POST, post, Action.REMOVE, null);
        call(get("/api/app/blog/posts/" + post), reader).andExpect(status().isNotFound()).andExpect(content().string(HIDDEN_POST));
        call(get("/api/app/blog/posts/" + post), author).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value(nullValue())).andExpect(jsonPath("$.blocks", hasSize(0)))
                .andExpect(jsonPath("$.moderation.kind").value("REMOVED"));
    }

    @Test
    @DisplayName("REQ-333 · 원 글이 REMOVED면 그 글을 인용한 남의 박스는 sourceDeleted true · 반영은 「원문이 지워졌어요」 409 · TEMP면 sourceDeleted false(양성 대조)")
    void removedSourceReadsAsDeleted() throws Exception {
        String author = ready("rd333a");
        String quoter = ready("rd333q");
        long tempSource = published(author, "가릴 원 글", "인용될 문장이다.");
        long goneSource = published(author, "지울 원 글", "지워질 문장이다.");
        long tempPost = create(quoter, "인용한 글", "앞 문장이다.");
        long tempBox = placedBox(quoter, tempPost, tempSource, sidsOf(author, tempSource).get(0));
        long gonePost = create(quoter, "또 인용한 글", "앞 문장이다.");
        long goneBox = placedBox(quoter, gonePost, goneSource, sidsOf(author, goneSource).get(0));
        moderation.apply(Unit.POST, tempSource, Action.TEMP, null);
        moderation.apply(Unit.POST, goneSource, Action.REMOVE, null);

        Map<String, Object> temp = ((List<Map<String, Object>>) JsonPath.read(read(quoter, tempPost), "$.blocks[?(@.type == 'Q')].quote")).get(0);
        assertThat(temp.get("sourceDeleted")).isEqualTo(false);
        Map<String, Object> gone = ((List<Map<String, Object>>) JsonPath.read(read(quoter, gonePost), "$.blocks[?(@.type == 'Q')].quote")).get(0);
        assertThat(gone.get("sourceDeleted")).isEqualTo(true);
        assertThat(gone.get("sourceLink")).isNull();
        assertThat(gone.get("text")).isEqualTo("지워질 문장이다.");
        json(post("/api/app/blog/quotes/" + goneBox + "/refresh"), quoter, "{}").andExpect(status().isConflict())
                .andExpect(content().string("원문이 지워졌어요"));
        json(post("/api/app/blog/quotes/" + tempBox + "/refresh"), quoter, "{}").andExpect(status().isConflict())
                .andExpect(content().string("원문을 지금 볼 수 없어요"));
    }

    @Test
    @DisplayName("REQ-333 · REMOVED 글의 초안 저장 · 올리기 · 글 박스 만들기 409 · TEMP · POLICY 글은 글쓴이가 고쳐 올릴 수 있고 가린 채 남는다")
    void removedPostRejectsWrites() throws Exception {
        String author = ready("rz333a");
        String reader = ready("rz333r");
        long gone = published(author, "지울 글", "첫 문장이다.");
        long temp = published(author, "가릴 글", "첫 문장이다.");
        long source = published(author, "원 글", "인용될 문장이다.");
        String sid = sidsOf(author, source).get(0);
        moderation.apply(Unit.POST, gone, Action.REMOVE, null);
        moderation.apply(Unit.POST, temp, Action.TEMP, null);
        String removed = "운영 정책으로 지워진 글이에요 — 새 글로 써 주세요";

        json(put("/api/app/blog/posts/" + gone), author, "{\"draftTitle\":\"t\",\"draftBody\":\"b\",\"revision\":0}")
                .andExpect(status().isConflict()).andExpect(content().string(removed));
        json(post("/api/app/blog/posts/" + gone + "/publish"), author, "{\"revision\":0}")
                .andExpect(status().isConflict()).andExpect(content().string(removed));
        json(post("/api/app/blog/posts/" + gone + "/quotes"), author,
                "{\"kind\":\"BLOG\",\"postId\":" + source + ",\"fromSid\":" + str(sid) + ",\"toSid\":" + str(sid) + "}")
                .andExpect(status().isConflict()).andExpect(content().string(removed));

        json(put("/api/app/blog/posts/" + temp), author, "{\"draftTitle\":\"고친 제목\",\"draftBody\":\"고친 문장이다.\",\"revision\":0}")
                .andExpect(status().isOk());
        json(post("/api/app/blog/posts/" + temp + "/publish"), author, "{\"revision\":1}").andExpect(status().isOk());
        call(get("/api/app/blog/posts/" + temp), reader).andExpect(status().isNotFound()).andExpect(content().string(HIDDEN_POST));
    }

    @Test
    @DisplayName("REQ-334 · note는 게시자 DTO에만 — 남의 보기 · 독자 DTO에 없다")
    void noteOnlyForPoster() throws Exception {
        String author = ready("rn334a");
        String reader = ready("rn334r");
        long post = published(author, "글", "첫 문장이다.");
        String sid = sidsOf(author, post).get(0);
        long c = commented(author, post, sid, "내 댓글");
        moderation.apply(Unit.COMMENT, c, Action.TEMP, "게시자전용글자N");

        String forReader = call(get(commentsUrl(post, sid)), reader).andReturn().getResponse().getContentAsString()
                + read(reader, post) + call(get("/api/app/blog/posts"), reader).andReturn().getResponse().getContentAsString();
        assertThat(forReader).doesNotContain("게시자전용글자N");
        assertThat(call(get(commentsUrl(post, sid)), author).andReturn().getResponse().getContentAsString())
                .contains("게시자전용글자N"); // 양성 대조
    }

    // ── REQ-254 차단(B2b — 앱에서 대상으로) ─────────────────────────────────

    @Test
    @DisplayName("REQ-254 · loginId 없는 앱 계정을 댓글 id로 차단 → 목록에 userId · 이름 → 풀기 204")
    void blockAppleAuthorByComment() throws Exception {
        String author = ready("bk254a");
        String me = ready("bk254m");
        User apple = appleProvisioning.register("001.blog.block254");
        apple.confirmBlogIdentity(Instant.now());
        userRepository.saveAndFlush(apple);
        String appleToken = apiTokenService.issue(apple);
        long post = published(author, "글", "첫 문장이다.");
        String sid = sidsOf(author, post).get(0);
        long c = commented(appleToken, post, sid, "애플 계정 댓글");

        blockBy(me, "COMMENT", c).andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(apple.getId())).andExpect(jsonPath("$.name").value(apple.getNickname()));
        call(get("/api/app/blocks"), me).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].userId").value(apple.getId()));
        assertThat(sheet(me, post, sid)).isEmpty();
        call(delete("/api/app/blocks/" + apple.getId()), me).andExpect(status().isNoContent());
        call(get("/api/app/blocks"), me).andExpect(jsonPath("$", hasSize(0)));
        call(delete("/api/app/blocks/" + apple.getId()), me).andExpect(status().isNoContent()); // 멱등
        assertThat(sheet(me, post, sid)).hasSize(1);
    }

    @Test
    @DisplayName("REQ-254 · 글 id로 차단하면 그 사람 글이 내 목록에서 0 · 글 404")
    void blockByPostHidesBothWays() throws Exception {
        String author = ready("bp254a");
        String me = ready("bp254m");
        long post = published(author, "글", "첫 문장이다.");
        long minePost = published(me, "내 글", "내 문장이다.");
        blockBy(me, "POST", post).andExpect(status().isOk());

        assertThat(feedIds(me)).doesNotContain(post);
        call(get("/api/app/blog/posts/" + post), me).andExpect(status().isNotFound());
        call(get("/api/app/blog/posts/" + minePost), author).andExpect(status().isNotFound()); // 양방향
        assertThat(feedIds(author)).doesNotContain(minePost);
    }

    @Test
    @DisplayName("REQ-254 · 나 자신 400 · 없는 대상 · 초안 404")
    void blockTargetRules() throws Exception {
        String me = ready("bt254m");
        String other = ready("bt254o");
        long mine = published(me, "내 글", "첫 문장이다.");
        long draft = create(other, "초안", "아직.");
        blockBy(me, "POST", mine).andExpect(status().isBadRequest()).andExpect(content().string("나를 차단할 수는 없어요"));
        blockBy(me, "POST", draft).andExpect(status().isNotFound()).andExpect(content().string("차단할 대상을 찾을 수 없어요"));
        blockBy(me, "COMMENT", 987_654_321L).andExpect(status().isNotFound());
        blockBy(me, "USER", mine).andExpect(status().isBadRequest());
        assertThat(blockRepository.findByBlockerOrderByCreatedAtDesc(user("bt254m"))).isEmpty();
    }

    @Test
    @DisplayName("REQ-254 · 가린 댓글 · 가린 글 id로 차단하면 404 · 응답에 이름 0 · 차단 행 0")
    void blockHiddenTargets404() throws Exception {
        String author = ready("bh254a");
        String writer = ready("bh254w");
        String me = ready("bh254m");
        long post = published(author, "글", "첫 문장이다.");
        String sid = sidsOf(author, post).get(0);
        long c = commented(writer, post, sid, "가려질 댓글");
        long hiddenPost = published(writer, "가릴 글", "문장이다.");
        moderation.apply(Unit.COMMENT, c, Action.POLICY, null);
        moderation.apply(Unit.POST, hiddenPost, Action.TEMP, null);

        for (ResultActions r : List.of(blockBy(me, "COMMENT", c), blockBy(me, "POST", hiddenPost))) {
            r.andExpect(status().isNotFound()).andExpect(content().string(not(containsString("닉_bh254w"))));
        }
        assertThat(blockRepository.findByBlockerOrderByCreatedAtDesc(user("bh254m"))).isEmpty();
        moderation.apply(Unit.COMMENT, c, Action.RESTORE, null);
        blockBy(me, "COMMENT", c).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("닉_bh254w")); // 양성 대조
    }

    @Test
    @DisplayName("REQ-254 · 신고해서 내 화면에서 빠진 댓글 · 이미 차단한 사람의 글로도 차단 200(멱등)")
    void blockAfterReportAndTwice() throws Exception {
        String author = ready("br254a");
        String writer = ready("br254w");
        String me = ready("br254m");
        long post = published(author, "글", "첫 문장이다.");
        String sid = sidsOf(author, post).get(0);
        long c = commented(writer, post, sid, "신고할 댓글");
        long writerPost = published(writer, "쓴 사람 글", "문장이다.");
        report(me, "COMMENT", c, "HARASSMENT", null).andExpect(status().isOk());

        blockBy(me, "COMMENT", c).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("닉_br254w"));
        blockBy(me, "POST", writerPost).andExpect(status().isOk());
        report(me, "POST", writerPost, "SPAM", null).andExpect(status().isOk()); // 차단 뒤 신고도 된다(§4.4)
        assertThat(blockRepository.findByBlockerOrderByCreatedAtDesc(user("br254m"))).hasSize(1);
    }

    @Test
    @DisplayName("REQ-254 · 운영자(ADMIN)가 쓴 글 id로 차단 200 · 목록에 실린다")
    void adminAuthorCanBeBlocked() throws Exception {
        String admin = ready("ba254x", Role.ADMIN);
        String me = ready("ba254m");
        long post = published(admin, "운영자 견본 글", "첫 문장이다.");
        blockBy(me, "POST", post).andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(user("ba254x").getId()));
        call(get("/api/app/blocks"), me).andExpect(jsonPath("$[0].name").value("닉_ba254x"));
    }

    // ── REQ-266 · 298 · 335 ─────────────────────────────────────────────────

    @Test
    @DisplayName("REQ-266 · 가린 댓글은 문장 뒤 수에서 빠진다")
    void hiddenCommentsLeaveSentenceCount() throws Exception {
        String author = ready("cn266a");
        String writer = ready("cn266w");
        long post = published(author, "글", "첫 문장이다.");
        String sid = sidsOf(author, post).get(0);
        long c = commented(writer, post, sid, "가려질 댓글");
        commented(writer, post, sid, "남는 댓글");
        String count = "$.blocks[0].sentences[0].comments";
        assertThat((Integer) JsonPath.read(read(author, post), count)).isEqualTo(2); // 양성 대조
        moderation.apply(Unit.COMMENT, c, Action.POLICY, null);
        assertThat((Integer) JsonPath.read(read(author, post), count)).isEqualTo(1);
        assertThat((Integer) JsonPath.read(read(writer, post), count)).as("쓴 사람이 봐도 같은 수(J39)").isEqualTo(1);
    }

    @Test
    @DisplayName("REQ-298 · 가린 댓글은 새 댓글 수에서 빠진다")
    void hiddenCommentsLeaveNewCount() throws Exception {
        String author = ready("nc298a");
        String writer = ready("nc298w");
        long post = published(author, "글", "첫 문장이다.");
        String sid = sidsOf(author, post).get(0);
        long c = commented(writer, post, sid, "가려질 댓글");
        commented(writer, post, sid, "남는 댓글");
        String q = "$[?(@.id == " + post + ")].newComments";
        call(get("/api/app/blog/posts/mine"), author).andExpect(jsonPath(q).value(org.hamcrest.Matchers.contains(2)));
        moderation.apply(Unit.COMMENT, c, Action.TEMP, null);
        call(get("/api/app/blog/posts/mine"), author).andExpect(jsonPath(q).value(org.hamcrest.Matchers.contains(1)));
        assertThat((Integer) JsonPath.read(read(author, post), "$.blocks[0].sentences[0].comments")).isEqualTo(1);
    }

    @Test
    @DisplayName("REQ-335 · GET /api/app/blog/reports = 내 신고만(남의 신고 0) · 처리 결과")
    void myReportsOnly() throws Exception {
        String author = ready("mr335a");
        String me = ready("mr335m");
        String other = ready("mr335o");
        long post = published(author, "글", "첫 문장이다.");
        long post2 = published(author, "둘째 글", "문장이다.");
        report(me, "POST", post, "SPAM", "내 부가 설명").andExpect(status().isOk());
        report(other, "POST", post2, "SPAM", "남의 부가 설명").andExpect(status().isOk());
        flushClear();
        long mine = reportRepository.findAll().stream().filter(r -> r.getTargetId().equals(post)).findFirst().orElseThrow().getId();
        moderation.resolve(mine, Action.TEMP, Unit.POST, post, null);

        String res = call(get("/api/app/blog/reports"), me).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].targetType").value("POST"))
                .andExpect(jsonPath("$[0].targetId").value(post)).andExpect(jsonPath("$[0].status").value("RESOLVED"))
                .andExpect(jsonPath("$[0].result").value("HIDDEN")).andExpect(jsonPath("$[0].resolvedAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(res).doesNotContain("남의 부가 설명");
        call(get("/api/app/blog/reports"), other).andExpect(jsonPath("$[0].status").value("OPEN"));
    }
}
