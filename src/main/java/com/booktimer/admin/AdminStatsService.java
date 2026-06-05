package com.booktimer.admin;

import com.booktimer.book.BookRepository;
import com.booktimer.session.ReadingSessionRepository;
import com.booktimer.user.Role;
import com.booktimer.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * 운영자 대시보드 통계 요약 유스케이스 (관리자 대시보드 PR-B, plan.md "통계 요약").
 *
 * <p><b>읽기 전용 집계</b>(N-037)다 — 매번 RDS에 직접 붙지 않고도 운영 지표를 웹에서 보려는 목적.
 * 기존 테이블을 count/sum 으로 요약할 뿐 새 데이터를 저장하지 않으므로 스키마(Flyway)를 건드리지 않는다.
 *
 * <p>"가입자 수"는 {@link Role#USER}만 센다 — 시드로 승격된 운영자(ADMIN)가 지표를 부풀리지 않게 한다.
 * "활성 사용자"는 최근 {@link #ACTIVE_WINDOW_DAYS}일 내 측정(세션)이 있는 distinct 사용자다("지금"은
 * 주입된 {@link Clock} 기준 — 테스트 결정성).
 */
@Service
@Transactional(readOnly = true)
public class AdminStatsService {

    /** "활성 사용자" 집계 창(일) — 최근 이 기간 안에 측정한 사용자를 활성으로 본다. */
    static final int ACTIVE_WINDOW_DAYS = 7;

    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final ReadingSessionRepository sessionRepository;
    private final Clock clock;

    public AdminStatsService(UserRepository userRepository,
                             BookRepository bookRepository,
                             ReadingSessionRepository sessionRepository,
                             Clock clock) {
        this.userRepository = userRepository;
        this.bookRepository = bookRepository;
        this.sessionRepository = sessionRepository;
        this.clock = clock;
    }

    /** 운영 통계 한 장(읽기 전용 집계 스냅샷)을 조립한다. */
    public AdminStats summary() {
        long totalUsers = userRepository.countByRole(Role.USER);
        long onboardedUsers = userRepository.countByRoleAndOnboarded(Role.USER, true);

        Instant since = clock.instant().minus(Duration.ofDays(ACTIVE_WINDOW_DAYS));
        long activeUsers = sessionRepository.countActiveUsersSince(since);

        long totalBooks = bookRepository.count();
        long totalSessions = sessionRepository.count();
        long totalReadingSeconds = sessionRepository.sumAllDurationSeconds();
        // 가입자 0명이면 0으로 — 0 나눗셈 회피.
        long avgReadingSecondsPerUser = totalUsers == 0 ? 0 : totalReadingSeconds / totalUsers;

        return new AdminStats(
                totalUsers,
                onboardedUsers,
                activeUsers,
                ACTIVE_WINDOW_DAYS,
                totalBooks,
                totalSessions,
                totalReadingSeconds,
                avgReadingSecondsPerUser);
    }
}
