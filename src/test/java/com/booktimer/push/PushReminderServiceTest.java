package com.booktimer.push;

import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
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
 * PushReminderService 단위 테스트 (Mockito, 빠른 실행).
 *
 * <p>검증 항목:
 * <ul>
 *   <li>리마인더 대상 사용자 → 구독 전체에 발송 시도</li>
 *   <li>발송 성공 → lastReminderSentAt 기록(멱등 상태 갱신)</li>
 *   <li>발송 EXPIRED → 구독 삭제</li>
 *   <li>발송 대상 0명이면 아무 것도 안 한다</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PushReminderServiceTest {

    @Mock PushSubscriptionRepository subRepo;
    @Mock UserRepository userRepo;
    @Mock PushSenderService senderService;
    @InjectMocks PushReminderService reminderService;

    private final Instant fixedNow = Instant.parse("2026-06-20T11:00:00Z");
    private final Clock fixedClock = Clock.fixed(fixedNow, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        reminderService = new PushReminderService(subRepo, userRepo, senderService, fixedClock);
    }

    private User user(String email) {
        return User.of(email, "$2a$10$abcdefghijklmnopqrstuv", "테스터", "Asia/Seoul", Role.USER);
    }

    private PushSubscription sub(User u) {
        return PushSubscription.of(u, "https://fcm/ep1", "p256", "auth");
    }

    @Test
    @DisplayName("리마인더 대상 사용자가 있으면 모든 구독에 발송하고 lastReminderSentAt을 기록한다")
    void sendReminders_sendsToAllSubscriptionsAndRecordsTimestamp() {
        User u = user("a@test.com");
        u.enableDailyReminder();
        PushSubscription s = sub(u);
        given(subRepo.findReminderTargetUsers(any())).willReturn(List.of(u));
        given(subRepo.findByUser(u)).willReturn(List.of(s));
        given(senderService.send(eq(s), any())).willReturn(PushSenderService.SendResult.OK);

        int sent = reminderService.sendReminders();

        assertThat(sent).isEqualTo(1);
        assertThat(u.getLastReminderSentAt()).isEqualTo(fixedNow);
        verify(userRepo).save(u);
    }

    @Test
    @DisplayName("발송 결과가 EXPIRED 면 구독을 삭제하고 lastReminderSentAt 을 기록하지 않는다")
    void sendReminders_expiredSubscription_deletesAndDoesNotRecordTimestamp() {
        User u = user("b@test.com");
        u.enableDailyReminder();
        PushSubscription s = sub(u);
        given(subRepo.findReminderTargetUsers(any())).willReturn(List.of(u));
        given(subRepo.findByUser(u)).willReturn(List.of(s));
        given(senderService.send(eq(s), any())).willReturn(PushSenderService.SendResult.EXPIRED);

        int sent = reminderService.sendReminders();

        assertThat(sent).isEqualTo(0);
        assertThat(u.getLastReminderSentAt()).isNull();
        verify(subRepo).delete(s);
        verify(userRepo, never()).save(any());
    }

    @Test
    @DisplayName("발송 대상이 0명이면 아무 발송도 없다")
    void sendReminders_noTargets_doesNothing() {
        given(subRepo.findReminderTargetUsers(any())).willReturn(List.of());

        int sent = reminderService.sendReminders();

        assertThat(sent).isEqualTo(0);
        verify(senderService, never()).send(any(), any());
    }

    @Test
    @DisplayName("cutoff 는 now - DEDUP_WINDOW(23h) 로 계산된다")
    void sendReminders_cutoffIsNowMinusDedupWindow() {
        given(subRepo.findReminderTargetUsers(any())).willReturn(List.of());
        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);

        reminderService.sendReminders();

        verify(subRepo).findReminderTargetUsers(cutoffCaptor.capture());
        Instant expectedCutoff = fixedNow.minus(PushReminderService.DEDUP_WINDOW);
        assertThat(cutoffCaptor.getValue()).isEqualTo(expectedCutoff);
    }
}
