package com.booktimer.chat;

import com.booktimer.block.BlockService;
import com.booktimer.follow.Follow;
import com.booktimer.follow.FollowRepository;
import com.booktimer.toss.TossMessengerClient;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 방·메시지 서비스(설계 §5-2·§6 PR-1). 실 빈 + H2.
 *
 * <p>가장 위험한 자리는 <b>자격 재검증 누락</b>이다 — 방이 한 번 열리면 그 뒤로는 자격을 안 보는 구현이
 * 가장 자연스럽고, 그러면 언팔·차단 뒤에도 메시지가 계속 간다. 그래서 언팔·차단·재맞팔을 전부 실제 관계
 * 테이블로 밟는다. 멤버가 아닌 사용자의 모든 경로(목록·조회·발송·읽음·숨김)가 404인지도 전수로 본다.
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = {
        "booktimer.toss.messenger.dm-message-enabled=true",
        "booktimer.toss.messenger.dm-message-template-code=DM_TEST"
})
class ChatRoomServiceTest {

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
        Clock mutableClock() {
            return new MutableClock();
        }
    }

    @Autowired ChatRoomService service;
    @Autowired UserRepository userRepository;
    @Autowired FollowRepository followRepository;
    @Autowired BlockService blockService;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

    @MockitoBean TossMessengerClient messenger;

    @BeforeEach
    void reset() {
        ((MutableClock) clock).set(NOW);
        when(messenger.sendMessage(anyString(), anyString(), anyMap())).thenReturn(true);
    }

    private void advance(Duration d) {
        MutableClock c = (MutableClock) clock;
        c.set(c.instant().plus(d));
    }

    private User toss(String name) {
        User u = User.of(name + "@room.test", "$2a$10$abcdefghijklmnopqrstuv", name, "Asia/Seoul", Role.USER);
        u.assignLoginId(name.replace("-", ""));
        u.linkTossUserKey("uk-" + name);
        return userRepository.save(u);
    }

    private void mutual(User a, User b) {
        followRepository.save(Follow.of(a, b));
        followRepository.save(Follow.of(b, a));
    }

    private static void assertStatus(Runnable call, HttpStatus status) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ChatException.class, e -> assertThat(e.getStatus()).isEqualTo(status));
    }

    private ChatRoomService.RoomSummary summaryOf(User viewer, long roomId) {
        return service.rooms(viewer).stream().filter(r -> r.roomId() == roomId).findFirst().orElse(null);
    }

    // ── 방 열기 ─────────────────────────────────────────

    @Test
    void cannotOpenWithoutMutualFollow() {
        User me = toss("nm-me");
        User other = toss("nm-other");
        followRepository.save(Follow.of(me, other)); // 나만 팔로우 = 공개 프로필 기반 발송(만남 트랙 트리거)

        assertStatus(() -> service.openOrGet(me, other), HttpStatus.FORBIDDEN);
    }

    @Test
    void webOnlyPartnerIsConflict() {
        User me = toss("wo-me");
        User web = userRepository.save(
                User.of("wo-web@room.test", "$2a$10$abcdefghijklmnopqrstuv", "웹", "Asia/Seoul", Role.USER));
        mutual(me, web);

        assertStatus(() -> service.openOrGet(me, web), HttpStatus.CONFLICT);
    }

    @Test
    void samePairGetsOneNormalizedRoom() {
        User me = toss("pair-me");
        User other = toss("pair-other");
        mutual(me, other);

        ChatRoom first = service.openOrGet(other, me);
        ChatRoom second = service.openOrGet(me, other);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(first.getUserA().getId()).isLessThan(first.getUserB().getId());
    }

    @Test
    void emptyRoomIsNotListed() {
        User me = toss("empty-me");
        User other = toss("empty-other");
        mutual(me, other);

        ChatRoom room = service.openOrGet(me, other);

        assertThat(summaryOf(me, room.getId())).isNull();
        assertThat(summaryOf(other, room.getId())).isNull();
    }

    @Test
    void roomCreationIsRateLimited() {
        User me = toss("rl-me");
        for (int i = 0; i < 10; i++) {
            User other = toss("rl-o" + i);
            mutual(me, other);
            service.openOrGet(me, other);
        }
        User eleventh = toss("rl-o10");
        mutual(me, eleventh);

        assertStatus(() -> service.openOrGet(me, eleventh), HttpStatus.TOO_MANY_REQUESTS);
    }

    // ── 멤버가 아닌 사용자: 전 경로 404 ─────────────────────

    @Test
    void strangerSeesNothingAndTouchesNothing() {
        User a = toss("idor-a");
        User b = toss("idor-b");
        User stranger = toss("idor-x");
        mutual(a, b);
        ChatRoom room = service.openOrGet(a, b);
        ChatMessage msg = service.send(a, room.getId(), "둘만의 대화");
        long id = room.getId();

        assertThat(summaryOf(stranger, id)).isNull();
        assertStatus(() -> service.messages(stranger, id, 0), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.send(stranger, id, "끼어들기"), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.markRead(stranger, id, msg.getId()), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.hide(stranger, id), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.messages(a, 987654321L, 0), HttpStatus.NOT_FOUND);
    }

    // ── 자격 재검증: 언팔 → 잠김, 재맞팔 → 저절로 풀림 ───────

    @Test
    void unfollowLocksTheRoomAndRefollowUnlocksWithoutTransition() {
        User me = toss("lock-me");
        User other = toss("lock-other");
        mutual(me, other);
        ChatRoom room = service.openOrGet(me, other);
        service.send(me, room.getId(), "안녕");

        followRepository.deleteByFollowerAndFollowee(other, me); // 상대가 언팔

        assertStatus(() -> service.send(me, room.getId(), "왜 언팔?"), HttpStatus.FORBIDDEN);
        assertStatus(() -> service.send(other, room.getId(), "잠김"), HttpStatus.FORBIDDEN);
        ChatRoomService.RoomMessages view = service.messages(me, room.getId(), 0);
        assertThat(view.writable()).isFalse();
        assertThat(view.lockReason()).isEqualTo("NOT_MUTUAL");
        assertThat(view.messages()).hasSize(1); // 읽기는 된다(읽기 전용 잠금)
        assertThat(summaryOf(me, room.getId()).writable()).isFalse();

        followRepository.save(Follow.of(other, me)); // 다시 맞팔

        service.send(me, room.getId(), "다시 반가워");
        assertThat(service.messages(other, room.getId(), 0).writable()).isTrue();
    }

    @Test
    void restrictedSenderCannotSendButCanRead() {
        User me = toss("rs-me");
        User other = toss("rs-other");
        mutual(me, other);
        ChatRoom room = service.openOrGet(me, other);
        service.send(other, room.getId(), "안녕");

        me.restrictChatUntil(clock.instant().plus(Duration.ofDays(7)));

        assertStatus(() -> service.send(me, room.getId(), "보내기"), HttpStatus.FORBIDDEN);
        assertThat(service.messages(me, room.getId(), 0).lockReason()).isEqualTo("RESTRICTED");
    }

    // ── 차단 → CLOSED, 해제·재맞팔 → 같은 행이 되살아남 ─────

    @Test
    void blockClosesRoomForBothAndReopenKeepsHistory() {
        User me = toss("bk-me");
        User other = toss("bk-other");
        mutual(me, other);
        ChatRoom room = service.openOrGet(me, other);
        service.send(me, room.getId(), "옛 메시지");

        blockService.block(other, me);

        em.flush();
        em.clear();
        assertThat(summaryOf(me, room.getId())).isNull();
        assertThat(summaryOf(other, room.getId())).isNull();
        assertStatus(() -> service.send(me, room.getId(), "차단 뒤"), HttpStatus.CONFLICT);
        assertStatus(() -> service.openOrGet(me, other), HttpStatus.FORBIDDEN);

        blockService.unblock(other, me);
        User me2 = userRepository.findById(me.getId()).orElseThrow();
        User other2 = userRepository.findById(other.getId()).orElseThrow();
        assertStatus(() -> service.send(me2, room.getId(), "해제만으론 안 열림"), HttpStatus.CONFLICT);
        mutual(me2, other2);

        ChatRoom reopened = service.openOrGet(me2, other2);
        // 여는 것만으로는 옛 대화가 양쪽 대화함에 되살아나지 않는다(리뷰 사소 5 — §4-1 「첫 메시지가 되살린다」).
        assertThat(summaryOf(me2, room.getId())).isNull();
        assertThat(summaryOf(other2, room.getId())).isNull();
        service.send(me2, reopened.getId(), "다시 시작");

        assertThat(summaryOf(me2, room.getId())).isNotNull();
        assertThat(summaryOf(other2, room.getId())).isNotNull();
        assertThat(reopened.getId()).isEqualTo(room.getId());
        assertThat(service.messages(other2, room.getId(), 0).messages())
                .extracting(ChatRoomService.MessageView::body)
                .containsExactly("옛 메시지", "다시 시작");
    }

    // ── 나가기(내 쪽 숨김) ────────────────────────────────

    @Test
    void hiddenRoomReturnsWhenPartnerWrites() {
        User me = toss("hd-me");
        User other = toss("hd-other");
        mutual(me, other);
        ChatRoom room = service.openOrGet(me, other);
        service.send(other, room.getId(), "첫 메시지");

        service.hide(me, room.getId());
        assertThat(summaryOf(me, room.getId())).isNull();
        assertThat(summaryOf(other, room.getId())).isNotNull(); // 상대는 모른다

        service.send(other, room.getId(), "또 보냄");
        assertThat(summaryOf(me, room.getId())).isNotNull();
    }

    // ── 조회·읽음 ─────────────────────────────────────────

    @Test
    void messagesAfterCursorAreCappedAt200() {
        User me = toss("pg-me");
        User other = toss("pg-other");
        mutual(me, other);
        ChatRoom room = service.openOrGet(me, other);
        ChatMessage first = service.send(me, room.getId(), "m0");
        for (int i = 1; i < 205; i++) {
            advance(Duration.ofSeconds(1));
            service.send(i % 2 == 0 ? me : other, room.getId(), "m" + i);
        }

        List<ChatRoomService.MessageView> page = service.messages(me, room.getId(), first.getId()).messages();

        assertThat(page).hasSize(200);
        assertThat(page.get(0).body()).isEqualTo("m1");
        assertThat(page.get(0).id()).isGreaterThan(first.getId());
        assertThat(page).extracting(ChatRoomService.MessageView::mine).contains(true, false);
    }

    @Test
    void unreadCountsPartnerMessagesAndReadCannotRunAhead() {
        User me = toss("ur-me");
        User other = toss("ur-other");
        mutual(me, other);
        ChatRoom room = service.openOrGet(me, other);
        service.send(other, room.getId(), "하나");
        ChatMessage second = service.send(other, room.getId(), "둘");
        service.send(me, room.getId(), "내 것은 미읽음이 아니다");

        assertThat(summaryOf(me, room.getId()).unread()).isEqualTo(2);
        assertThat(service.unreadRooms(me)).isEqualTo(1);

        service.markRead(me, room.getId(), second.getId() + 1_000_000); // 미래 id로 앞질러 가기
        service.send(other, room.getId(), "셋");

        assertThat(summaryOf(me, room.getId()).unread()).isEqualTo(1); // 앞지르기가 막혀 새 글은 미읽음
        assertThat(summaryOf(me, room.getId()).lastMessage().body()).isEqualTo("셋");
    }

    // ── 발송 입력·표시·암호화 ─────────────────────────────

    @Test
    void bodyLengthIsValidated() {
        User me = toss("len-me");
        User other = toss("len-other");
        mutual(me, other);
        long id = service.openOrGet(me, other).getId();

        assertStatus(() -> service.send(me, id, "   "), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.send(me, id, "가".repeat(1001)), HttpStatus.BAD_REQUEST);
        assertThat(service.send(me, id, "가".repeat(1000)).getId()).isNotNull();
    }

    @Test
    void messageRateIsLimited() {
        User me = toss("mrl-me");
        User other = toss("mrl-other");
        mutual(me, other);
        long id = service.openOrGet(me, other).getId();
        for (int i = 0; i < 60; i++) {
            service.send(me, id, "도배 " + i);
        }

        assertStatus(() -> service.send(me, id, "61번째"), HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void suspiciousMessageIsFlaggedNotBlocked() {
        User me = toss("fl-me");
        User other = toss("fl-other");
        mutual(me, other);
        long id = service.openOrGet(me, other).getId();

        service.send(me, id, "카톡 아이디 줄게요");
        service.send(me, id, "이 책 좋아요");

        assertThat(service.messages(other, id, 0).messages())
                .extracting(ChatRoomService.MessageView::flagged)
                .containsExactly(true, false);
    }

    /** 리뷰 사소 4 — 복호화 안 되는 행 하나가 대화함·미읽음 전체를 500으로 만들지 않는다. 그 방만 빠진다. */
    @Test
    void undecryptableRoomIsSkippedNotFatal() {
        User me = toss("bad-me");
        User broken = toss("bad-broken");
        User fine = toss("bad-fine");
        mutual(me, broken);
        mutual(me, fine);
        long brokenRoom = service.openOrGet(me, broken).getId();
        long fineRoom = service.openOrGet(me, fine).getId();
        service.send(broken, brokenRoom, "곧 깨질 메시지");
        service.send(fine, fineRoom, "멀쩡한 메시지");
        em.flush();
        jdbc.update("update chat_message set body = ? where room_id = ?", new byte[40], brokenRoom); // 태그가 안 맞는 쓰레기
        em.clear();

        assertThat(service.rooms(me)).extracting(ChatRoomService.RoomSummary::roomId).containsExactly(fineRoom);
        assertThat(service.unreadRooms(me)).isEqualTo(1);
    }

    @Test
    void bodyIsEncryptedAtRestAndDecryptedOnRead() {
        User me = toss("enc-me");
        User other = toss("enc-other");
        mutual(me, other);
        long id = service.openOrGet(me, other).getId();
        service.send(me, id, "plain-marker-평문");
        em.flush();
        em.clear();

        byte[] raw = jdbc.queryForObject("select body from chat_message where room_id = ?", byte[].class, id);

        assertThat(new String(raw, StandardCharsets.UTF_8)).doesNotContain("plain-marker");
        assertThat(service.messages(other, id, 0).messages().get(0).body()).isEqualTo("plain-marker-평문");
    }
}
