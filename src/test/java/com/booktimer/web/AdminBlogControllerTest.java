package com.booktimer.web;

import com.booktimer.blog.BlogComment;
import com.booktimer.blog.BlogCommentRepository;
import com.booktimer.blog.BlogModerationService;
import com.booktimer.blog.BlogPost;
import com.booktimer.blog.BlogPostRepository;
import com.booktimer.blog.BlogReport;
import com.booktimer.blog.BlogReportRepository;
import com.booktimer.blog.BlogReportService;
import com.booktimer.blog.Moderation;
import com.booktimer.blog.QuoteBox;
import com.booktimer.blog.QuoteBoxRepository;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리자 블로그 처리 문(B2b · REQ-338 — {@code AdminReportControllerTest}와 같은 세 줄 세계). 접근 경계 1순위 — ADMIN만 · CSRF.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminBlogControllerTest {

    private static final Instant AT = Instant.parse("2026-11-11T00:00:00Z");

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired BlogPostRepository postRepository;
    @Autowired BlogCommentRepository commentRepository;
    @Autowired QuoteBoxRepository quoteBoxRepository;
    @Autowired BlogReportRepository reportRepository;
    @Autowired BlogReportService reportService;
    @Autowired BlogModerationService moderation;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    private User person(String loginId) {
        User u = User.of(loginId + "@booktimer.com", "$2a$10$x", "닉_" + loginId, "Asia/Seoul", Role.USER);
        u.assignLoginId(loginId);
        return userRepository.saveAndFlush(u);
    }

    private BlogPost postOf(User author, String title) {
        BlogPost p = BlogPost.draft(author, title, "문장이다.");
        p.publish(title, "[]", true, AT);
        return postRepository.saveAndFlush(p);
    }

    private QuoteBox box(QuoteBox.Owner owner, long ownerId, BlogPost source, String text) {
        return quoteBoxRepository.saveAndFlush(QuoteBox.blog(owner, ownerId, 1, source.getId(), source.getUser().getId(),
                List.of("AAAAAAAA"), 1, text, source.getTitle(), AT));
    }

    private BlogReport reportOf(User reporter, BlogReport.Target type, long id, String detail) {
        reportService.report(reporter, type, id, "SPAM", detail);
        em.flush();
        return reportRepository.findAll().stream().filter(r -> r.getTargetId().equals(id) && r.getReporter().getId().equals(reporter.getId()))
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("REQ-338 · 관리자 처리 문 셋은 USER 403 · 미인증은 로그인으로 · CSRF 없으면 403")
    void accessBoundary() throws Exception {
        User author = person("adbauthor");
        BlogPost p = postOf(author, "글");
        for (String path : List.of("/admin/blog/reports/1/resolve", "/admin/blog/moderate", "/admin/blog/users/" + author.getId() + "/restriction")) {
            mvc.perform(post(path).param("unit", "POST").param("unitId", String.valueOf(p.getId())).param("action", "TEMP")
                    .param("on", "true").with(user("u@booktimer.com")).with(csrf())).andExpect(status().isForbidden());
            mvc.perform(post(path).param("unit", "POST").param("unitId", String.valueOf(p.getId())).param("action", "TEMP")
                    .param("on", "true").with(csrf())).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
            mvc.perform(post(path).param("unit", "POST").param("unitId", String.valueOf(p.getId())).param("action", "TEMP")
                    .param("on", "true").with(user("boss").roles("ADMIN"))).andExpect(status().isForbidden());
        }
        em.flush();
        em.clear();
        assertThat(postRepository.findById(p.getId()).orElseThrow().hidden()).isFalse();
        assertThat(userRepository.findById(author.getId()).orElseThrow().getBlogRestrictedAt()).isNull();
        mvc.perform(post("/admin/blog/moderate").param("unit", "POST").param("unitId", String.valueOf(p.getId()))
                        .param("action", "TEMP").with(user("boss").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/reports")); // 양성 대조
        em.flush();
        em.clear();
        assertThat(postRepository.findById(p.getId()).orElseThrow().hidden()).isTrue();
    }

    @Test
    @DisplayName("REQ-338 · 신고함에 블로그 신고 · 기간 끝난 임시조치 · 이 글을 인용한 박스가 보인다")
    void reportsPageShowsBlogSection() throws Exception {
        mvc.perform(get("/admin/reports").with(user("boss").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("접수된 블로그 신고가 없습니다.")));
        User author = person("adbsauthor");
        User reporter = person("adbsreporter");
        User quoter = person("adbsquoter");
        BlogPost p = postOf(author, "신고된 글 제목Q");
        BlogPost theirs = postOf(quoter, "인용한 글");
        QuoteBox quoting = box(QuoteBox.Owner.POST, theirs.getId(), p, "인용된 글자");
        reportOf(reporter, BlogReport.Target.POST, p.getId(), "부가설명글자Z");
        BlogPost expired = postOf(author, "오래 가린 글");
        moderation.apply(BlogModerationService.Unit.POST, expired.getId(), Moderation.Action.TEMP, null);
        em.flush();
        jdbc.update("update blog_post set hidden_at = ? where id = ?", Timestamp.from(Instant.now().minus(Duration.ofDays(31))), expired.getId());
        em.clear();

        mvc.perform(get("/admin/reports").with(user("boss").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("부가설명글자Z")))
                .andExpect(content().string(containsString("신고된 글 제목Q")))
                .andExpect(content().string(containsString("닉_adbsauthor")))
                .andExpect(content().string(containsString("인용 #" + quoting.getId())))
                .andExpect(content().string(containsString("기간 끝난 임시조치")))
                .andExpect(content().string(containsString("글 #" + expired.getId())))
                .andExpect(content().string(not(containsString("접수된 블로그 신고가 없습니다."))));
        person("boss"); // /admin은 운영자 본인을 이름으로 찾는다
        mvc.perform(get("/admin").with(user("boss").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("data-open-blog-reports=\"1\"")))
                .andExpect(content().string(containsString("data-expired-temp=\"1\"")));
    }

    @Test
    @DisplayName("REQ-338 · 글쓰기 막기 · 풀기가 blog_restricted_at을 쓴다")
    void restrictionToggle() throws Exception {
        User author = person("adbrauthor");
        mvc.perform(post("/admin/blog/users/" + author.getId() + "/restriction").param("on", "true").with(user("boss").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        em.flush();
        em.clear();
        assertThat(userRepository.findById(author.getId()).orElseThrow().getBlogRestrictedAt()).isNotNull();
        mvc.perform(post("/admin/blog/users/" + author.getId() + "/restriction").param("on", "false").with(user("boss").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        em.flush();
        em.clear();
        assertThat(userRepository.findById(author.getId()).orElseThrow().getBlogRestrictedAt()).isNull();
        assertThat(jdbc.queryForList("select action from blog_moderation_log where unit = 'USER' and unit_id = ? order by id",
                String.class, author.getId())).containsExactly("RESTRICT", "UNRESTRICT");
    }

    @Test
    @DisplayName("REQ-338 · 신고와 무관한 단위(다른 글 · 다른 글의 박스 · 다른 댓글)로 resolve하면 flash 오류 · 그 신고는 OPEN 그대로 · 단위 상태 그대로 · 그 글의 박스로는 된다(양성 대조)")
    void resolveBindsUnitToTarget() throws Exception {
        User author = person("adbbauthor");
        User reporter = person("adbbreporter");
        User reporter2 = person("adbbreporter2");
        BlogPost p = postOf(author, "신고된 글");
        BlogPost other = postOf(author, "다른 글");
        QuoteBox ownBox = box(QuoteBox.Owner.POST, p.getId(), other, "그 글의 박스");
        QuoteBox otherBox = box(QuoteBox.Owner.POST, other.getId(), p, "다른 글의 박스");
        BlogComment otherComment = commentRepository.saveAndFlush(BlogComment.of(p, "AAAAAAAA", reporter, "다른 댓글"));
        BlogReport r = reportOf(reporter, BlogReport.Target.POST, p.getId(), "quote:" + ownBox.getId());
        reportOf(reporter2, BlogReport.Target.POST, p.getId(), null);
        String bad = "신고 대상과 다른 게시물이에요 — 그 게시물은 id로 처리 폼을 쓰세요";

        for (String[] unit : new String[][]{{"POST", String.valueOf(other.getId())}, {"QUOTE", String.valueOf(otherBox.getId())},
                {"COMMENT", String.valueOf(otherComment.getId())}}) {
            mvc.perform(post("/admin/blog/reports/" + r.getId() + "/resolve").param("action", "TEMP").param("unit", unit[0])
                            .param("unitId", unit[1]).with(user("boss").roles("ADMIN")).with(csrf()))
                    .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("error", bad));
        }
        em.flush();
        em.clear();
        assertThat(reportRepository.findById(r.getId()).orElseThrow().getStatus()).isEqualTo(BlogReport.Status.OPEN);
        assertThat(postRepository.findById(other.getId()).orElseThrow().hidden()).isFalse();
        assertThat(quoteBoxRepository.findById(otherBox.getId()).orElseThrow().hidden()).isFalse();

        mvc.perform(post("/admin/blog/reports/" + r.getId() + "/resolve").param("action", "POLICY").param("unit", "QUOTE")
                        .param("unitId", String.valueOf(ownBox.getId())).param("note", "박스만 가림").with(user("boss").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeCount(0));
        em.flush();
        em.clear();
        assertThat(quoteBoxRepository.findById(ownBox.getId()).orElseThrow().hidden()).isTrue();
        assertThat(reportRepository.findAll()).filteredOn(x -> x.getTargetId().equals(p.getId()))
                .allSatisfy(x -> assertThat(x.getStatus()).isEqualTo(BlogReport.Status.RESOLVED)).hasSize(2);
    }

    @Test
    @DisplayName("REQ-338 · 대상 댓글을 지운 뒤에도 신고함 행에 글쓴이 · [글쓰기 막기] 폼")
    void deletedTargetKeepsAuthorAndForm() throws Exception {
        User author = person("adbdauthor");
        User writer = person("adbdwriter");
        User reporter = person("adbdreporter");
        BlogPost p = postOf(author, "글");
        BlogComment c = commentRepository.saveAndFlush(BlogComment.of(p, "AAAAAAAA", writer, "지워질 댓글"));
        reportOf(reporter, BlogReport.Target.COMMENT, c.getId(), null);
        commentRepository.delete(c);
        em.flush();
        em.clear();

        mvc.perform(get("/admin/reports").with(user("boss").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("대상 없음")))
                .andExpect(content().string(containsString("닉_adbdwriter")))
                .andExpect(content().string(containsString("/admin/blog/users/" + writer.getId() + "/restriction")));
    }

    @Test
    @DisplayName("REQ-338 · 신고함은 열린 신고를 오래된 것부터 100건 · 넘으면 「오래된 100건만 보입니다」 · 처리한 신고는 그 뒤에")
    void oldestOpenReportsFirst() throws Exception {
        User reporter = person("adboreporter");
        Instant base = Instant.parse("2026-01-01T00:00:00Z"); // 다른 시험이 남긴 열린 신고보다 앞서게
        String insert = "insert into blog_report (reporter_id, target_type, target_id, reason, status, result, resolved_at, created_at, updated_at) "
                + "values (?, 'POST', ?, 'SPAM', ?, ?, ?, ?, ?)";
        Timestamp resolvedAt = Timestamp.from(base.plus(Duration.ofDays(2)));
        jdbc.update(insert, reporter.getId(), 970_999L, "RESOLVED", "NONE", resolvedAt, Timestamp.from(base.minusSeconds(60)), resolvedAt);
        for (int i = 0; i < 101; i++) {
            Timestamp at = Timestamp.from(base.plusSeconds(i));
            jdbc.update(insert, reporter.getId(), 970_000L + i, "OPEN", null, null, at, at);
        }

        List<BlogModerationService.AdminRow> rows = moderation.adminRows();
        assertThat(rows).hasSize(100).allSatisfy(r -> assertThat(r.status()).isEqualTo("OPEN"));
        assertThat(rows.get(0).targetId()).as("24시간 검토가 가장 급한 것 — 가장 오래된 열린 신고가 맨 위").isEqualTo(970_000L);
        assertThat(rows).extracting(BlogModerationService.AdminRow::targetId).doesNotContain(970_100L, 970_999L);
        mvc.perform(get("/admin/reports").with(user("boss").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("오래된 100건만 보입니다")));

        jdbc.update("update blog_report set status = 'RESOLVED', result = 'NONE', resolved_at = ? where target_id between ? and ?",
                Timestamp.from(base.plus(Duration.ofDays(1))), 970_002L, 970_100L); // 970_999가 가장 최근 처리
        em.clear();
        rows = moderation.adminRows();
        assertThat(rows).extracting(BlogModerationService.AdminRow::targetId).as("열린 것 뒤에 처리한 것 — 양성 대조")
                .startsWith(970_000L, 970_001L).contains(970_999L);
        mvc.perform(get("/admin/reports").with(user("boss").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("오래된 100건만 보입니다"))));
    }

    @Test
    @DisplayName("REQ-333 · 30일 지난 임시조치만 만료 목록(29일 23:59는 아니다) · 저작권 중단 · 정책 가림은 만료 목록에 없다")
    void expiryBoundaryOnRealPath() {
        User author = person("adbxauthor");
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        BlogPost thirty = postOf(author, "30일 된 임시조치");
        BlogPost almost = postOf(author, "29일 23:59 된 임시조치");
        BlogPost copyright = postOf(author, "오래된 저작권 중단");
        BlogPost policy = postOf(author, "오래된 정책 가림");
        moderation.apply(BlogModerationService.Unit.POST, thirty.getId(), Moderation.Action.TEMP, null);
        moderation.apply(BlogModerationService.Unit.POST, almost.getId(), Moderation.Action.TEMP, null);
        moderation.apply(BlogModerationService.Unit.POST, copyright.getId(), Moderation.Action.COPYRIGHT, null);
        moderation.apply(BlogModerationService.Unit.POST, policy.getId(), Moderation.Action.POLICY, null);
        em.flush();
        long before = moderation.expiredCount(now);
        Instant cutoff = now.minus(Duration.ofDays(30));
        jdbc.update("update blog_post set hidden_at = ? where id = ?", Timestamp.from(cutoff), thirty.getId());
        jdbc.update("update blog_post set hidden_at = ? where id = ?", Timestamp.from(cutoff.plusSeconds(60)), almost.getId());
        jdbc.update("update blog_post set hidden_at = ? where id in (?, ?)", Timestamp.from(now.minus(Duration.ofDays(400))),
                copyright.getId(), policy.getId());
        em.clear();

        assertThat(moderation.expiredCount(now)).isEqualTo(before + 1);
        assertThat(moderation.expired(now)).filteredOn(e -> e.unit().equals("POST"))
                .extracting(BlogModerationService.Expired::id).contains(thirty.getId())
                .doesNotContain(almost.getId(), copyright.getId(), policy.getId());
    }

    @Test
    @DisplayName("REQ-338 · note 칸 옆에 「게시자에게 그대로 보입니다 — 저작권 중단 요구(COPYRIGHT)가 아니면」 리터럴")
    void noteWarningLiteral() throws Exception {
        mvc.perform(get("/admin/reports").with(user("boss").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("게시자에게 그대로 보입니다 — 저작권 중단 요구(COPYRIGHT)가 아니면 요청한 사람의 이름 · 연락처를 적지 마세요(처리방침 6절)")));
    }
}
