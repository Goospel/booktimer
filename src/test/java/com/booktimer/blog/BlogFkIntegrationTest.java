package com.booktimer.blog;

import com.booktimer.story.Story;
import com.booktimer.story.StoryRepository;
import com.booktimer.user.AccountService;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 블로그 FK 연쇄(실 H2 — mock은 FK를 검증 못 한다, T-023 · T-029). users → blog_post → blog_sentence → blog_sentence_rev가
 * 전부 cascade 없는 FK라, 부모를 지우는 길(탈퇴 · 글 지우기)이 자식을 먼저 정리하지 않으면 제약 위반 500이 난다.
 */
@SpringBootTest
@Transactional
class BlogFkIntegrationTest {

    @Autowired AccountService accountService;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired BlogPostRepository postRepository;
    @Autowired BlogSentenceRepository sentenceRepository;
    @Autowired BlogSentenceRevRepository revRepository;
    @Autowired BlogPostService postService;
    @Autowired QuoteBoxRepository quoteBoxRepository;
    @Autowired QuoteBoxService quoteBoxService;
    @Autowired StoryRepository storyRepository;
    @Autowired BlogCommentRepository commentRepository;
    @Autowired EntityManager em;

    // ── B2a 댓글(blog_comment.post_id · user_id FK — 부모 삭제는 실 H2로만 잰다) ─────────────────────

    @Test
    @DisplayName("REQ-243 · 남의 댓글이 달린 글 지우기 → 댓글 행 0 · FK 위반 0(실 H2)")
    void deletePostClearsComments() {
        User author = user("cmtdelete");
        User reader = user("cmtreader");
        BlogPost post = publishedPost(author);
        BlogPost keeper = publishedPostWithSid(author, "KKKKKKKK");
        commentRepository.saveAndFlush(BlogComment.of(post, "AAAAAAAA", reader, "남의 댓글"));
        commentRepository.saveAndFlush(BlogComment.of(post, "BBBBBBBB", author, "내 댓글"));
        BlogComment kept = commentRepository.saveAndFlush(BlogComment.of(keeper, "KKKKKKKK", reader, "남는 댓글"));
        Long postId = post.getId();

        assertThatCode(() -> {
            postService.delete(author, postId);
            postRepository.flush();
        }).doesNotThrowAnyException();
        assertThat(postRepository.findById(postId)).isEmpty();
        assertThat(commentRepository.findAll()).extracting(BlogComment::getId).containsExactly(kept.getId());
    }

    @Test
    @DisplayName("REQ-257 · 남의 글에 단 댓글 · 내 글에 달린 남의 댓글이 있는 계정의 탈퇴 — FK 위반 0 · 그 댓글 행 0(실 H2)")
    void purgeClearsComments() {
        User leaver = user("cmtpurge");
        User other = user("cmtother");
        BlogPost mine = publishedPost(leaver);
        BlogPost theirs = publishedPostWithSid(other, "KKKKKKKK");
        commentRepository.saveAndFlush(BlogComment.of(theirs, "KKKKKKKK", leaver, "남의 글에 단 내 댓글"));
        commentRepository.saveAndFlush(BlogComment.of(mine, "AAAAAAAA", other, "내 글에 달린 남의 댓글"));
        BlogComment kept = commentRepository.saveAndFlush(BlogComment.of(theirs, "KKKKKKKK", other, "남의 글 남의 댓글"));

        assertThatCode(() -> accountService.deleteAccount("cmtpurge@booktimer.com", "rawpw1234")).doesNotThrowAnyException();
        em.flush();
        em.clear();
        assertThat(userRepository.findByEmail("cmtpurge@booktimer.com")).isEmpty();
        assertThat(commentRepository.findAll()).extracting(BlogComment::getId).containsExactly(kept.getId());
    }

    private User user(String loginId) {
        User u = User.of(loginId + "@booktimer.com", passwordEncoder.encode("rawpw1234"), "글쓴이", "Asia/Seoul", Role.USER);
        u.assignLoginId(loginId);
        return userRepository.saveAndFlush(u);
    }

