package com.booktimer.email;

import com.booktimer.user.AuthProvider;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** 아이폰 앱 연결 알림 메일(v1 REQ-42) — 연결 코드가 샜을 때 주인이 알아채게 하는 한 통. */
class AppLinkNotificationServiceTest {

    private final EmailDispatcher dispatcher = mock(EmailDispatcher.class);
    private final AppLinkNotificationService service = new AppLinkNotificationService(dispatcher,
            "https://booktimer.app", Clock.fixed(Instant.parse("2026-10-21T05:30:00Z"), ZoneOffset.UTC));

    private static User google(String email, boolean verified) {
        User u = User.ofOAuth(email, "<b>독자</b>", "Asia/Seoul", Role.USER, AuthProvider.GOOGLE);
        if (verified) {
            u.verifyEmail();
        }
        return u;
    }

    @Test
    @DisplayName("REQ-42 · 검증된 실제 이메일 계정에 LINK하면 알림 1통")
    void verifiedRealEmailGetsOne() {
        service.notifyLinked(google("owner@gmail.com", true));

        verify(dispatcher).dispatch(eq("owner@gmail.com"), eq("북타이머 — 아이폰 앱 「책생글」이 이 계정에 연결됐어요"),
                contains("2026-10-21 14:30"));
        verify(dispatcher).dispatch(any(), any(), contains("https://booktimer.app/settings#app-link"));
        verify(dispatcher).dispatch(any(), any(), contains("본인이 아니면"));
        verify(dispatcher).dispatch(any(), any(), contains("&lt;b&gt;독자&lt;/b&gt;")); // 닉네임은 이스케이프한다
    }

    @Test
    @DisplayName("REQ-42 · 합성·미검증 이메일이면 0통")
    void syntheticOrUnverifiedGetsNone() {
        service.notifyLinked(google("unverified@gmail.com", false));
        // 검증 표시가 있어도 합성 주소면 보내지 않는다 — 두 조건을 따로 잰다
        service.notifyLinked(google("toss-uk1@noreply.booktimer.app", true));

        verify(dispatcher, never()).dispatch(any(), any(), any());
    }
}
