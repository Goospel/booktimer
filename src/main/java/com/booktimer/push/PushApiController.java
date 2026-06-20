package com.booktimer.push;

import com.booktimer.security.CurrentUserService;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/**
 * Web Push 구독 관리 API (PWA L3a).
 *
 * <p>{@code /api/**} 는 SecurityConfig default-deny 에 의해 자동으로 인증 보호된다.
 * <ul>
 *   <li>POST /api/push/subscribe — 구독 등록(upsert: 같은 endpoint 면 키 갱신, 없으면 신규)</li>
 *   <li>POST /api/push/unsubscribe — 구독 삭제(본인 것만 — IDOR 차단). 구독 0개 시 리마인더 비활성.</li>
 *   <li>GET  /api/push/public-key — VAPID 공개키 반환(클라이언트 subscribe에 필요)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/push")
public class PushApiController {

    record SubscribeRequest(String endpoint, String p256dh, String auth) {}
    record UnsubscribeRequest(String endpoint) {}

    private final CurrentUserService currentUserService;
    private final PushSubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final String vapidPublicKey;

    public PushApiController(CurrentUserService currentUserService,
                             PushSubscriptionRepository subscriptionRepository,
                             UserRepository userRepository,
                             @Value("${booktimer.push.vapid.public-key:not-configured}") String vapidPublicKey) {
        this.currentUserService = currentUserService;
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.vapidPublicKey = vapidPublicKey;
    }

    /** VAPID 공개키 반환 — 클라이언트가 pushManager.subscribe() 호출 시 applicationServerKey로 사용. */
    @GetMapping("/public-key")
    public String getPublicKey() {
        return vapidPublicKey;
    }

    /**
     * 구독 등록(upsert). 같은 endpoint 가 이미 있으면 p256dh·auth 갱신, 없으면 신규 생성.
     * 성공 시 dailyReminderEnabled=true 로 opt-in.
     */
    @PostMapping("/subscribe")
    public ResponseEntity<Void> subscribe(Principal principal, @RequestBody SubscribeRequest req) {
        User user = currentUserService.resolve(principal);
        subscriptionRepository.findByEndpoint(req.endpoint())
                .ifPresentOrElse(
                        existing -> existing.updateKeys(req.p256dh(), req.auth()),
                        () -> subscriptionRepository.save(
                                PushSubscription.of(user, req.endpoint(), req.p256dh(), req.auth()))
                );
        user.enableDailyReminder();
        userRepository.save(user);
        return ResponseEntity.ok().build();
    }

    /**
     * 구독 삭제 — <b>본인 소유 구독만</b> 삭제한다(IDOR 차단: user + endpoint 동시 조회).
     * 구독이 0개가 되면 dailyReminderEnabled=false 로 opt-out.
     */
    @PostMapping("/unsubscribe")
    public ResponseEntity<Void> unsubscribe(Principal principal, @RequestBody UnsubscribeRequest req) {
        User user = currentUserService.resolve(principal);
        subscriptionRepository.findByUserAndEndpoint(user, req.endpoint())
                .ifPresent(subscriptionRepository::delete);
        if (subscriptionRepository.countByUser(user) == 0) {
            user.disableDailyReminder();
            userRepository.save(user);
        }
        return ResponseEntity.ok().build();
    }
}