    /** 문장 둘 · 판 행 셋(하나는 고친 판)을 가진 올린 글. */
    private BlogPost publishedPost(User author) {
        BlogPost post = BlogPost.draft(author, "제목", "첫 문장이다. 둘째 문장이다.");
        post.publish("제목", "[]", true, Instant.parse("2026-11-11T00:00:00Z"));
        postRepository.saveAndFlush(post);
        BlogSentence a = sentenceRepository.saveAndFlush(BlogSentence.create(post, "AAAAAAAA", "첫 문장이다.", null));
        BlogSentence b = sentenceRepository.saveAndFlush(BlogSentence.create(post, "BBBBBBBB", "둘째 문장이다.", null));
        revRepository.saveAndFlush(BlogSentenceRev.of(a, 1, "첫 문장이다."));
        revRepository.saveAndFlush(BlogSentenceRev.of(b, 1, "둘째 문장."));
        b.edit("둘째 문장이다.");
        revRepository.saveAndFlush(BlogSentenceRev.of(b, 2, "둘째 문장이다."));
        return post;
    }

    @Test
    @DisplayName("REQ-204 · 올린 글(문장 · 판)을 가진 계정의 탈퇴가 FK 위반 없이 정리된다(실 H2)")
    void purgeClearsBlogTables() {
        User author = user("blogpurge");
        BlogPost post = publishedPost(author);
        Long postId = post.getId();

        assertThatCode(() -> accountService.deleteAccount("blogpurge@booktimer.com", "rawpw1234"))
                .doesNotThrowAnyException();
        assertThat(userRepository.findByEmail("blogpurge@booktimer.com")).isEmpty();
        assertThat(postRepository.findById(postId)).isEmpty();
        assertThat(sentenceRepository.findByPostId(postId)).isEmpty();
        assertThat(revRepository.count()).isZero();
    }

    @Test
    @DisplayName("REQ-243 · 글 지우기 → 문장 · 판 행 0(실 H2 — FK 위반 0)")
    void deletePostClearsSentencesAndRevs() {
        User author = user("blogdelete");
        BlogPost post = publishedPost(author);
        BlogPost keeper = publishedPostWithSid(author, "KKKKKKKK");
        Long postId = post.getId();

        assertThatCode(() -> {
            postService.delete(author, postId);
            postRepository.flush();
        }).doesNotThrowAnyException();
        assertThat(postRepository.findById(postId)).isEmpty();
        assertThat(sentenceRepository.findByPostId(postId)).isEmpty();
        assertThat(revRepository.findAll()).as("남은 판 행은 다른 글의 것뿐").singleElement()
                .satisfies(r -> assertThat(r.getText()).isEqualTo("남는 글"));
        assertThat(sentenceRepository.findByPostId(keeper.getId())).hasSize(1);
    }

    @Test
    @DisplayName("문장 · 판 행 지우기는 일괄 DELETE — 행을 엔티티로 읽어 한 줄씩 지우지 않는다(계획 §5.1 · 리뷰 auth-F3)")
    void sentenceAndRevDeletesAreBulk() {
        User author = user("blogbulk");
        BlogPost one = publishedPost(author);
        BlogPost two = publishedPostWithSid(author, "KKKKKKKK");
        em.clear(); // 영속성 문맥에 남은 엔티티로 세지 않게
        Statistics stats = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        boolean was = stats.isStatisticsEnabled();
        stats.setStatisticsEnabled(true);
        try {
            stats.clear();
            BlogPost post = postRepository.findById(one.getId()).orElseThrow();
            revRepository.deleteBySentencePost(post);
            sentenceRepository.deleteByPost(post);
            User u = userRepository.findById(author.getId()).orElseThrow();
            revRepository.deleteBySentencePostUser(u);
            sentenceRepository.deleteByPostUser(u);
            em.flush(); // 파생 지우기는 flush 때 엔티티 DELETE를 센다
            assertThat(stats.getEntityDeleteCount()).as("엔티티 단위 지우기 수(일괄이면 0)").isZero();
        } finally {
            stats.setStatisticsEnabled(was);
        }
        em.clear();
        assertThat(sentenceRepository.findByPostId(one.getId())).isEmpty();
        assertThat(sentenceRepository.findByPostId(two.getId())).isEmpty();
        assertThat(revRepository.count()).isZero();
    }

