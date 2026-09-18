package com.booktimer.web.api;

import com.booktimer.chat.ChatException;
import com.booktimer.chat.ChatGate;
import com.booktimer.chat.ChatMessage;
import com.booktimer.chat.ChatRoomService;
import com.booktimer.security.CurrentUserService;
import com.booktimer.security.RateLimitAction;
import com.booktimer.security.RateLimitService;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 맞팔 DM API {@code /api/chat/**}(설계 §5-3). 모든 경로가 먼저 {@link ChatGate}를 지난다 — 스위치 OFF면 404.
 *
 * <p>표면 중립이다(미니앱 Bearer·웹 세션 둘 다 같은 사용자 해석). 판단은 전부 {@link ChatRoomService}에 있고
 * 여기는 입출력 모양과 폴링 상한만 진다.
 */
@RestController
public class ChatApiController {

    private final ChatGate gate;
    private final ChatRoomService chatRoomService;
    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;
    private final RateLimitService rateLimitService;

    public ChatApiController(ChatGate gate,
                             ChatRoomService chatRoomService,
                             CurrentUserService currentUserService,
                             UserRepository userRepository,
                             RateLimitService rateLimitService) {
        this.gate = gate;
        this.chatRoomService = chatRoomService;
        this.currentUserService = currentUserService;
        this.userRepository = userRepository;
        this.rateLimitService = rateLimitService;
    }

    @GetMapping("/api/chat/me")
    public MeResponse me(Principal principal) {
        User me = pollingUser(principal);
        return new MeResponse(chatRoomService.unreadRooms(me), me.getChatRestrictedUntil(), me.getChatBannedAt() != null);
    }

    @PostMapping("/api/chat/rooms")
    public Map<String, Long> open(@RequestBody OpenRequest request, Principal principal) {
        User me = user(principal);
        User other = resolveTarget(request.loginId());
        long roomId;
        try {
            roomId = chatRoomService.openOrGet(me, other).getId();
        } catch (DataIntegrityViolationException raced) {
            // 두 사람이 동시에 첫 방을 열어 uk_chat_room_pair가 막았다 — 새 트랜잭션에서 상대가 만든 행을 찾는다.
            roomId = chatRoomService.openOrGet(me, other).getId();
        }
        return Map.of("roomId", roomId);
    }

    @GetMapping("/api/chat/rooms")
    public List<ChatRoomService.RoomSummary> rooms(Principal principal) {
        return chatRoomService.rooms(pollingUser(principal));
    }

    @GetMapping("/api/chat/rooms/{roomId}/messages")
    public ChatRoomService.RoomMessages messages(@PathVariable long roomId,
                                                 @RequestParam(defaultValue = "0") long after,
                                                 Principal principal) {
        return chatRoomService.messages(pollingUser(principal), roomId, after);
    }

    @PostMapping("/api/chat/rooms/{roomId}/messages")
    public SentResponse send(@PathVariable long roomId, @RequestBody SendRequest request, Principal principal) {
        ChatMessage saved = chatRoomService.send(user(principal), roomId, request.body());
        return new SentResponse(saved.getId(), saved.getCreatedAt());
    }

    @PostMapping("/api/chat/rooms/{roomId}/read")
    public Map<String, Object> read(@PathVariable long roomId, @RequestBody ReadRequest request, Principal principal) {
        chatRoomService.markRead(user(principal), roomId, request.lastMessageId());
        return Map.of();
    }

    @PostMapping("/api/chat/rooms/{roomId}/hide")
    public Map<String, Object> hide(@PathVariable long roomId, Principal principal) {
        chatRoomService.hide(user(principal), roomId);
        return Map.of();
    }

    /** 문구는 미니앱이 그대로 띄운다. text/plain + UTF-8 고정(반사 XSS·한글 깨짐 방지 — 계정 API와 같은 계약). */
    @ExceptionHandler(ChatException.class)
    public ResponseEntity<String> handle(ChatException e) {
        return ResponseEntity.status(e.getStatus())
                .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                .body(e.getMessage());
    }

    private User user(Principal principal) {
        gate.requireEnabled();
        return currentUserService.resolve(principal);
    }

    /** 폴링 경로(대화함·방·미읽음)는 한 키로 센다 — 초과 429는 클라가 다음 틱으로 넘긴다. */
    private User pollingUser(Principal principal) {
        User me = user(principal);
        if (!rateLimitService.allow(RateLimitAction.CHAT_POLL, me.getId())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
        }
        return me;
    }

    /** 운영자는 없는 것으로 본다 — 팔로우 API와 같은 이유(핸들 존재 확인 누설 방지). */
    private User resolveTarget(String loginId) {
        return userRepository.findByLoginId(loginId)
                .filter(u -> u.getRole() != Role.ADMIN)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다"));
    }

    public record OpenRequest(String loginId) {
    }

    public record SendRequest(String body) {
    }

    public record ReadRequest(long lastMessageId) {
    }

    public record MeResponse(long unreadRooms, Instant restrictedUntil, boolean banned) {
    }

    public record SentResponse(long id, Instant createdAt) {
    }
}
