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
 * 미니앱 핸들 설정 API — {@code POST /api/miniapp/handle}.
 *
 * <p>토스로 시작한 계정은 {@code login_id=null}이라 소셜 전 경로(검색·팔로우 목록·스토리·프로필)의
 * 노출 필터에 걸려 <b>영구히 발견되지 않는다</b>(N-055 불변식). 이 엔드포인트가 유일한 탈출구다 —
 * 그래서 핵심 단언은 "핸들을 만든 순간부터 <b>남의 검색에 실제로 잡힌다</b>"(end-to-end)이고,
 * 나머지는 once-set 불변식·유니크·형식이 그대로 지켜지는지(회귀 가드)다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MiniappHandleApiControllerTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired MockMvc mockMvc;
    @Autowired UserRegistrationService registrationService;
    @Autowired UserRepository userRepository;
    @Autowired ApiTokenService apiTokenService;
    @Autowired Clock clock;

    /** 미니앱에서 시작한 신규 계정 — login_id 없이·onboarded=false로 산다(§2.4). */
    private User tossUser(String email) {
        return registrationService.registerOAuth(email, "토스유저", SEOUL, AuthProvider.TOSS,
                LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL)), false);
    }

    private ResultActions createHandle(String token, String loginId) throws Exception {
        return mockMvc.perform(post("/api/miniapp/handle")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"" + loginId + "\"}"));
    }

    @Test
    @DisplayName("핸들을 만들면 정규화돼 확정되고 온보딩이 완료되며, 그 순간부터 남의 검색에 잡힌다")
    void createHandle_normalizesAndBecomesDiscoverable() throws Exception {
        User me = tossUser("miniapp-handle@noreply.booktimer.app");
        assertThat(me.getLoginId()).isNull();

        createHandle(apiTokenService.issue(me), "BookWorm_1")
                .andExpect(status().isOk())
                // 서버가 정규화한 값을 돌려줘야 클라이언트가 재조회 없이 상태를 반영한다.
                .andExpect(jsonPath("$.loginId").value("bookworm_1"));

        userRepository.flush(); // 쓰기가 DB까지 도달하는지 — 제약 위반이면 여기서 터진다
        User reloaded = userRepository.findById(me.getId()).orElseThrow();
        assertThat(reloaded.getLoginId()).isEqualTo("bookworm_1");
        // 두 채널이 요구하는 온보딩(목표=미니앱, 핸들=여기)을 다 마쳤다 — CHECK(onboarded ⟹ login_id)도 만족.
        assertThat(reloaded.isOnboarded()).isTrue();

        // 회귀의 본질 — 노출 필터를 통과하게 됐다(login_id is not null 조건의 역방향 증거).
        User other = tossUser("miniapp-searcher@noreply.booktimer.app");
        mockMvc.perform(get("/api/search")
                        .param("q", "bookworm")
                        .header("Authorization", "Bearer " + apiTokenService.issue(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].loginId").value("bookworm_1"));
    }

    @Test
    @DisplayName("이미 핸들이 있으면 409 — 한 번 정하면 못 바꾼다(once-set 불변식 회귀 가드)")
    void createHandle_alreadySet_409() throws Exception {
        User me = tossUser("miniapp-handle-twice@noreply.booktimer.app");
        String token = apiTokenService.issue(me);
        createHandle(token, "firsthandle").andExpect(status().isOk());

        createHandle(token, "secondhandle").andExpect(status().isConflict());

        User reloaded = userRepository.findById(me.getId()).orElseThrow();
        assertThat(reloaded.getLoginId()).isEqualTo("firsthandle"); // 덮어쓰이지 않았다
        assertThat(reloaded.isOnboarded()).isTrue();
    }

    @Test
    @DisplayName("남이 쓰는 아이디는 409 — 대문자로 우회해도 정규화 후 같은 값이라 막힌다")
    void createHandle_duplicate_409() throws Exception {
        User taken = tossUser("miniapp-handle-taken@noreply.booktimer.app");
        createHandle(apiTokenService.issue(taken), "bookclub").andExpect(status().isOk());

        User me = tossUser("miniapp-handle-dup@noreply.booktimer.app");
        String token = apiTokenService.issue(me);

        createHandle(token, "bookclub").andExpect(status().isConflict());
        // 정규화 충돌 — 대소문자를 바꿔도 같은 아이디다(검색·프로필 URL이 소문자 기준).
        createHandle(token, "BookClub").andExpect(status().isConflict());

        assertThat(userRepository.findById(me.getId()).orElseThrow().getLoginId()).isNull();
    }

    @Test
    @DisplayName("형식 위반(짧음·한글·공백)은 400이고 계정은 핸들 없는 그대로 남는다")
    void createHandle_malformed_400() throws Exception {
        User me = tossUser("miniapp-handle-format@noreply.booktimer.app");
        String token = apiTokenService.issue(me);

        createHandle(token, "ab").andExpect(status().isBadRequest());
        createHandle(token, "한글아이디").andExpect(status().isBadRequest());
        createHandle(token, "has space").andExpect(status().isBadRequest());
        createHandle(token, "twentyonecharacters_x").andExpect(status().isBadRequest()); // 21자 경계

        User reloaded = userRepository.findById(me.getId()).orElseThrow();
        assertThat(reloaded.getLoginId()).isNull();
        assertThat(reloaded.isOnboarded()).isFalse(); // 실패했는데 온보딩만 완료되면 CHECK 위반이다
    }

    @Test
    @DisplayName("예약어(admin)는 400 — 사칭·경로 충돌 방지 규칙이 이 경로에도 걸린다")
    void createHandle_reserved_400() throws Exception {
        User me = tossUser("miniapp-handle-reserved@noreply.booktimer.app");

        createHandle(apiTokenService.issue(me), "admin").andExpect(status().isBadRequest());

        assertThat(userRepository.findById(me.getId()).orElseThrow().getLoginId()).isNull();
    }

    @Test
    @DisplayName("지어낸 토큰은 401 — 미니앱 체인의 인증이 이 새 경로에도 걸린다(SecurityConfig 무변경 전제)")
    void createHandle_withoutToken_401() throws Exception {
        createHandle("지어낸토큰", "someone").andExpect(status().isUnauthorized());
    }
}