    // ── B1b 인용 박스(FK가 없어 「행 0」으로 잰다 — 고아가 조용히 남는 실패를 잡는다) ─────────────────

    private static final Instant AT = Instant.parse("2026-11-11T00:00:00Z");

    /** 그 글의 문장 AAAAAAAA를 인용한 박스(주인 = owner · 원 글쓴이 = 글 주인). */
    private QuoteBox quoting(QuoteBox.Owner owner, long ownerId, BlogPost source) {
        return quoteBoxRepository.saveAndFlush(QuoteBox.blog(owner, ownerId, 1, source.getId(), source.getUser().getId(),
                java.util.List.of("AAAAAAAA"), 1, "첫 문장이다.", "제목", AT));
    }

    @Test
    @DisplayName("REQ-204 · 내 글 · 내 쪽지의 박스 행 0 · 남의 글이 내 문장을 인용한 박스는 남는다")
    void purgeClearsOwnBoxesKeepsOthers() {
        User author = user("boxpurge");
        User reader = user("boxkeep");
        BlogPost mine = publishedPost(author);
        Story myNote = storyRepository.saveAndFlush(Story.of(author, "내 생각.", null, null));
        quoting(QuoteBox.Owner.POST, mine.getId(), mine);
        quoting(QuoteBox.Owner.NOTE, myNote.getId(), mine);
        BlogPost theirs = publishedPostWithSid(reader, "KKKKKKKK");
        QuoteBox kept = quoting(QuoteBox.Owner.POST, theirs.getId(), mine);
        Long postId = mine.getId();
        Long noteId = myNote.getId();

        assertThatCode(() -> accountService.deleteAccount("boxpurge@booktimer.com", "rawpw1234")).doesNotThrowAnyException();
        em.flush();
        em.clear();
        assertThat(quoteBoxRepository.countByOwnerTypeAndOwnerId(QuoteBox.Owner.POST, postId)).isZero();
        assertThat(quoteBoxRepository.countByOwnerTypeAndOwnerId(QuoteBox.Owner.NOTE, noteId)).isZero();
        assertThat(quoteBoxRepository.findById(kept.getId())).isPresent();
    }

    @Test
    @DisplayName("REQ-209 · 원 글쓴이 탈퇴 → 「탈퇴한 사용자」 · source_user_id · source_post_id · members · seen_sentence_id null · 박스 글 · 제목 스냅샷 그대로")
    void purgeDetachesSourceUser() {
        User author = user("boxgone");
        User reader = user("boxreader");
        BlogPost source = publishedPost(author);
        BlogPost theirs = publishedPostWithSid(reader, "KKKKKKKK");
        QuoteBox box = quoting(QuoteBox.Owner.POST, theirs.getId(), source);
        QuoteBox untouched = quoting(QuoteBox.Owner.NOTE, 987_654L, theirs); // 다른 원 글쓴이 — 양성 대조

        accountService.deleteAccount("boxgone@booktimer.com", "rawpw1234");
        em.flush();
        em.clear();
        QuoteBox row = quoteBoxRepository.findById(box.getId()).orElseThrow();
        assertThat(row.getSourceUserId()).isNull();
        assertThat(row.getSourcePostId()).isNull();
        assertThat(row.members()).isEmpty();
        assertThat(row.getSeenSentenceId()).isNull();
        assertThat(row.getText()).isEqualTo("첫 문장이다.");
        assertThat(row.getAttribution()).isEqualTo("제목");
        assertThat(quoteBoxRepository.findById(untouched.getId()).orElseThrow().getSourceUserId()).isEqualTo(reader.getId());

        BlogViews.QuoteBoxView view = quoteBoxService.views(QuoteBox.Owner.POST, theirs.getId(),
                userRepository.findById(reader.getId()).orElseThrow()).get(0);
        assertThat(view.attribution()).isEqualTo("탈퇴한 사용자 · 「제목」");
        assertThat(view.sourceDeleted()).isTrue();
        assertThat(view.sourceLink()).isNull();
        assertThat(view.text()).isEqualTo("첫 문장이다.");
    }

