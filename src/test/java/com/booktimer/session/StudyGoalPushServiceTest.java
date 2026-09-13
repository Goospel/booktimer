package com.booktimer.session;

import com.booktimer.book.StudyBook;
import com.booktimer.book.StudyBookRepository;
import com.booktimer.config.TossProperties;
import com.booktimer.toss.TossMessengerClient;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 공부 「회당 시간」 도달 푸시 — 측정이 그 책의 회당 시간에 닿으면 세션당 한 통만 나가는지(실제 빈 + H2).
 *
 * <p>이 테스트가 단독으로 잡는 실패:
 * <ul>
 *   <li>닿음 부등식({@code startedAt + goal ≤ now})의 방향·경계가 클라이언트 「달성」 표시와 어긋나는 것,</li>
 *   <li>GRACE(10분)가 깨져 서버 공백·다크런치 동안 닿은 세션이 점등 순간 한꺼번에 쏟아지는 것,</li>
 *   <li>세션당 1회 멱등이 깨져 분마다(스케줄 주기) 같은 세션에 계속 발송되는 것,</li>
 *   <li>발송 실패인데 마킹해 재시도가 막히는 것 / 다크런치에서 조용히 마킹되는 것,</li>
 *   <li>마킹이 전 컬럼 UPDATE라 스케줄러와 {@code stop}이 겹칠 때 {@code endedAt}이 되살아나는 것(F16).</li>
 * </ul>
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = "booktimer.toss.messenger.study-goal-template-code=STUDY_GOAL_MET")
class StudyGoalPushServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    /** 2026-06-17 18:00 KST — 자정에서 멀다. */
    private static final Instant NOW = Instant.parse("2026-06-17T09:00:00Z");
    private static final int GOAL = 50 * 60;

    static class MutableClock extends Clock {
        private Instant now = NOW;

        void set(Instant instant) {
            this.now = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @TestConfiguration
    static class MutableClockConfig {
        @Bean
        @Primary
        Clock mutableClock() {
            return new MutableClock();
        }
    }

    @Autowired StudyGoalPushService pushService;
    @Autowired StudySessionService studySessionService;
    @Autowired StudySessionRepository sessionRepository;
    @Autowired StudyBookRepository bookRepository;
    @Autowired UserRepository userRepository;
    @Autowired TossProperties tossProperties;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired Clock clock;

    @MockitoBean TossMessengerClient messengerClient;

    @BeforeEach
    void resetClock() {
        ((MutableClock) clock).set(NOW);
    }

    private User user(String email, boolean linkToss) {
        User u = User.of(email, "$2a$10$abcdefghijklmnopqrstuv", "공부러", SEOUL.getId(), Role.USER);
        if (linkToss) {
            u.linkTossUserKey("uk-" + email);
        }
        return userRepository.save(u);
    }

    private StudyBook book(User u, Integer goalSeconds) {
        StudyBook b = StudyBook.register(u, "정보처리기사", null, null, null, null, null);
        b.changeSessionGoal(goalSeconds);
        return bookRepository.save(b);
    }

    private StudySession active(User u, StudyBook b, Instant startedAt) {
        return sessionRepository.save(StudySession.start(u, startedAt, b));
    }

    private Instant notifiedAt(StudySession s) {
        return sessionRepository.findById(s.getId()).orElseThrow().getGoalNotifiedAt();
    }

    @Test
    @DisplayName("회당 시간에 닿으면 빈 컨텍스트로 1회 발송 + 마킹, 다음 틱엔 안 보낸다")
    void push_whenReached_sendsOnceAndMarks() {
        User u = user("sg1@booktimer.com", true);
        when(messengerClient.sendMessage(anyString(), anyString(), any())).thenReturn(true);
        StudySession s = active(u, book(u, GOAL), NOW.minusSeconds(51 * 60));

        pushService.detectAndPush();

        verify(messengerClient, times(1))
                .sendMessage(eq("uk-sg1@booktimer.com"), eq("STUDY_GOAL_MET"), eq(Map.of()));
        assertThat(notifiedAt(s)).isEqualTo(NOW);

        pushService.detectAndPush();
        verify(messengerClient, times(1)).sendMessage(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("아직 못 닿았으면(49분) 안 보내고, 정확히 닿은 순간(50분)엔 보낸다 — startedAt + goal ≤ now")
    void push_notYet_doesNothing() {
        User early = user("sg2a@booktimer.com", true);
        User exact = user("sg2b@booktimer.com", true);
        when(messengerClient.sendMessage(anyString(), anyString(), any())).thenReturn(true);
        StudySession notYet = active(early, book(early, GOAL), NOW.minusSeconds(49 * 60));
        active(exact, book(exact, GOAL), NOW.minusSeconds(GOAL));

        pushService.detectAndPush();

        verify(messengerClient, never()).sendMessage(eq("uk-sg2a@booktimer.com"), anyString(), any());
        verify(messengerClient, times(1)).sendMessage(eq("uk-sg2b@booktimer.com"), anyString(), any());
        assertThat(notifiedAt(notYet)).isNull();
    }

    @Test
    @DisplayName("닿은 지 10분이 넘으면 조용히 건너뛴다(마킹도 없음), 5분·정확히 10분이면 보낸다")
    void push_tooLate_isSkipped() {
        User late = user("sg3a@booktimer.com", true);
        User fresh = user("sg3b@booktimer.com", true);
        User edge = user("sg3c@booktimer.com", true);
        when(messengerClient.sendMessage(anyString(), anyString(), any())).thenReturn(true);
        StudySession tooLate = active(late, book(late, GOAL), NOW.minusSeconds(61 * 60));
        active(fresh, book(fresh, GOAL), NOW.minusSeconds(55 * 60));
        active(edge, book(edge, GOAL), NOW.minusSeconds(60 * 60));

        pushService.detectAndPush();

        verify(messengerClient, never()).sendMessage(eq("uk-sg3a@booktimer.com"), anyString(), any());
        verify(messengerClient, times(1)).sendMessage(eq("uk-sg3b@booktimer.com"), anyString(), any());
        verify(messengerClient, times(1)).sendMessage(eq("uk-sg3c@booktimer.com"), anyString(), any());
        assertThat(notifiedAt(tooLate)).isNull();
    }

    @Test
    @DisplayName("회당 시간이 없는 책·책 없는 세션은 대상이 아니다")
    void push_bookWithoutGoal_or_noBook_isSkipped() {
        User a = user("sg4a@booktimer.com", true);
        User b = user("sg4b@booktimer.com", true);
        active(a, book(a, null), NOW.minusSeconds(51 * 60));
        active(b, null, NOW.minusSeconds(51 * 60));

        pushService.detectAndPush();

        verify(messengerClient, never()).sendMessage(any(), any(), any());
    }

    @Test
    @DisplayName("끝난 세션은 대상이 아니다")
    void push_endedSession_isSkipped() {
        User u = user("sg5@booktimer.com", true);
        StudySession s = StudySession.start(u, NOW.minusSeconds(51 * 60), book(u, GOAL));
        s.end(NOW.minusSeconds(30));
        sessionRepository.save(s);

        pushService.detectAndPush();

        verify(messengerClient, never()).sendMessage(any(), any(), any());
    }

    @Test
    @DisplayName("토스 미연결(userKey null) 계정은 대상이 아니다")
    void push_webOnlyUser_isSkipped() {
        User u = user("sg6@booktimer.com", false);
        StudySession s = active(u, book(u, GOAL), NOW.minusSeconds(51 * 60));

        pushService.detectAndPush();

        verify(messengerClient, never()).sendMessage(any(), any(), any());
        assertThat(notifiedAt(s)).isNull();
    }

    @Test
    @DisplayName("발송 실패(false)면 마킹하지 않는다 — 다음 틱에 재시도")
    void push_sendFailure_doesNotMark() {
        User u = user("sg7@booktimer.com", true);
        when(messengerClient.sendMessage(anyString(), anyString(), any())).thenReturn(false);
        StudySession s = active(u, book(u, GOAL), NOW.minusSeconds(51 * 60));

        pushService.detectAndPush();
        assertThat(notifiedAt(s)).isNull();

        pushService.detectAndPush();
        verify(messengerClient, times(2)).sendMessage(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("한 세션이 터져도 나머지는 계속 처리된다")
    void push_oneFailureDoesNotStopBatch() {
        User boom = user("sg8-boom@booktimer.com", true);
        User ok = user("sg8-ok@booktimer.com", true);
        when(messengerClient.sendMessage(eq("uk-sg8-boom@booktimer.com"), anyString(), any()))
                .thenThrow(new RuntimeException("toss down"));
        when(messengerClient.sendMessage(eq("uk-sg8-ok@booktimer.com"), anyString(), any()))
                .thenReturn(true);
        StudySession boomSession = active(boom, book(boom, GOAL), NOW.minusSeconds(51 * 60));
        StudySession okSession = active(ok, book(ok, GOAL), NOW.minusSeconds(51 * 60));

        pushService.detectAndPush();

        assertThat(notifiedAt(okSession)).isEqualTo(NOW);
        assertThat(notifiedAt(boomSession)).isNull();
    }

    @Test
    @DisplayName("템플릿 코드가 비면 발송도 마킹도 없다 — 다크런치 규약")
    void push_blankTemplate_marksNothing() {
        User u = user("sg9@booktimer.com", true);
        when(messengerClient.sendMessage(anyString(), anyString(), any())).thenReturn(true);
        StudySession s = active(u, book(u, GOAL), NOW.minusSeconds(51 * 60));

        String saved = tossProperties.getMessenger().getStudyGoalTemplateCode();
        tossProperties.getMessenger().setStudyGoalTemplateCode("");
        try {
            pushService.detectAndPush();
        } finally {
            tossProperties.getMessenger().setStudyGoalTemplateCode(saved);
        }

        verify(messengerClient, never()).sendMessage(any(), any(), any());
        assertThat(notifiedAt(s)).isNull();
    }

    @Test
    @DisplayName("마킹 뒤 측정 중 책을 바꿔도(새 책 기준 닿음·GRACE 안) 다시 보내지 않는다 — 세션당 1회")
    void changeActiveBook_afterNotified_doesNotResend() {
        User u = user("sg10@booktimer.com", true);
        when(messengerClient.sendMessage(anyString(), anyString(), any())).thenReturn(true);
        StudySession s = active(u, book(u, GOAL), NOW.minusSeconds(51 * 60));
        pushService.detectAndPush();

        // 45분 책: 닿은 지 6분 — 마킹이 없다면 GRACE 안이라 보낼 조건이다.
        studySessionService.changeActiveBook(u, book(u, 45 * 60));
        pushService.detectAndPush();

        verify(messengerClient, times(1)).sendMessage(anyString(), anyString(), any());
        assertThat(notifiedAt(s)).isEqualTo(NOW);
    }

    @Test
    @DisplayName("자정 분할 뒤 원본 행만 goalNotifiedAt을 갖고 조각은 null이다")
    void midnightSplit_pieceHasNoNotifiedAt() {
        Instant started = LocalDateTime.parse("2026-06-17T23:40").atZone(SEOUL).toInstant();
        ((MutableClock) clock).set(started.plusSeconds(10 * 60));
        User u = user("sg11@booktimer.com", true);
        when(messengerClient.sendMessage(anyString(), anyString(), any())).thenReturn(true);
        StudySession original = active(u, book(u, 5 * 60), started);
        pushService.detectAndPush();

        studySessionService.stop(u, LocalDateTime.parse("2026-06-18T00:30").atZone(SEOUL).toInstant());

        List<StudySession> rows = sessionRepository.findByUserAndEndedAtIsNotNull(u);
        assertThat(rows).hasSize(2);
        for (StudySession row : rows) {
            if (row.getId().equals(original.getId())) {
                assertThat(row.getGoalNotifiedAt()).isNotNull();
            } else {
                assertThat(row.getGoalNotifiedAt()).isNull();
            }
        }
    }

    /**
     * F16 경합 — 스케줄러가 후보를 로드한 뒤(발송 중) 다른 트랜잭션에서 {@code stop}이 커밋된다. 마킹이 엔티티
     * save(전 컬럼 UPDATE)면 스케줄러가 들고 있던 {@code endedAt=null}이 덮여 측정이 되살아난다.
     *
     * <p>클래스의 테스트 트랜잭션을 끄고(같은 영속성 컨텍스트면 경합이 재현되지 않는다) stop을 발송 콜백 안의
     * {@code REQUIRES_NEW}로 커밋한다. 커밋된 행은 직접 지운다.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("스케줄러가 로드한 뒤 stop이 커밋돼도 마킹이 endedAt을 되살리지 않는다(H2 통합)")
    void markGoalNotified_touchesOnlyThatColumn() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        TransactionTemplate txNew = new TransactionTemplate(transactionManager);
        txNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        User u = tx.execute(st -> user("sg12@booktimer.com", true));
        StudyBook b = tx.execute(st -> book(u, GOAL));
        StudySession s = tx.execute(st -> active(u, b, NOW.minusSeconds(51 * 60)));
        try {
            when(messengerClient.sendMessage(anyString(), anyString(), any())).thenAnswer(inv -> {
                txNew.executeWithoutResult(st -> studySessionService.stop(u, NOW));
                return true;
            });

            pushService.detectAndPush();

            StudySession reloaded = sessionRepository.findById(s.getId()).orElseThrow();
            assertThat(reloaded.getEndedAt()).isEqualTo(NOW);
            assertThat(reloaded.getDurationSeconds()).isEqualTo(51 * 60);
            assertThat(reloaded.getGoalNotifiedAt()).isEqualTo(NOW);
        } finally {
            tx.executeWithoutResult(st -> {
                sessionRepository.deleteByUser(u);
                bookRepository.deleteById(b.getId());
                userRepository.deleteById(u.getId());
            });
        }
    }

    /**
     * 마킹 격리 — 한 세션의 마킹이 DB 예외로 실패해도, 먼저 성공한 다른 세션의 마킹은 커밋돼 남는다.
     * 배치 전체가 한 트랜잭션이면 실패한 마킹이 롤백 전용 표시를 남겨 <b>이미 발송한 세션들의 마킹까지</b> 롤백되고,
     * 다음 틱에 그 사람들에게 재발송된다.
     *
     * <p>예외는 그 세션 행만 막는 CHECK 제약으로 주입한다. 행 락 타임아웃은 쓰지 않는다 — JPA 규약상
     * {@code LockTimeoutException}은 트랜잭션에 롤백 표시를 남기지 않아, 단일 트랜잭션 코드에서도 이 테스트가
     * 통과했다(판별력 없음 실측). 제약과 커밋된 행은 직접 지운다.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("한 세션 마킹이 DB 예외로 실패해도 다른 세션의 마킹은 커밋돼 남는다(H2 통합)")
    void markFailureOnOneSession_keepsOtherSessionsMarked() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        User okUser = tx.execute(st -> user("sg13-ok@booktimer.com", true));
        User lockedUser = tx.execute(st -> user("sg13-locked@booktimer.com", true));
        StudyBook okBook = tx.execute(st -> book(okUser, GOAL));
        StudyBook lockedBook = tx.execute(st -> book(lockedUser, GOAL));
        StudySession ok = tx.execute(st -> active(okUser, okBook, NOW.minusSeconds(51 * 60)));
        StudySession locked = tx.execute(st -> active(lockedUser, lockedBook, NOW.minusSeconds(51 * 60)));

        RuntimeException thrown = null;
        try {
            when(messengerClient.sendMessage(any(), any(), any())).thenReturn(true);
            jdbcTemplate.execute("alter table study_session add constraint chk_sg13_mark_fails check ("
                    + "goal_notified_at is null or id <> " + locked.getId() + ")");

            try {
                pushService.detectAndPush();
            } catch (RuntimeException e) {
                thrown = e;
            }

            assertThat(notifiedAt(ok)).as("먼저 성공한 세션의 마킹은 커밋돼야 한다").isEqualTo(NOW);
            assertThat(notifiedAt(locked)).isNull();
            assertThat(thrown).as("배치는 세션별 실패를 삼킨다").isNull();
        } finally {
            jdbcTemplate.execute("alter table study_session drop constraint if exists chk_sg13_mark_fails");
            tx.executeWithoutResult(st -> {
                sessionRepository.deleteByUser(okUser);
                sessionRepository.deleteByUser(lockedUser);
                bookRepository.deleteById(okBook.getId());
                bookRepository.deleteById(lockedBook.getId());
                userRepository.deleteById(okUser.getId());
                userRepository.deleteById(lockedUser.getId());
            });
        }
    }
}
