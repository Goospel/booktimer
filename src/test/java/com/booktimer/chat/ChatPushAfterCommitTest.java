package com.booktimer.chat;

import com.booktimer.block.BlockService;
import com.booktimer.follow.Follow;
import com.booktimer.follow.FollowRepository;
import com.booktimer.toss.TossMessengerClient;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 새 메시지 푸시는 <b>발송이 커밋된 뒤</b>에 나가고, 그 사이 방 행을 통째로 덮어쓰지 않는다(리뷰 #1167 중요 1).
 *
 * <p><b>일부러 {@code @Transactional}이 아니다</b> — 테스트 트랜잭션이 감싸면 커밋이 없어 afterCommit이 영영
 * 안 돌고, 「토스 호출 도중 다른 트랜잭션이 차단을 커밋」하는 경합도 재현되지 않는다. 대신 만든 행을
 * {@link #cleanUp()}이 지운다.
 *
 * <p>단독으로 잡는 실패: ① 푸시가 발송 트랜잭션 안에서 돌고, 그 뒤의 flush가 전 컬럼 UPDATE로 그 사이 커밋된
 * 차단(CLOSED)을 OPEN으로 되돌리는 것 ② 롤백된 발송인데 푸시가 나가는 것 ③ 30분 판정이 원자적이지 않아
 * 동시 발송 둘이 둘 다 보내는 것 ④ 발송 실패가 30분 창을 삼켜 다음 메시지도 안 보내는 것.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "booktimer.toss.messenger.dm-message-enabled=true",
        "booktimer.toss.messenger.dm-message-template-code=DM_TEST"
})
class ChatPushAfterCommitTest {

    static final Instant NOW = Instant.parse("2026-09-18T03:00:00Z");

    static class MutableClock extends Clock {
        private Instant now = NOW;

        void set(Instant instant) {
            now = instant;
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
    static class ClockConfig {
        @Bean
        @Primary
        Clock pushTestClock() {
            return new MutableClock();
        }
    }

    @Autowired ChatRoomService service;
    @Autowired ChatRoomRepository roomRepository;
    @Autowired UserRepository userRepository;
    @Autowired FollowRepository followRepository;
    @Autowired BlockService blockService;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager txManager;
    @Autowired Clock clock;

    @MockitoBean TossMessengerClient messenger;

    private TransactionTemplate newTx() {
        TransactionTemplate t = new TransactionTemplate(txManager);
        t.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return t;
    }

    @BeforeEach
    void reset() {
        ((MutableClock) clock).set(NOW);
        when(messenger.sendMessage(anyString(), anyString(), anyMap())).thenReturn(true);
    }

    @AfterEach
    void cleanUp() {
        String mine = "(select id from users where email like '%@push.test')";
        jdbc.update("delete from chat_message where room_id in (select id from chat_room where user_a_id in " + mine
                + " or user_b_id in " + mine + ")");
        jdbc.update("delete from chat_room where user_a_id in " + mine + " or user_b_id in " + mine);
        jdbc.update("delete from block where blocker_id in " + mine + " or blocked_id in " + mine);
        jdbc.update("delete from follow where follower_id in " + mine + " or followee_id in " + mine);
        jdbc.update("delete from users where email like '%@push.test'");
    }

    private void advance(Duration d) {
        MutableClock c = (MutableClock) clock;
        c.set(c.instant().plus(d));
    }

    private User toss(String name) {
        User u = User.of(name + "@push.test", "$2a$10$abcdefghijklmnopqrstuv", name, "Asia/Seoul", Role.USER);
        u.assignLoginId(name.replace("-", ""));
        u.linkTossUserKey("uk-" + name);
        return userRepository.save(u);
    }

    private void mutual(User a, User b) {
        followRepository.save(Follow.of(a, b));
        followRepository.save(Follow.of(b, a));
    }

    @Test
    void blockCommittedDuringPushKeepsRoomClosed() {
        User me = toss("race-me");
        User other = toss("race-other");
        mutual(me, other);
        long id = service.openOrGet(me, other).getId();
        // 토스 호출이 걸린 사이 상대가 차단을 커밋한다(리뷰어 재현 프로브와 같은 모양).
        when(messenger.sendMessage(anyString(), anyString(), anyMap())).thenAnswer(inv -> {
            newTx().executeWithoutResult(s -> blockService.block(
                    userRepository.findById(other.getId()).orElseThrow(),
                    userRepository.findById(me.getId()).orElseThrow()));
            return true;
        });

        service.send(me, id, "차단 직전 메시지");

        assertThat(roomRepository.findById(id).orElseThrow().getStatus()).isEqualTo(ChatRoom.Status.CLOSED);
        User otherNow = userRepository.findById(other.getId()).orElseThrow();
        assertThat(service.rooms(otherNow)).isEmpty();
    }

    @Test
    void rolledBackSendPushesNothing() {
        User me = toss("rb-me");
        User other = toss("rb-other");
        mutual(me, other);
        long id = service.openOrGet(me, other).getId();

        newTx().executeWithoutResult(status -> {
            service.send(me, id, "되돌려질 메시지");
            status.setRollbackOnly();
        });

        verify(messenger, never()).sendMessage(anyString(), anyString(), anyMap());
    }

    @Test
    void pushWindowIsClaimedAtomically() {
        User me = toss("claim-me");
        User other = toss("claim-other");
        mutual(me, other);
        ChatRoom room = service.openOrGet(me, other);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Instant cutoff = now.minus(Duration.ofMinutes(30));

        // 같은 판정 조건으로 두 발송이 동시에 물으면 첫째만 1행, 둘째는 0행이어야 한다.
        int first = newTx().execute(s -> roomRepository.claimPushB(room.getId(), now, cutoff));
        int second = newTx().execute(s -> roomRepository.claimPushB(room.getId(), now, cutoff));

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
    }

    @Test
    void pushGoesToPartnerAtMostOncePer30Minutes() {
        User me = toss("push-me");
        User other = toss("push-other");
        mutual(me, other);
        long id = service.openOrGet(me, other).getId();

        service.send(me, id, "1");
        advance(Duration.ofMinutes(29));
        service.send(me, id, "2");
        verify(messenger, times(1)).sendMessage(eq("uk-push-other"), eq("DM_TEST"), eq(Map.of()));

        advance(Duration.ofMinutes(2));
        service.send(me, id, "3");
        verify(messenger, times(2)).sendMessage(eq("uk-push-other"), eq("DM_TEST"), eq(Map.of()));
        verify(messenger, never()).sendMessage(eq("uk-push-me"), anyString(), anyMap());
    }

    @Test
    void failedPushIsRetriedOnNextMessage() {
        User me = toss("pf-me");
        User other = toss("pf-other");
        mutual(me, other);
        long id = service.openOrGet(me, other).getId();
        when(messenger.sendMessage(anyString(), anyString(), anyMap())).thenReturn(false);

        service.send(me, id, "실패");
        clearInvocations(messenger);
        when(messenger.sendMessage(anyString(), anyString(), anyMap())).thenReturn(true);
        service.send(me, id, "재시도");

        verify(messenger, times(1)).sendMessage(eq("uk-pf-other"), eq("DM_TEST"), eq(Map.of()));
    }
}
