package com.booktimer.blog;

import com.booktimer.blog.BlogModerationService.Unit;
import com.booktimer.blog.BlogReport.Status;
import com.booktimer.blog.BlogReport.Target;
import com.booktimer.blog.Moderation.Action;
import com.booktimer.report.ReportReason;
import com.booktimer.story.StoryRepository;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 운영자 처리 — 게시자 · 신고자 알림과 처리 기록 줄(계획 §4.5 · §4.6 — 스프링 없는 Mockito). */
class BlogModerationServiceTest {

    private static final Instant NOW = Instant.parse("2026-12-05T00:00:00Z");

    private final BlogPostRepository posts = mock(BlogPostRepository.class);
    private final BlogCommentRepository comments = mock(BlogCommentRepository.class);
    private final QuoteBoxRepository boxes = mock(QuoteBoxRepository.class);
    private final BlogReportRepository reports = mock(BlogReportRepository.class);
    private final BlogModerationLogRepository logs = mock(BlogModerationLogRepository.class);
    private final BlogPostService postService = mock(BlogPostService.class);
    private final BlogNotifier notifier = mock(BlogNotifier.class);
    private final BlogModerationService service = new BlogModerationService(posts, comments, boxes, reports, logs,
            mock(UserRepository.class), mock(StoryRepository.class), mock(BlogSentenceRepository.class), postService, notifier, Clock.fixed(NOW, ZoneOffset.UTC));

    private final User author = withId(User.of("a@booktimer.com", "h", "글쓴이", "Asia/Seoul", Role.USER), 2L);

    private static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private BlogPost post(long id, String title) {
        BlogPost p = BlogPost.draft(author, title, "문장이다.");
        p.publish(title, "[]", true, NOW);
        return withId(p, id);
    }

    @Test
    @DisplayName("REQ-334 · 운영자 삭제(글) 메일의 제목은 지우기 전 제목")
    void removeMailCarriesOldTitle() {
        BlogPost p = post(5L, "원래 제목");
        when(posts.findForUpdateById(5L)).thenReturn(Optional.of(p));
        service.apply(Unit.POST, 5L, Action.REMOVE, "지운 까닭");
        verify(notifier).poster(author, Action.REMOVE, "글", null, "원래 제목", "지운 까닭");
        verify(postService).wipeContent(p);
        assertThat(p.getTitle()).isNull();
        assertThat(p.removed()).isTrue();
    }

    @Test
    @DisplayName("REQ-339 · 「그대로」 · 400 전이는 기록하지 않는다 · 신고 처리 줄에 report_id")
    void logsOnlyChanges() {
        BlogPost p = post(5L, "제목");
        when(posts.findForUpdateById(5L)).thenReturn(Optional.of(p));
        assertThat(service.apply(Unit.POST, 5L, Action.RESTORE, null)).isFalse(); // 보임 → 다시 보이기 = 그대로
        verify(logs, never()).save(any());
        verify(notifier, never()).poster(any(), any(), any(), any(), any(), any());
        service.apply(Unit.POST, 5L, Action.REMOVE, null);
        assertThatThrownBy(() -> service.apply(Unit.POST, 5L, Action.RESTORE, null)).isInstanceOf(IllegalArgumentException.class);
        verify(logs, times(1)).save(any());

        BlogPost q = post(6L, "다른 글");
        when(posts.findForUpdateById(6L)).thenReturn(Optional.of(q));
        BlogReport r = withId(BlogReport.of(author, Target.POST, 6L, 2L, ReportReason.SPAM, null), 40L);
        when(reports.findById(40L)).thenReturn(Optional.of(r));
        when(reports.findByTargetTypeAndTargetIdAndStatus(Target.POST, 6L, Status.OPEN)).thenReturn(List.of(r));
        service.resolve(40L, Action.TEMP, Unit.POST, 6L, null);
        ArgumentCaptor<BlogModerationLog> line = ArgumentCaptor.forClass(BlogModerationLog.class);
        verify(logs, times(2)).save(line.capture());
        assertThat(line.getAllValues().get(1)).extracting(BlogModerationLog::getUnit, BlogModerationLog::getUnitId,
                BlogModerationLog::getAction, BlogModerationLog::getReportId).containsExactly("POST", 6L, "TEMP", 40L);
    }

    @Test
    @DisplayName("REQ-335 · 처리하면 같은 대상의 OPEN 신고 전부 RESOLVED · 신고자마다 메일(검증된 진짜 주소만)")
    void resolveClosesAllOpenReportsOfTarget() {
        User a = withId(User.of("x@booktimer.com", "h", "갑", "Asia/Seoul", Role.USER), 7L);
        User b = withId(User.of("y@booktimer.com", "h", "을", "Asia/Seoul", Role.USER), 8L);
        BlogReport r1 = withId(BlogReport.of(a, Target.COMMENT, 9L, 2L, ReportReason.SPAM, null), 41L);
        BlogReport r2 = withId(BlogReport.of(b, Target.COMMENT, 9L, 2L, ReportReason.OTHER, null), 42L);
        when(reports.findById(41L)).thenReturn(Optional.of(r1));
        when(reports.findByTargetTypeAndTargetIdAndStatus(Target.COMMENT, 9L, Status.OPEN)).thenReturn(List.of(r1, r2));

        service.resolve(41L, null, null, null, null);

        assertThat(List.of(r1, r2)).allSatisfy(r -> {
            assertThat(r.getStatus()).isEqualTo(Status.RESOLVED);
            assertThat(r.getResult()).isEqualTo("NONE");
            assertThat(r.getResolvedAt()).isEqualTo(NOW);
        });
        verify(notifier).reporter(a, Target.COMMENT, "NONE");
        verify(notifier).reporter(b, Target.COMMENT, "NONE");
        ArgumentCaptor<BlogModerationLog> line = ArgumentCaptor.forClass(BlogModerationLog.class);
        verify(logs).save(line.capture());
        assertThat(line.getValue().getAction()).isEqualTo("NONE");
        assertThat(line.getValue().getReportId()).isEqualTo(41L);
        verify(notifier, never()).poster(any(), any(), any(), any(), any(), isNull());
    }

    @Test
    @DisplayName("REQ-338 · 신고와 무관한 단위로 resolve하면 오류 · 그 신고는 OPEN 그대로")
    void resolveRejectsUnrelatedUnit() {
        BlogReport r = withId(BlogReport.of(author, Target.POST, 6L, 2L, ReportReason.SPAM, null), 43L);
        when(reports.findById(43L)).thenReturn(Optional.of(r));
        QuoteBox foreign = withId(QuoteBox.blog(QuoteBox.Owner.POST, 99L, 1, 6L, 2L, List.of("AAAAAAAA"), 1, "글", "제목", NOW), 70L);
        when(boxes.findById(70L)).thenReturn(Optional.of(foreign));
        assertThatThrownBy(() -> service.resolve(43L, Action.TEMP, Unit.POST, 7L, null))
                .hasMessage("신고 대상과 다른 게시물이에요 — 그 게시물은 id로 처리 폼을 쓰세요");
        assertThatThrownBy(() -> service.resolve(43L, Action.TEMP, Unit.QUOTE, 70L, null)) // 다른 글의 박스
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.resolve(43L, Action.TEMP, Unit.COMMENT, 6L, null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(r.getStatus()).isEqualTo(Status.OPEN);
        verify(posts, never()).findForUpdateById(any());
        verify(notifier, never()).reporter(any(), any(), eq("HIDDEN"));
    }
}
