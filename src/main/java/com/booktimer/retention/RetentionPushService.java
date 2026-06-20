package com.booktimer.retention;

import com.booktimer.push.PushSenderService;
import com.booktimer.push.PushSubscription;
import com.booktimer.push.PushSubscriptionRepository;
import com.booktimer.session.ReadingSessionRepository;
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
 * 비활동 복귀 nudge 푸시 발송 배치 (PWA L3b).
 *
 * <p>7일 이상 비활동 + marketingPushConsent + 구독 보유 사용자에게 §50 광고성 Web Push를 보낸다.
 * 이메일 RetentionNudgeService와 <b>완전 독립</b> — 이메일 코드 무손상, 멱등 필드도 별도.
 *
 * <p>멱등: 성공 발송(anySent=true) 시에만 {@code recordPushNudgeSent}를 기록. per-recipient
 * try-catch 격리(SendResult.ERROR는 무시하고 배치 계속).
 */
@Service
public class RetentionPushService {

    private static final Logger log = LoggerFactory.getLogger(RetentionPushService.class);

    /** 이메일 nudge(RetentionNudgeService)와 동일한 비활동 창. */
    static final Duration INACTIVITY_WINDOW = Duration.ofDays(7);

    /** §50 의무: title 맨 앞에 "(광고)" 표기. */
    private static final String PAYLOAD =
            "{\"title\":\"(광고) 독서 마을\",\"body\":\"오랜만이에요! 오늘 책 한 장 어떠세요? \\uD83D\\uDCDA\",\"url\":\"/village\"}";

    private final ReadingSessionRepository sessionRepository;
    private final PushSubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final PushSenderService senderService;
    private final Clock clock;

    public RetentionPushService(ReadingSessionRepository sessionRepository,
                                PushSubscriptionRepository subscriptionRepository,
                                UserRepository userRepository,
                                PushSenderService senderService,
                                Clock clock) {
        this.sessionRepository = sessionRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.senderService = senderService;
        this.clock = clock;
    }

    /**
     * 비활동 복귀 nudge 푸시 발송 배치. {@link RetentionPushScheduler}가 KST 19시에 호출.
     *
     * @return 실제 발송에 성공한 구독 수(사용자 × 디바이스)
     */
    @Transactional
    public int sendNudges() {
        Instant now = clock.instant();
        Instant cutoff = now.minus(INACTIVITY_WINDOW);
        List<User> targets = sessionRepository.findRetentionPushTargets(cutoff);
        int successCount = 0;

        for (User user : targets) {
            boolean anySent = false;
            for (PushSubscription sub : subscriptionRepository.findByUser(user)) {
                PushSenderService.SendResult result = senderService.send(sub, PAYLOAD);
                if (result == PushSenderService.SendResult.OK) {
                    sub.recordSuccess(now);
                    successCount++;
                    anySent = true;
                } else if (result == PushSenderService.SendResult.EXPIRED) {
                    subscriptionRepository.delete(sub);
                    log.info("만료 구독 삭제 — userId={}, endpoint={}", user.getId(), sub.getEndpoint());
                }
            }
            if (anySent) {
                user.recordPushNudgeSent(now);
                userRepository.save(user);
            }
        }
        log.info("비활동 복귀 nudge 푸시 배치 완료 — 대상 {}명, 발송 성공 구독 {}건", targets.size(), successCount);
        return successCount;
    }
}
