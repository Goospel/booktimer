package com.booktimer.web.api;

import com.booktimer.auth.ApiTokenService;
import com.booktimer.blog.BlogAccess;
import com.booktimer.blog.BlogPostRepository;
import com.booktimer.blog.BlogSentence;
import com.booktimer.blog.BlogSentenceRepository;
import com.booktimer.block.Block;
import com.booktimer.block.BlockRepository;
import com.booktimer.security.RateLimitService;
import com.booktimer.user.AppleUserProvisioningService;
import com.booktimer.user.AuthProvider;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 책생글 B1a — 앱 블로그 API {@code /api/app/blog/**}(계획 2026-11-11-b1-blog-core §5.1 · §6.3 · §6.4 · §7) — 실 H2 + Bearer.
 *
 * <p>메인 스위트는 허용 목록이 {@code *}다(테스트 속성). 닫힌 쪽은 REQ-244 시험만 {@code ReflectionTestUtils}로 바꾸고 finally로
 * 되돌린다(새 컨텍스트 금지 — T-235). {@code hidden_at} · 올린 시각은 저장소(JDBC)로 직접 쓰고 영속성 문맥을 비운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AppBlogApiControllerTest {

    private static final String SEOUL = "Asia/Seoul";
    private static final String CLOSED = "블로그 기능은 12월 1일부터 열려요";

    @Autowired MockMvc mvc;
    @Autowired UserRegistrationService registrationService;
    @Autowired AppleUserProvisioningService appleProvisioning;
    @Autowired UserRepository userRepository;
    @Autowired ApiTokenService apiTokenService;
    @Autowired BlogPostRepository postRepository;
    @Autowired BlogSentenceRepository sentenceRepository;
    @Autowired BlockRepository blockRepository;
    @Autowired BlogAccess blogAccess;
    @Autowired RateLimitService rateLimitService;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired Clock clock;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping;

    @BeforeEach
    void clearRateLimits() {
        rateLimitService.clearForTest();
    }

    // ── 도우미 ──────────────────────────────────────────────────────────────

    private User register(String loginId) {
        registrationService.register(loginId + "@booktimer.com", "pw1234qwer!!", loginId, "닉_" + loginId, SEOUL, Role.USER,
                LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL)));
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
        return s == null ? "null" : "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    private static String draft(String title, String body) {
        return "{\"draftTitle\":" + str(title) + ",\"draftBody\":" + str(body) + "}";
    }

    private static String save(String title, String body, int revision) {
        return "{\"draftTitle\":" + str(title) + ",\"draftBody\":" + str(body) + ",\"revision\":" + revision + "}";
    }

    private long create(String token, String title, String body) throws Exception {
        String res = json(post("/api/app/blog/posts"), token, draft(title, body)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(res, "$.id")).longValue();
    }

    private String publish(String token, long id, int revision) throws Exception {
        return json(post("/api/app/blog/posts/" + id + "/publish"), token, "{\"revision\":" + revision + "}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private void edit(String token, long id, String title, String body, int revision) throws Exception {
        json(put("/api/app/blog/posts/" + id), token, save(title, body, revision)).andExpect(status().isOk());
    }

    /** 글을 쓰고 올린 뒤 그 글 id. */
    private long published(String token, String title, String body) throws Exception {
        long id = create(token, title, body);
        publish(token, id, 0);
        return id;
    }

    private static List<String> sids(String view) {
        return JsonPath.read(view, "$.blocks[*].sentences[*].sid");
    }

    private String read(String token, long id, String s) throws Exception {
        return call(get("/api/app/blog/posts/" + id + (s == null ? "" : "?s=" + s)), token)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private BlogSentence row(String sid) {
        return sentenceRepository.findBySid(sid).orElseThrow();
    }

    private long postId(String title) {
        return jdbc.queryForObject("select id from blog_post where draft_title = ?", Long.class, title);
    }

    /** 저장소로 직접 쓰고(엔티티에 setter가 없는 열) 영속성 문맥을 비운다 — 캐시에 남은 옛 엔티티를 읽지 않게. */
    private void sql(String statement, Object... args) {
        em.flush();
        jdbc.update(statement, args);
        em.clear();
    }

    // ── REQ-230 초안 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("REQ-230 · 남의 초안 404 · 낡은 판 409")
    void othersDraft404AndStaleRevision409() throws Exception {
        String me = ready("bdraft1");
        String other = ready("bdraft2");
        long id = create(me, "내 초안", "첫 글.");

        call(get("/api/app/blog/posts/" + id), other).andExpect(status().isNotFound())
                .andExpect(content().string("글을 찾을 수 없어요"));
        json(put("/api/app/blog/posts/" + id), other, save("남이 고침", "x", 0)).andExpect(status().isNotFound());

        json(put("/api/app/blog/posts/" + id), me, save("내 초안", "둘째 글.", 0)).andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(1)).andExpect(jsonPath("$.updatedAt").exists());
        json(put("/api/app/blog/posts/" + id), me, save("내 초안", "낡은 판의 글.", 0)).andExpect(status().isConflict())
                .andExpect(content().string("다른 기기에서 이 글이 바뀌었어요 — 그쪽을 불러와 주세요"));
        call(get("/api/app/blog/posts/" + id), me).andExpect(jsonPath("$.draft.body").value("둘째 글."))
                .andExpect(jsonPath("$.draft.revision").value(1));
    }

    @Test
    @DisplayName("REQ-230 · 10,001자 400 · 제목 101자 400")
    void lengthLimits() throws Exception {
        String me = ready("blen");
        json(post("/api/app/blog/posts"), me, draft("제목", "가".repeat(10_001))).andExpect(status().isBadRequest())
                .andExpect(content().string("본문은 10,000자까지 쓸 수 있어요"));
        json(post("/api/app/blog/posts"), me, draft("가".repeat(101), "본문.")).andExpect(status().isBadRequest())
                .andExpect(content().string("제목은 100자까지 쓸 수 있어요"));
        long id = create(me, "가".repeat(100), "가".repeat(10_000)); // 양성 대조 — 경계값은 받는다
        json(put("/api/app/blog/posts/" + id), me, save("제목", "가".repeat(10_001), 0)).andExpect(status().isBadRequest())
                .andExpect(content().string("본문은 10,000자까지 쓸 수 있어요"));
    }

    @Test
    @DisplayName("REQ-230 · 제목 · 본문 다 빈 POST 400(빈 행 0)")
    void emptyPostIsRejected() throws Exception {
        String me = ready("bempty");
        json(post("/api/app/blog/posts"), me, "{}").andExpect(status().isBadRequest())
                .andExpect(content().string("제목이나 본문을 적어 주세요"));
        json(post("/api/app/blog/posts"), me, draft("  ", " \n ")).andExpect(status().isBadRequest());
        assertThat(postRepository.findTop200ByUserOrderByUpdatedAtDescIdDesc(userRepository.findByLoginId("bempty").orElseThrow()))
                .isEmpty();
    }

    @Test
    @DisplayName("REQ-230 · 본문 null · 제목만 POST 201(본문 빈 글자)")
    void titleOnlyDraft() throws Exception {
        String me = ready("btitle");
        json(post("/api/app/blog/posts"), me, "{\"draftTitle\":\"제목만\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.draft.title").value("제목만")).andExpect(jsonPath("$.draft.body").value(""))
                .andExpect(jsonPath("$.draft.revision").value(0)).andExpect(jsonPath("$.draft.quotes", hasSize(0)))
                .andExpect(jsonPath("$.publishedAt").value(nullValue())).andExpect(jsonPath("$.blocks", hasSize(0)));
    }

    // ── REQ-232 · 233 판 ────────────────────────────────────────────────────

    @Test
    @DisplayName("REQ-232 · 그대로 다시 올리면 id · 판 그대로 · republished_at 그대로")
    void republishSameKeepsEverything() throws Exception {
        String me = ready("bsame");
        long id = create(me, "제목", "하나다. 둘이다.");
        List<String> first = sids(publish(me, id, 0));
        String again = publish(me, id, 0); // 같은 판으로 두 번 올리기(§8.1 11)

        assertThat(sids(again)).isEqualTo(first).hasSize(2);
        assertThat(JsonPath.<Object>read(again, "$.republishedAt")).isNull();
        assertThat(sentenceRepository.findByPostId(id)).hasSize(2).allSatisfy(s -> assertThat(s.getRev()).isEqualTo(1));
    }

    @Test
    @DisplayName("REQ-233 · 한 단어 고침 → 같은 id · rev 2 · rev 1 글 보존")
    void oneWordEdit() throws Exception {
        String me = ready("bedit");
        long id = published(me, "제목", "나는 어제 책을 읽었다. 재미있었다.");
        String s1 = sids(read(me, id, null)).get(0);
        edit(me, id, "제목", "나는 어제 소설을 읽었다. 재미있었다.", 0);
        String view = publish(me, id, 1);

        assertThat(sids(view).get(0)).isEqualTo(s1);
        assertThat(JsonPath.<Boolean>read(view, "$.blocks[0].sentences[0].edited")).isTrue();
        assertThat(row(s1).getRev()).isEqualTo(2);
        call(get("/api/app/blog/sentences/" + s1 + "/revs"), me).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].rev").value(1))
                .andExpect(jsonPath("$[0].text").value("나는 어제 책을 읽었다.")).andExpect(jsonPath("$[0].at").exists());
        // REQ-236 · 한 문장 주소의 산 상태 둘 — 고친 문장 EDITED · 그대로인 문장 LIVE(앱이 그 값으로 그린다)
        String s2 = sids(view).get(1);
        assertThat(JsonPath.<String>read(read(me, id, s1), "$.highlight.status")).isEqualTo("EDITED");
        assertThat(JsonPath.<List<String>>read(read(me, id, s1), "$.highlight.sids")).containsExactly(s1);
        assertThat(JsonPath.<String>read(read(me, id, s2), "$.highlight.status")).isEqualTo("LIVE");
        assertThat(JsonPath.<List<String>>read(read(me, id, s2), "$.highlight.sids")).containsExactly(s2);

        // 한 번 더 고치면 판 2의 글도 예전 판으로 남는다(고칠 때 그 판의 행을 쓴다 — 최근 판 먼저)
        edit(me, id, "제목", "나는 오늘 소설을 읽었다. 재미있었다.", 1);
        publish(me, id, 2);
        call(get("/api/app/blog/sentences/" + s1 + "/revs"), me)
                .andExpect(jsonPath("$[*].rev").value(org.hamcrest.Matchers.contains(2, 1)))
                .andExpect(jsonPath("$[0].text").value("나는 어제 소설을 읽었다."));
    }

    @Test
    @DisplayName("REQ-233 · 공백 무리만 다른 같은 문장은 판을 올리지 않는다")
    void whitespaceOnlyKeepsRevision() throws Exception {
        String me = ready("bspace");
        long id = published(me, "제목", "나는  책을" + (char) 0xA0 + "읽었다.");
        String s1 = sids(read(me, id, null)).get(0);
        edit(me, id, "제목", "나는 책을 읽었다.", 0);
        String view = publish(me, id, 1);

        assertThat(sids(view)).containsExactly(s1);
        assertThat(row(s1).getRev()).isEqualTo(1);
        assertThat(row(s1).getText()).isEqualTo("나는 책을 읽었다.");
        assertThat(JsonPath.<Object>read(view, "$.republishedAt")).as("공백만 다른 다시 올리기는 고쳐 올린 날짜를 바꾸지 않는다").isNull();
    }

    @Test
    @DisplayName("REQ-233 · 한 단어만 고쳐 올려도 republished_at = 지금")
    void oneWordEditSetsRepublishedAt() throws Exception {
        String me = ready("brepub");
        long id = published(me, "제목", "나는 어제 책을 읽었다. 재미있었다.");
        edit(me, id, "제목", "나는 어제 소설을 읽었다. 재미있었다.", 0);
        Instant before = Instant.now().minusSeconds(5);
        String view = publish(me, id, 1);

        assertThat(Instant.parse(JsonPath.read(view, "$.republishedAt"))).isAfter(before);
    }

    // ── REQ-234 · 235 · 236 · 249 판 비교 · 주소 ─────────────────────────────

    @Test
    @DisplayName("REQ-234 · 합침: 옛 주소 둘 다 새 문장")
    void mergeKeepsBothAddresses() throws Exception {
        String me = ready("bmerge");
        long id = published(me, "제목", "나는 책을 읽었다. 그 책은 재미있었다.");
        List<String> old = sids(read(me, id, null));
        edit(me, id, "제목", "나는 책을 읽었고 그 책은 재미있었다.", 0);
        publish(me, id, 1);

        String viaFirst = read(me, id, old.get(0));
        String viaSecond = read(me, id, old.get(1));
        assertThat(JsonPath.<List<String>>read(viaFirst, "$.highlight.sids")).containsExactly(old.get(0));
        assertThat(JsonPath.<String>read(viaSecond, "$.highlight.status")).isEqualTo("MERGED");
        assertThat(JsonPath.<List<String>>read(viaSecond, "$.highlight.sids")).containsExactly(old.get(0));
        assertThat(row(old.get(1)).getMergedInto()).isEqualTo(old.get(0));
    }

    @Test
    @DisplayName("REQ-234 · 문단 순서 바꿈: id 그대로")
    void paragraphReorderKeepsIds() throws Exception {
        String me = ready("border");
        long id = published(me, "제목", "첫 문단 하나. 첫 문단 둘.\n\n둘째 문단.");
        List<String> old = sids(read(me, id, null));
        edit(me, id, "제목", "둘째 문단.\n\n첫 문단 하나. 첫 문단 둘.", 0);
        String view = publish(me, id, 1);

        assertThat(sids(view)).containsExactly(old.get(2), old.get(0), old.get(1));
        assertThat(sentenceRepository.findByPostId(id)).hasSize(3).allSatisfy(s -> assertThat(s.getRev()).isEqualTo(1));
    }

    @Test
    @DisplayName("REQ-234 · 지움: 어미가 비슷한 짧은 이웃이 있어도 묘비(합침 아님)")
    void deleteShortSentenceIsTombstone() throws Exception {
        String me = ready("btomb");
        long id = published(me, "제목", "첫째다. 둘째다. 셋째다.");
        List<String> old = sids(read(me, id, null));
        edit(me, id, "제목", "첫째다. 셋째다.", 0);
        publish(me, id, 1);

        BlogSentence gone = row(old.get(1));
        assertThat(gone.live()).isFalse();
        assertThat(gone.getMergedInto()).isNull();
        assertThat(JsonPath.<String>read(read(me, id, old.get(1)), "$.highlight.status")).isEqualTo("DELETED");
    }

    @Test
    @DisplayName("REQ-234 · 셋을 하나로 합치면 옛 주소 셋 다 새 문장")
    void threeIntoOne() throws Exception {
        String me = ready("bthree");
        long id = published(me, "제목", "나는 책을 읽었다. 그 책은 두꺼웠다. 그래도 재미있었다.");
        List<String> old = sids(read(me, id, null));
        edit(me, id, "제목", "나는 책을 읽었고 그 책은 두꺼웠지만 그래도 재미있었다.", 0);
        String view = publish(me, id, 1);

        String survivor = sids(view).get(0);
        for (String sid : old) {
            assertThat(JsonPath.<List<String>>read(read(me, id, sid), "$.highlight.sids")).as(sid).containsExactly(survivor);
        }
    }

    @Test
    @DisplayName("REQ-235 · 지운 문장 주소 → DELETED · 글 200")
    void deletedAddressOpensPost() throws Exception {
        String me = ready("bdel");
        String reader = ready("bdelr");
        long id = published(me, "제목", "오늘은 비가 왔다.");
        String s1 = sids(read(me, id, null)).get(0);
        edit(me, id, "제목", "내일은 소풍을 간다.", 0);
        publish(me, id, 1);

        call(get("/api/app/blog/posts/" + id + "?s=" + s1), reader).andExpect(status().isOk())
                .andExpect(jsonPath("$.highlight.status").value("DELETED")).andExpect(jsonPath("$.highlight.sids", hasSize(0)))
                .andExpect(jsonPath("$.blocks[0].sentences[0].text").value("내일은 소풍을 간다."));
    }

    @Test
    @DisplayName("REQ-236 · 합쳐진 주소는 합친 문장의 글(MERGED)")
    void mergedAddressShowsMergedText() throws Exception {
        String me = ready("bm236");
        long id = published(me, "제목", "나는 책을 읽었다. 그 책은 재미있었다.");
        List<String> old = sids(read(me, id, null));
        edit(me, id, "제목", "나는 책을 읽었고 그 책은 재미있었다.", 0);
        publish(me, id, 1);

        String view = read(me, id, old.get(1));
        assertThat(JsonPath.<String>read(view, "$.highlight.status")).isEqualTo("MERGED");
        String target = JsonPath.<List<String>>read(view, "$.highlight.sids").get(0);
        List<Map<String, Object>> sentences = JsonPath.read(view, "$.blocks[*].sentences[*]");
        assertThat(sentences).filteredOn(s -> target.equals(s.get("sid"))).singleElement()
                .satisfies(s -> assertThat(s.get("text")).isEqualTo("나는 책을 읽었고 그 책은 재미있었다."));
    }

    @Test
    @DisplayName("REQ-236 · 합쳐진 문장이 다시 지워짐 → DELETED · 다시 고쳐짐 → 최신 판")
    void mergedThenEditedThenDeleted() throws Exception {
        String me = ready("bchain");
        long id = published(me, "제목", "나는 책을 읽었다. 그 책은 재미있었다.");
        List<String> old = sids(read(me, id, null));
        edit(me, id, "제목", "나는 책을 읽었고 그 책은 재미있었다.", 0);
        publish(me, id, 1);
        edit(me, id, "제목", "나는 책을 읽었고 그 책은 정말 재미있었다.", 1);
        publish(me, id, 2);

        String edited = read(me, id, old.get(1));
        assertThat(JsonPath.<String>read(edited, "$.highlight.status")).isEqualTo("MERGED");
        assertThat(JsonPath.<String>read(edited, "$.blocks[0].sentences[0].text")).isEqualTo("나는 책을 읽었고 그 책은 정말 재미있었다.");

        edit(me, id, "제목", "전혀 다른 이야기를 새로 적는다.", 2);
        publish(me, id, 3);
        String deleted = read(me, id, old.get(1));
        assertThat(JsonPath.<String>read(deleted, "$.highlight.status")).isEqualTo("DELETED");
        assertThat(JsonPath.<List<String>>read(deleted, "$.highlight.sids")).isEmpty();
    }

    @Test
    @DisplayName("REQ-236 · 예전 판 — 지운 · 합친 문장 404 · 남의 초안 · 가린 글 · 차단 관계 404 · 주인은 가린 글도 200")
    void revisionVisibility() throws Exception {
        String me = ready("brevs");
        String reader = ready("brevsr");
        String blocked = ready("brevsb");
        long id = published(me, "제목", "나는 어제 책을 읽었다. 그 책은 재미있었다. 끝이다.");
        List<String> old = sids(read(me, id, null));
        edit(me, id, "제목", "나는 어제 소설을 읽었다. 그 책은 재미있었다.", 0);
        publish(me, id, 1);
        String s1 = old.get(0);

        call(get("/api/app/blog/sentences/" + s1 + "/revs"), reader).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1))); // 양성 대조
        call(get("/api/app/blog/sentences/" + old.get(2) + "/revs"), reader).andExpect(status().isNotFound()); // 지움
        call(get("/api/app/blog/sentences/ZZZZZZZZ/revs"), reader).andExpect(status().isNotFound());

        long draftId = create(me, "초안만", "아직 안 올렸다.");
        call(get("/api/app/blog/posts/" + draftId), reader).andExpect(status().isNotFound());

        blockRepository.saveAndFlush(Block.of(userRepository.findByLoginId("brevsb").orElseThrow(),
                userRepository.findByLoginId("brevs").orElseThrow()));
        call(get("/api/app/blog/sentences/" + s1 + "/revs"), blocked).andExpect(status().isNotFound());
        call(get("/api/app/blog/posts/" + id), blocked).andExpect(status().isNotFound());

        sql("update blog_post set hidden_at = ? where id = ?", Timestamp.from(Instant.now()), id);
        call(get("/api/app/blog/sentences/" + s1 + "/revs"), reader).andExpect(status().isNotFound());
        call(get("/api/app/blog/posts/" + id), reader).andExpect(status().isNotFound());
        call(get("/api/app/blog/sentences/" + s1 + "/revs"), me).andExpect(status().isOk());
        call(get("/api/app/blog/posts/" + id), me).andExpect(status().isOk());
    }

    @Test
    @DisplayName("REQ-236 · 예전 판 — 합친 문장 404")
    void mergedSentenceRevs404() throws Exception {
        String me = ready("brevm");
        long id = published(me, "제목", "나는 책을 읽었다. 그 책은 재미있었다.");
        List<String> old = sids(read(me, id, null));
        edit(me, id, "제목", "나는 책을 읽었고 그 책은 재미있었다.", 0);
        publish(me, id, 1);

        call(get("/api/app/blog/sentences/" + old.get(1) + "/revs"), me).andExpect(status().isNotFound());
        call(get("/api/app/blog/sentences/" + old.get(0) + "/revs"), me).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("REQ-249 · 나뉨 → 새 조각 split_from = 옛 sid")
    void splitRecordsSource() throws Exception {
        String me = ready("bsplit");
        long id = published(me, "제목", "나는 책을 읽었고 그 책은 재미있었다.");
        String s1 = sids(read(me, id, null)).get(0);
        edit(me, id, "제목", "나는 책을 읽었다. 그 책은 재미있었다.", 0);
        List<String> now = sids(publish(me, id, 1));

        assertThat(now).hasSize(2).contains(s1);
        String fragment = now.stream().filter(s -> !s.equals(s1)).findFirst().orElseThrow();
        assertThat(row(fragment).getSplitFrom()).isEqualTo(s1);
    }

    @Test
    @DisplayName("REQ-249 · 한 문장 주소 해석은 그 문장만(나뉜 조각 0)")
    void singleAddressExcludesFragments() throws Exception {
        String me = ready("bsplit1");
        long id = published(me, "제목", "나는 책을 읽었고 그 책은 재미있었다.");
        String s1 = sids(read(me, id, null)).get(0);
        edit(me, id, "제목", "나는 책을 읽었다. 그 책은 재미있었다.", 0);
        publish(me, id, 1);

        assertThat(JsonPath.<List<String>>read(read(me, id, s1), "$.highlight.sids")).containsExactly(s1);
    }

    // ── REQ-242 목록 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("REQ-242 · 최신순 · 차단한 사람 글 빠짐 · limit 3")
    void listNewestFirstWithoutBlocked() throws Exception {
        String viewer = ready("blistv");
        String a = ready("blista");
        String b = ready("blistb");
        for (int i = 1; i <= 4; i++) {
            published(a, "가" + i, "글 " + i + ".");
        }
        published(b, "차단글", "차단한 사람의 글.");
        for (int i = 1; i <= 4; i++) {
            sql("update blog_post set published_at = ? where id = ?", Timestamp.from(Instant.parse("2026-11-0" + i + "T00:00:00Z")),
                    postId("가" + i));
        }
        sql("update blog_post set published_at = ? where id = ?", Timestamp.from(Instant.parse("2026-11-09T00:00:00Z")),
                postId("차단글"));
        blockRepository.saveAndFlush(Block.of(userRepository.findByLoginId("blistv").orElseThrow(),
                userRepository.findByLoginId("blistb").orElseThrow()));

        call(get("/api/app/blog/posts?limit=3"), viewer).andExpect(status().isOk())
                .andExpect(jsonPath("$.posts[*].title").value(org.hamcrest.Matchers.contains("가4", "가3", "가2")))
                .andExpect(jsonPath("$.posts[0].authorName").value("닉_blista"))
                .andExpect(jsonPath("$.posts[0].mine").value(false))
                .andExpect(jsonPath("$.next").isString());
        call(get("/api/app/blog/posts?limit=3"), b).andExpect(jsonPath("$.posts[0].title").value("차단글"))
                .andExpect(jsonPath("$.posts[0].mine").value(true)); // 내 글도 실린다
    }

    @Test
    @DisplayName("REQ-242 · 초안 · 가린 글은 목록에 없다(null-state 대조 — 양성: 올린 글은 있다)")
    void listSkipsDraftAndHidden() throws Exception {
        String me = ready("bnull");
        create(me, "초안글", "아직이다.");
        published(me, "가린글", "가려질 글.");
        published(me, "보이는글", "보이는 글.");
        sql("update blog_post set hidden_at = ? where id = ?", Timestamp.from(Instant.now()), postId("가린글"));

        call(get("/api/app/blog/posts"), me).andExpect(status().isOk())
                .andExpect(jsonPath("$.posts[*].title").value(org.hamcrest.Matchers.contains("보이는글")));
    }

    @Test
    @DisplayName("REQ-242 · 커서로 다음 쪽 · 같은 시각은 id로")
    void cursorPaging() throws Exception {
        String me = ready("bcursor");
        published(me, "c1", "하나.");
        published(me, "c2", "둘.");
        published(me, "c3", "셋.");
        sql("update blog_post set published_at = ?", Timestamp.from(Instant.parse("2026-11-05T00:00:00Z")));

        String first = call(get("/api/app/blog/posts?limit=2"), me).andExpect(status().isOk())
                .andExpect(jsonPath("$.posts[*].title").value(org.hamcrest.Matchers.contains("c3", "c2")))
                .andReturn().getResponse().getContentAsString();
        String next = JsonPath.read(first, "$.next");
        call(get("/api/app/blog/posts").param("limit", "2").param("cursor", next), me)
                .andExpect(jsonPath("$.posts[*].title").value(org.hamcrest.Matchers.contains("c1")))
                .andExpect(jsonPath("$.next").value(nullValue()));
        call(get("/api/app/blog/posts").param("cursor", "깨진-커서"), me).andExpect(status().isBadRequest())
                .andExpect(content().string("목록 위치가 올바르지 않아요"));
    }

    @Test
    @DisplayName("REQ-242 · 내 글 목록 — 최근 고친 순 · 초안은 올린 제목 null")
    void mineList() throws Exception {
        String me = ready("bmine");
        published(me, "올린글", "올렸다.");
        create(me, "초안글", "아직이다.");
        call(get("/api/app/blog/posts/mine"), me).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.draftTitle == '초안글')].title").value(org.hamcrest.Matchers.contains(nullValue())))
                .andExpect(jsonPath("$[?(@.draftTitle == '올린글')].title").value(org.hamcrest.Matchers.contains("올린글")));
    }

    // ── REQ-244 허용 목록 ───────────────────────────────────────────────────

    @ParameterizedTest(name = "REQ-244 · 목록 밖 403 — 쓰기 문 전부 · 같은 문구 「블로그 기능은 12월 1일부터 열려요」 · 요청자는 Apple 가입 계정 · {0} {1}")
    @CsvSource(delimiter = '|', value = {
            "POST | /api/app/blog/posts | {\"draftTitle\":\"t\",\"draftBody\":\"b\"}",
            "PUT | /api/app/blog/posts/{id} | {\"draftTitle\":\"t\",\"draftBody\":\"b\",\"revision\":0}",
            "POST | /api/app/blog/posts/{id}/publish | {\"revision\":0}",
            "PUT | /api/app/blog/me | {\"name\":\"새이름\",\"agreeTerms\":true}"
    })
    void closedListRejectsWrites(String method, String path, String body) throws Exception {
        User apple = appleProvisioning.register("001.blog.closed");
        String token = apiTokenService.issue(apple);
        long id = create(token, "열려 있을 때 쓴 글", "문장."); // 열린 동안 만든 내 글 — 소유 404가 403을 가리지 않는지도 본다
        String url = path.replace("{id}", String.valueOf(id));
        ReflectionTestUtils.setField(blogAccess, "allowed", "none");
        try {
            json("POST".equals(method) ? post(url) : put(url), token, body).andExpect(status().isForbidden())
                    .andExpect(content().string(CLOSED));
            // 없는 id · 빈 글자 몸도 같은 403 — 소유 404 · 검증 400보다 앞
            String emptyish = body.replace("\"t\"", "\"\"").replace("\"b\"", "\"\"").replace("새이름", "");
            json("POST".equals(method) ? post(path.replace("{id}", "987654321")) : put(path.replace("{id}", "987654321")),
                    token, emptyish).andExpect(status().isForbidden()).andExpect(content().string(CLOSED));
        } finally {
            ReflectionTestUtils.setField(blogAccess, "allowed", "*");
        }
    }

    /** 쓰기 문이 새로 생기면 어느 목록에 넣을지 정하기 전엔 빨갛다(§4.8 · 비평 SEC-6). B2가 열어 둔 목록에 차단 · 확인 시각을 넣는다. */
    private static final Set<String> BLOCKED = Set.of(
            "POST /api/app/blog/posts", "PUT /api/app/blog/posts/{id}", "POST /api/app/blog/posts/{id}/publish",
            "PUT /api/app/blog/me",
            "POST /api/app/blog/posts/{id}/quotes", "POST /api/app/blog/quotes/{id}/refresh", "PUT /api/app/blog/quotes/{id}/source",
            "POST /api/app/margins/{id}/quotes");
    /** 내 데이터를 지우는 길은 목록과 무관하게 늘 연다(§4.8 — readsAndDeletesStayOpen · AppMarginLinkApiTest가 200을 잰다). */
    private static final Set<String> OPEN = Set.of("DELETE /api/app/blog/posts/{id}", "DELETE /api/app/margins/{id}",
            "DELETE /api/app/margins/{id}/quotes/{quoteId}");
    /** 문 단위로 허용 목록을 걸지 않는 쪽지 쓰기 문 — 줄마다 까닭. 새 쪽지 쓰기 문은 세 목록 가운데 하나에 넣어야 초록이다(§16 AUTH-4). */
    private static final Set<String> MARGIN_NOT_BLOG = Set.of(
            "POST /api/app/margins",           // 쪽지 만들기 — 몸에 link가 있을 때만 막는다(걸기 · 매개변수 403 시험이 잰다)
            "PUT /api/app/margins/{id}",       // 쪽지 고치기(자동저장) — 블로그 문 아님
            "POST /api/app/margins/{id}/ai");  // 여백 AI 질문 — 동의 · 몫이 막는다(BlogAccess 아님)

    @Test
    @DisplayName("REQ-244 · /api/app/blog · /api/app/margins의 쓰기 경로(GET · HEAD · OPTIONS 밖 전부) = 막는 목록 ∪ 열어 둔 목록 ∪ 블로그 아닌 쪽지 문")
    void writeRoutesAreAllAccountedFor() {
        Set<String> routes = new HashSet<>();
        Set<String> outside = new HashSet<>(); // 블로그 박스를 쥔 컨트롤러가 두 접두 밖에 연 쓰기 문 — 접두만 바꾼 새 문도 빨갛다(리뷰 C-1)
        Set<Class<?>> quoteHolders = new HashSet<>();
        handlerMapping.getHandlerMethods().forEach((info, handler) -> info.getPatternValues().forEach(p -> {
            boolean holdsQuotes = java.util.Arrays.stream(handler.getBeanType().getDeclaredFields())
                    .anyMatch(f -> f.getType() == com.booktimer.blog.QuoteBoxService.class || f.getType() == BlogAccess.class);
            if (holdsQuotes) quoteHolders.add(handler.getBeanType());
            Set<String> methods = new HashSet<>();
            info.getMethodsCondition().getMethods().forEach(m -> methods.add(m.name()));
            if (methods.isEmpty()) methods.add("ANY"); // 메서드를 정하지 않은 매핑은 쓰기도 받는다
            methods.forEach(method -> {
                boolean write = !Set.of("GET", "HEAD", "OPTIONS").contains(method);
                boolean blog = p.equals("/api/app/blog") || p.startsWith("/api/app/blog/");
                boolean margin = p.equals("/api/app/margins") || p.startsWith("/api/app/margins/");
                if (write && (blog || margin)) {
                    routes.add(method + " " + p);
                } else if (write && holdsQuotes) {
                    outside.add(method + " " + p);
                }
            });
        }));
        assertThat(quoteHolders).extracting(Class::getSimpleName) // 양성 대조 — 필드 검사가 빈 집합으로 공허해지지 않게
                .contains("AppBlogApiController", "AppMarginApiController");
        assertThat(outside).isEmpty();
        Set<String> expected = new HashSet<>(BLOCKED);
        expected.addAll(OPEN);
        expected.addAll(MARGIN_NOT_BLOG);
        assertThat(routes).isEqualTo(expected);
    }

    @Test
    @DisplayName("REQ-244 · '*'면 200 · 목록 밖도 읽기 · 지우기 200")
    void readsAndDeletesStayOpen() throws Exception {
        User apple = appleProvisioning.register("001.blog.open");
        String token = apiTokenService.issue(apple);
        long id = create(token, "글", "문장."); // '*' — 양성 대조
        ReflectionTestUtils.setField(blogAccess, "allowed", "none");
        try {
            call(get("/api/app/blog/posts/" + id), token).andExpect(status().isOk());
            call(get("/api/app/blog/posts"), token).andExpect(status().isOk());
            call(get("/api/app/blog/posts/mine"), token).andExpect(status().isOk());
            call(get("/api/app/blog/me"), token).andExpect(status().isOk());
            call(delete("/api/app/blog/posts/" + id), token).andExpect(status().isNoContent());
        } finally {
            ReflectionTestUtils.setField(blogAccess, "allowed", "*");
        }
        assertThat(postRepository.findById(id)).isEmpty();
    }

    // ── REQ-245 제목 · 상한 ─────────────────────────────────────────────────

    @Test
    @DisplayName("REQ-245 · 제목 없이 올리기 400")
    void publishNeedsTitle() throws Exception {
        String me = ready("bnotitle");
        long id = create(me, null, "본문만 있다.");
        json(post("/api/app/blog/posts/" + id + "/publish"), me, "{\"revision\":0}").andExpect(status().isBadRequest())
                .andExpect(content().string("제목을 적어 주세요"));
        long blank = create(me, "   ", "본문만 있다.");
        json(post("/api/app/blog/posts/" + blank + "/publish"), me, "{\"revision\":0}").andExpect(status().isBadRequest());
        assertThat(sentenceRepository.findByPostId(id)).isEmpty();
    }

    @Test
    @DisplayName("REQ-245 · 올린 글의 초안 제목을 바꿔도 목록 · 글 읽기 제목은 그대로 · 다시 올리면 바뀜")
    void draftTitleIsNotPublishedTitle() throws Exception {
        String me = ready("btitle2");
        String reader = ready("btitle2r");
        long id = published(me, "처음 제목", "본문이다.");
        edit(me, id, "새 제목", "본문이다.", 0);

        call(get("/api/app/blog/posts/" + id), reader).andExpect(jsonPath("$.title").value("처음 제목"))
                .andExpect(jsonPath("$.draft").value(nullValue()));
        call(get("/api/app/blog/posts"), reader).andExpect(jsonPath("$.posts[0].title").value("처음 제목"));
        publish(me, id, 1);
        call(get("/api/app/blog/posts/" + id), reader).andExpect(jsonPath("$.title").value("새 제목"))
                .andExpect(jsonPath("$.republishedAt").exists());
    }

    @Test
    @DisplayName("REQ-245 · 배치 항목 1,501개 400")
    void layoutCap() throws Exception {
        String me = ready("bcap");
        long over = create(me, "넘침", String.join("\n", java.util.Collections.nCopies(1501, "가.")));
        json(post("/api/app/blog/posts/" + over + "/publish"), me, "{\"revision\":0}").andExpect(status().isBadRequest())
                .andExpect(content().string("문장이 너무 많아요 — 1,500개까지 올릴 수 있어요"));
        assertThat(sentenceRepository.findByPostId(over)).isEmpty();
        long cap = create(me, "경계", String.join("\n", java.util.Collections.nCopies(1500, "가.")));
        publish(me, cap, 0); // 양성 대조 — 1,500개는 올라간다
        assertThat(sentenceRepository.findByPostId(cap)).hasSize(1500);
    }

    // ── REQ-246 · 259 첫 설정 ───────────────────────────────────────────────

    @Test
    @DisplayName("REQ-246 · 구글 가입 · 이름 미확인이면 올리기 409")
    void googleUnconfirmed409() throws Exception {
        User google = registrationService.registerOAuth("blog-google@booktimer.com", "김실명", SEOUL, AuthProvider.GOOGLE,
                LocalDate.of(2026, 11, 1));
        String token = apiTokenService.issue(google);
        long id = create(token, "제목", "본문이다.");
        json(post("/api/app/blog/posts/" + id + "/publish"), token, "{\"revision\":0}").andExpect(status().isConflict())
                .andExpect(content().string("블로그에 보일 이름과 커뮤니티 규칙을 먼저 확인해 주세요"));
        assertThat(sentenceRepository.findByPostId(id)).isEmpty();
    }

    @Test
    @DisplayName("REQ-246 · 「독자」(Apple) 미확인 409")
    void appleDefaultName409() throws Exception {
        User apple = appleProvisioning.register("001.blog.reader");
        String token = apiTokenService.issue(apple);
        call(get("/api/app/blog/me"), token).andExpect(jsonPath("$.name").value("독자"))
                .andExpect(jsonPath("$.nameConfirmed").value(false)).andExpect(jsonPath("$.termsAgreed").value(false));
        long id = create(token, "제목", "본문이다.");
        json(post("/api/app/blog/posts/" + id + "/publish"), token, "{\"revision\":0}").andExpect(status().isConflict());
    }

    @Test
    @DisplayName("REQ-246 · blog/me 뒤 200 · 닉네임이 그 이름")
    void confirmThenPublish() throws Exception {
        User apple = appleProvisioning.register("001.blog.confirm");
        String token = apiTokenService.issue(apple);
        long id = create(token, "제목", "본문이다.");
        json(put("/api/app/blog/me"), token, "{\"name\":\"  책 읽는 밤 \",\"agreeTerms\":true}").andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("책 읽는 밤")).andExpect(jsonPath("$.nameConfirmed").value(true))
                .andExpect(jsonPath("$.termsAgreed").value(true));
        String view = publish(token, id, 0);
        assertThat(JsonPath.<String>read(view, "$.authorName")).isEqualTo("책 읽는 밤");
        assertThat(userRepository.findById(apple.getId()).orElseThrow().getNickname()).isEqualTo("책 읽는 밤");
    }

    @ParameterizedTest(name = "REQ-246 · 이름 1자 · 21자 · 줄바꿈 400 · {0}")
    @CsvSource(delimiter = '|', value = {
            "가 | 이름은 2~20자로 적어 주세요",
            "가나다라마바사아자차카타파하가나다라마바사 | 이름은 2~20자로 적어 주세요",
            "가\\n나 | 이름에 줄바꿈이나 보이지 않는 글자는 쓸 수 없어요",
            "가\\t나 | 이름에 줄바꿈이나 보이지 않는 글자는 쓸 수 없어요"
    })
    void badNames(String name, String message) throws Exception {
        String token = apiTokenService.issue(register("bname"));
        json(put("/api/app/blog/me"), token, "{\"name\":\"" + name + "\",\"agreeTerms\":true}").andExpect(status().isBadRequest())
                .andExpect(content().string(message));
        json(put("/api/app/blog/me"), token, "{\"name\":\"좋은이름\",\"agreeTerms\":false}").andExpect(status().isBadRequest())
                .andExpect(content().string("커뮤니티 규칙에 동의해 주세요"));
        assertThat(userRepository.findByLoginId("bname").orElseThrow().getBlogNameConfirmedAt()).isNull();
    }

    @Test
    @DisplayName("REQ-246 · 이름 길이는 UTF-16 — 😊 10개(20) 200 · 11개(22) 400")
    void nameLengthIsUtf16() throws Exception {
        String token = apiTokenService.issue(register("bemoji"));
        json(put("/api/app/blog/me"), token, "{\"name\":\"" + "😊".repeat(11) + "\",\"agreeTerms\":true}")
                .andExpect(status().isBadRequest());
        json(put("/api/app/blog/me"), token, "{\"name\":\"" + "😊".repeat(10) + "\",\"agreeTerms\":true}")
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("REQ-259 · ugc_terms_at null이면 올리기 409")
    void termsMissing409() throws Exception {
        String token = ready("bterms");
        long id = create(token, "제목", "본문이다.");
        sql("update users set ugc_terms_at = null where login_id = ?", "bterms");
        json(post("/api/app/blog/posts/" + id + "/publish"), token, "{\"revision\":0}").andExpect(status().isConflict())
                .andExpect(content().string("블로그에 보일 이름과 커뮤니티 규칙을 먼저 확인해 주세요"));
        sql("update users set ugc_terms_at = ? where login_id = ?", Timestamp.from(Instant.now()), "bterms");
        publish(token, id, 0); // 양성 대조 — 동의가 있으면 올라간다
    }

    // ── 엣지(계획 §8.1 11) ──────────────────────────────────────────────────

    @Test
    @DisplayName("엣지 · 빈 줄만 있는 본문은 문장 0 → 400")
    void blankLinesOnly() throws Exception {
        String me = ready("bblank");
        long id = create(me, "제목", "\n\n   \n" + (char) 0xA0 + "\n");
        json(post("/api/app/blog/posts/" + id + "/publish"), me, "{\"revision\":0}").andExpect(status().isBadRequest())
                .andExpect(content().string("본문을 한 문장 이상 적어 주세요"));
        assertThat(postRepository.findById(id).orElseThrow().getPublishedAt()).isNull();
    }

    @Test
    @DisplayName("엣지 · 같은 문장 둘은 주소가 저마다 · 하나 더 쓰면 새 주소 하나")
    void duplicateSentences() throws Exception {
        String me = ready("bdup");
        long id = published(me, "제목", "같은 문장이다. 같은 문장이다.");
        List<String> two = sids(read(me, id, null));
        assertThat(two).hasSize(2).doesNotHaveDuplicates();
        edit(me, id, "제목", "같은 문장이다. 같은 문장이다. 같은 문장이다.", 0);
        List<String> three = sids(publish(me, id, 1));
        assertThat(three).hasSize(3).doesNotHaveDuplicates().containsAll(two);
    }

    @Test
    @DisplayName("엣지 · 이모지 · URL만 있는 줄은 글자 그대로 한 문장 · 줄 끝 br")
    void emojiAndUrlLine() throws Exception {
        String me = ready("burl");
        String view = publish(me, create(me, "제목", "https://example.com/a.b?c=1\n좋았다 😊. 🎉"), 0);
        List<Map<String, Object>> s = JsonPath.read(view, "$.blocks[0].sentences[*]");
        assertThat(s).extracting(x -> x.get("text")).containsExactly("https://example.com/a.b?c=1", "좋았다 😊.", "🎉");
        assertThat(s).extracting(x -> x.get("br")).containsExactly(true, false, false);
    }

    @Test
    @DisplayName("엣지 · 글쓴이가 차단한 독자도 글 GET 404(차단은 양방향)")
    void authorBlocksReader() throws Exception {
        String me = ready("bblk");
        String reader = ready("bblkr");
        long id = published(me, "제목", "문장이다.");
        call(get("/api/app/blog/posts/" + id), reader).andExpect(status().isOk()); // 양성 대조
        blockRepository.saveAndFlush(Block.of(userRepository.findByLoginId("bblk").orElseThrow(),
                userRepository.findByLoginId("bblkr").orElseThrow()));
        call(get("/api/app/blog/posts/" + id), reader).andExpect(status().isNotFound());
        call(get("/api/app/blog/posts"), reader).andExpect(jsonPath("$.posts", hasSize(0)));
    }

    @Test
    @DisplayName("엣지 · 남의 글 PUT · DELETE · publish 404(글 그대로) — 내 글은 2xx")
    void othersPostIdor() throws Exception {
        String me = ready("bidor");
        String other = ready("bidoro");
        long id = published(me, "제목", "문장이다.");

        json(put("/api/app/blog/posts/" + id), other, save("남", "남의 글.", 0)).andExpect(status().isNotFound());
        call(delete("/api/app/blog/posts/" + id), other).andExpect(status().isNotFound());
        json(post("/api/app/blog/posts/" + id + "/publish"), other, "{\"revision\":0}").andExpect(status().isNotFound());
        call(get("/api/app/blog/posts/" + id), me).andExpect(jsonPath("$.draft.revision").value(0))
                .andExpect(jsonPath("$.title").value("제목"));

        edit(me, id, "제목", "문장이다.", 0);
        publish(me, id, 1);
        call(delete("/api/app/blog/posts/" + id), me).andExpect(status().isNoContent());
        call(get("/api/app/blog/posts/" + id), me).andExpect(status().isNotFound());
    }

    @ParameterizedTest(name = "엣지 · limit {0} → {1}")
    @CsvSource({"0, 400", "51, 400", "-1, 400", "1, 200", "50, 200"})
    void limitBounds(int limit, int expected) throws Exception {
        String me = ready("blimit");
        call(get("/api/app/blog/posts?limit=" + limit), me).andExpect(status().is(expected));
    }
}
