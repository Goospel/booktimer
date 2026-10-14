package com.booktimer.web.api;

import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 연결 끊기 콜백의 CORS — 앱인토스 콘솔 「테스트하기」는 브라우저에서 직접 fetch한다(2026-10-14 운영 실측:
 * {@code Origin: https://apps-in-toss.toss.im} 프리플라이트가 403, 콘솔엔 「Failed to fetch」).
 *
 * <p>운영처럼 허용 오리진 목록이 <b>비어 있지 않은</b> 컨텍스트여야 원래 403이 재현된다 — 그래서
 * {@link PublicNewsApiControllerTest}와 같은 속성을 쓴다(같은 캐시 키라 컨텍스트가 늘지 않는다).
 */
@SpringBootTest(properties = "booktimer.miniapp.allowed-origins=https://miniapp.test")
@AutoConfigureMockMvc
@Transactional
class TossUnlinkCallbackCorsTest {

    private static final String PATH = "/api/toss/unlink-callback";
    private static final String CONSOLE = "https://apps-in-toss.toss.im";

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;

    @Value("${booktimer.toss.unlink-callback.basic-auth}")
    String secret;

    private static String basic(String v) {
        return "Basic " + Base64.getEncoder().encodeToString(v.getBytes(StandardCharsets.UTF_8));
    }

    private ResultActions preflight(String path, String origin) throws Exception {
        return mockMvc.perform(options(path)
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type"));
    }

    private ResultActions callbackFromConsole(String authorization, String body) throws Exception {
        return mockMvc.perform(post(PATH).header("Origin", CONSOLE).header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    @DisplayName("콘솔 출처의 프리플라이트가 200이고 ACAO가 실리며, ACAH에 authorization이 든다")
    void consolePreflight_isAllowed() throws Exception {
        // ACAH 단언이 핵심 — 스프링은 허용 안 된 요청 헤더를 빼고도 200을 준다. 브라우저는 ACAH에 authorization이
        // 없으면 본 요청을 막아 콘솔에 다시 「Failed to fetch」가 뜬다(200·ACAO만 보면 이 회귀가 초록으로 남는다).
        preflight(PATH, CONSOLE)
                .andExpect(status().isOk())
                .andExpect(header().exists("Access-Control-Allow-Origin"))
                .andExpect(header().string("Access-Control-Allow-Headers", containsStringIgnoringCase("authorization")));
    }

    @Test
    @DisplayName("콘솔 출처 + 맞는 Basic + 모르는 userKey → 200 + ACAO")
    void consolePost_withValidBasic_isOkWithAcao() throws Exception {
        callbackFromConsole(basic(secret), "{\"userKey\": 999000111, \"referrer\": \"UNLINK\"}")
                .andExpect(status().isOk())
                .andExpect(header().exists("Access-Control-Allow-Origin"));
    }

    @Test
    @DisplayName("CORS가 열려도 틀린 Basic은 401(403 아님)이고 계정은 그대로다")
    void consolePost_withWrongBasic_is401AndKeepsAccount() throws Exception {
        User u = User.of("cors-unlink@booktimer.com", "hash", "콜백", "Asia/Seoul", Role.USER);
        u.linkTossUserKey("443731199");
        userRepository.save(u);

        callbackFromConsole(basic("wrong"), "{\"userKey\": 443731199, \"referrer\": \"UNLINK\"}")
                .andExpect(status().isUnauthorized());

        assertThat(userRepository.findByTossUserKey("443731199")).isPresent();
    }

    @Test
    @DisplayName("회귀 가드 — 다른 /api 경로는 그대로: 목록 밖 출처는 403·ACAO 없음, 목록 안 출처는 허용")
    void otherApiPath_keepsAllowList() throws Exception {
        preflight("/api/dashboard", CONSOLE)
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        preflight("/api/dashboard", "https://miniapp.test") // 양성 대조
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://miniapp.test"));
    }
}
