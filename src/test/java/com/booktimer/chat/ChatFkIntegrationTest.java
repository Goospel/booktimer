package com.booktimer.chat;

import com.booktimer.follow.Follow;
import com.booktimer.follow.FollowRepository;
import com.booktimer.user.AccountService;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 대화가 있는 사용자의 탈퇴 — 실 H2 스키마로 FK 순서를 본다(mock은 FK를 모른다, T-023·T-029).
 * 단 이 스키마는 엔티티 매핑에서 만들어져, 엔티티 연관으로 선언된 FK(메시지 → 방·유저, 방 → 유저)만 있다.
 * V96의 신고 → 방 FK는 없다(아래 두 번째 테스트 주석).
 *
 * <p>{@code chat_message}는 {@code chat_room}과 {@code users}를 둘 다 참조하고 {@code chat_room}은 {@code users}를
 * 두 번 참조한다. purge가 메시지 → 방 순서를 어기거나 한쪽을 빼먹으면, 대화를 한 번이라도 한 사람은
 * <b>탈퇴 자체가 제약 위반으로 실패한다</b>. 상대 쪽에서 방이 사라지는지도 같이 본다(유령 방 금지).
 */
@SpringBootTest
@Transactional
class ChatFkIntegrationTest {

    @Autowired AccountService accountService;
    @Autowired ChatRoomService chatRoomService;
    @Autowired UserRepository userRepository;
    @Autowired FollowRepository followRepository;
    @Autowired ChatRoomRepository chatRoomRepository;
    @Autowired ChatMessageRepository chatMessageRepository;
    @Autowired EntityManager em;
    @Autowired ChatSafetyService chatSafetyService;
    @Autowired com.booktimer.report.ReportRepository reportRepository;

    private User toss(String name) {
        User u = User.of(name + "@fk.test", "$2a$10$abcdefghijklmnopqrstuv", name, "Asia/Seoul", Role.USER);
        u.assignLoginId(name.replace("-", ""));
        u.linkTossUserKey("uk-" + name);
        return userRepository.save(u);
    }

    @Test
    void userWithRoomsAndMessagesCanLeaveAndPartnerLosesTheRoom() {
        User quitter = toss("fk-quit");
        User partner = toss("fk-partner");
        User third = toss("fk-third");
        followRepository.save(Follow.of(quitter, partner));
        followRepository.save(Follow.of(partner, quitter));
        followRepository.save(Follow.of(partner, third));
        followRepository.save(Follow.of(third, partner));

        long room = chatRoomService.openOrGet(quitter, partner).getId();
        chatRoomService.send(quitter, room, "내가 보낸 것");
        chatRoomService.send(partner, room, "상대가 보낸 것"); // 탈퇴자가 sender가 아닌 메시지도 방 FK로 걸린다
        long keep = chatRoomService.openOrGet(partner, third).getId();
        chatRoomService.send(third, keep, "남는 대화");
        em.flush();

        assertThatCode(() -> {
            accountService.deleteTossVerifiedAccount(quitter, "uk-fk-quit");
            em.flush(); // 위반은 flush 시점에 터진다
        }).doesNotThrowAnyException();

        em.clear();
        assertThat(userRepository.findById(quitter.getId())).isEmpty();
        assertThat(chatRoomRepository.findById(room)).isEmpty();
        User partnerNow = userRepository.findById(partner.getId()).orElseThrow();
        assertThat(chatRoomService.rooms(partnerNow))
                .extracting(ChatRoomService.RoomSummary::roomId)
                .containsExactly(keep); // 남의 방은 건드리지 않는다
        assertThat(chatMessageRepository.count()).isEqualTo(1);
    }

    /**
     * V96 — 신고가 방을 FK로 가리킨다. 법적 보존이 걸린 신고여도 탈퇴는 즉시 삭제다(정책 문서 §5 — 보존 표시는
     * <b>자동</b> 삭제에서만 뺀다).
     *
     * <p>⚠️ 이 테스트가 판정하는 것은 「탈퇴가 끝까지 가고 방 참조 신고가 남지 않는다」까지다. <b>신고 → 방 FK 순서</b>는
     * 여기서 판정되지 않는다 — 메인 스위트는 Hibernate 스키마라 {@code Report.chatRoomId}(평범한 Long)에 대한
     * V96 FK가 없다. 그 순서는 V96 스키마 위의 {@code FlywayMigrationTest}가 본다(리뷰 #1169 중요 1).
     */
    @Test
    void userWithRoomReferencingReportsCanLeave() {
        User quitter = toss("fk-rep-quit");
        User partner = toss("fk-rep-partner");
        followRepository.save(Follow.of(quitter, partner));
        followRepository.save(Follow.of(partner, quitter));
        long room = chatRoomService.openOrGet(quitter, partner).getId();
        chatRoomService.send(quitter, room, "신고당할 말");
        var byPartner = chatSafetyService.reportRoom(partner, room, "SPAM", null);
        chatSafetyService.setLegalHold(byPartner.getId(), true);
        chatSafetyService.reportRoom(quitter, room, "OTHER", "맞신고");
        em.flush();

        assertThatCode(() -> {
            accountService.deleteTossVerifiedAccount(quitter, "uk-fk-rep-quit");
            em.flush();
        }).doesNotThrowAnyException();

        em.clear();
        assertThat(chatRoomRepository.findById(room)).isEmpty();
        assertThat(reportRepository.findAll()).noneMatch(r -> Long.valueOf(room).equals(r.getChatRoomId()));
    }
}
