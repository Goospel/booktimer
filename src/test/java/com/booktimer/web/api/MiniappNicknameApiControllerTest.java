package com.booktimer.web.api;

import com.booktimer.auth.ApiTokenService;
import com.booktimer.user.AuthProvider;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 미니앱 닉네임 변경 API — {@code POST /api/miniapp/nickname}.
 *
 * <p>토스로 시작한 계정은 닉네임이 전원 기본값("토스유저")인데 바꿀 길이 없었다 — 변경 경로가 웹
 * {@code /settings} 하나뿐인데 그 계정들은 비밀번호가 없어 웹 로그인 자체가 불가능하기 때문이다.
 * 그래서 핵심 단언은 "바꾼 값이 <b>대시보드에 실제로 실려 온다</b>"(end-to-end)이고, 나머지는
 * 검증(공백·길이 상한)이 도메인 단일 출처를 그대로 타는지다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MiniappNicknameApiControllerTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired MockMvc mockMvc;
    @Autowired UserRegistrationService registrationService;
    @Autowired UserRepository userRepository;
    @Autowired ApiTokenService apiTokenService;
    @Autowired Clock clock;

    /** 미니앱에서 시작한 신규 계정 — 닉네임이 기본값 "토스유저"인 바로 그 모양이다. */
    private User tossUser(String email) {
        return registrationService.registerOAuth(email, "토스유저", SEOUL, AuthProvider.TOSS,
                LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL)), false);
    }

    private ResultActions updateNickname(String token, String nickname) throws Exception {
        return mockMvc.perform(post("/api/miniapp/nickname")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"" + nickname + "\"}"));
    }

    @Test
    @DisplayName("닉네임을 바꾸면 저장되고, 그 값이 대시보드에도 실려 온다")
    void updateNickname_savesAndAppearsInDashboard() throws Exception {
        User me = tossUser("miniapp-nickname@noreply.booktimer.app");
        String token = apiTokenService.issue(me);
        assertThat(me.getNickname()).isEqualTo("토스유저");

        updateNickname(token, "독서왕")
                .andExpect(status().isOk())
                // 서버가 저장한 값을 돌려줘야 클라이언트가 재조회 없이 표시를 갱신한다.
                .andExpect(jsonPath("$.nickname").value("독서왕"));

        userRepository.flush(); // 쓰기가 DB까지 도달하는지 — 제약 위반이면 여기서 터진다
        assertThat(userRepository.findById(me.getId()).orElseThrow().getNickname()).isEqualTo("독서왕");

        // 회귀의 본질 — 다른 화면이 보는 값도 같이 바뀌었다(홈 인사말·소셜 표시 이름이 이 필드다).
        mockMvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("독서왕"));
    }

    @Test
    @DisplayName("공백만 있는 닉네임은 400이고 계정은 옛 이름 그대로 남는다")
    void updateNickname_blank_400() throws Exception {
        User me = tossUser("miniapp-nickname-blank@noreply.booktimer.app");

        updateNickname(apiTokenService.issue(me), "   ").andExpect(status().isBadRequest());

        assertThat(userRepository.findById(me.getId()).orElseThrow().getNickname()).isEqualTo("토스유저");
    }

    @Test
    @DisplayName("길이 상한 경계 — 30자는 저장되고 31자는 400(도메인 단일 출처를 그대로 탄다)")
    void updateNickname_lengthBoundary() throws Exception {
        User me = tossUser("miniapp-nickname-length@noreply.booktimer.app");
        String token = apiTokenService.issue(me);
        String exactly = "가".repeat(User.NICKNAME_MAX_LENGTH);

        updateNickname(token, exactly).andExpect(status().isOk());
        updateNickname(token, "가".repeat(User.NICKNAME_MAX_LENGTH + 1)).andExpect(status().isBadRequest());

        // 거부된 뒤에도 마지막 성공값이 살아 있다 — 실패가 값을 지우고 가면 안 된다.
        assertThat(userRepository.findById(me.getId()).orElseThrow().getNickname()).isEqualTo(exactly);
    }

    @Test
    @DisplayName("지어낸 토큰은 401 — 미니앱 체인의 인증이 이 새 경로에도 걸린다(SecurityConfig 무변경 전제)")
    void updateNickname_withoutToken_401() throws Exception {
        updateNickname("지어낸토큰", "누구든").andExpect(status().isUnauthorized());
    }
}