    // ── B2b 신고 · 운영자 가리기 · 처리 기록(blog_report.reporter_id FK · 처리 기록은 FK 없음 — 실 H2로만 잰다) ─────────────

    @Autowired BlogReportRepository reportRepository;
    @Autowired BlogModerationLogRepository logRepository;
    @Autowired BlogModerationService moderationService;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    @DisplayName("REQ-257 · 블로그 신고를 한 계정의 탈퇴 — 그 신고 행 0 · 남이 내 글을 신고한 행은 남는다(실 H2)")
    void purgeClearsMyReports() {
        User leaver = user("rptpurge");
        User other = user("rptother");
        BlogPost theirs = publishedPostWithSid(other, "KKKKKKKK");
        BlogPost mine = publishedPost(leaver);
        reportRepository.saveAndFlush(BlogReport.of(leaver, BlogReport.Target.POST, theirs.getId(), other.getId(),
                com.booktimer.report.ReportReason.SPAM, null));
        BlogReport kept = reportRepository.saveAndFlush(BlogReport.of(other, BlogReport.Target.POST, mine.getId(), leaver.getId(),
                com.booktimer.report.ReportReason.SPAM, null));

        assertThatCode(() -> accountService.deleteAccount("rptpurge@booktimer.com", "rawpw1234")).doesNotThrowAnyException();
        em.flush();
        em.clear();
        assertThat(userRepository.findByEmail("rptpurge@booktimer.com")).isEmpty();
        assertThat(reportRepository.findAll()).extracting(BlogReport::getId).containsExactly(kept.getId());
    }

    @Test
    @DisplayName("REQ-257 · 신고당한 글쓴이의 탈퇴 — 그 신고 행은 남고 target_user_id만 null · 처리 기록 행은 그대로(실 H2)")
    void purgeDetachesReportedAuthor() {
        User leaver = user("rptgone");
        User reporter = user("rptkeeper");
        User bystander = user("rptbystander");
        BlogPost mine = publishedPost(leaver);
        BlogPost theirs = publishedPostWithSid(bystander, "KKKKKKKK");
        BlogReport onMine = reportRepository.saveAndFlush(BlogReport.of(reporter, BlogReport.Target.POST, mine.getId(),
                leaver.getId(), com.booktimer.report.ReportReason.HARASSMENT, null));
        BlogReport onOther = reportRepository.saveAndFlush(BlogReport.of(reporter, BlogReport.Target.POST, theirs.getId(),
                bystander.getId(), com.booktimer.report.ReportReason.SPAM, null)); // 양성 대조 — 다른 글쓴이는 그대로
        moderationService.apply(BlogModerationService.Unit.POST, mine.getId(), Moderation.Action.TEMP, "가린 까닭");
        long logs = logRepository.count();

        assertThatCode(() -> accountService.deleteAccount("rptgone@booktimer.com", "rawpw1234")).doesNotThrowAnyException();
        em.flush();
        em.clear();
        assertThat(reportRepository.findById(onMine.getId()).orElseThrow().getTargetUserId()).isNull();
        assertThat(reportRepository.findById(onOther.getId()).orElseThrow().getTargetUserId()).isEqualTo(bystander.getId());
        assertThat(logRepository.count()).as("처리 기록은 탈퇴와 따로 3년").isEqualTo(logs).isPositive();
    }

