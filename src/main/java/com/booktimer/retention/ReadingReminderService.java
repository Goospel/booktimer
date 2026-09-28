package com.booktimer.retention;

import com.booktimer.config.TossProperties;
import com.booktimer.session.ReadingSessionRepository;
import com.booktimer.session.StudySessionRepository;
import com.booktimer.toss.TossMessengerClient;
import com.booktimer.user.ReadingReminderKind;
import com.booktimer.user.TossLinkConflictException;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 사용자가 직접 켜는 독서 알림(N3) — 설정 조회·저장과 매시 정각 배치. 규칙의 단일 출처는 {@link #dueThreshold}다.
 *
 * <p>켠 사람에게만, 그 사람이 고른 현지 시각에, 하루 최대 1통. 동의는 토스가 정본이고 서버는 사용자가 고른
 * 설정만 안다 — 미니앱은 동의 성공 뒤에만 켬을 저장한다. 광고성으로 판정이 뒤집히면 문구 보정이 아니라
 * SSM 토글 소등이 유일한 대응이다.
 */
@Service
public class ReadingReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReadingReminderService.class);

    /** 쉬는 날수 — 고정. ponytail: 3일 고정. 선택지는 요청이 오면 붙인다(동의문이 「며칠째」라 새 템플릿만 더하면 된다). */
    static final int REST_DAYS = 3;

    /**
     * 미니앱이 받는 모양 — 대시보드와 저장 응답이 같이 쓴다.
     *
     * @param available     토글 ∧ 발송 클라이언트 ∧ 두 템플릿 코드 ∧ 토스 연동 — 아니면 화면이 통째로 숨는다
     * @param everOn        한 번이라도 켰다({@code reading_reminder_on_at} 유무) — 홈 제안 제외의 정본
     * @param agreementCode 가용일 때만 「3일 쉬면」 템플릿 코드(카드가 권하는 방식). 두 템플릿이 같은 동의문이라
     *                      어느 코드로 요청해도 같다
     */
    public record View(boolean available, String kind, int hour, boolean everOn, String agreementCode) {}

    private final UserRepository userRepository;
    private final ReadingSessionRepository sessionRepository;
    private final StudySessionRepository studySessionRepository;
    /** 메신저 게이트 OFF(다크런치)면 빈이 없다 — Optional 주입(다른 토스 푸시와 동일). */
    private final Optional<TossMessengerClient> client;
    private final TossProperties properties;
    private final Clock clock;

    public ReadingReminderService(UserRepository userRepository,
                                  ReadingSessionRepository sessionRepository,
                                  StudySessionRepository studySessionRepository,
                                  Optional<TossMessengerClient> client,
                                  TossProperties properties,
                                  Clock clock) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.studySessionRepository = studySessionRepository;
        this.client = client;
        this.properties = properties;
        this.clock = clock;
    }

    public View view(User user) {
        TossProperties.Messenger m = properties.getMessenger();
        boolean available = m.isReadingReminderEnabled() && client.isPresent()
                && !isBlank(m.getReadingReminderDailyTemplateCode()) && !isBlank(m.getReadingReminderRestTemplateCode())
                && user.getTossUserKey() != null;
        return new View(available, user.getReadingReminderKind().name(), user.getReadingReminderHour(),
                user.getReadingReminderOnAt() != null,
                available ? m.getReadingReminderRestTemplateCode().strip() : null);
    }

    /**
     * 설정을 통째로 저장한다. 가용이 아니어도 받는다(설정은 선호일 뿐 — 화면이 숨으니 실사용 경로는 없다).
     *
     * @throws TossLinkConflictException 토스 미연결 계정(→ 409)
     * @throws IllegalArgumentException  방식이 없거나 모르는 이름이거나 시각이 범위 밖(→ 400)
     */
    @Transactional
    public View configure(User user, String kind, int hour) {
        if (user.getTossUserKey() == null) {
            throw new TossLinkConflictException("토스 앱에 연결되지 않은 계정입니다");
        }
        if (kind == null) {
            throw new IllegalArgumentException("kind is required"); // valueOf(null)은 NPE라 400이 아니라 500이 된다
        }
        user.configureReadingReminder(ReadingReminderKind.valueOf(kind), hour, clock.instant()); // 모르는 이름 → IAE
        userRepository.save(user);
        return view(user);
    }

    /**
     * 매시 배치 — 이번 정각에 차례인 사람에게 보낸다. 발송 수를 돌려준다.
     *
     * <p><b>배치 트랜잭션이 없다</b>({@code StudyGoalPushService.detectAndPush}와 같은 이유): 선점 행 락이 뒤 사람들의
     * HTTP 동안 잡히지 않게, 한 명의 DB 예외가 다른 사람들의 선점을 롤백하지 않게.
     *
     * <p>ponytail: 켠 사람 전원을 매시 불러 자바에서 현지 시를 거른다(현지 시는 zone마다 달라 SQL로 못 거른다) —
     * 수천 명이면 시각별 인덱스 컬럼으로 승격.
     * <p>ponytail: 스케줄러 스레드가 1개라 이 배치가 도는 동안 분 폴러(목표 달성·공부)가 최대 (이번 시각 차례 × 3초
     * 타임아웃)만큼 밀린다.
     * <p>ponytail: 배포가 정각 배치와 겹쳐 선점 뒤 발송 전에 옛 컨테이너가 내려가면(SIGTERM) 그 사람의 알림 1통이
     * 사라진다(REST는 그 쉬는 구간, DAILY는 그날) — 잦아지면 {@code spring.task.scheduling.shutdown.await-termination=true}.
     */
    public int sendDue() {
        TossProperties.Messenger m = properties.getMessenger();
        String daily = m.getReadingReminderDailyTemplateCode();
        String rest = m.getReadingReminderRestTemplateCode();
        if (client.isEmpty() || isBlank(daily) || isBlank(rest)) {
            return 0; // 다크런치 — 조회도 선점도 안 한다
        }
        Instant now = clock.instant();
        List<User> on = userRepository.findByReadingReminderKindNotAndTossUserKeyIsNotNull(ReadingReminderKind.OFF);
        int due = 0;
        int sent = 0;
        for (User u : on) {
            try {
                ZoneId zone = ZoneId.of(u.getTimezone());
                if (now.atZone(zone).getHour() != u.getReadingReminderHour()) {
                    continue; // 대부분 여기서 끝난다 — 측정 조회 0
                }
                boolean measuring = sessionRepository.findByUserAndEndedAtIsNull(u).isPresent()
                        || studySessionRepository.findByUserAndEndedAtIsNull(u).isPresent(); // 앱을 쓰는 중엔 보내지 않는다
                Optional<Instant> threshold = dueThreshold(u.getReadingReminderKind(), u.getReadingReminderHour(), zone,
                        now, sessionRepository.findLastStartedAt(u), measuring,
                        u.getReadingReminderOnAt(), u.getReadingReminderSentAt());
                if (threshold.isEmpty()) {
                    continue;
                }
                due++;
                if (userRepository.claimReadingReminder(u.getId(), now, threshold.get()) == 0) {
                    continue; // 겹친 인스턴스가 먼저 가져감
                }
                String code = (u.getReadingReminderKind() == ReadingReminderKind.DAILY ? daily : rest).strip();
                if (client.get().sendMessage(u.getTossUserKey(), code, Map.of())) {
                    sent++;
                } else {
                    userRepository.releaseReadingReminder(u.getId(), u.getReadingReminderSentAt()); // 반납 — 다음 차례에 다시
                }
            } catch (RuntimeException e) {
                log.warn("독서 알림 실패(나머지는 계속) — userId={}: {}", u.getId(), e.toString());
            }
        }
        log.info("독서 알림 배치 — 켠 사람 {}명 · 이번 차례 {}명 · 발송 {}명", on.size(), due, sent);
        return sent;
    }

    /**
     * 지금(now) 이 사람에게 보낼 차례면 선점 기준 시각(threshold)을, 아니면 empty.
     * {@code sentAt ≥ threshold}이면 이번 차례에 이미 보낸 것이다(DAILY = 오늘 0시, REST = 이번 쉬는 구간의 시작).
     *
     * <p>「읽었다」 = 독서 측정의 시작. 「측정 중」 = 독서 또는 공부 측정 진행 중(보내지 않는다 — DAILY는 그날을 건너뛰고
     * REST는 다음 날 같은 시각에 다시 본다). 어느 조합이든 하루 최대 1통이다.
     */
    static Optional<Instant> dueThreshold(ReadingReminderKind kind, int hour, ZoneId zone, Instant now,
                                          Instant lastReadStartedAt, boolean measuring, Instant onAt, Instant sentAt) {
        if (kind == ReadingReminderKind.OFF || measuring) {
            return Optional.empty();
        }
        ZonedDateTime local = now.atZone(zone);
        if (local.getHour() != hour) {
            return Optional.empty();
        }
        Instant threshold;
        if (kind == ReadingReminderKind.DAILY) {
            Instant todayStart = local.toLocalDate().atStartOfDay(zone).toInstant();
            if (lastReadStartedAt != null && !lastReadStartedAt.isBefore(todayStart)) {
                return Optional.empty(); // 오늘 읽었다
            }
            threshold = todayStart;
        } else { // REST
            Instant anchor = later(lastReadStartedAt, onAt); // 켠 날부터 센다 — 오래 쉰 사람이 켜자마자 받지 않게
            if (anchor == null) {
                return Optional.empty(); // 방어 — 켜면 onAt이 늘 박힌다
            }
            long idle = ChronoUnit.DAYS.between(anchor.atZone(zone).toLocalDate(), local.toLocalDate());
            if (idle < REST_DAYS) {
                return Optional.empty();
            }
            threshold = anchor;
        }
        if (sentAt != null && !sentAt.isBefore(threshold)) {
            return Optional.empty(); // 이번 차례 이미 보냄
        }
        return Optional.of(threshold);
    }

    private static Instant later(Instant a, Instant b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
