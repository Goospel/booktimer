package com.booktimer.push;

import com.booktimer.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    List<PushSubscription> findByUser(User user);

    /** 특정 사용자의 endpoint 구독 조회 — 본인 것인지 확인 후 삭제할 때 사용(IDOR 차단). */
    Optional<PushSubscription> findByUserAndEndpoint(User user, String endpoint);

    long countByUser(User user);

    /**
     * 리마인더 발송 대상 사용자 조회 —
     * daily_reminder_enabled=true AND 구독 보유 AND 오늘 아직 발송 안 한 사용자.
     *
     * <p>cutoff = now - 23h: 같은 날 두 번 발송 방지(하루 1회 멱등).
     */
    @Query("SELECT DISTINCT p.user FROM PushSubscription p " +
           "WHERE p.user.dailyReminderEnabled = true " +
           "AND (p.user.lastReminderSentAt IS NULL OR p.user.lastReminderSentAt < :cutoff)")
    List<User> findReminderTargetUsers(@Param("cutoff") Instant cutoff);
}
