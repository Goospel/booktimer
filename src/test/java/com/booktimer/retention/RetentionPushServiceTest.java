package com.booktimer.retention;

import com.booktimer.push.PushSenderService;
import com.booktimer.push.PushSubscription;
import com.booktimer.push.PushSubscriptionRepository;
import com.booktimer.session.ReadingSessionRepository;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * RetentionPushService 단위 테스트 (Mockito, 빠른 실행 — PWA L3b).
 *
 * <p>검증 항목:
 * <ul>
 *   <li>발송 대상 → 구독 전체에 발송 + marketingPushNudgeSentAt 기록(멱등)</li>
 *   <li>EXPIRED → 구독 삭제, 멱등 시각 미기록</li>
 *   <li>EXPIRED + OK 혼재 → 배치 중단 없음, OK 구독은 성공·기록</li>
 *   <li>대상 0명 → 아무 발송 없음</li>
 *   <li>cutoff = now - INACTIVITY_WINDOW(7d)</li>
 *   <li>§50: payload title에 "(광고)" 포함</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class RetentionPushServiceTest {

    @Mock ReadingSessionRepository sessionRepo;
    @Mock PushSubscriptionRepository subRepo;
    @Mock UserRepository userRepo;
    @Mock PushSenderService senderService;

    private final Instant fixedNow = Instant.parse("2026-06-20T10:00:00Z");
    private final Clock fixedClock = Clock.fixed(fixedNow, ZoneOffset.UTC);

    private RetentionPushService service;

    @BeforeEach
    void setUp() {
        service = new RetentionPushService(sessionRepo, subRepo, userRepo, senderService, fixedClock);
    }

    private User user(String email) {
        return User.of(email, "$2a$10$abcdefghijklmnopqrstuv", "테스터", "Asia/Seoul", Role.USER);
    }

    private PushSubscription sub(User u, String ep) {
        return PushSubscription.of(u, ep, "p256", "auth");
    }

    @Test
    @DisplayName("발송 대상 유저 → 구독 전체에 발송하고 marketingPushNudgeSentAt을 기록한다(멱등)")
    void sendNudges_sendsToAllSubscriptionsAndRecordsTimestamp() {
        User u = user("a@test.com");
        u.consentToMarketingPush(fixedClock);
        PushSubscription s = sub(u, "https://fcm/ep1");
        given(sessionRepo.findRetentionPushTargets(any())).willReturn(List.of(u));
        given(subRepo.findByUser(u)).willReturn(List.of(s));
        given(senderService.send(eq(s), any())).willReturn(PushSenderService.SendResult.OK);

        int sent = service.sendNudges();

        assertThat(sent).isEqualTo(1);
        assertThat(u.getMarketingPushNudgeSentAt()).isEqualTo(fixedNow);
        verify(userRepo).save(u);
    }

    @Test
    @DisplayName("발송 결과 EXPIRED → 구독 삭제, marketingPushNudgeSentAt 기록 안 함(anySent=false)")
    void sendNudges_expiredSubscription_deletesAndDoesNotRecord() {
        User u = user("b@test.com");
        u.consentToMarketingPush(fixedClock);
        PushSubscription s = sub(u, "https://fcm/ep2");
        given(sessionRepo.findRetentionPushTargets(any())).willReturn(List.of(u));
        given(subRepo.findByUser(u)).willReturn(List.of(s));
        given(senderService.send(eq(s), any())).willReturn(PushSenderService.SendResult.EXPIRED);

        int sent = service.sendNudges();

        assertThat(sent).isEqualTo(0);
        assertThat(u.getMarketingPushNudgeSentAt()).isNull();
        verify(subRepo).delete(s);
        verify(userRepo, never()).save(any());
    }

    @Test
    @DisplayName("EXPIRED + OK 혼재 → EXPIRED 구독만 삭제, OK 구독은 성공·멱등 기록 — 배치 중단 없음")
    void sendNudges_mixedExpiredAndOk_continuesAndRecordsOnSuccess() {
        User u = user("c@test.com");
        u.consentToMarketingPush(fixedClock);
        PushSubscription expSub = sub(u, "https://fcm/expired");
        PushSubscription okSub = sub(u, "https://fcm/ok");
        given(sessionRepo.findRetentionPushTargets(any())).willReturn(List.of(u));
        given(subRepo.findByUser(u)).willReturn(List.of(expSub, okSub));
        given(senderService.send(eq(expSub), any())).willReturn(PushSenderService.SendResult.EXPIRED);
        given(senderService.send(eq(okSub), any())).willReturn(PushSenderService.SendResult.OK);

        int sent = service.sendNudges();

        assertThat(sent).isEqualTo(1);
        assertThat(u.getMarketingPushNudgeSentAt()).isEqualTo(fixedNow);
        verify(subRepo).delete(expSub);
        verify(userRepo).save(u);
    }

    @Test
    @DisplayName("발송 대상 0명이면 아무 발송도 없다")
    void sendNudges_noTargets_doesNothing() {
        given(sessionRepo.findRetentionPushTargets(any())).willReturn(List.of());

        int sent = service.sendNudges();

        assertThat(sent).isEqualTo(0);
        verify(senderService, never()).send(any(), any());
    }

    @Test
    @DisplayName("cutoff는 now - INACTIVITY_WINDOW(7d)로 계산된다")
    void sendNudges_cutoffIsNowMinusInactivityWindow() {
        given(sessionRepo.findRetentionPushTargets(any())).willReturn(List.of());
        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);

        service.sendNudges();

        verify(sessionRepo).findRetentionPushTargets(captor.capture());
        assertThat(captor.getValue()).isEqualTo(fixedNow.minus(RetentionPushService.INACTIVITY_WINDOW));
    }

    @Test
    @DisplayName("§50: payload title에 '(광고)'가 포함된다")
    void sendNudges_payloadContainsAdLabel() {
        User u = user("d@test.com");
        u.consentToMarketingPush(fixedClock);
        PushSubscription s = sub(u, "https://fcm/ep4");
        given(sessionRepo.findRetentionPushTargets(any())).willReturn(List.of(u));
        given(subRepo.findByUser(u)).willReturn(List.of(s));
        given(senderService.send(any(), any())).willReturn(PushSenderService.SendResult.OK);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

        service.sendNudges();

        verify(senderService).send(eq(s), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue()).contains("(광고)");
    }
}
