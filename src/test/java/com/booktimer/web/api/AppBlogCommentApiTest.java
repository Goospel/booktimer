package com.booktimer.web.api;

import com.booktimer.auth.ApiTokenService;
import com.booktimer.blog.BlogCommentRepository;
import com.booktimer.blog.BlogSentence;
import com.booktimer.blog.BlogSentenceRepository;
import com.booktimer.blog.BlogSentenceRevRepository;
import com.booktimer.blog.QuoteBoxRepository;
import com.booktimer.block.Block;
import com.booktimer.block.BlockRepository;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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
 * 책생글 B2a — 문장 댓글 · 걸러내기 · 제한 · 새 댓글 수 · 예전 판 지우기(계획 2026-11-16-blog-b2 §8.1 5 · §7) — 실 H2 + Bearer.
 *
 * <p>{@code AppBlogApiControllerTest}와 같은 세 줄 세계(새 컨텍스트 0 — T-235). 시계는 운영과 같은 {@code Clock.systemUTC()}라
 * seen(서비스 시계)과 댓글 시각(JPA 감사 — 시스템 시각)이 맞는다(§8 머리 「시계 함정」). 보이지 않는 글자는 코드 포인트로 조립한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AppBlogCommentApiTest {

    private static final String SEOUL = "Asia/Seoul";
    private static final String SIBAL = new String(new int[]{0xC2DC, 0xBC1C}, 0, 2); // 금지어 픽스처

    @Autowired MockMvc mvc;
    @Autowired UserRegistrationService registrationService;
    @Autowired UserRepository userRepository;
    @Autowired ApiTokenService apiTokenService;
    @Autowired BlogSentenceRepository sentenceRepository;
    @Autowired BlogSentenceRevRepository revRepository;
    @Autowired BlogCommentRepository commentRepository;
    @Autowired QuoteBoxRepository quoteBoxRepository;
    @Autowired StoryRepository storyRepository;
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

    private User register(String loginId) {
        registrationService.register(loginId + "@booktimer.com", "pw1234qwer!!", loginId, "닉_" + loginId, SEOUL, Role.USER,
                LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL)));
        return user(loginId);
    }

    private User user(String loginId) {
        return userRepository.findByLoginId(loginId).orElseThrow();
    }

    /** 첫 설정을 마친 사용자의 토큰. */
    private String ready(String loginId) {
        User u = register(loginId);
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
        return s == null ? "null" : "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                .replace("\t", "\\t") + "\"";
    }

    private long create(String token, String title, String body) throws Exception {
        String res = json(post("/api/app/blog/posts"), token,
                "{\"draftTitle\":" + str(title) + ",\"draftBody\":" + str(body) + "}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(res, "$.id")).longValue();
    }

    private ResultActions publishCall(String token, long id, int revision) throws Exception {
        return json(post("/api/app/blog/posts/" + id + "/publish"), token, "{\"revision\":" + revision + "}");
    }

    private void edit(String token, long id, String title, String body, int revision) throws Exception {
        json(put("/api/app/blog/posts/" + id), token,
                "{\"draftTitle\":" + str(title) + ",\"draftBody\":" + str(body) + ",\"revision\":" + revision + "}")
                .andExpect(status().isOk());
    }

    private long published(String token, String title, String body) throws Exception {
        long id = create(token, title, body);
        publishCall(token, id, 0).andExpect(status().isOk());
        return id;
    }

    private String read(String token, long id) throws Exception {
        return call(get("/api/app/blog/posts/" + id), token).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private List<String> sidsOf(String token, long id) throws Exception {
        return JsonPath.read(read(token, id), "$.blocks[*].sentences[*].sid");
    }

    private static String commentsUrl(long postId, String sid) {
        return "/api/app/blog/posts/" + postId + "/sentences/" + sid + "/comments";
    }

    private ResultActions comment(String token, long postId, String sid, String body) throws Exception {
        return json(post(commentsUrl(postId, sid)), token, "{\"body\":" + str(body) + "}");
    }

    private long commented(String token, long postId, String sid, String body) throws Exception {
        String res = comment(token, postId, sid, body).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(res, "$.id")).longValue();
    }

    private List<Map<String, Object>> sheet(String token, long postId, String sid) throws Exception {
        return JsonPath.read(call(get(commentsUrl(postId, sid)), token).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$");
    }

    private BlogSentence row(String sid) {
        em.flush();
        em.clear();
        return sentenceRepository.findBySid(sid).orElseThrow();
    }

    private void sql(String statement, Object... args) {
        em.flush();
        jdbc.update(statement, args);
        em.clear();
    }

    private void block(String blocker, String blocked) {
        blockRepository.saveAndFlush(Block.of(user(blocker), user(blocked)));
    }

    // ── REQ-250 댓글 시트 ───────────────────────────────────────────────────

    @Test
    @DisplayName("REQ-250 · 합친 문장 시트에 옛 문장 댓글(merged true)")
    void mergedSheetCarriesOldComments() throws Exception {
        String me = ready("cmerge");
        String reader = ready("cmerger");
        long id = published(me, "제목", "나는 책을 읽었다. 그 책은 재미있었다.");
        List<String> old = sidsOf(me, id);
        long first = commented(reader, id, old.get(0), "첫 문장 댓글");
        long second = commented(reader, id, old.get(1), "둘째 문장 댓글");
        edit(me, id, "제목", "나는 책을 읽었고 그 책은 재미있었다.", 0);
        publishCall(me, id, 1).andExpect(status().isOk());
        String target = sidsOf(me, id).get(0);

        List<Map<String, Object>> viaNew = sheet(reader, id, target);
        assertThat(viaNew).extracting(c -> ((Number) c.get("id")).longValue()).containsExactly(first, second); // 오래된 순
        assertThat(viaNew).filteredOn(c -> !target.equals(c.get("sid")))
                .allSatisfy(c -> assertThat(c.get("merged")).isEqualTo(true)).isNotEmpty();
        assertThat(viaNew).filteredOn(c -> target.equals(c.get("sid")))
                .allSatisfy(c -> assertThat(c.get("merged")).isEqualTo(false));
        assertThat(sheet(reader, id, old.get(1))).as("옛 주소로 불러도 같은 묶음").hasSize(2);
    }

    @Test
    @DisplayName("REQ-250 · 글쓴이 댓글 isAuthor · 내 댓글 mine")
    void authorAndMineFlags() throws Exception {
        String me = ready("cflag");
        String reader = ready("cflagr");
        long id = published(me, "제목", "한 문장이다.");
        String sid = sidsOf(me, id).get(0);
        commented(me, id, sid, "글쓴이가 단 댓글");
        commented(reader, id, sid, "독자가 단 댓글");

        List<Map<String, Object>> asReader = sheet(reader, id, sid);
        assertThat(asReader).hasSize(2);
        assertThat(asReader.get(0)).containsEntry("body", "글쓴이가 단 댓글").containsEntry("isAuthor", true)
                .containsEntry("mine", false).containsEntry("authorName", "닉_cflag").containsEntry("merged", false);
        assertThat(asReader.get(1)).containsEntry("isAuthor", false).containsEntry("mine", true)
                .containsEntry("authorName", "닉_cflagr");
        assertThat(asReader.get(1).get("createdAt")).isNotNull();
    }

    @Test
    @DisplayName("REQ-250 · 셋을 하나로 합쳐 묘비가 된 옛 문장의 댓글은 그 옛 주소로 부르면 보인다 · 새 문장 시트엔 0(A32)")
    void tombstonedCommentsReachableByOldAddress() throws Exception {
        String me = ready("ca32");
        String reader = ready("ca32r");
        long id = published(me, "제목", "나는 이 책을 주말에 샀다. 일요일 밤에 읽기 시작했다. 월요일 새벽에 다 읽었다.");
        List<String> old = sidsOf(me, id);
        assertThat(old).hasSize(3);
        commented(reader, id, old.get(1), "둘째 문장에 단 댓글");
        edit(me, id, "제목", "나는 이 책을 주말에 사서 일요일 밤에 읽기 시작했고 월요일 새벽에 다 읽었다.", 0);
        publishCall(me, id, 1).andExpect(status().isOk());
        assertThat(row(old.get(1)).live()).as("A32 전제 — 묘비").isFalse();
        assertThat(row(old.get(1)).getMergedInto()).as("A32 전제 — 합침 표시 없음").isNull();
        String fresh = sidsOf(me, id).get(0);

        assertThat(sheet(reader, id, fresh)).isEmpty();
        assertThat(sheet(reader, id, old.get(1))).singleElement()
                .satisfies(c -> assertThat(c).containsEntry("body", "둘째 문장에 단 댓글").containsEntry("merged", false));
        // 묘비에는 새로 쓸 수 없다 — 산 문장만(§6.3)
        comment(reader, id, old.get(1), "묘비에 쓰기").andExpect(status().isNotFound())
                .andExpect(content().string("문장을 찾을 수 없어요"));
    }

    @Test
    @DisplayName("REQ-250 · 1,000자 201 · 1,001자 400 · 빈 · 보이지 않는 글자뿐 400")
    void bodyLimits() throws Exception {
        String me = ready("clen");
        long id = published(me, "제목", "한 문장이다.");
        String sid = sidsOf(me, id).get(0);

        commented(me, id, sid, "가나다라마바사아자차".repeat(100));
        comment(me, id, sid, "가나다라마바사아자차".repeat(100) + "카").andExpect(status().isBadRequest())
                .andExpect(content().string("댓글은 1,000자까지 쓸 수 있어요"));
        comment(me, id, sid, "  \n ").andExpect(status().isBadRequest()).andExpect(content().string("댓글을 적어 주세요"));
        comment(me, id, sid, null).andExpect(status().isBadRequest()).andExpect(content().string("댓글을 적어 주세요"));
        comment(me, id, sid, new String(new int[]{0x200B, 0xA0, 0x3164}, 0, 3)).andExpect(status().isBadRequest())
                .andExpect(content().string("댓글을 적어 주세요"));
        assertThat(sheet(me, id, sid)).hasSize(1);
    }

    @Test
    @DisplayName("REQ-250 · 산 문장이 아닌 sid · 다른 글의 sid로 쓰면 404 · 그 글의 문장 아닌 sid 목록 404")
    void sidMustBelongToPost() throws Exception {
        String me = ready("csid");
        long a = published(me, "가 글", "가 문장이다.");
        long b = published(me, "나 글", "나 문장이다.");
        String bSid = sidsOf(me, b).get(0);

        comment(me, a, bSid, "남의 글 문장").andExpect(status().isNotFound()).andExpect(content().string("문장을 찾을 수 없어요"));
        call(get(commentsUrl(a, bSid)), me).andExpect(status().isNotFound()).andExpect(content().string("문장을 찾을 수 없어요"));
        call(get(commentsUrl(a, "zz")), me).andExpect(status().isNotFound());
        comment(me, a, "zz", "꼴이 틀린 주소").andExpect(status().isNotFound());
        commented(me, a, sidsOf(me, a).get(0), "양성 대조"); // 그 글의 산 문장은 된다
        comment(me, 987_654_321L, bSid, "없는 글").andExpect(status().isNotFound()).andExpect(content().string("글을 찾을 수 없어요"));
    }

    @Test
    @DisplayName("REQ-250 · 초안 · 남의 가린 글에 쓰기 · 목록 404")
    void draftAndHiddenPosts404() throws Exception {
        String me = ready("chide");
        String reader = ready("chider");
        long draft = create(me, "초안", "아직 올리지 않았다.");
        long hidden = published(me, "가릴 글", "가려질 문장이다.");
        String sid = sidsOf(me, hidden).get(0);
        long c = commented(reader, hidden, sid, "가리기 전 댓글");
        sql("update blog_post set hidden_at = ? where id = ?", Timestamp.from(Instant.now()), hidden);

        comment(reader, draft, sid, "초안에").andExpect(status().isNotFound()).andExpect(content().string("글을 찾을 수 없어요"));
        call(get(commentsUrl(draft, sid)), reader).andExpect(status().isNotFound());
        comment(reader, hidden, sid, "가린 글에").andExpect(status().isNotFound()).andExpect(content().string("글을 찾을 수 없어요"));
        call(get(commentsUrl(hidden, sid)), reader).andExpect(status().isNotFound());
        comment(me, hidden, sid, "주인도 가린 글엔 못 쓴다").andExpect(status().isNotFound());
        assertThat(sheet(me, hidden, sid)).as("게시자는 가린 글의 댓글을 본다(§6.4)")
                .extracting(x -> ((Number) x.get("id")).longValue()).containsExactly(c);
    }

    @Test
    @DisplayName("REQ-250 · 댓글 300개 넘는 문장 — 최근 300개 · 오래된 순")
    void sheetKeepsLatest300() throws Exception {
        String me = ready("c300");
        long id = published(me, "제목", "한 문장이다.");
        String sid = sidsOf(me, id).get(0);
        com.booktimer.blog.BlogPost post = em.find(com.booktimer.blog.BlogPost.class, id);
        User author = user("c300");
        for (int i = 0; i < 301; i++) { // 상한(20/시간)을 비켜 저장소로 직접 — 시트 자르기만 잰다
            commentRepository.save(com.booktimer.blog.BlogComment.of(post, sid, author, "댓글 " + i));
        }
        commentRepository.flush();

        List<Map<String, Object>> rows = sheet(me, id, sid);
        assertThat(rows).hasSize(300);
        assertThat(rows.get(0).get("body")).isEqualTo("댓글 1"); // 가장 오래된 「댓글 0」이 빠진다
        assertThat(rows.get(299).get("body")).isEqualTo("댓글 300");
    }

    // ── REQ-246 · 259 첫 설정 ───────────────────────────────────────────────

    @Test
    @DisplayName("REQ-246 · 같은 계정 첫 댓글 409(이름 미확인)")
    void firstCommentNeedsIdentity() throws Exception {
        String me = ready("cident");
        long id = published(me, "제목", "한 문장이다.");
        String sid = sidsOf(me, id).get(0);
        User fresh = register("cidentr");
        String token = apiTokenService.issue(fresh);

        comment(token, id, sid, "첫 댓글").andExpect(status().isConflict())
                .andExpect(content().string("블로그에 보일 이름과 커뮤니티 규칙을 먼저 확인해 주세요"));
        json(put("/api/app/blog/me"), token, "{\"name\":\"새 독자\",\"agreeTerms\":true}").andExpect(status().isOk());
        comment(token, id, sid, "첫 댓글").andExpect(status().isCreated()); // 양성 대조
    }

    @Test
    @DisplayName("REQ-259 · ugc_terms_at null이면 댓글 409")
    void commentNeedsTerms() throws Exception {
        String me = ready("cterms");
        String reader = ready("ctermsr");
        long id = published(me, "제목", "한 문장이다.");
        String sid = sidsOf(me, id).get(0);
        sql("update users set ugc_terms_at = null where login_id = ?", "ctermsr");

        comment(reader, id, sid, "규칙 동의 전").andExpect(status().isConflict());
        assertThat(commentRepository.count()).isZero();
    }

    // ── REQ-254 차단(B2a — 차단 관계는 저장소로) ────────────────────────────

    @Test
    @DisplayName("REQ-254 · 차단 관계면 내 글 댓글 403 · 그 사람 댓글은 서로의 목록에서 빠진다(양방향)")
    void blockedRelationship() throws Exception {
        String a = ready("cblka");
        String b = ready("cblkb");
        String c = ready("cblkc");
        long aPost = published(a, "가 글", "가 문장이다.");
        long bPost = published(b, "나 글", "나 문장이다.");
        long cPost = published(c, "다 글", "다 문장이다.");
        String cSid = sidsOf(c, cPost).get(0);
        long byA = commented(a, cPost, cSid, "가가 단 댓글");
        long byB = commented(b, cPost, cSid, "나가 단 댓글");
        block("cblka", "cblkb"); // 가가 나를 막았다 — 한 행

        comment(b, aPost, sidsOf(a, aPost).get(0), "막힌 사람이 막은 사람 글에").andExpect(status().isForbidden());
        comment(a, bPost, sidsOf(b, bPost).get(0), "막은 사람이 막힌 사람 글에").andExpect(status().isForbidden());
        assertThat(sheet(a, cPost, cSid)).extracting(x -> ((Number) x.get("id")).longValue()).containsExactly(byA);
        assertThat(sheet(b, cPost, cSid)).extracting(x -> ((Number) x.get("id")).longValue()).containsExactly(byB);
        assertThat(sheet(c, cPost, cSid)).hasSize(2); // 양성 대조 — 제3자는 둘 다 본다
    }

    // ── REQ-255 지우기 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("REQ-255 · 글쓴이가 남의 댓글 지우기 403 · 쓴 사람 204 · 없는 id 404")
    void deleteOnlyByCommenter() throws Exception {
        String me = ready("cdel");
        String reader = ready("cdelr");
        long id = published(me, "제목", "한 문장이다.");
        String sid = sidsOf(me, id).get(0);
        long c = commented(reader, id, sid, "남의 댓글");

        call(delete("/api/app/blog/comments/" + c), me).andExpect(status().isForbidden())
                .andExpect(content().string("내가 쓴 댓글만 지울 수 있어요"));
        assertThat(commentRepository.findById(c)).isPresent();
        call(delete("/api/app/blog/comments/" + c), reader).andExpect(status().isNoContent());
        assertThat(commentRepository.findById(c)).isEmpty();
        call(delete("/api/app/blog/comments/" + c), reader).andExpect(status().isNotFound());
    }

    // ── REQ-266 문장 뒤 댓글 수 ─────────────────────────────────────────────

    @Test
    @DisplayName("REQ-266 · 문장 뒤 댓글 수 = 그 문장 묶음 · 차단한 사람이 봐도 같은 수")
    void sentenceCounts() throws Exception {
        String me = ready("ccount");
        String reader = ready("ccountr");
        String blocker = ready("ccountb");
        long id = published(me, "제목", "나는 책을 읽었다. 그 책은 재미있었다. 셋째 문장이다.");
        List<String> old = sidsOf(me, id);
        commented(reader, id, old.get(0), "하나");
        commented(reader, id, old.get(1), "둘");
        commented(me, id, old.get(2), "셋");
        edit(me, id, "제목", "나는 책을 읽었고 그 책은 재미있었다. 셋째 문장이다.", 0);
        publishCall(me, id, 1).andExpect(status().isOk());
        block("ccountb", "ccountr");

        for (String viewer : List.of(me, reader, blocker)) {
            assertThat(JsonPath.<List<Integer>>read(read(viewer, id), "$.blocks[*].sentences[*].comments"))
                    .containsExactly(2, 1);
        }
    }

    // ── REQ-298 새 댓글 수 · seen ───────────────────────────────────────────

    private int newComments(String token, long id) throws Exception {
        List<Integer> n = JsonPath.read(call(get("/api/app/blog/posts/mine"), token).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$[?(@.id == " + id + ")].newComments");
        return n.get(0);
    }

    @Test
    @DisplayName("REQ-298 · 새 댓글 수 = seen 뒤 남의 댓글만(내 댓글 제외)")
    void newCommentsCountOthersAfterSeen() throws Exception {
        String me = ready("cnew");
        String reader = ready("cnewr");
        long id = published(me, "제목", "한 문장이다.");
        String sid = sidsOf(me, id).get(0);
        commented(reader, id, sid, "남 하나");
        commented(reader, id, sid, "남 둘");
        commented(me, id, sid, "내 댓글");
        assertThat(newComments(me, id)).as("확인 시각 null — 남의 댓글 전부").isEqualTo(2);

        call(post("/api/app/blog/posts/" + id + "/seen"), me).andExpect(status().isNoContent());
        commented(reader, id, sid, "확인 뒤 남");
        commented(me, id, sid, "확인 뒤 나");
        assertThat(newComments(me, id)).isEqualTo(1);
    }

    @Test
    @DisplayName("REQ-298 · seen 뒤 0")
    void seenResets() throws Exception {
        String me = ready("cseen");
        String reader = ready("cseenr");
        long id = published(me, "제목", "한 문장이다.");
        commented(reader, id, sidsOf(me, id).get(0), "남");
        assertThat(newComments(me, id)).isEqualTo(1);
        call(post("/api/app/blog/posts/" + id + "/seen"), me).andExpect(status().isNoContent());
        assertThat(newComments(me, id)).isZero();
    }

    @Test
    @DisplayName("REQ-298 · 남의 글 seen 404")
    void seenOthersPost404() throws Exception {
        String me = ready("cseen4");
        String reader = ready("cseen4r");
        long id = published(me, "제목", "한 문장이다.");
        call(post("/api/app/blog/posts/" + id + "/seen"), reader).andExpect(status().isNotFound())
                .andExpect(content().string("글을 찾을 수 없어요"));
        call(post("/api/app/blog/posts/987654321/seen"), me).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("REQ-298 · seen 전후로 「내 글」 순서와 updatedAt이 같다 · 초안 저장 뒤엔 updatedAt이 바뀐다(양성 대조)")
    void seenDoesNotTouchUpdatedAt() throws Exception {
        String me = ready("cupd");
        long older = published(me, "먼저 쓴 글", "먼저 쓴 문장이다.");
        long newer = published(me, "나중 쓴 글", "나중 쓴 문장이다.");
        em.flush();
        em.clear(); // 두 읽기 모두 DB 값(마이크로초)으로 — 문맥에 남은 나노초 값과 견주지 않게
        String before = call(get("/api/app/blog/posts/mine"), me).andReturn().getResponse().getContentAsString();

        call(post("/api/app/blog/posts/" + older + "/seen"), me).andExpect(status().isNoContent());
        em.flush();
        em.clear();
        String after = call(get("/api/app/blog/posts/mine"), me).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(after, "$[*].id")).isEqualTo(JsonPath.<List<Integer>>read(before, "$[*].id"))
                .containsExactly((int) newer, (int) older);
        assertThat(JsonPath.<List<String>>read(after, "$[*].updatedAt"))
                .isEqualTo(JsonPath.<List<String>>read(before, "$[*].updatedAt"));

        edit(me, older, "먼저 쓴 글", "고친 문장이다.", 0);
        em.flush();
        em.clear();
        String saved = call(get("/api/app/blog/posts/mine"), me).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Integer>read(saved, "$[0].id")).as("양성 대조 — 초안 저장은 맨 위로").isEqualTo((int) older);
    }

    // ── REQ-258 예전 판 지우기 ──────────────────────────────────────────────

    @Test
    @DisplayName("REQ-258 · 예전 판 지운 뒤 revs 빈 목록 · edited 그대로 · 지금 판 글 그대로")
    void deleteRevsKeepsCurrent() throws Exception {
        String me = ready("crev");
        long id = published(me, "제목", "책을 읽었다.");
        String sid = sidsOf(me, id).get(0);
        edit(me, id, "제목", "책을 천천히 읽었다.", 0);
        publishCall(me, id, 1).andExpect(status().isOk());
        call(get("/api/app/blog/sentences/" + sid + "/revs"), me).andExpect(jsonPath("$", hasSize(1)));

        call(delete("/api/app/blog/sentences/" + sid + "/revs"), me).andExpect(status().isNoContent());
        call(get("/api/app/blog/sentences/" + sid + "/revs"), me).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        String view = read(me, id);
        assertThat(JsonPath.<Boolean>read(view, "$.blocks[0].sentences[0].edited")).isTrue();
        assertThat(JsonPath.<String>read(view, "$.blocks[0].sentences[0].text")).isEqualTo("책을 천천히 읽었다.");
        assertThat(row(sid).getRev()).isEqualTo(2);
        assertThat(revRepository.findBySentenceAndRevLessThanOrderByRevDesc(row(sid), 3)).hasSize(1); // 지금 판 행은 남는다
    }

    @Test
    @DisplayName("REQ-258 · 남이 지우면 404 · 지운 · 합친 문장 404")
    void deleteRevsGuards() throws Exception {
        String me = ready("crev4");
        String other = ready("crev4o");
        long id = published(me, "제목", "나는 책을 읽었다. 그 책은 재미있었다. 지울 문장이다.");
        List<String> old = sidsOf(me, id);
        edit(me, id, "제목", "나는 책을 읽었고 그 책은 재미있었다.", 0);
        publishCall(me, id, 1).andExpect(status().isOk());
        String live = sidsOf(me, id).get(0);
        String dead = old.stream().filter(s -> !s.equals(live)).filter(s -> !row(s).live()).findFirst().orElseThrow();

        call(delete("/api/app/blog/sentences/" + live + "/revs"), other).andExpect(status().isNotFound())
                .andExpect(content().string("문장을 찾을 수 없어요"));
        call(delete("/api/app/blog/sentences/" + dead + "/revs"), me).andExpect(status().isNotFound());
        call(delete("/api/app/blog/sentences/" + old.get(2) + "/revs"), me).andExpect(status().isNotFound());
        call(delete("/api/app/blog/sentences/zz/revs"), me).andExpect(status().isNotFound());
        call(delete("/api/app/blog/sentences/" + live + "/revs"), me).andExpect(status().isNoContent()); // 양성 대조
    }

    // ── REQ-251 걸러내기(통합) ──────────────────────────────────────────────

    @Test
    @DisplayName("REQ-251 · 금지어 댓글 400 · 같은 사람의 평문 댓글 201")
    void bannedComment() throws Exception {
        String me = ready("cban");
        long id = published(me, "제목", "한 문장이다.");
        String sid = sidsOf(me, id).get(0);
        comment(me, id, sid, "정말 " + SIBAL + " 같다").andExpect(status().isBadRequest())
                .andExpect(content().string("이 표현은 올릴 수 없어요"));
        comment(me, id, sid, "ㅋ".repeat(30)).andExpect(status().isBadRequest())
                .andExpect(content().string("같은 글자를 너무 많이 이어 썼어요"));
        commented(me, id, sid, "정말 좋은 문장이다");
        assertThat(commentRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("REQ-251 · 제목 · 본문 · 여백 박스에 금지어가 있으면 올리기 400 · 문장 행 0")
    void bannedPublish() throws Exception {
        String me = ready("cbanpub");
        long inTitle = create(me, "제목 " + SIBAL, "멀쩡한 문장이다.");
        publishCall(me, inTitle, 0).andExpect(status().isBadRequest()).andExpect(content().string("이 표현은 올릴 수 없어요"));
        long inBody = create(me, "제목", "멀쩡한 문장이다. " + SIBAL + " 문장이다.");
        publishCall(me, inBody, 0).andExpect(status().isBadRequest()).andExpect(content().string("이 표현은 올릴 수 없어요"));
        long zwspTitle = create(me, new String(new int[]{0x200B, 0x200B}, 0, 2), "멀쩡한 문장이다.");
        publishCall(me, zwspTitle, 0).andExpect(status().isBadRequest()).andExpect(content().string("제목을 적어 주세요"));

        Story note = storyRepository.saveAndFlush(Story.of(user("cbanpub"), "내 생각엔 " + SIBAL + " 같다.", null, null, null));
        long inBox = create(me, "제목", "멀쩡한 문장이다.");
        json(post("/api/app/blog/posts/" + inBox + "/quotes"), me,
                "{\"kind\":\"MARGIN\",\"storyId\":" + note.getId() + ",\"revision\":0,\"from\":0,\"to\":0}")
                .andExpect(status().isCreated());
        edit(me, inBox, "제목", "멀쩡한 문장이다.\n⟦인용 1⟧", 0);
        publishCall(me, inBox, 1).andExpect(status().isBadRequest()).andExpect(content().string("이 표현은 올릴 수 없어요"));

        for (long id : List.of(inTitle, inBody, zwspTitle, inBox)) {
            assertThat(sentenceRepository.findByPostId(id)).isEmpty();
        }
        long clean = create(me, "제목", "멀쩡한 문장이다.\n⟦인용 9⟧"); // 양성 대조 — 모르는 표시는 평범한 글
        publishCall(me, clean, 0).andExpect(status().isOk());
    }

    @Test
    @DisplayName("REQ-251 · 반영 · 다시 고르기로 금지어가 공개 박스에 들어가면 400 · 박스 글 그대로")
    void bannedRefreshAndRepick() throws Exception {
        String a = ready("cbanref");
        String b = ready("cbanrefb");
        long source = published(a, "원 글", "원래 문장이다.");
        String sid = sidsOf(a, source).get(0);
        long mine = create(b, "내 글", "내 문장이다.");
        String res = json(post("/api/app/blog/posts/" + mine + "/quotes"), b,
                "{\"kind\":\"BLOG\",\"postId\":" + source + ",\"fromSid\":" + str(sid) + ",\"toSid\":" + str(sid) + "}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long blogBox = ((Number) JsonPath.read(res, "$.id")).longValue();
        // 걸러내기 전에 올라간 옛 글(B1) — 원문에 금지어가 있을 수 있다
        sql("update blog_sentence set text = ?, rev = 2 where sid = ?", "원래 " + SIBAL + " 문장이다.", sid);

        json(post("/api/app/blog/quotes/" + blogBox + "/refresh"), b, "{}").andExpect(status().isBadRequest())
                .andExpect(content().string("이 표현은 올릴 수 없어요"));
        assertThat(quoteBoxRepository.findById(blogBox).orElseThrow().getText()).isEqualTo("원래 문장이다.");

        Story note = storyRepository.saveAndFlush(Story.of(user("cbanrefb"), "멀쩡한 생각이다. " + SIBAL + " 생각이다.", null, null, null));
        String mres = json(post("/api/app/blog/posts/" + mine + "/quotes"), b,
                "{\"kind\":\"MARGIN\",\"storyId\":" + note.getId() + ",\"revision\":0,\"from\":0,\"to\":0}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long marginBox = ((Number) JsonPath.read(mres, "$.id")).longValue();
        json(put("/api/app/blog/quotes/" + marginBox + "/source"), b, "{\"revision\":0,\"from\":0,\"to\":1}")
                .andExpect(status().isBadRequest()).andExpect(content().string("이 표현은 올릴 수 없어요"));
        assertThat(quoteBoxRepository.findById(marginBox).orElseThrow().getText()).isEqualTo("멀쩡한 생각이다.");
        json(put("/api/app/blog/quotes/" + marginBox + "/source"), b, "{\"revision\":0,\"from\":0,\"to\":0}")
                .andExpect(status().isOk()); // 양성 대조
    }

    @Test
    @DisplayName("REQ-251 · blog/me 이름 금지어 400")
    void bannedBlogName() throws Exception {
        String token = apiTokenService.issue(register("cbanname"));
        json(put("/api/app/blog/me"), token, "{\"name\":\"" + SIBAL + "독자\",\"agreeTerms\":true}")
                .andExpect(status().isBadRequest()).andExpect(content().string("이 표현은 올릴 수 없어요"));
        assertThat(user("cbanname").getBlogNameConfirmedAt()).isNull();
        assertThat(user("cbanname").getNickname()).isEqualTo("닉_cbanname");
    }

    // ── REQ-265 이름 규칙(blog/me) ──────────────────────────────────────────

    static String cp(int... cps) {
        return new String(cps, 0, cps.length);
    }

    static Stream<Arguments> invisibleNames() {
        return Stream.of(
                Arguments.of("line-separator", "가" + cp(0x2028) + "나"),
                Arguments.of("paragraph-separator", "가" + cp(0x2029) + "나"),
                Arguments.of("zwsp-pair", cp(0x200B, 0x200B)),
                Arguments.of("zwsp-inner", "가" + cp(0x200B) + "나"),
                Arguments.of("rlo", cp(0x202E) + "독자"),
                Arguments.of("nbsp-pair", cp(0xA0, 0xA0)),
                Arguments.of("hangul-filler-pair", cp(0x3164, 0x3164)),
                Arguments.of("braille-blank-pair", cp(0x2800, 0x2800)),
                Arguments.of("zwj-hangul", "가" + cp(0x200D) + "나"));
    }

    @ParameterizedTest(name = "REQ-265 · 보이지 않는 글자 이름 400 · {0}")
    @MethodSource("invisibleNames")
    void invisibleBlogNames(String label, String name) throws Exception {
        String token = apiTokenService.issue(register("cinv"));
        json(put("/api/app/blog/me"), token, "{\"name\":" + str(name) + ",\"agreeTerms\":true}")
                .andExpect(status().isBadRequest());
        assertThat(user("cinv").getNickname()).isEqualTo("닉_cinv");
        assertThat(user("cinv").getBlogNameConfirmedAt()).isNull();
    }

    static Stream<Arguments> zwjNames() {
        return Stream.of(
                Arguments.of("woman-technologist", cp(0x1F469, 0x200D, 0x1F4BB) + "독서가"),
                Arguments.of("family", cp(0x1F468, 0x200D, 0x1F469, 0x200D, 0x1F467)),
                Arguments.of("rainbow-flag", cp(0x1F3F3, 0xFE0F, 0x200D, 0x1F308)));
    }

    @ParameterizedTest(name = "REQ-265 · 이모지 사이 ZWJ 이름 200 · {0}")
    @MethodSource("zwjNames")
    void zwjBlogNames(String label, String name) throws Exception {
        String token = apiTokenService.issue(register("czwj"));
        json(put("/api/app/blog/me"), token, "{\"name\":" + str(name) + ",\"agreeTerms\":true}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value(name));
        assertThat(user("czwj").getNickname()).isEqualTo(name);
    }

    // ── REQ-256 속도 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("REQ-256 · 21번째 댓글 429")
    void commentRateLimit() throws Exception {
        String me = ready("crate");
        long id = published(me, "제목", "한 문장이다.");
        String sid = sidsOf(me, id).get(0);
        for (int i = 0; i < 20; i++) {
            commented(me, id, sid, "댓글 " + i);
        }
        comment(me, id, sid, "스물한째").andExpect(status().isTooManyRequests())
                .andExpect(content().string("댓글을 너무 자주 달았어요 — 잠시 뒤 다시 해 주세요"));
        assertThat(commentRepository.count()).isEqualTo(20);
    }

    @Test
    @DisplayName("REQ-256 · 11번째 올리기(하루) 429 · 문장 행 그대로 · 409 · 400으로 막힌 올리기는 세지 않는다")
    void publishRateLimit() throws Exception {
        String me = ready("cpubrate");
        long id = create(me, "제목", "멀쩡한 문장이다. " + SIBAL + " 문장이다.");
        publishCall(me, id, 0).andExpect(status().isBadRequest());
        publishCall(me, id, 0).andExpect(status().isBadRequest());
        edit(me, id, "제목", "멀쩡한 문장이다.", 0);
        publishCall(me, id, 0).andExpect(status().isConflict());
        publishCall(me, id, 0).andExpect(status().isConflict());
        for (int i = 0; i < 10; i++) {
            publishCall(me, id, 1).andExpect(status().isOk());
        }
        publishCall(me, id, 1).andExpect(status().isTooManyRequests())
                .andExpect(content().string("오늘은 글을 너무 많이 올렸어요 — 내일 다시 올려 주세요"));
        assertThat(sentenceRepository.findByPostId(id)).hasSize(1);
    }
}
