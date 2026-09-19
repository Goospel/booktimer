package com.booktimer.chat;

import com.booktimer.chat.ChatSanctionService.Action;
import com.booktimer.follow.Follow;
import com.booktimer.follow.FollowRepository;
import com.booktimer.report.Report;
import com.booktimer.report.ReportReason;
import com.booktimer.report.ReportRepository;
import com.booktimer.report.ReportService;
import com.booktimer.report.ReportStatus;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * 대화방 신고·제재(설계 §5-2·§5-5, 정책 문서 §2·§3). 실 빈 + H2.
 *
 * <p>단독으로 잡는 실패: 신고가 방을 기록하지 않아 운영자가 대본을 못 여는 것 · 남의 방을 신고하는 것 ·
 * 같은 신고자가 여러 번 눌러 자동 정지가 걸리는 것(「서로 다른 신고자 2명」이 1명으로 무너짐) · 대화와 무관한
 * 프로필 신고가 대화 정지를 거는 것 · 자동 정지가 운영자가 건 더 긴 정지·영구 정지를 줄이는 것 ·
 * 처리 결과(경고·정지·영구·해제)가 사용자 상태에 안 닿는 것.
 */
@SpringBootTest
@Transactional
class ChatSafetyServiceTest {

    @Autowired ChatSafetyService safety;
    @Autowired ChatSanctionService sanctions;
    @Autowired ChatRoomService rooms;
    @Autowired ReportRepository reportRepository;
    @Autowired ReportService reportService;
    @Autowired UserRepository userRepository;
    @Autowired FollowRepository followRepository;
    @Autowired Clock clock;

    private User toss(String name) {
        User u = User.of(name + "@safety.test", "$2a$10$abcdefghijklmnopqrstuv", name, "Asia/Seoul", Role.USER);
        u.assignLoginId(name.replace("-", ""));
        u.linkTossUserKey("uk-" + name);
        return userRepository.save(u);
    }

    private long roomOf(User a, User b) {
        followRepository.save(Follow.of(a, b));
        followRepository.save(Follow.of(b, a));
        long id = rooms.openOrGet(a, b).getId();
        rooms.send(b, id, "신고당할 메시지");
        return id;
    }

