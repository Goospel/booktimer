package com.booktimer.chat;

import com.booktimer.block.BlockService;
import com.booktimer.chat.ChatSanctionService.Action;
import com.booktimer.follow.Follow;
import com.booktimer.follow.FollowRepository;
import com.booktimer.report.Report;
import com.booktimer.report.ReportRepository;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 차단으로 닫힌 방의 30일 보존 삭제(정책 문서 §5·§6). 실 H2 — 방을 지우는 경로라 FK(메시지·신고 참조)를 실제로 밟는다.
 *
 * <p>단독으로 잡는 실패: 30일 전에 지우는 것 · 열린 방을 지우는 것 · 처리 전 신고나 법적 보존이 걸린 방을
 * 지우는 것(증거 소실) · 처리 끝난 신고가 가리키는 방을 지우다 FK 위반으로 배치 전체가 죽는 것.
 */
@SpringBootTest
@Transactional
class ChatRetentionServiceTest {

    @Autowired ChatRetentionService retention;
    @Autowired ChatRoomService rooms;
    @Autowired ChatSafetyService safety;
    @Autowired ChatRoomRepository roomRepository;
    @Autowired ChatMessageRepository messageRepository;
    @Autowired ReportRepository reportRepository;
    @Autowired BlockService blockService;
    @Autowired UserRepository userRepository;
    @Autowired FollowRepository followRepository;
    @Autowired EntityManager em;
    @Autowired Clock clock;

    private User toss(String name) {
        User u = User.of(name + "@keep.test", "$2a$10$abcdefghijklmnopqrstuv", name, "Asia/Seoul", Role.USER);
        u.assignLoginId(name.replace("-", ""));
        u.linkTossUserKey("uk-" + name);
        return userRepository.save(u);
    }

    /** 대화가 있고 b가 a를 차단해 닫힌 방. */
    private long closedRoom(String prefix) {
        User a = toss(prefix + "-a");
        User b = toss(prefix + "-b");
        followRepository.save(Follow.of(a, b));
        followRepository.save(Follow.of(b, a));
        long id = rooms.openOrGet(a, b).getId();
        rooms.send(a, id, "남을까 지워질까");
        blockService.block(b, a);
        return id;
    }

    private Instant closedAt(long id) {
        return roomRepository.findById(id).orElseThrow().getClosedAt();
    }

    private void purgeAt(Instant now) {
        retention.purgeExpiredClosedRooms(now);
        em.flush();
        em.clear();
    }

    @Test
    void keepsForThirtyDaysThenDeletesRoomAndMessages() {
        long id = closedRoom("k30");
        Instant closed = closedAt(id);

        purgeAt(closed.plus(Duration.ofDays(29)));
        assertThat(roomRepository.findById(id)).isPresent();

        purgeAt(closed.plus(Duration.ofDays(31)));
        assertThat(roomRepository.findById(id)).isEmpty();
        assertThat(messageRepository.count()).isZero();
    }

    @Test
    void openRoomsAreNeverTouched() {
        User a = toss("open-a");
        User b = toss("open-b");
        followRepository.save(Follow.of(a, b));
        followRepository.save(Follow.of(b, a));
        long id = rooms.openOrGet(a, b).getId();
        rooms.send(a, id, "살아 있는 대화");

        purgeAt(clock.instant().plus(Duration.ofDays(400)));

        assertThat(roomRepository.findById(id)).isPresent();
    }

    @Test
    void openReportKeepsTheRoom() {
        long id = closedRoom("rep");
        User reporter = roomRepository.findById(id).orElseThrow().getUserA();
        safety.reportRoom(reporter, id, "SPAM", null);

        purgeAt(closedAt(id).plus(Duration.ofDays(31)));

        assertThat(roomRepository.findById(id)).isPresent();
    }

    @Test
    void legalHoldKeepsTheRoomEvenAfterResolution() {
        long id = closedRoom("hold");
        User reporter = roomRepository.findById(id).orElseThrow().getUserA();
        Report r = safety.reportRoom(reporter, id, "SPAM", null);
        safety.resolve(r.getId(), Action.NONE);
        safety.setLegalHold(r.getId(), true);

        purgeAt(closedAt(id).plus(Duration.ofDays(31)));

        assertThat(roomRepository.findById(id)).isPresent();
    }

    @Test
    void resolvedReportLetsTheRoomGoButTheReportStays() {
        long id = closedRoom("done");
        User reporter = roomRepository.findById(id).orElseThrow().getUserA();
        Report r = safety.reportRoom(reporter, id, "SPAM", null);
        safety.resolve(r.getId(), Action.WARN);
        long reportId = r.getId();

        purgeAt(closedAt(id).plus(Duration.ofDays(31)));

        assertThat(roomRepository.findById(id)).isEmpty();
        Report kept = reportRepository.findById(reportId).orElseThrow();
        assertThat(kept.getChatRoomId()).isNull();          // 가리키던 방만 풀린다
        assertThat(kept.getResolution()).isEqualTo("WARN"); // 조치 기록은 남는다
    }
}
