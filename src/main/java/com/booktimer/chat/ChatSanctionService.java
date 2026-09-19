package com.booktimer.chat;

import com.booktimer.report.ReportRepository;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * 대화 제재 사다리 — 경고 → 7일 정지 → 영구 정지(정책 문서 §3, 약관 제10조 5항).
 *
 * <p>경고는 사용자 상태를 바꾸지 않는다(대화 가능). 기록은 처리한 신고의 {@code resolution}에 남는다.
 * 정지·영구는 {@link User}의 V94 컬럼이고 {@link ChatEligibility}가 매 요청 읽는다 — 새 방·발송이 막히고
 * 그 사람과의 방은 양쪽 모두 읽기 전용이 된다.
 *
 * <p><b>자동은 하나뿐이다</b>: 서로 다른 신고자 2명이 대화방에서 신고했고 둘 다 미처리면 7일 정지
 * (사용자 확정 Q3 — 운영자 부재 시 최소 안전장치). 나머지는 전부 운영자가 고른다.
 */
@Service
@Transactional
public class ChatSanctionService {

    static final Duration SUSPENSION = Duration.ofDays(7);
    static final int AUTO_REPORTERS = 2;

    public enum Action {
        /** 무혐의 — 처리만 닫는다. */
        NONE,
        /** 경고 — 대화 가능, 기록만 남는다. */
        WARN,
        /** 지금부터 7일 정지(이미 더 길게 걸려 있으면 그대로). */
        SUSPEND_7D,
        /** 남은 정지 끝(없으면 지금)에서 7일 더. */
        EXTEND_7D,
        /** 영구 정지. */
        BAN,
        /** 정지·영구 모두 해제(이의 제기 인용). */
        LIFT
    }

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public ChatSanctionService(ReportRepository reportRepository, UserRepository userRepository, Clock clock) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    public void apply(User target, Action action) {
        Instant now = clock.instant();
        switch (action) {
            case NONE, WARN -> { }
            case SUSPEND_7D -> target.restrictChatUntil(later(target.getChatRestrictedUntil(), now.plus(SUSPENSION)));
            case EXTEND_7D -> target.restrictChatUntil(later(target.getChatRestrictedUntil(), now).plus(SUSPENSION));
            case BAN -> target.banChat(now);
            case LIFT -> {
                target.restrictChatUntil(null);
                target.banChat(null);
            }
        }
        userRepository.save(target);
    }

    /**
     * 대화방 신고가 들어온 직후 — 미처리 대화방 신고의 서로 다른 신고자가 2명 이상이고 지금 제재 중이 아니면
     * 7일 정지한다. 이미 정지·영구 중이면 건드리지 않는다(운영자가 건 더 긴 조치를 줄이지 않는다).
     *
     * @return 이번에 자동 정지를 걸었으면 true
     */
    // ponytail: 판정은 잠금 없는 카운트다. REPEATABLE READ에서 서로 다른 신고자의 신고 2건이 동시에 커밋되면 두
    // 트랜잭션이 서로의 행을 못 봐 둘 다 「1명」으로 세고 자동 정지를 놓칠 수 있다. 1인 운영·DAU 한 자릿수에선 드물고
    // 운영자 배너가 남는다. 업그레이드 경로: 판정 전에 대상 users 행을 {@code select … for update}로 잠가 직렬화한다.
    public boolean onChatReport(User reported) {
        if (reported.isChatRestricted(clock.instant())) {
            return false;
        }
        if (reportRepository.countOpenChatReporters(reported) < AUTO_REPORTERS) {
            return false;
        }
        apply(reported, Action.SUSPEND_7D);
        return true;
    }

    /** 관리자 배너 — 지금 대화 제재 중인 사용자 수(영구 + 끝나지 않은 정지). */
    @Transactional(readOnly = true)
    public long sanctionedCount() {
        return userRepository.countChatSanctioned(clock.instant());
    }

    private static Instant later(Instant a, Instant b) {
        return a == null || a.isBefore(b) ? b : a;
    }
}
