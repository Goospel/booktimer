package com.booktimer.blog;

import com.booktimer.blog.BlogReport.Status;
import com.booktimer.blog.BlogReport.Target;
import com.booktimer.security.RateLimitAction;
import com.booktimer.security.RateLimitService;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** 신고의 운영자 알림 — 열린 블로그 신고가 0 → 1일 때만(계획 §4.6 · REQ-252 · 336 — 스프링 없는 Mockito). */
class BlogReportServiceTest {

    private final BlogAccess access = mock(BlogAccess.class);
    private final RateLimitService rateLimit = mock(RateLimitService.class);
    private final BlogReportRepository reports = mock(BlogReportRepository.class);
    private final BlogTargets targets = mock(BlogTargets.class);
    private final BlogNotifier notifier = mock(BlogNotifier.class);
    private final UserRepository users = mock(UserRepository.class);
    private final BlogReportService service = new BlogReportService(access, rateLimit, reports, targets,
            mock(BlogSentenceRepository.class), mock(QuoteBoxRepository.class), notifier, users);

    private final User reporter = withId(User.of("r@booktimer.com", "h", "신고자", "Asia/Seoul", Role.USER), 1L);
    private final User author = withId(User.of("a@booktimer.com", "h", "글쓴이", "Asia/Seoul", Role.USER), 2L);

    private static User withId(User u, long id) {
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    @BeforeEach
    void allow() {
        when(rateLimit.allow(RateLimitAction.REPORT, 1L)).thenReturn(true);
        when(targets.publicAuthor(any(), anyLong())).thenReturn(Optional.of(author));
        when(users.lockById(2L)).thenReturn(Optional.of(author));
    }

    @Test
    @DisplayName("REQ-252 · 열린 신고가 0 → 1일 때 운영자 메일 1통 · 멱등 두 번째 0통")
    void mailOnFirstOpenOnly() {
        when(reports.existsByReporterAndTargetTypeAndTargetId(reporter, Target.POST, 10L)).thenReturn(false, true);
        when(reports.countByStatus(Status.OPEN)).thenReturn(1L);
        service.report(reporter, Target.POST, 10L, "SPAM", "고유글자QZ — 부가 설명");
        service.report(reporter, Target.POST, 10L, "SPAM", "고유글자QZ — 부가 설명");
        verify(reports, times(1)).saveAndFlush(any());
        verify(notifier, times(1)).newReport(1L); // 건수 하나만 넘긴다 — 부가 설명 · 신고자 · 대상이 갈 자리가 없다(U-154 ③)
        verifyNoMoreInteractions(notifier);
    }

    @Test
    @DisplayName("REQ-336 · 처리하지 않은 채 신고 다섯 건 → 메일 1통 · 처리해 0이 된 뒤 새 신고 → 1통 더")
    void mailAgainAfterQueueEmptied() {
        when(reports.countByStatus(Status.OPEN)).thenReturn(1L, 2L, 3L, 4L, 5L, 1L);
        for (long t = 1; t <= 6; t++) {
            service.report(reporter, Target.COMMENT, t, "SPAM", null);
        }
        verify(notifier, times(2)).newReport(1L);
        verify(notifier, never()).newReport(2L);
    }
}
