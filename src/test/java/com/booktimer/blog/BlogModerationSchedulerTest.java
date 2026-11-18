package com.booktimer.blog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 매일 09:00 KST 요약(계획 §4.6 · 사용자 결정 2 — 기간 끝난 임시조치는 운영자가 확인해 지운다). */
class BlogModerationSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-12-05T00:00:00Z");

    private final BlogModerationService service = mock(BlogModerationService.class);
    private final BlogNotifier notifier = mock(BlogNotifier.class);
    private final BlogModerationScheduler scheduler = new BlogModerationScheduler(service, notifier, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("REQ-333 · 매일 요약 — 열린 신고 · 만료 임시조치 가운데 하나라도 1건 이상이면 운영자 메일 1통 · 둘 다 0이면 0통")
    void dailySummary() {
        when(service.openCount()).thenReturn(0L, 2L, 0L);
        when(service.expiredCount(NOW)).thenReturn(0L, 0L, 3L);
        scheduler.run();
        verify(notifier, never()).daily(anyLong(), anyLong());
        scheduler.run();
        verify(notifier).daily(2L, 0L);
        scheduler.run();
        verify(notifier).daily(0L, 3L);
    }
}