    @Test
    @DisplayName("REQ-333 · 운영자 삭제(글) = 문장 · 판 · 박스 · 댓글 행 0 · 제목 · 배치 null · hide_kind REMOVED · 글 행은 남는다(실 H2)")
    void adminRemoveWipesContentKeepsRow() {
        User author = user("rmvauthor");
        User reader = user("rmvreader");
        BlogPost post = publishedPost(author);
        quoting(QuoteBox.Owner.POST, post.getId(), post);
        commentRepository.saveAndFlush(BlogComment.of(post, "AAAAAAAA", reader, "댓글"));
        BlogPost keeper = publishedPostWithSid(author, "KKKKKKKK"); // 양성 대조 — 다른 글은 그대로
        Long id = post.getId();

        assertThatCode(() -> {
            moderationService.apply(BlogModerationService.Unit.POST, id, Moderation.Action.REMOVE, "지운 까닭");
            em.flush();
        }).doesNotThrowAnyException();
        em.clear();
        // 메모리의 엔티티가 아니라 DB 값 — markRemoved가 일괄 지우기의 문맥 비움에 버려지면 여기서 빨갛다(§4.5)
        java.util.Map<String, Object> row = jdbc.queryForMap(
                "select title, layout_json, draft_body, hidden_at, hide_kind from blog_post where id = ?", id);
        assertThat(row.get("TITLE")).isNull();
        assertThat(row.get("LAYOUT_JSON")).isNull();
        assertThat(row.get("DRAFT_BODY")).isEqualTo("");
        assertThat(row.get("HIDDEN_AT")).isNotNull();
        assertThat(row.get("HIDE_KIND")).isEqualTo("REMOVED");
        assertThat(sentenceRepository.findByPostId(id)).isEmpty();
        assertThat(quoteBoxRepository.countByOwnerTypeAndOwnerId(QuoteBox.Owner.POST, id)).isZero();
        assertThat(commentRepository.findAll()).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from blog_sentence_rev r join blog_sentence s on s.id = r.sentence_id "
                + "where s.post_id = ?", Integer.class, id)).isZero();
        assertThat(sentenceRepository.findByPostId(keeper.getId())).hasSize(1);
    }

    @Test
    @DisplayName("REQ-339 · 가리기 → 다시 보이기 → 글쓴이 지우기 → 글쓴이 탈퇴 뒤에도 처리 기록 두 줄(TEMP · RESTORE)이 남는다(실 H2)")
    void moderationLogOutlivesEverything() {
        User author = user("logauthor");
        BlogPost post = publishedPost(author);
        Long id = post.getId();
        moderationService.apply(BlogModerationService.Unit.POST, id, Moderation.Action.TEMP, "요청서 사본");
        moderationService.apply(BlogModerationService.Unit.POST, id, Moderation.Action.TEMP, null); // 그대로 — 기록 0
        moderationService.apply(BlogModerationService.Unit.POST, id, Moderation.Action.RESTORE, null);
        em.flush();
        em.clear();
        postService.delete(userRepository.findById(author.getId()).orElseThrow(), id);
        accountService.deleteAccount("logauthor@booktimer.com", "rawpw1234");
        em.flush();
        em.clear();

        assertThat(logRepository.findAll()).extracting(BlogModerationLog::getUnit, BlogModerationLog::getUnitId,
                        BlogModerationLog::getAction, BlogModerationLog::getNote)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("POST", id, "TEMP", "요청서 사본"),
                        org.assertj.core.groups.Tuple.tuple("POST", id, "RESTORE", null));
    }

    private BlogPost publishedPostWithSid(User author, String sid) {
        BlogPost post = BlogPost.draft(author, "남는 제목", "남는 글");
        post.publish("남는 제목", "[]", true, Instant.parse("2026-11-11T00:00:00Z"));
        postRepository.saveAndFlush(post);
        BlogSentence s = sentenceRepository.saveAndFlush(BlogSentence.create(post, sid, "남는 글", null));
        revRepository.saveAndFlush(BlogSentenceRev.of(s, 1, "남는 글"));
        return post;
    }
}
