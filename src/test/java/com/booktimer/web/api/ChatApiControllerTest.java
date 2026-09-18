package com.booktimer.web.api;

import com.booktimer.auth.ApiTokenService;
import com.booktimer.follow.Follow;
import com.booktimer.follow.FollowRepository;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /api/chat/**} — 스위치를 켠 컨텍스트에서 Bearer로 왕복한다(설계 §5-3).
 * 꺼진 스위치의 404는 {@link ChatApiKillSwitchTest}가 기본 컨텍스트에서 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "booktimer.chat.enabled=true")
class ChatApiControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired FollowRepository followRepository;
    @Autowired ApiTokenService apiTokenService;

    private final JsonMapper json = JsonMapper.builder().build();

    private User toss(String handle) {
        User u = User.of(handle + "@chatapi.test", "$2a$10$abcdefghijklmnopqrstuv", "닉" + handle, "Asia/Seoul", Role.USER);
        u.assignLoginId(handle);
        u.linkTossUserKey("uk-" + handle);
        return userRepository.save(u);
    }

    private void mutual(User a, User b) {
        followRepository.save(Follow.of(a, b));
        followRepository.save(Follow.of(b, a));
    }

    private ResultActions postJson(String token, String path, String body) throws Exception {
        return mockMvc.perform(post(path).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions getAs(String token, String path) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", "Bearer " + token));
    }

    private long openRoom(String token, String loginId) throws Exception {
        String res = postJson(token, "/api/chat/rooms", "{\"loginId\":\"" + loginId + "\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("roomId").asLong();
    }

    @Test
    void roundTripBetweenMutualFollowers() throws Exception {
        User me = toss("apime");
        User other = toss("apiother");
        mutual(me, other);
        String myToken = apiTokenService.issue(me);
        String otherToken = apiTokenService.issue(other);

        long room = openRoom(myToken, "apiother");
        String sent = postJson(myToken, "/api/chat/rooms/" + room + "/messages", "{\"body\":\"안녕하세요\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.createdAt").exists())
                .andReturn().getResponse().getContentAsString();
        long messageId = json.readTree(sent).get("id").asLong();

        getAs(otherToken, "/api/chat/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadRooms").value(1))
                .andExpect(jsonPath("$.banned").value(false));
        getAs(otherToken, "/api/chat/rooms")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].roomId").value(room))
                .andExpect(jsonPath("$[0].partner.loginId").value("apime"))
                .andExpect(jsonPath("$[0].partner.nickname").value("닉apime"))
                .andExpect(jsonPath("$[0].writable").value(true))
                .andExpect(jsonPath("$[0].unread").value(1))
                .andExpect(jsonPath("$[0].lastMessage.body").value("안녕하세요"));
        getAs(otherToken, "/api/chat/rooms/" + room + "/messages?after=0")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[0].body").value("안녕하세요"))
                .andExpect(jsonPath("$.messages[0].mine").value(false))
                .andExpect(jsonPath("$.messages[0].flagged").value(false))
                .andExpect(jsonPath("$.writable").value(true));

        postJson(otherToken, "/api/chat/rooms/" + room + "/read", "{\"lastMessageId\":" + messageId + "}")
                .andExpect(status().isOk());
        getAs(otherToken, "/api/chat/me").andExpect(jsonPath("$.unreadRooms").value(0));

        postJson(otherToken, "/api/chat/rooms/" + room + "/hide", "{}").andExpect(status().isOk());
        getAs(otherToken, "/api/chat/rooms").andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void notMutualIsForbiddenAndUnknownHandleIsNotFound() throws Exception {
        User me = toss("apinm");
        toss("apinmother");
        String token = apiTokenService.issue(me);

        postJson(token, "/api/chat/rooms", "{\"loginId\":\"apinmother\"}").andExpect(status().isForbidden());
        postJson(token, "/api/chat/rooms", "{\"loginId\":\"nobodyhere\"}").andExpect(status().isNotFound());
    }

    @Test
    void adminHandleLooksLikeNobody() throws Exception {
        User me = toss("apiadm");
        User admin = User.of("adm@chatapi.test", "$2a$10$abcdefghijklmnopqrstuv", "운영", "Asia/Seoul", Role.ADMIN);
        admin.assignLoginId("apiadmin");
        userRepository.save(admin);

        postJson(apiTokenService.issue(me), "/api/chat/rooms", "{\"loginId\":\"apiadmin\"}")
                .andExpect(status().isNotFound());
    }

    @Test
    void strangerGetsNotFoundOnSomeoneElsesRoom() throws Exception {
        User a = toss("apiidora");
        User b = toss("apiidorb");
        User x = toss("apiidorx");
        mutual(a, b);
        long room = openRoom(apiTokenService.issue(a), "apiidorb");
        String xToken = apiTokenService.issue(x);

        getAs(xToken, "/api/chat/rooms/" + room + "/messages").andExpect(status().isNotFound());
        postJson(xToken, "/api/chat/rooms/" + room + "/messages", "{\"body\":\"끼어들기\"}")
                .andExpect(status().isNotFound());
    }

    @Test
    void unfollowedSendIsForbiddenWithPlainKoreanReason() throws Exception {
        User me = toss("apiuf");
        User other = toss("apiufother");
        mutual(me, other);
        String token = apiTokenService.issue(me);
        long room = openRoom(token, "apiufother");
        followRepository.deleteByFollowerAndFollowee(other, me);

        String body = postJson(token, "/api/chat/rooms/" + room + "/messages", "{\"body\":\"잠김?\"}")
                .andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        org.assertj.core.api.Assertions.assertThat(body).contains("서로 팔로우");
        getAs(token, "/api/chat/rooms/" + room + "/messages")
                .andExpect(jsonPath("$.writable").value(false))
                .andExpect(jsonPath("$.lockReason").value("NOT_MUTUAL"));
    }

    @Test
    void blankBodyIsBadRequest() throws Exception {
        User me = toss("apiblank");
        User other = toss("apiblankother");
        mutual(me, other);
        String token = apiTokenService.issue(me);
        long room = openRoom(token, "apiblankother");

        postJson(token, "/api/chat/rooms/" + room + "/messages", "{\"body\":\"  \"}").andExpect(status().isBadRequest());
    }

    // ── 프로필 dmAvailable — 스위치가 켜진 이 컨텍스트에서만 자격이 판정을 가른다 ──
    // (꺼진 쪽 음성 대조군은 ProfileApiControllerTest.profile_dmAvailable_falseWhileChatSwitchedOff)

    @Test
    void profileDmAvailableForMutualTossOwner() throws Exception {
        User viewer = toss("apidmv");
        User owner = toss("apidmo");
        mutual(viewer, owner);

        getAs(apiTokenService.issue(viewer), "/api/profile?loginId=apidmo")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dmAvailable").value(true));
    }

    @Test
    void profileDmUnavailableForOneWayOrWebOnlyOwner() throws Exception {
        User viewer = toss("apidmv2");
        User oneWay = toss("apidmone");
        followRepository.save(Follow.of(viewer, oneWay)); // 나 → 주인만
        User webOnly = User.of("apidmweb@chatapi.test", "$2a$10$abcdefghijklmnopqrstuv", "웹", "Asia/Seoul", Role.USER);
        webOnly.assignLoginId("apidmweb");
        webOnly = userRepository.save(webOnly);
        mutual(viewer, webOnly); // 맞팔이지만 토스 미연결
        String token = apiTokenService.issue(viewer);

        getAs(token, "/api/profile?loginId=apidmone").andExpect(jsonPath("$.dmAvailable").value(false));
        getAs(token, "/api/profile?loginId=apidmweb").andExpect(jsonPath("$.dmAvailable").value(false));
    }

    @Test
    void invalidBearerIsUnauthorized() throws Exception {
        getAs("지어낸토큰", "/api/chat/me").andExpect(status().isUnauthorized());
    }
}