    private static void assertStatus(Runnable call, HttpStatus status) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ChatException.class, e -> assertThat(e.getStatus()).isEqualTo(status));
    }

    @Test
    void reportRecordsTheRoomAndLeavesItOpen() {
        User me = toss("rep-me");
        User other = toss("rep-other");
        long room = roomOf(me, other);

        Report r = safety.reportRoom(me, room, "SPAM", "링크를 계속 보내요");

        assertThat(r.getChatRoomId()).isEqualTo(room);
        assertThat(r.getReported().getId()).isEqualTo(other.getId());
        assertThat(r.getReason()).isEqualTo(ReportReason.SPAM);
        assertThat(r.getStatus()).isEqualTo(ReportStatus.OPEN);
        assertThat(rooms.messages(me, room, 0).writable()).isTrue(); // 방은 그대로 — 끝내려면 차단
    }

    @Test
    void strangerCannotReportSomeoneElsesRoom() {
        User a = toss("rs-a");
        User b = toss("rs-b");
        User x = toss("rs-x");
        long room = roomOf(a, b);

        assertStatus(() -> safety.reportRoom(x, room, "SPAM", null), HttpStatus.NOT_FOUND);
    }

    @Test
    void reReportIsIdempotentButReopensAResolvedOne() {
        User me = toss("rr-me");
        User other = toss("rr-other");
        long room = roomOf(me, other);
        Report first = safety.reportRoom(me, room, "SPAM", "처음");
        safety.reportRoom(me, room, "OTHER", "또");

        assertThat(reportRepository.findAll()).filteredOn(r -> r.getReporter().getId().equals(me.getId())).hasSize(1);
        assertThat(first.getDetail()).isEqualTo("처음"); // 첫 신고 보존

        safety.resolve(first.getId(), Action.NONE);
        safety.reportRoom(me, room, "SPAM", "다시");

        assertThat(first.getStatus()).isEqualTo(ReportStatus.OPEN);
    }

    @Test
    void existingProfileReportGetsTheRoomAttached() {
        User me = toss("pr-me");
        User other = toss("pr-other");
        long room = roomOf(me, other);
        reportService.report(me, other, ReportReason.HARASSMENT, "프로필에서 먼저 신고");

        Report r = safety.reportRoom(me, room, "SPAM", null);

        assertThat(r.getChatRoomId()).isEqualTo(room);
        assertThat(r.getReason()).isEqualTo(ReportReason.HARASSMENT);
    }

    // ── 자동 7일 정지: 서로 다른 신고자 2명 ─────────────

    @Test
    void twoDistinctReportersRestrictForSevenDays() {
        User target = toss("auto-target");
        User r1 = toss("auto-r1");
        User r2 = toss("auto-r2");
        long room1 = roomOf(r1, target);
        long room2 = roomOf(r2, target);

        safety.reportRoom(r1, room1, "SPAM", null);
        assertThat(target.isChatRestricted(clock.instant())).isFalse(); // 1명으로는 안 걸린다

        safety.reportRoom(r2, room2, "SPAM", null);

        assertThat(target.getChatRestrictedUntil()).isCloseTo(clock.instant().plus(Duration.ofDays(7)), within(1, ChronoUnit.SECONDS));
    }

    @Test
    void sameReporterTwiceDoesNotRestrict() {
        User target = toss("same-target");
        User r1 = toss("same-r1");
        long room = roomOf(r1, target);

        safety.reportRoom(r1, room, "SPAM", null);
        safety.reportRoom(r1, room, "HARASSMENT", "또 신고");

        assertThat(target.isChatRestricted(clock.instant())).isFalse();
    }

    @Test
    void profileReportsDoNotCountTowardChatRestriction() {
        User target = toss("prof-target");
        User r1 = toss("prof-r1");
        User r2 = toss("prof-r2");
        long room = roomOf(r1, target);
        reportService.report(r2, target, ReportReason.SPAM, "프로필 신고 — 대화와 무관");

        safety.reportRoom(r1, room, "SPAM", null);

        assertThat(target.isChatRestricted(clock.instant())).isFalse();
    }

    @Test
    void resolvedReportsDoNotCount() {
        User target = toss("res-target");
        User r1 = toss("res-r1");
        User r2 = toss("res-r2");
        long room1 = roomOf(r1, target);
        long room2 = roomOf(r2, target);
        Report first = safety.reportRoom(r1, room1, "SPAM", null);
        safety.resolve(first.getId(), Action.NONE); // 무혐의로 닫힘

        safety.reportRoom(r2, room2, "SPAM", null);

        assertThat(target.isChatRestricted(clock.instant())).isFalse();
    }

    @Test
    void autoRestrictionNeverShortensAnOperatorSanction() {
        User target = toss("long-target");
        User r1 = toss("long-r1");
        User r2 = toss("long-r2");
        long room1 = roomOf(r1, target);
        long room2 = roomOf(r2, target);
        Instant longer = clock.instant().plus(Duration.ofDays(30));
        target.restrictChatUntil(longer);

        safety.reportRoom(r1, room1, "SPAM", null);
        safety.reportRoom(r2, room2, "SPAM", null);

        assertThat(target.getChatRestrictedUntil()).isEqualTo(longer);
    }

    /** 방어선 ① — 자동 정지의 조기 반환. 영구 정지 중이면 정지 기간을 새로 찍지 않는다(`later()`로는 못 막는 경우). */
    @Test
    void autoRestrictionLeavesABannedUserUntouched() {
        User target = toss("ban-target");
        User r1 = toss("ban-r1");
        User r2 = toss("ban-r2");
        long room1 = roomOf(r1, target);
        long room2 = roomOf(r2, target);
        target.banChat(clock.instant());

        safety.reportRoom(r1, room1, "SPAM", null);
        safety.reportRoom(r2, room2, "SPAM", null);

        assertThat(target.getChatRestrictedUntil()).isNull();
    }

    /** 방어선 ② — {@code apply(SUSPEND_7D)} 자체가 더 긴 정지를 줄이지 않는다(운영자가 직접 7일을 골라도). */
    @Test
    void suspendNeverShortensALongerRestriction() {
        User u = toss("later-u");
        Instant longer = clock.instant().plus(Duration.ofDays(30));
        u.restrictChatUntil(longer);

        sanctions.apply(u, Action.SUSPEND_7D);

        assertThat(u.getChatRestrictedUntil()).isEqualTo(longer);
    }

    /** 리뷰 #1169 사소 4 — 처리 끝난 신고의 재신고(RESOLVED → OPEN)도 자동 정지 판정을 다시 부른다. */
    @Test
    void reReportingAResolvedReportCountsAgain() {
        User target = toss("again-target");
        User r1 = toss("again-r1");
        User r2 = toss("again-r2");
        long room1 = roomOf(r1, target);
        long room2 = roomOf(r2, target);
        Report first = safety.reportRoom(r1, room1, "SPAM", null);
        safety.resolve(first.getId(), Action.NONE);
        safety.reportRoom(r2, room2, "SPAM", null);
        assertThat(target.isChatRestricted(clock.instant())).isFalse(); // 미처리는 r2 하나뿐

        safety.reportRoom(r1, room1, "HARASSMENT", "또 그래요");

        assertThat(target.isChatRestricted(clock.instant())).isTrue();
    }

    /** 리뷰 #1169 사소 3 — 재신고로 되살릴 때 새 사유·상세·접수 시각을 쓴다. */
    @Test
    void reReportTakesTheNewReasonDetailAndTime() {
        User me = toss("new-me");
        User other = toss("new-other");
        long room = roomOf(me, other);
        Report r = safety.reportRoom(me, room, "SPAM", "처음 사유");
        safety.resolve(r.getId(), Action.WARN);

        safety.reportRoom(me, room, "HARASSMENT", "다시 괴롭혀요");

        assertThat(r.getReason()).isEqualTo(ReportReason.HARASSMENT);
        assertThat(r.getDetail()).isEqualTo("다시 괴롭혀요");
        assertThat(r.getReportedAt()).isNotNull();
    }

    /**
     * 리뷰 #1169 중요 2 — 운영자는 <b>신고 시점까지의</b> 대화만 본다. 신고 뒤 오간 메시지는 처리가 끝나도 방이 열려
     * 있는 한 기한 없이 보이던 구멍이다. 같은 신고자가 다시 신고하면 그 시점까지로 넓어진다.
     */
    @Test
    void transcriptStopsAtTheMomentOfReport() {
        User me = toss("cut-me");
        User other = toss("cut-other");
        long room = roomOf(me, other);
        Report r = safety.reportRoom(me, room, "SPAM", null);
        rooms.send(other, room, "신고 뒤의 사적인 말");

        assertThat(safety.transcript(r.getId()).orElseThrow().lines())
                .extracting(ChatSafetyService.Line::body).containsExactly("신고당할 메시지");
        assertThat(safety.exportText(r.getId()).orElseThrow()).doesNotContain("신고 뒤의 사적인 말");

        safety.resolve(r.getId(), Action.NONE);
        safety.reportRoom(me, room, "SPAM", "또");

        assertThat(safety.transcript(r.getId()).orElseThrow().lines())
                .extracting(ChatSafetyService.Line::body).containsExactly("신고당할 메시지", "신고 뒤의 사적인 말");
    }

    // ── 운영자 조치 ─────────────────────────────────────

    @Test
    void resolveAppliesTheChosenSanctionAndRecordsIt() {
        User me = toss("act-me");
        User other = toss("act-other");
        long room = roomOf(me, other);
        Report r = safety.reportRoom(me, room, "SPAM", null);

        safety.resolve(r.getId(), Action.SUSPEND_7D);

        assertThat(r.getStatus()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(r.getResolution()).isEqualTo("SUSPEND_7D");
        assertThat(other.getChatRestrictedUntil()).isCloseTo(clock.instant().plus(Duration.ofDays(7)), within(1, ChronoUnit.SECONDS));
    }

    @Test
    void warningLeavesChatUsable() {
        User me = toss("warn-me");
        User other = toss("warn-other");
        long room = roomOf(me, other);
        Report r = safety.reportRoom(me, room, "SPAM", null);

        safety.resolve(r.getId(), Action.WARN);

        assertThat(r.getResolution()).isEqualTo("WARN");
        assertThat(other.isChatRestricted(clock.instant())).isFalse();
    }

    @Test
    void banExtendAndLift() {
        User u = toss("sanc-u");
        Instant now = clock.instant();

        sanctions.apply(u, Action.SUSPEND_7D);
        sanctions.apply(u, Action.EXTEND_7D);
        assertThat(u.getChatRestrictedUntil()).isCloseTo(now.plus(Duration.ofDays(14)), within(1, ChronoUnit.SECONDS));

        sanctions.apply(u, Action.BAN);
        assertThat(u.getChatBannedAt()).isNotNull();
        assertThat(sanctions.sanctionedCount()).isGreaterThanOrEqualTo(1);

        sanctions.apply(u, Action.LIFT);
        assertThat(u.isChatRestricted(now)).isFalse();
        assertThat(u.getChatBannedAt()).isNull();
        assertThat(u.getChatRestrictedUntil()).isNull();
    }

    // ── 대본 열람·내보내기·법적 보존 ─────────────────────

    @Test
    void transcriptIsDecryptedAndOnlyForReportedRooms() {
        User me = toss("tr-me");
        User other = toss("tr-other");
        long room = roomOf(me, other);
        rooms.send(me, room, "카톡 아이디 줄게요");
        Report r = safety.reportRoom(me, room, "SPAM", null);
        reportService.report(toss("tr-x"), me, ReportReason.OTHER, "프로필 신고");
        Report profileOnly = reportRepository.findAll().stream()
                .filter(x -> x.getChatRoomId() == null && x.getReported().getId().equals(me.getId()))
                .findFirst().orElseThrow();

        ChatSafetyService.Transcript t = safety.transcript(r.getId()).orElseThrow();

        assertThat(t.lines()).extracting(ChatSafetyService.Line::body)
                .containsExactly("신고당할 메시지", "카톡 아이디 줄게요");
        assertThat(t.lines()).extracting(ChatSafetyService.Line::flagged).containsExactly(false, true);
        assertThat(safety.transcript(profileOnly.getId())).isEmpty(); // 신고가 없는 대화방은 못 연다
        assertThat(safety.exportText(r.getId()).orElseThrow())
                .contains("카톡 아이디 줄게요").contains("@trme").contains("@trother");
    }

    @Test
    void legalHoldToggles() {
        User me = toss("lh-me");
        User other = toss("lh-other");
        long room = roomOf(me, other);
        Report r = safety.reportRoom(me, room, "SPAM", null);

        safety.setLegalHold(r.getId(), true);
        assertThat(r.isLegalHold()).isTrue();
        safety.setLegalHold(r.getId(), false);
        assertThat(r.isLegalHold()).isFalse();
    }
}
