package com.booktimer.chat;

import com.booktimer.chat.ChatEligibility.Verdict;
import com.booktimer.security.RateLimitAction;
import com.booktimer.security.RateLimitService;
import com.booktimer.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 맞팔 DM 방·메시지 유스케이스(설계 §5-2).
 *
 * <p><b>자격은 매 경로에서 다시 본다</b>: 방 열기·발송은 {@link ChatEligibility}가 OK가 아니면 거부하고,
 * 목록·조회는 같은 판정으로 {@code writable}·{@code lockReason}을 파생한다. 발송의 재검증은 저장과 <b>같은
 * 트랜잭션 안</b>이다 — 언팔 직후의 발송이 판정과 저장 사이로 새지 않게.
 *
 * <p>멤버가 아닌 사용자의 방 경로는 전부 404다({@link ChatException#notFound()}) — 403이면 그 id의 방이
 * 있다는 사실이 샌다. 자기 방에서 자격이 없는 것은 403이다(존재가 이미 자기에게 알려져 있다).
 */
@Service
@Transactional
public class ChatRoomService {

    private final ChatRoomRepository roomRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatEligibility eligibility;
    private final RateLimitService rateLimitService;
    private final ChatPushService pushService;
    private final ChatProperties properties;
    private final Clock clock;

    public ChatRoomService(ChatRoomRepository roomRepository,
                           ChatMessageRepository messageRepository,
                           ChatEligibility eligibility,
                           RateLimitService rateLimitService,
                           ChatPushService pushService,
                           ChatProperties properties,
                           Clock clock) {
        this.roomRepository = roomRepository;
        this.messageRepository = messageRepository;
        this.eligibility = eligibility;
        this.rateLimitService = rateLimitService;
        this.pushService = pushService;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 두 사람의 방을 돌려준다 — 없으면 만들고, 차단으로 닫혀 있었으면(그 뒤 해제·재맞팔) 같은 행을 되살린다.
     *
     * <p>방 행은 여기서 생기지만 <b>빈 방은 어느 목록에도 안 뜬다</b>({@link #rooms}) — 목록에 나타나는 시점은
     * 첫 메시지다(설계 §4-1 「빈 방 없음」). 동시 첫 열기의 {@code uk_chat_room_pair} 충돌은 호출자가 한 번
     * 재시도하면 이 메서드가 기존 행을 찾는다(새 트랜잭션이어야 해서 여기서 잡지 않는다).
     */
    public ChatRoom openOrGet(User me, User other) {
        requireOk(eligibility.check(me, other));
        ChatRoom probe = ChatRoom.of(me, other);
        ChatRoom room = roomRepository.findByUserAAndUserB(probe.getUserA(), probe.getUserB()).orElse(null);
        if (room == null) {
            if (!rateLimitService.allow(RateLimitAction.CHAT_ROOM_OPEN, me.getId())) {
                throw ChatException.rateLimited();
            }
            return roomRepository.saveAndFlush(probe);
        }
        if (!room.isOpen()) {
            room.reopen();
        }
        return room;
    }

    /** 대화함 — 열린 방 중 내가 숨기지 않았고 메시지가 하나라도 있는 것, 최근 메시지 순. */
    @Transactional(readOnly = true)
    public List<RoomSummary> rooms(User me) {
        // ponytail: 방마다 마지막 메시지·미읽음·자격을 따로 묻는다(방 N개 → 쿼리 ~5N). 맞팔 DM 방은 사람당
        // 한 자릿수라 충분하다. 수십 개가 되면 마지막 메시지·미읽음을 group by 한 번으로 모은다.
        return roomRepository.findOpenByMember(me).stream()
                .filter(r -> !r.isHiddenFor(me))
                .map(r -> summarize(me, r))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingLong(RoomSummary::lastMessageId).reversed())
                .toList();
    }

    /** 미읽음이 있는 방 수 — 홈 카드·책방 배지용. */
    @Transactional(readOnly = true)
    public long unreadRooms(User me) {
        return rooms(me).stream().filter(r -> r.unread() > 0).count();
    }

    /** 방의 메시지 — {@code afterId} 이후 오래된 순 최대 200건 + 지금 보낼 수 있는지. 멤버가 아니면 404. */
    @Transactional(readOnly = true)
    public RoomMessages messages(User me, long roomId, long afterId) {
        ChatRoom room = memberRoom(me, roomId);
        List<MessageView> views = messageRepository.findTop200ByRoomAndIdGreaterThanOrderByIdAsc(room, afterId)
                .stream()
                .map(m -> MessageView.of(m, me))
                .toList();
        String lock = lockReason(me, room);
        return new RoomMessages(views, lock == null, lock);
    }

    /**
     * 보낸다. 순서: 멤버(404) → 본문(400) → 닫힘(409) → <b>자격 재검증</b>(403·409) → 속도(429) → 저장.
     * 저장 후 숨김을 풀고(상대가 「나가기」 했어도 새 메시지면 돌아온다), 상대에게 방·수신자당 {@link ChatProperties#getPushIntervalMinutes()}분에 1통 푸시한다.
     */
    public ChatMessage send(User me, long roomId, String text) {
        ChatRoom room = memberRoom(me, roomId);
        if (text == null || text.isBlank()) {
            throw ChatException.badRequest("메시지를 입력해 주세요.");
        }
        if (text.length() > ChatMessage.MAX_LENGTH) {
            throw ChatException.badRequest("메시지는 " + ChatMessage.MAX_LENGTH + "자까지 보낼 수 있어요.");
        }
        if (!room.isOpen()) {
            throw ChatException.closed();
        }
        User partner = room.partnerOf(me);
        requireOk(eligibility.check(me, partner));
        if (!rateLimitService.allow(RateLimitAction.CHAT_MESSAGE, me.getId())) {
            throw ChatException.rateLimited();
        }
        Instant now = clock.instant();
        ChatMessage saved = messageRepository.save(
                ChatMessage.of(room, me, text, ContactExchangeDetector.suspicious(text), now));
        room.unhideAll();
        pushIfDue(room, partner, now);
        return saved;
    }

    /** 읽음을 올린다 — 뒤로 가지 않고, 방의 마지막 메시지를 앞지르지 않는다(미래 id로 새 글을 미리 읽음 처리 금지). */
    public void markRead(User me, long roomId, long lastMessageId) {
        ChatRoom room = memberRoom(me, roomId);
        long latest = messageRepository.findTopByRoomOrderByIdDesc(room).map(ChatMessage::getId).orElse(0L);
        room.markRead(me, Math.min(lastMessageId, latest));
    }

    /** 「나가기」 — 내 쪽만 숨긴다. 상대는 모르고, 상대가 새 메시지를 보내면 다시 뜬다. */
    public void hide(User me, long roomId) {
        memberRoom(me, roomId).hide(me);
    }

    /** 차단 훅({@code BlockService.block}) — 둘의 방이 있으면 닫는다. 해제해도 닫힌 채다(다시 열려면 재맞팔). */
    public void closeByBlock(User a, User b) {
        ChatRoom probe = ChatRoom.of(a, b);
        roomRepository.findByUserAAndUserB(probe.getUserA(), probe.getUserB())
                .ifPresent(room -> room.close(clock.instant()));
    }

    // ── 내부 ─────────────────────────────────────────────

    private ChatRoom memberRoom(User me, long roomId) {
        return roomRepository.findById(roomId).filter(r -> r.has(me)).orElseThrow(ChatException::notFound);
    }

    private static void requireOk(Verdict verdict) {
        if (verdict != Verdict.OK) {
            throw ChatException.denied(verdict);
        }
    }

    /** 잠김 사유 — 저장하지 않고 지금 파생한다. 보낼 수 있으면 {@code null}. */
    private String lockReason(User me, ChatRoom room) {
        if (!room.isOpen()) {
            return "CLOSED";
        }
        Verdict v = eligibility.check(me, room.partnerOf(me));
        return v == Verdict.OK ? null : v.name();
    }

    private RoomSummary summarize(User me, ChatRoom room) {
        ChatMessage last = messageRepository.findTopByRoomOrderByIdDesc(room).orElse(null);
        if (last == null) {
            return null; // 빈 방은 목록에 없다
        }
        User partner = room.partnerOf(me);
        String lock = lockReason(me, room);
        long unread = messageRepository.countByRoomAndSenderNotAndIdGreaterThan(room, me, room.lastReadIdOf(me));
        return new RoomSummary(room.getId(), new Partner(partner.getLoginId(), partner.getNickname()),
                lock == null, lock, new LastMessage(last.getBody(), last.getSender().getId().equals(me.getId()),
                last.getCreatedAt()), unread, last.getId());
    }

    /**
     * 방·수신자당 N분에 1통. 성공했을 때만 시각을 남겨, 실패하면 다음 메시지가 다시 시도한다.
     *
     * <p>ponytail: 토스 호출(타임아웃 3초)이 발송 트랜잭션 안에서 돈다 — 실패해도 던지지 않아 메시지 저장은
     * 안전하지만, 느린 토스가 응답을 붙잡는다. 발송량이 늘면 커밋 후 비동기로 뺀다.
     */
    private void pushIfDue(ChatRoom room, User recipient, Instant now) {
        Instant last = room.lastPushAtOf(recipient);
        Duration interval = Duration.ofMinutes(properties.getPushIntervalMinutes());
        if (last != null && now.isBefore(last.plus(interval))) {
            return;
        }
        if (pushService.push(recipient)) {
            room.markPushed(recipient, now);
        }
    }

    // ── 응답 모양 ────────────────────────────────────────

    public record Partner(String loginId, String nickname) {
    }

    public record LastMessage(String body, boolean mine, Instant createdAt) {
    }

    /** @param lastMessageId 정렬 키 — 응답엔 {@code lastMessage}가 있어 따로 쓸 일은 없다. */
    public record RoomSummary(long roomId, Partner partner, boolean writable, String lockReason,
                              LastMessage lastMessage, long unread, long lastMessageId) {
    }

    public record MessageView(long id, boolean mine, String body, boolean flagged, Instant createdAt) {
        static MessageView of(ChatMessage m, User me) {
            return new MessageView(m.getId(), m.getSender().getId().equals(me.getId()), m.getBody(),
                    m.isFlagged(), m.getCreatedAt());
        }
    }

    public record RoomMessages(List<MessageView> messages, boolean writable, String lockReason) {
    }
}
