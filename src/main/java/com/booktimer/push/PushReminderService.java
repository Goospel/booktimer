package com.booktimer.push;

import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 매일 독서 리마인더 푸시 발송 오케스트레이션 (PWA L3a).
 *
 * <p>daily_reminder_enabled=true & 구독 보유 & 오늘 아직 발송 안 한 사용자의 모든 구독으로
 * 리마인더를 보낸다. 발송 404/410 응답 → 만료 구독 자동 삭제. 하루 1회 멱등.
 *
 * <p><b>비활동 nudge 조건(L3b)은 일체 포함하지 않는다</b> — 본인 opt-in 리마인더(L3a)만.
 */
@Service
public class PushReminderService {

    private static final Logger log = LoggerFactory.getLogger(PushReminderService.class);

    /** 하루 1회 멱등 창 — 이 시간 이내에 발송한 사용자는 재발송 제외. */
    static final Duration DEDUP_WINDOW = Duration.ofHours(23);

    private static final String PAYLOAD_TEMPLATE =
            "{\"title\":\"독서 서재\",\"body\":\"%s\",\"url\":\"/village\"}";
    private static final String REMINDER_BODY = "오늘도 책 한 장 읽어볼까요? 📚";

    private final PushSubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final PushSenderService senderService;
    private final Clock clock;

    public PushReminderService(PushSubscriptionRepository subscriptionRepository,
                               UserRepository userRepository,
                               PushSenderService senderService,
                               Clock clock) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.senderService = senderService;
        this.clock = clock;
    }

    /**
     * 리마인더 발송 배치. {@link PushReminderScheduler}가 매일 KST 20시에 호출.
     *
     * @return 실제 발송에 성공한 구독 수(사용자×디바이스)
     */
    @Transactional
    public int sendReminders() {
        Instant now = clock.instant();
        Instant cutoff = now.minus(DEDUP_WINDOW);
        List<User> targets = subscriptionRepository.findReminderTargetUsers(cutoff);
        String payload = PAYLOAD_TEMPLATE.formatted(REMINDER_BODY);
        int successCount = 0;

        for (User user : targets) {
            List<PushSubscription> subs = subscriptionRepository.findByUser(user);
            boolean anySent = false;
            for (PushSubscription sub : subs) {
                PushSenderService.SendResult result = senderService.send(sub, payload);
                if (result == PushSenderService.SendResult.OK) {
                    sub.recordSuccess(now);
                    successCount++;
                    anySent = true;
                } else if (result == PushSenderService.SendResult.EXPIRED) {
                    // 만료된 구독 삭제
                    subscriptionRepository.delete(sub);
                    log.info("만료 구독 삭제 — userId={}, endpoint={}",
                            user.getId(), sub.getEndpoint());
                }
            }
            if (anySent) {
                user.recordReminderSent(now);
                userRepository.save(user);
            }
        }
        log.info("독서 리마인더 배치 완료 — 대상 {}명, 발송 성공 구독 {}건", targets.size(), successCount);
        return successCount;
    }
}
