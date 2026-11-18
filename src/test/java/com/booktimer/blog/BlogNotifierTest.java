package com.booktimer.blog;

import com.booktimer.blog.Moderation.Action;
import com.booktimer.email.EmailDispatcher;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 블로그 알림 메일(B2b · 계획 §4.6 · §6.6 — 스프링 없는 Mockito). 받는 주소 규칙(검증된 진짜 주소만) · 본문 규칙(운영자 메일엔 건수와
 * 링크만 — U-154 ③) · 발송 두 갈래(동기화가 있으면 커밋 뒤 · 없으면 곧바로 — 매일 09:00 스케줄러가 트랜잭션 밖에서 부른다).
 */
class BlogNotifierTest {

    private static final String BASE = "https://booktimer.test";

    private final EmailDispatcher dispatcher = mock(EmailDispatcher.class);
    private final UserRepository users = mock(UserRepository.class);
    private final BlogNotifier notifier = new BlogNotifier(dispatcher, users, BASE);

    @AfterEach
    void closeSync() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static User person(String email, boolean verified) {
        User u = User.of(email, "hash", "닉<b>", "Asia/Seoul", Role.ADMIN);
        if (verified) u.verifyEmail();
        return u;
    }

    private String sentBody() {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(dispatcher).dispatch(anyString(), anyString(), body.capture());
        return body.getValue();
    }

    @Test
    @DisplayName("REQ-336 · 새 신고 메일 본문에 N과 링크만 — 신고자 · 대상 id · 이름 · 사유 · 부가 설명 · 원문 0")
    void newReportBodyIsCountAndLinkOnly() {
        when(users.findByRole(Role.ADMIN)).thenReturn(List.of(person("boss@booktimer.com", true)));
        notifier.newReport(3);
        String link = BASE + "/admin/reports";
        assertThat(sentBody()).isEqualTo("<p>새 신고 3건 — 관리자 화면에서 확인해 주세요</p><p><a href=\"" + link + "\">" + link + "</a></p>");
    }

    @Test
    @DisplayName("REQ-336 · ADMIN이 아니거나 합성 주소인 ADMIN에겐 0통")
    void adminAddressRule() {
        when(users.findByRole(Role.ADMIN)).thenReturn(List.of(person("boss@booktimer.com", true),
                person("apple" + User.SYNTHETIC_EMAIL_DOMAIN, true), person("unverified@booktimer.com", false)));
        notifier.daily(1, 0);
        verify(users).findByRole(Role.ADMIN);
        verify(dispatcher, times(1)).dispatch(eq("boss@booktimer.com"), anyString(), anyString());
        verify(dispatcher, times(1)).dispatch(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("REQ-336 · 트랜잭션 동기화 없이 daily(1, 0) → dispatch 1회")
    void dailyWithoutSyncSendsNow() {
        when(users.findByRole(Role.ADMIN)).thenReturn(List.of(person("boss@booktimer.com", true)));
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
        notifier.daily(1, 0);
        String link = BASE + "/admin/reports";
        assertThat(sentBody()).isEqualTo("<p>열린 신고 1건 · 기간이 끝난 임시조치 0건 — 관리자 화면에서 확인해 주세요</p><p><a href=\""
                + link + "\">" + link + "</a></p>");
    }

    @Test
    @DisplayName("REQ-336 · 동기화를 열고 롤백하면 newReport의 dispatch 0회 · 커밋하면 1회")
    void newReportWaitsForCommit() {
        when(users.findByRole(Role.ADMIN)).thenReturn(List.of(person("boss@booktimer.com", true)));
        TransactionSynchronizationManager.initSynchronization();
        notifier.newReport(1);
        verify(dispatcher, never()).dispatch(anyString(), anyString(), anyString());
        List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
        syncs.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK)); // 롤백 — afterCommit은 불리지 않는다
        verify(dispatcher, never()).dispatch(anyString(), anyString(), anyString());
        syncs.forEach(TransactionSynchronization::afterCommit); // 양성 대조 — 커밋
        verify(dispatcher, times(1)).dispatch(eq("boss@booktimer.com"), anyString(), anyString());
    }

    @Test
    @DisplayName("REQ-334 · 가리기 · 다시 보이기 · 삭제마다 게시자 메일 1통 · 합성 · 미검증 주소엔 0통")
    void posterMailRule() {
        User real = person("poster@booktimer.com", true);
        for (Action a : List.of(Action.TEMP, Action.COPYRIGHT, Action.POLICY, Action.RESTORE, Action.REMOVE)) {
            notifier.poster(real, a, "댓글", Instant.parse("2026-12-31T00:00:00Z"), "제목", null);
        }
        verify(dispatcher, times(5)).dispatch(eq("poster@booktimer.com"), anyString(), anyString());
        notifier.poster(person("apple" + User.SYNTHETIC_EMAIL_DOMAIN, true), Action.TEMP, "글", null, "제목", null);
        notifier.poster(person("unverified@booktimer.com", false), Action.TEMP, "글", null, "제목", null);
        notifier.reporter(person("apple" + User.SYNTHETIC_EMAIL_DOMAIN, true), BlogReport.Target.POST, "NONE");
        verify(dispatcher, times(5)).dispatch(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("REQ-334 · 게시자 메일 본문에 note가 이스케이프돼 실린다")
    void posterNoteEscaped() {
        notifier.poster(person("poster@booktimer.com", true), Action.COPYRIGHT, "글", null, "제목<i>", "요청서 <script>사본</script> & 연락처");
        String body = sentBody();
        assertThat(body).contains("요청서 &lt;script&gt;사본&lt;/script&gt; &amp; 연락처").contains("제목&lt;i&gt;")
                .contains("닉&lt;b&gt;").doesNotContain("<script>");
    }

    @Test
    @DisplayName("REQ-334 · 저작권 중단(COPYRIGHT) 게시자 메일은 「권리자의 복제 · 전송 중단 요구로」 · 정책 가림 메일엔 그 글자 0")
    void copyrightWording() {
        User real = person("poster@booktimer.com", true);
        notifier.poster(real, Action.COPYRIGHT, "인용", null, null, null);
        notifier.poster(real, Action.POLICY, "인용", null, null, null);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(dispatcher, times(2)).dispatch(anyString(), anyString(), body.capture());
        assertThat(body.getAllValues().get(0)).contains("권리자의 복제 · 전송 중단 요구로");
        assertThat(body.getAllValues().get(1)).doesNotContain("권리자").contains("운영 정책으로 가려졌어요");
    }

    @Test
    @DisplayName("REQ-335 · 신고자 메일은 대상 내용 0 · 결과만")
    void reporterBody() {
        notifier.reporter(person("reporter@booktimer.com", true), BlogReport.Target.COMMENT, "HIDDEN");
        assertThat(sentBody()).isEqualTo("<p>신고하신 블로그 댓글을 검토했어요 — 결과: 가렸어요</p>");
        verify(dispatcher).dispatch(eq("reporter@booktimer.com"), any(), any());
    }
}
