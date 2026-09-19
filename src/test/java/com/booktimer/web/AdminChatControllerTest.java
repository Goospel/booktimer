package com.booktimer.web;

import com.booktimer.chat.ChatRoomService;
import com.booktimer.chat.ChatSafetyService;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리자 대화 대본·조치(정책 문서 §2 확인·조치, §6 수사 협조). MockMvc + 실 빈·H2.
 *
 * <p>단독으로 잡는 실패: 일반 사용자가 남의 대화를 여는 것 · 신고가 없는 대화방을 여는 것 · 대본이 암호문으로
 * 보이는 것 · 처리·제재·법적 보존이 화면 버튼에서 실제 상태로 안 닿는 것 · 배너 숫자가 안 뜨는 것.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminChatControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired FollowRepository followRepository;
    @Autowired ChatRoomService rooms;
    @Autowired ChatSafetyService safety;
    @Autowired ReportService reportService;
    @Autowired ReportRepository reportRepository;
    @Autowired Clock clock;

    private User toss(String handle) {
        User u = User.of(handle + "@adminchat.test", "$2a$10$abcdefghijklmnopqrstuv", "닉" + handle, "Asia/Seoul", Role.USER);
        u.assignLoginId(handle);
        u.linkTossUserKey("uk-" + handle);
        return userRepository.save(u);
    }

    /** me가 other를 방에서 신고한 상태. 방엔 other가 보낸 수상한 메시지가 있다. */
    private Report reported(String prefix) {
        User me = toss(prefix + "me");
        User other = toss(prefix + "other");
        followRepository.save(Follow.of(me, other));
        followRepository.save(Follow.of(other, me));
        long id = rooms.openOrGet(me, other).getId();
        rooms.send(other, id, "먼저 입금해 주시면 돼요");
        return safety.reportRoom(me, id, "SPAM", "돈 요구");
    }

    @Test
    void userCannotOpenTranscript() throws Exception {
        Report r = reported("acu");

        mockMvc.perform(get("/admin/reports/{id}/chat", r.getId()).with(user("acu@adminchat.test")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminReadsDecryptedTranscriptWithFlagHighlight() throws Exception {
        Report r = reported("acr");

        mockMvc.perform(get("/admin/reports/{id}/chat", r.getId()).with(user("boss").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("먼저 입금해 주시면 돼요")))
                .andExpect(content().string(containsString("chat-line flagged")))
                .andExpect(content().string(containsString("@acrother")));
    }

    @Test
    void reportWithoutRoomHasNoTranscript() throws Exception {
        User a = toss("acna");
        User b = toss("acnb");
        reportService.report(a, b, ReportReason.OTHER, "프로필 신고");
        Long id = reportRepository.findAll().stream().filter(x -> x.getReporter().getId().equals(a.getId()))
                .findFirst().orElseThrow().getId();

        mockMvc.perform(get("/admin/reports/{id}/chat", id).with(user("boss").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void exportIsPlainTextAttachment() throws Exception {
        Report r = reported("ace");

        mockMvc.perform(get("/admin/reports/{id}/chat.txt", r.getId()).with(user("boss").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(content().contentTypeCompatibleWith("text/plain"))
                .andExpect(content().string(containsString("먼저 입금해 주시면 돼요")));
    }

    @Test
    void resolveWithSuspensionAndLegalHold() throws Exception {
        Report r = reported("acs");

        mockMvc.perform(post("/admin/reports/{id}/resolve", r.getId()).param("action", "SUSPEND_7D")
                        .with(user("boss").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/admin/reports/{id}/legal-hold", r.getId()).param("hold", "true")
                        .with(user("boss").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        Report after = reportRepository.findById(r.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(after.isLegalHold()).isTrue();
        assertThat(after.getReported().isChatRestricted(clock.instant())).isTrue();
    }

    @Test
    void operatorCanLiftASanction() throws Exception {
        User u = toss("aclift");
        u.banChat(clock.instant());

        mockMvc.perform(post("/admin/users/{loginId}/chat-sanction", "aclift").param("action", "LIFT")
                        .with(user("boss").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(userRepository.findByLoginId("aclift").orElseThrow().getChatBannedAt()).isNull();
    }

    @Test
    void userCannotSanction() throws Exception {
        toss("acus");

        mockMvc.perform(post("/admin/users/{loginId}/chat-sanction", "acus").param("action", "BAN")
                        .with(user("acus@adminchat.test")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void reportsListLinksToTranscriptAndDashboardShowsBanner() throws Exception {
        Report r = reported("acl");
        User admin = User.of("acladmin@adminchat.test", "$2a$10$abcdefghijklmnopqrstuv", "운영", "Asia/Seoul", Role.ADMIN);
        admin.assignLoginId("acladmin");
        userRepository.save(admin);

        mockMvc.perform(get("/admin/reports").with(user("boss").roles("ADMIN")))
                .andExpect(content().string(containsString("/admin/reports/" + r.getId() + "/chat")));
        mockMvc.perform(get("/admin").with(user("acladmin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-open-reports=\"")))
                .andExpect(content().string(not(containsString("data-open-reports=\"0\""))));
    }
}
