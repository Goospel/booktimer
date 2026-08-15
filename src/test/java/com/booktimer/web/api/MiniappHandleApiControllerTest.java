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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

    private ResultActions changeHandle(String token, String loginId) throws Exception {
        return mockMvc.perform(post("/api/miniapp/handle/change")
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

        createHandle(token, "secondhandle")
                .andExpect(status().isConflict())
                // 생성 재호출은 "이미 있다"는 뜻뿐이다 — 「바꿀 수 없어요」는 변경 경로가 생긴 뒤로는 거짓말이다.
                .andExpect(content().string("이미 아이디가 있어요."));

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

    // ── 아이디 바꾸기(POST /api/miniapp/handle/change) ───────────────────────────
    //
    // 토스 전용 계정은 웹에 로그인조차 못 하므로 이 엔드포인트가 아이디를 바꾸는 유일한 경로다.
    // 몸통(평생 1회 가드·옛 핸들 예약)은 AccountService가 이미 지키므로, 여기서 못 박는 것은
    // "그 계약이 이 경로에 제대로 배선됐는가"(상태코드·평문·발견 가능성의 전이)다.

    @Test
    @DisplayName("아이디를 바꾸면 정규화돼 확정되고, 그 순간부터 새 아이디로만 검색에 잡힌다")
    void changeHandle_normalizesAndMovesDiscoverability() throws Exception {
        User me = tossUser("miniapp-change@noreply.booktimer.app");
        String token = apiTokenService.issue(me);
        createHandle(token, "oldworm").andExpect(status().isOk());

        changeHandle(token, "NewWorm_2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").value("newworm_2"));

        userRepository.flush(); // 두 컬럼 UNIQUE를 실제로 통과하는지 — 제약 위반이면 여기서 터진다
        User reloaded = userRepository.findById(me.getId()).orElseThrow();
        assertThat(reloaded.getLoginId()).isEqualTo("newworm_2");
        assertThat(reloaded.getPreviousLoginId()).isEqualTo("oldworm"); // 옛 핸들은 영구 예약된다

        // 소진 여부의 유일한 출처 — 클라이언트는 이 필드로 「바꾸기」 버튼을 켜고 끈다.
        mockMvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").value("newworm_2"))
                .andExpect(jsonPath("$.previousLoginId").value("oldworm"));

        // 발견 가능성이 통째로 옮겨갔는지 — 새 아이디로는 잡히고, 옛 아이디로는 아무도 안 나온다.
        String otherToken = apiTokenService.issue(tossUser("miniapp-change-searcher@noreply.booktimer.app"));
        mockMvc.perform(get("/api/search").param("q", "newworm")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].loginId").value("newworm_2"));
        mockMvc.perform(get("/api/search").param("q", "oldworm")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").isEmpty());
    }

    @Test
    @DisplayName("두 번째 변경은 409 — 평생 1번이라는 불변식이 이 경로에도 걸린다")
    void changeHandle_alreadyUsed_409() throws Exception {
        User me = tossUser("miniapp-change-twice@noreply.booktimer.app");
        String token = apiTokenService.issue(me);
        createHandle(token, "firstname").andExpect(status().isOk());
        changeHandle(token, "secondname").andExpect(status().isOk());

        changeHandle(token, "thirdname")
                .andExpect(status().isConflict())
                .andExpect(content().string("아이디 변경은 평생 1번이에요. 이미 사용했어요."));

        User reloaded = userRepository.findById(me.getId()).orElseThrow();
        assertThat(reloaded.getLoginId()).isEqualTo("secondname"); // 세 번째 값으로 덮이지 않았다
        assertThat(reloaded.getPreviousLoginId()).isEqualTo("firstname");
    }

    @Test
    @DisplayName("남이 쓰는 아이디도, 남이 버리고 간 옛 아이디도 409 — 예약이 이 경로에도 걸린다")
    void changeHandle_takenOrReserved_409() throws Exception {
        // 남 A: 현행 핸들 hasitnow. 남 B: bygone → movedon 으로 바꿔 bygone 을 예약해 둔 상태.
        createHandle(apiTokenService.issue(tossUser("miniapp-change-a@noreply.booktimer.app")), "hasitnow")
                .andExpect(status().isOk());
        String bToken = apiTokenService.issue(tossUser("miniapp-change-b@noreply.booktimer.app"));
        createHandle(bToken, "bygone").andExpect(status().isOk());
        changeHandle(bToken, "movedon").andExpect(status().isOk());

        User me = tossUser("miniapp-change-taken@noreply.booktimer.app");
        String token = apiTokenService.issue(me);
        createHandle(token, "mineforNow").andExpect(status().isOk());

        changeHandle(token, "hasitnow")
                .andExpect(status().isConflict())
                .andExpect(content().string("이미 사용 중인 아이디예요. 다른 아이디를 지어 주세요."));
        // 예약의 본체 — 남이 버린 핸들로 갈아타는 사칭을 막는다(어느 컬럼에 걸렸는지는 알리지 않는다).
        changeHandle(token, "bygone")
                .andExpect(status().isConflict())
                .andExpect(content().string("이미 사용 중인 아이디예요. 다른 아이디를 지어 주세요."));

        User reloaded = userRepository.findById(me.getId()).orElseThrow();
        assertThat(reloaded.getLoginId()).isEqualTo("minefornow");
        assertThat(reloaded.getPreviousLoginId()).isNull(); // 실패한 시도가 변경권을 태우지 않았다
    }

    @Test
    @DisplayName("형식 위반·예약어·지금과 같은 아이디는 400이고 계정은 그대로 — 변경권도 안 쓴다")
    void changeHandle_malformedOrSame_400() throws Exception {
        User me = tossUser("miniapp-change-format@noreply.booktimer.app");
        String token = apiTokenService.issue(me);
        createHandle(token, "goodname").andExpect(status().isOk());

        changeHandle(token, "ab").andExpect(status().isBadRequest());
        changeHandle(token, "한글아이디").andExpect(status().isBadRequest());
        changeHandle(token, "admin").andExpect(status().isBadRequest());
        // 지금과 같은 값 — 대문자로 우회해도 정규화 후 같은 아이디라 막힌다(1회를 헛되이 태우지 않게).
        changeHandle(token, "GoodName")
                .andExpect(status().isBadRequest())
                .andExpect(content().string(
                        "사용할 수 없는 아이디예요. 영문 소문자·숫자·밑줄(_) 3~20자, 지금 아이디와 달라야 해요."));

        User reloaded = userRepository.findById(me.getId()).orElseThrow();
        assertThat(reloaded.getLoginId()).isEqualTo("goodname");
        assertThat(reloaded.getPreviousLoginId()).isNull();
    }

    @Test
    @DisplayName("지어낸 토큰은 401 — 변경 경로도 미니앱 체인이 그대로 막는다")
    void changeHandle_withoutToken_401() throws Exception {
        changeHandle("지어낸토큰", "someone").andExpect(status().isUnauthorized());
    }
}
