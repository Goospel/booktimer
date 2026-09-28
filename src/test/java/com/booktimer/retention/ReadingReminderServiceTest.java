package com.booktimer.retention;

import com.booktimer.config.TossProperties;
import com.booktimer.session.ReadingSession;
import com.booktimer.session.ReadingSessionRepository;
import com.booktimer.session.StudySession;
import com.booktimer.session.StudySessionRepository;
import com.booktimer.toss.TossMessengerClient;
import com.booktimer.user.ReadingReminderKind;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 독서 알림(N3) — 순수 규칙 {@code dueThreshold} 경계 전수 + 매시 배치 오케스트레이션 배선.
 *
 * <p>기준 시각은 {@code 2026-10-01T11:00:00Z} = KST 10-01 20:00, 고른 시각 20.
 */
class ReadingReminderServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Instant NOW = Instant.parse("2026-10-01T11:00:00Z");
    private static final Instant TODAY_START = Instant.parse("2026-09-30T15:00:00Z"); // KST 10-01 00:00
    private static final Instant OLD_ON = Instant.parse("2026-09-01T00:00:00Z");

    private static Optional<Instant> due(ReadingReminderKind kind, Instant now, Instant lastRead,
                                         boolean measuring, Instant onAt, Instant sentAt) {
        return ReadingReminderService.dueThreshold(kind, 20, SEOUL, now, lastRead, measuring, onAt, sentAt);
    }

    // ── A. 순수 규칙 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("REQ-05 · 매일: 고른 시각이고 오늘 기록이 없으면 오늘 0시(KST)가 기준이다")
    void daily_dueAtChosenHour() {
        assertThat(due(ReadingReminderKind.DAILY, NOW, Instant.parse("2026-09-29T00:00:00Z"), false, OLD_ON, null))
                .contains(TODAY_START);
    }

    @Test
    @DisplayName("REQ-05 · 매일: 한 시간 앞·뒤(19·21시)엔 보내지 않는다")
    void daily_notAtNeighbourHours() {
        assertThat(due(ReadingReminderKind.DAILY, NOW.minus(Duration.ofHours(1)), null, false, OLD_ON, null)).isEmpty();
        assertThat(due(ReadingReminderKind.DAILY, NOW.plus(Duration.ofHours(1)), null, false, OLD_ON, null)).isEmpty();
    }

    @Test
    @DisplayName("REQ-05 · 매일: 오늘 0시 정각에 시작한 측정이 있으면 보내지 않는다(경계 포함)")
    void daily_readAtMidnight_skips() {
        assertThat(due(ReadingReminderKind.DAILY, NOW, TODAY_START, false, OLD_ON, null)).isEmpty();
    }

    @Test
    @DisplayName("REQ-05 · 매일: 어제 23:59:59에 시작한 측정뿐이면 보낸다")
    void daily_readJustBeforeMidnight_sends() {
        assertThat(due(ReadingReminderKind.DAILY, NOW, TODAY_START.minusSeconds(1), false, OLD_ON, null))
                .contains(TODAY_START);
    }

    @Test
    @DisplayName("REQ-05 · 측정 중이면 어느 방식이든 보내지 않는다")
    void measuring_skipsBothKinds() {
        assertThat(due(ReadingReminderKind.DAILY, NOW, null, true, OLD_ON, null)).isEmpty();
        assertThat(due(ReadingReminderKind.REST, NOW, Instant.parse("2026-09-20T00:00:00Z"), true, OLD_ON, null)).isEmpty();
    }

    @Test
    @DisplayName("REQ-05 · 매일: 오늘 이미 보냈으면 안 보내고, 어제 보낸 기록은 막지 않는다")
    void daily_sentToday_skips_sentYesterday_sends() {
        assertThat(due(ReadingReminderKind.DAILY, NOW, null, false, OLD_ON, TODAY_START)).isEmpty();
        assertThat(due(ReadingReminderKind.DAILY, NOW, null, false, OLD_ON, Instant.parse("2026-09-30T11:00:00Z")))
                .contains(TODAY_START);
    }

    @Test
    @DisplayName("REQ-05 · 매일: 한 번도 안 읽은 사람도 받는다(null-state)")
    void daily_neverRead_sends() {
        assertThat(due(ReadingReminderKind.DAILY, NOW, null, false, OLD_ON, null)).contains(TODAY_START);
    }

    @Test
    @DisplayName("REQ-05 · 시각은 사용자 zone 기준이다")
    void hourIsInUserZone() {
        Instant now = Instant.parse("2026-10-02T00:00:00Z"); // EDT 10-01 20:00 · KST 10-02 09:00
        ZoneId ny = ZoneId.of("America/New_York");

        assertThat(ReadingReminderService.dueThreshold(ReadingReminderKind.DAILY, 20, ny, now, null, false, OLD_ON, null))
                .contains(Instant.parse("2026-10-01T04:00:00Z")); // 뉴욕 10-01 0시
        assertThat(ReadingReminderService.dueThreshold(ReadingReminderKind.DAILY, 20, SEOUL, now, null, false, OLD_ON, null))
                .isEmpty();
    }

    @Test
    @DisplayName("REQ-06 · 3일 쉬면: 마지막 독서일로부터 3일째(경계 포함)면 그 독서 시작 시각이 기준이다")
    void rest_thirdDay_sends() {
        Instant lastRead = Instant.parse("2026-09-28T12:00:00Z"); // KST 09-28 21:00
        assertThat(due(ReadingReminderKind.REST, NOW, lastRead, false, OLD_ON, null)).contains(lastRead);
    }

    @Test
    @DisplayName("REQ-06 · 3일 쉬면: 2일째면 보내지 않는다")
    void rest_secondDay_skips() {
        Instant lastRead = Instant.parse("2026-09-28T22:00:00Z"); // KST 09-29 07:00
        assertThat(due(ReadingReminderKind.REST, NOW, lastRead, false, OLD_ON, null)).isEmpty();
    }

    @Test
    @DisplayName("REQ-06 · 3일 쉬면: 켠 시각이 마지막 독서보다 늦으면 켠 날부터 센다")
    void rest_countsFromOnAtWhenLater() {
        Instant onAt = Instant.parse("2026-09-30T01:00:00Z"); // KST 09-30 10:00
        assertThat(due(ReadingReminderKind.REST, NOW, OLD_ON, false, onAt, null)).isEmpty();
    }

    @Test
    @DisplayName("REQ-06 · 3일 쉬면: 한 번도 안 읽었으면 켠 날부터 센다(null-state)")
    void rest_neverRead_countsFromOnAt() {
        Instant onAt = Instant.parse("2026-09-28T00:00:00Z"); // KST 09-28 09:00
        assertThat(due(ReadingReminderKind.REST, NOW, null, false, onAt, null)).contains(onAt);
    }

    @Test
    @DisplayName("REQ-06 · 3일 쉬면: 이 구간에 이미 보냈으면 다시 안 보내고, 구간 시작 전 기록은 막지 않는다")
    void rest_sentInThisInterval_skips() {
        Instant lastRead = Instant.parse("2026-09-28T12:00:00Z");
        assertThat(due(ReadingReminderKind.REST, NOW, lastRead, false, OLD_ON, lastRead.plus(Duration.ofHours(1))))
                .isEmpty();
        assertThat(due(ReadingReminderKind.REST, NOW, lastRead, false, OLD_ON, lastRead.minus(Duration.ofDays(1))))
                .contains(lastRead);
    }

    @Test
    @DisplayName("REQ-06 · 3일 쉬면: 기준이 둘 다 null이면 empty(방어)")
    void rest_noAnchor_empty() {
        assertThat(due(ReadingReminderKind.REST, NOW, null, false, null, null)).isEmpty();
    }

    @Test
    @DisplayName("REQ-05 · 꺼짐(OFF)은 어떤 조건에서도 empty")
    void off_neverDue() {
        assertThat(due(ReadingReminderKind.OFF, NOW, null, false, OLD_ON, null)).isEmpty();
        assertThat(due(ReadingReminderKind.OFF, NOW, Instant.parse("2026-09-20T00:00:00Z"), false, OLD_ON, null)).isEmpty();
    }

    // ── B. 오케스트레이션 ───────────────────────────────────────────────────

    private static final String DAILY_CODE = "booktimer-reading-reminder-daily";
    private static final String REST_CODE = "booktimer-reading-reminder-rest";

    private static User tossUser(long id, String key, ReadingReminderKind kind, Instant onAt) {
        return tossUser(id, key, kind, 20, onAt);
    }

    private static User tossUser(long id, String key, ReadingReminderKind kind, int hour, Instant onAt) {
        User u = User.of("u" + id + "@booktimer.com", "$2a$10$abcdefghijklmnopqrstuv", "책벌레", "Asia/Seoul", Role.USER);
        ReflectionTestUtils.setField(u, "id", id);
        if (key != null) u.linkTossUserKey(key);
        u.configureReadingReminder(kind, hour, onAt);
        return u;
    }

    private static void sentAt(User u, Instant at) {
        ReflectionTestUtils.setField(u, "readingReminderSentAt", at);
    }

    private static TossProperties properties(boolean enabled, String daily, String rest) {
        TossProperties p = new TossProperties();
        p.getMessenger().setReadingReminderEnabled(enabled);
        p.getMessenger().setReadingReminderDailyTemplateCode(daily);
        p.getMessenger().setReadingReminderRestTemplateCode(rest);
        return p;
    }

    @Nested
    @ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
    class Batch {

        @Mock UserRepository userRepository;
        @Mock ReadingSessionRepository sessionRepository;
        @Mock StudySessionRepository studySessionRepository;
        @Mock TossMessengerClient client;

        private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        /** 예외 격리 테스트만 켠다 — 나머지는 「사용자별 catch가 아무것도 삼키지 않았다」를 매번 단언한다. */
        private boolean expectsSwallowedFailure;

        private ReadingReminderService service(TossProperties p, Optional<TossMessengerClient> c) {
            return new ReadingReminderService(userRepository, sessionRepository, studySessionRepository, c, p, clock);
        }

        private ReadingReminderService service() {
            return service(properties(true, DAILY_CODE, REST_CODE), Optional.of(client));
        }

        /**
         * sendDue의 사용자별 catch는 목의 스텁 예외(PotentialStubbingProblem 등)까지 삼킨다 — 그러면 「선점하지 않는다」류
         * 단언이 우연히 참이 된다(리뷰 R-1: 독서 측정 검사를 지운 돌연변이가 이 경로로 생존했다). 삼킨 흔적이 없어야 한다.
         */
        @AfterEach
        void noFailureSwallowed(CapturedOutput output) {
            if (!expectsSwallowedFailure) {
                assertThat(output).doesNotContain("독서 알림 실패");
            }
        }

        @Test
        @DisplayName("REQ-08 · 템플릿 코드가 하나라도 비면 후보 조회도 발송도 없다")
        void blankCode_noQueryNoSend() {
            assertThat(service(properties(true, DAILY_CODE, " "), Optional.of(client)).sendDue()).isZero();
            assertThat(service(properties(true, null, REST_CODE), Optional.of(client)).sendDue()).isZero();
            assertThat(service(properties(true, DAILY_CODE, REST_CODE), Optional.empty()).sendDue()).isZero();

            verifyNoInteractions(userRepository, sessionRepository, studySessionRepository, client);
        }

        @Test
        @DisplayName("REQ-05 · 차례인 매일 사용자는 매일 코드로, 3일 쉬면 사용자는 3일 코드로 보낸다")
        void dueUsers_sentWithTheirKindCode() {
            User daily = tossUser(1, "tk-daily", ReadingReminderKind.DAILY, OLD_ON);
            User rest = tossUser(2, "tk-rest", ReadingReminderKind.REST, NOW.minus(Duration.ofDays(4)));
            when(userRepository.findByReadingReminderKindNotAndTossUserKeyIsNotNull(ReadingReminderKind.OFF))
                    .thenReturn(List.of(daily, rest));
            when(userRepository.claimReadingReminder(any(), any(), any())).thenReturn(1);
            when(client.sendMessage(any(), any(), any())).thenReturn(true);

            assertThat(service().sendDue()).isEqualTo(2);

            verify(client).sendMessage("tk-daily", DAILY_CODE, Map.of());
            verify(client).sendMessage("tk-rest", REST_CODE, Map.of());
            verify(userRepository).claimReadingReminder(1L, NOW, TODAY_START);
        }

        @Test
        @DisplayName("REQ-05 · 배치는 각자 고른 시각으로 거른다 — 21시 사용자는 21시 정각에 선점하고 20시 사용자는 건너뛴다")
        void hourWiring_usesEachUsersChosenHour() {
            Instant ninePm = NOW.plus(Duration.ofHours(1)); // KST 10-01 21:00
            clock = Clock.fixed(ninePm, ZoneOffset.UTC);
            User at21 = tossUser(1, "tk-21", ReadingReminderKind.DAILY, 21, OLD_ON);
            User at20 = tossUser(2, "tk-20", ReadingReminderKind.DAILY, 20, OLD_ON);
            when(userRepository.findByReadingReminderKindNotAndTossUserKeyIsNotNull(ReadingReminderKind.OFF))
                    .thenReturn(List.of(at21, at20));
            when(userRepository.claimReadingReminder(any(), any(), any())).thenReturn(1);
            when(client.sendMessage(any(), any(), any())).thenReturn(true);

            assertThat(service().sendDue()).isEqualTo(1);

            verify(userRepository).claimReadingReminder(1L, ninePm, TODAY_START);
            verify(userRepository, never()).claimReadingReminder(eq(2L), any(), any());
        }

        @Test
        @DisplayName("REQ-05 · 오늘 독서를 시작한 매일 사용자는 선점하지 않는다")
        void dailyReadToday_notClaimed() {
            User daily = tossUser(1, "tk-daily", ReadingReminderKind.DAILY, OLD_ON);
            when(userRepository.findByReadingReminderKindNotAndTossUserKeyIsNotNull(ReadingReminderKind.OFF))
                    .thenReturn(List.of(daily));
            when(sessionRepository.findLastStartedAt(daily)).thenReturn(Instant.parse("2026-10-01T00:00:00Z")); // KST 09:00

            service().sendDue();

            verify(userRepository, never()).claimReadingReminder(any(), any(), any());
            verifyNoInteractions(client);
        }

        @Test
        @DisplayName("REQ-05 · 독서 측정 중이거나 공부 측정 중인 사용자는 선점하지 않는다")
        void measuring_notClaimed() {
            User reading = tossUser(1, "tk-1", ReadingReminderKind.DAILY, OLD_ON);
            User studying = tossUser(2, "tk-2", ReadingReminderKind.DAILY, OLD_ON);
            when(userRepository.findByReadingReminderKindNotAndTossUserKeyIsNotNull(ReadingReminderKind.OFF))
                    .thenReturn(List.of(reading, studying));
            // 공부 조회는 누구로 불려도 스텁 오류 없이 「측정 없음」 — 독서 측정 검사가 빠지면 reading이 선점까지 간다
            lenient().when(studySessionRepository.findByUserAndEndedAtIsNull(any())).thenReturn(Optional.empty());
            when(sessionRepository.findByUserAndEndedAtIsNull(reading))
                    .thenReturn(Optional.of(ReadingSession.start(reading, NOW.minusSeconds(60))));
            when(sessionRepository.findByUserAndEndedAtIsNull(studying)).thenReturn(Optional.empty()); // strict stubs — 명시
            when(studySessionRepository.findByUserAndEndedAtIsNull(studying))
                    .thenReturn(Optional.of(StudySession.start(studying, NOW.minusSeconds(60))));

            service().sendDue();

            verify(userRepository, never()).claimReadingReminder(any(), any(), any());
            verifyNoInteractions(client);
        }

        @Test
        @DisplayName("REQ-06 · 3일 쉬면: 선점 기준은 마지막 독서·켠 시각 중 늦은 쪽이고, 이번 구간에 보냈으면 선점하지 않는다")
        void rest_claimThresholdWiring() {
            Instant aOn = NOW.minus(Duration.ofDays(4));
            Instant lastRead = NOW.minus(Duration.ofDays(5));
            User a = tossUser(1, "tk-a", ReadingReminderKind.REST, aOn);
            User b = tossUser(2, "tk-b", ReadingReminderKind.REST, NOW.minus(Duration.ofDays(10)));
            User c = tossUser(3, "tk-c", ReadingReminderKind.REST, NOW.minus(Duration.ofDays(10)));
            sentAt(c, NOW.minus(Duration.ofDays(1)));
            when(userRepository.findByReadingReminderKindNotAndTossUserKeyIsNotNull(ReadingReminderKind.OFF))
                    .thenReturn(List.of(a, b, c));
            when(sessionRepository.findLastStartedAt(a)).thenReturn(null); // 한 번도 안 읽음
            when(sessionRepository.findLastStartedAt(b)).thenReturn(lastRead);
            when(sessionRepository.findLastStartedAt(c)).thenReturn(lastRead);
            when(userRepository.claimReadingReminder(any(), any(), any())).thenReturn(1);
            when(client.sendMessage(any(), any(), any())).thenReturn(true);

            service().sendDue();

            verify(userRepository).claimReadingReminder(1L, NOW, aOn);
            verify(userRepository).claimReadingReminder(2L, NOW, lastRead);
            verify(userRepository, never()).claimReadingReminder(eq(3L), any(), any());
        }

        @Test
        @DisplayName("REQ-07 · 선점이 0이면(겹친 인스턴스가 가져감) 보내지 않는다")
        void claimLost_noSend() {
            User daily = tossUser(1, "tk-daily", ReadingReminderKind.DAILY, OLD_ON);
            when(userRepository.findByReadingReminderKindNotAndTossUserKeyIsNotNull(ReadingReminderKind.OFF))
                    .thenReturn(List.of(daily));
            when(userRepository.claimReadingReminder(any(), any(), any())).thenReturn(0);

            assertThat(service().sendDue()).isZero();

            verifyNoInteractions(client);
        }

        @Test
        @DisplayName("REQ-07 · 발송이 false면 선점 전 값으로 반납한다")
        void sendFailed_releasesToPrevious() {
            Instant yesterday = Instant.parse("2026-09-30T11:00:00Z");
            User daily = tossUser(1, "tk-daily", ReadingReminderKind.DAILY, OLD_ON);
            sentAt(daily, yesterday);
            when(userRepository.findByReadingReminderKindNotAndTossUserKeyIsNotNull(ReadingReminderKind.OFF))
                    .thenReturn(List.of(daily));
            when(userRepository.claimReadingReminder(any(), any(), any())).thenReturn(1);
            when(client.sendMessage(any(), any(), any())).thenReturn(false);

            assertThat(service().sendDue()).isZero();

            verify(userRepository).releaseReadingReminder(1L, yesterday);
        }

        @Test
        @DisplayName("REQ-07 · 한 명이 예외로 터져도 나머지는 보낸다")
        void oneFailure_othersStillSent() {
            expectsSwallowedFailure = true;
            User boom = tossUser(1, "tk-boom", ReadingReminderKind.DAILY, OLD_ON);
            User ok = tossUser(2, "tk-ok", ReadingReminderKind.DAILY, OLD_ON);
            when(userRepository.findByReadingReminderKindNotAndTossUserKeyIsNotNull(ReadingReminderKind.OFF))
                    .thenReturn(List.of(boom, ok));
            when(sessionRepository.findLastStartedAt(boom)).thenThrow(new IllegalStateException("db down"));
            when(sessionRepository.findLastStartedAt(ok)).thenReturn(null);
            when(userRepository.claimReadingReminder(any(), any(), any())).thenReturn(1);
            when(client.sendMessage(any(), any(), any())).thenReturn(true);

            assertThat(service().sendDue()).isEqualTo(1);

            verify(client).sendMessage("tk-ok", DAILY_CODE, Map.of());
        }

        @Test
        @DisplayName("REQ-09 · 배치 한 번에 「켠 사람 · 이번 차례 · 발송」 수를 한 줄로 남긴다")
        void batch_logsOneSummaryLine(CapturedOutput output) {
            User sent = tossUser(1, "tk-1", ReadingReminderKind.DAILY, OLD_ON);
            User failed = tossUser(2, "tk-2", ReadingReminderKind.DAILY, OLD_ON);
            when(userRepository.findByReadingReminderKindNotAndTossUserKeyIsNotNull(ReadingReminderKind.OFF))
                    .thenReturn(List.of(sent, failed));
            when(userRepository.claimReadingReminder(any(), any(), any())).thenReturn(1);
            when(client.sendMessage(eq("tk-1"), any(), any())).thenReturn(true);
            when(client.sendMessage(eq("tk-2"), any(), any())).thenReturn(false);

            service().sendDue();

            assertThat(output).contains("독서 알림 배치 — 켠 사람 2명 · 이번 차례 2명 · 발송 1명");
        }

        @Test
        @DisplayName("REQ-04 · view: 토글·클라이언트·두 코드·토스 연동이 다 있어야 available=true, 아니면 false·agreementCode=null · everOn은 켠 시각 유무를 따른다")
        void view_availabilityAndEverOn() {
            User linked = tossUser(1, "tk-1", ReadingReminderKind.OFF, OLD_ON); // OFF로 두면 켠 시각 없음
            User unlinked = tossUser(2, null, ReadingReminderKind.OFF, OLD_ON);

            ReadingReminderService.View full = service().view(linked);
            assertThat(full.available()).isTrue();
            assertThat(full.agreementCode()).isEqualTo(REST_CODE);
            assertThat(full.kind()).isEqualTo("OFF");
            assertThat(full.hour()).isEqualTo(20);
            assertThat(full.everOn()).isFalse();

            List<ReadingReminderService.View> missingOne = List.of(
                    service(properties(false, DAILY_CODE, REST_CODE), Optional.of(client)).view(linked),
                    service(properties(true, DAILY_CODE, REST_CODE), Optional.empty()).view(linked),
                    service(properties(true, "", REST_CODE), Optional.of(client)).view(linked),
                    service(properties(true, DAILY_CODE, null), Optional.of(client)).view(linked),
                    service().view(unlinked));
            assertThat(missingOne).allSatisfy(v -> {
                assertThat(v.available()).isFalse();
                assertThat(v.agreementCode()).isNull();
            });

            linked.configureReadingReminder(ReadingReminderKind.REST, 21, NOW);
            ReadingReminderService.View on = service().view(linked);
            assertThat(on.everOn()).isTrue();
            assertThat(on.kind()).isEqualTo("REST");
            assertThat(on.hour()).isEqualTo(21);
        }
    }
}
