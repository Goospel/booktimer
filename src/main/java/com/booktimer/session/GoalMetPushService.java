package com.booktimer.session;

import com.booktimer.config.TossProperties;
import com.booktimer.toss.TossMessengerClient;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

/**
 * "오늘 목표 달성" 푸시 감지 — <b>읽고 있는 도중</b> 오늘 누적이 하루 목표를 넘는 순간 토스 앱으로 한 통 보낸다.
 *
 * <p>왜 폴링인가: 토스 푸시 API엔 예약 발송이 없고, 달성 순간은 사용자가 아무 행동도 하지 않는 시점이라
 * 요청 경로에 걸 훅이 없다. 그래서 스케줄러({@link GoalMetPushScheduler})가 분마다 이 메서드를 부르고,
 * 여기서 활성 세션만 훑어 조건을 본다 — 지연 상한은 폴링 주기(약 1분)다.
 *
 * <p><b>날짜 귀속</b>은 {@link ReadingHistoryService}와 같은 규칙이다: 세션은 {@code startedAt}의 유저 TZ 날짜에
 * 속한다. 그래서 어제 시작해 아직 도는 세션의 경과는 오늘 누적에 넣지 않는다 — 넣으면 자정을 넘겨 읽던 사람에게
 * 새벽에 오발송된다.
 *
 * <p><b>멱등</b>은 {@link User#markGoalMetPushed}(유저 TZ 날짜)가 보장한다. <b>발송에 성공한 경우에만</b>
 * 마킹하므로 실패한 사람은 다음 틱에 다시 시도된다(성공 후 마킹 저장이 깨지면 중복 1통 — 축하 문구뿐이라 허용).
 * 한 사람의 실패가 배치를 멈추지 않도록 per-user try/catch로 격리한다({@code RetentionNudgeService}와 같은 계약).
 */
@Service
public class GoalMetPushService {

    private static final Logger log = LoggerFactory.getLogger(GoalMetPushService.class);

    private final ReadingSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final ReadingDebtService debtService;
    /** 게이트 OFF(다크런치)면 빈이 없다 — 그래도 앱 컨텍스트는 떠야 하므로 Optional 주입(완독 축하와 동일). */
    private final Optional<TossMessengerClient> client;
    private final TossProperties properties;
    private final Clock clock;

    public GoalMetPushService(ReadingSessionRepository sessionRepository,
                              UserRepository userRepository,
                              ReadingDebtService debtService,
                              Optional<TossMessengerClient> client,
                              TossProperties properties,
                              Clock clock) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.debtService = debtService;
        this.client = client;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 지금 읽고 있는 사람들 중 오늘 목표를 막 채운 사람에게 푸시를 보낸다(스케줄러가 분마다 호출).
     *
     * <p>ponytail: 배치 전체가 한 트랜잭션이다 — 동시 활성 세션이 몇 개 수준인 현 규모에선 충분하다.
     * 대상이 커지면 per-user 트랜잭션으로 분리한다(장기 트랜잭션 천장).
     */
    @Transactional
    public void detectAndPush() {
        String templateCode = properties.getMessenger().getGoalMetTemplateCode();
        if (templateCode == null || templateCode.isBlank()) {
            return; // 콘솔 검수 전 다크런치 — 마킹도 하지 않아 점등 후 그날 바로 첫 발송이 나간다
        }
        if (client.isEmpty()) {
            return; // 메신저 게이트 OFF
        }
        Instant now = clock.instant();
        for (ReadingSession session : sessionRepository.findByEndedAtIsNull()) {
            try {
                pushIfGoalMet(session, now, templateCode.strip());
            } catch (RuntimeException e) {
                // 한 명 실패가 배치를 멈추지 않게 격리 — userKey가 새지 않게 식별자만 로그.
                log.warn("목표 달성 푸시 실패(나머지는 계속) — sessionId={}: {}", session.getId(), e.toString());
            }
        }
    }

    private void pushIfGoalMet(ReadingSession session, Instant now, String templateCode) {
        User user = session.getUser();
        String userKey = user.getTossUserKey();
        if (userKey == null) {
            return; // 웹 전용 사용자 — 토스 채널 없음
        }
        LocalDate today = debtService.today(user);
        if (today.equals(user.getGoalMetPushedOn())) {
            return; // 오늘 이미 보냈다(하루 1회)
        }
        long goalSeconds = debtService.todayGoalSeconds(user);
        if (goalSeconds <= 0) {
            return; // 목표 미설정 — '달성' 알림은 스팸이다
        }
        if (accumulatedTodaySeconds(user, session, today, now) < goalSeconds) {
            return;
        }
        if (client.get().sendMessage(userKey, templateCode, Map.of())) {
            user.markGoalMetPushed(today); // 성공한 사람만 마킹 — 실패는 다음 틱 재시도
            userRepository.save(user);
        }
    }

    /** 오늘 읽은 초 = 오늘 시작해 끝난 세션들의 합 + (이 활성 세션이 오늘 시작했으면) 지금까지의 경과. */
    private long accumulatedTodaySeconds(User user, ReadingSession session, LocalDate today, Instant now) {
        ZoneId zone = ZoneId.of(user.getTimezone());
        Instant dayStart = today.atStartOfDay(zone).toInstant();
        Instant nextDayStart = today.plusDays(1).atStartOfDay(zone).toInstant();

        long seconds = sessionRepository.sumCompletedSeconds(user, dayStart, nextDayStart);
        if (!session.getStartedAt().isBefore(dayStart)) {
            seconds += Duration.between(session.getStartedAt(), now).getSeconds();
        }
        return seconds;
    }
}
