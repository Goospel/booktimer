package com.booktimer.chat;

import com.booktimer.config.TossProperties;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 대화방 신고 → 운영자 토스 푸시(정책 문서 §2 「알림」). 커밋 뒤에 보내므로 일부러 {@code @Transactional}이 아니다.
 *
 * <p>단독으로 잡는 실패: 토스 연결된 ADMIN이 아닌 사람(일반 사용자·토스 없는 운영자)에게 가는 것 · 롤백된 신고에도
 * 가는 것 · 템플릿·스위치 없이도 나가려는 것(콘솔 미등록 템플릿 호출).
 */
@SpringBootTest
@TestPropertySource(properties = {
        "booktimer.toss.messenger.ops-alert-enabled=true",
        "booktimer.toss.messenger.ops-alert-template-code=OPS_TEST"
})
class ChatOpsAlertTest {

    @Autowired ChatSafetyService safety;
    @Autowired ChatRoomService rooms;
    @Autowired UserRepository userRepository;
    @Autowired FollowRepository followRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager txManager;

    @MockitoBean TossMessengerClient messenger;

    @BeforeEach
    void stub() {
        when(messenger.sendMessage(anyString(), anyString(), anyMap())).thenReturn(true);
    }

    @AfterEach
    void cleanUp() {
        String mine = "(select id from users where email like '%@ops.test')";
        jdbc.update("delete from report where reporter_id in " + mine + " or reported_id in " + mine);
        jdbc.update("delete from chat_message where room_id in (select id from chat_room where user_a_id in " + mine
                + " or user_b_id in " + mine + ")");
        jdbc.update("delete from chat_room where user_a_id in " + mine + " or user_b_id in " + mine);
        jdbc.update("delete from follow where follower_id in " + mine + " or followee_id in " + mine);
        jdbc.update("delete from users where email like '%@ops.test'");
    }

    private User user(String name, Role role, boolean toss) {
        User u = User.of(name + "@ops.test", "$2a$10$abcdefghijklmnopqrstuv", name, "Asia/Seoul", role);
        u.assignLoginId(name.replace("-", ""));
        if (toss) {
            u.linkTossUserKey("uk-" + name);
        }
        return userRepository.save(u);
    }

    private long room(User a, User b) {
        followRepository.save(Follow.of(a, b));
        followRepository.save(Follow.of(b, a));
        long id = rooms.openOrGet(a, b).getId();
        rooms.send(b, id, "문제의 메시지");
        return id;
    }

    @Test
    void chatReportPushesTossLinkedAdminsAfterCommit() {
        user("ops-admin", Role.ADMIN, true);
        user("ops-admin-web", Role.ADMIN, false);
        User me = user("ops-me", Role.USER, true);
        User other = user("ops-other", Role.USER, true);
        long id = room(me, other);

        safety.reportRoom(me, id, "SPAM", null);

        verify(messenger, times(1)).sendMessage(eq("uk-ops-admin"), eq("OPS_TEST"), eq(Map.of()));
        verify(messenger, never()).sendMessage(eq("uk-ops-me"), eq("OPS_TEST"), anyMap());
        verify(messenger, never()).sendMessage(eq("uk-ops-other"), eq("OPS_TEST"), anyMap());
    }

    /** 리뷰 #1169 사소 4 — 처리 끝난 신고를 다시 신고하면 운영자가 다시 알아야 한다. 미처리 중복 신고는 알리지 않는다. */
    @Test
    void reReportingAResolvedReportAlertsAgain() {
        user("ops-admin3", Role.ADMIN, true);
        User me = user("ops-again-me", Role.USER, true);
        User other = user("ops-again-other", Role.USER, true);
        long id = room(me, other);
        long reportId = safety.reportRoom(me, id, "SPAM", null).getId();
        safety.reportRoom(me, id, "SPAM", "미처리 중복 — 알림 없음");
        safety.resolve(reportId, com.booktimer.chat.ChatSanctionService.Action.NONE);

        safety.reportRoom(me, id, "SPAM", "처리 뒤 재신고");

        verify(messenger, times(2)).sendMessage(eq("uk-ops-admin3"), eq("OPS_TEST"), eq(Map.of()));
    }

    @Test
    void rolledBackReportAlertsNobody() {
        user("ops-admin2", Role.ADMIN, true);
        User me = user("ops-rb-me", Role.USER, true);
        User other = user("ops-rb-other", Role.USER, true);
        long id = room(me, other);
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        tx.executeWithoutResult(status -> {
            safety.reportRoom(me, id, "SPAM", null);
            status.setRollbackOnly();
        });

        verify(messenger, never()).sendMessage(anyString(), eq("OPS_TEST"), anyMap());
    }

    /** 스위치·템플릿 둘 중 하나라도 없으면 운영자를 찾지도 않는다(기본값 = 다크). */
    @Test
    void noSwitchOrTemplateMeansNoOp() {
        TossMessengerClient client = mock(TossMessengerClient.class);
        UserRepository users = mock(UserRepository.class);
        TossProperties off = new TossProperties();
        TossProperties noTemplate = new TossProperties();
        noTemplate.getMessenger().setOpsAlertEnabled(true);

        new ChatOpsAlertService(Optional.of(client), off, users).notifyNewChatReport();
        new ChatOpsAlertService(Optional.of(client), noTemplate, users).notifyNewChatReport();

        verifyNoInteractions(client, users);
    }
}
