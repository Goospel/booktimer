package com.booktimer.web.api;

import com.booktimer.garden.GardenWorld;
import com.booktimer.user.Role;
import com.booktimer.user.UserRegistrationService;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * GET /api/garden 컨트롤러 통합 테스트.
 *
 * <p>건물(BUILDING)축 은퇴 후: catalog에 authorCharacters만(buildings·decorationCatalog·식물 4축 없음).
 * 배치/편집 엔진 제거(PR-2): {@code placed}·{@code owned} 필드와 POST {@code /api/garden/layout}
 * 엔드포인트가 사라졌다 — 응답엔 world·nickname·catalog·characters·foodBalance만 남는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GardenApiControllerTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRegistrationService registrationService;

    @Autowired
    private Clock clock;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUpObjectMapper() {
        objectMapper = JsonMapper.builder().findAndAddModules().build();
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    // ── GET /api/garden ────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/garden 미인증 → 302 로그인 리다이렉트 (기본 잠김)")
    void getGarden_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/api/garden"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("GET /api/garden 인증 → 200 JSON + 필수 키(world·nickname·catalog·characters·foodBalance), 제거된 키(placed·owned) 부재")
    void getGarden_authenticated_returnsJsonStructure() throws Exception {
        registrationService.register("api-garden@booktimer.com", "rawpw1234", "API사용자", SEOUL, Role.USER, today());

        mockMvc.perform(get("/api/garden")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("api-garden@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.world").exists())
                .andExpect(jsonPath("$.world.width").value(GardenWorld.WORLD_WIDTH))
                .andExpect(jsonPath("$.world.height").value(GardenWorld.WORLD_HEIGHT))
                .andExpect(jsonPath("$.nickname").value("API사용자"))
                // 배치 엔진 제거(PR-2) — placed·owned 필드 부재
                .andExpect(jsonPath("$.placed").doesNotExist())
                .andExpect(jsonPath("$.owned").doesNotExist())
                // decorationCatalog 제거됨
                .andExpect(jsonPath("$.decorationCatalog").doesNotExist())
                .andExpect(jsonPath("$.catalog").exists())
                // 식물 4축 제거됨
                .andExpect(jsonPath("$.catalog.plants").doesNotExist())
                .andExpect(jsonPath("$.catalog.genrePlants").doesNotExist())
                .andExpect(jsonPath("$.catalog.diversityPlants").doesNotExist())
                .andExpect(jsonPath("$.catalog.recipePlants").doesNotExist())
                // 유지 — 작가
                .andExpect(jsonPath("$.catalog.authorCharacters").isArray())
                .andExpect(jsonPath("$.catalog.ownedAuthorCharacterCount").exists())
                .andExpect(jsonPath("$.catalog.ownedCharacters").isArray())
                // 건물 은퇴(PR-1) — catalog에서 건물 필드 전부 제거됨
                .andExpect(jsonPath("$.catalog.buildings").doesNotExist())
                .andExpect(jsonPath("$.catalog.ownedBuildingCount").doesNotExist())
                .andExpect(jsonPath("$.catalog.totalBuildingCount").doesNotExist())
                // 배회 캐릭터 — 게임 직접 소비용(유지)
                .andExpect(jsonPath("$.characters").isArray())
                // 먹이주기 루프 — foodBalance(top-level); affection 직렬화는 s3_authorCharacterDto_hasAffection
                .andExpect(jsonPath("$.foodBalance").exists())
                .andExpect(jsonPath("$.foodBalance").isNumber());
    }

    @Test
    @DisplayName("GET /api/garden: 엔티티 lazy·순환 없이 JSON 직렬화된다 (DTO 평탄화 — 직렬화 에러 없으면 성공)")
    void getGarden_responseSerializesWithoutEntityLeak() throws Exception {
        registrationService.register("api-serial@booktimer.com", "rawpw1234", "직렬사용자", SEOUL, Role.USER, today());

        mockMvc.perform(get("/api/garden")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("api-serial@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("GET /api/garden: principal 기준 유저만 조회된다 (닉네임이 해당 유저의 것)")
    void getGarden_returnsCurrentUserNickname() throws Exception {
        registrationService.register("api-me@booktimer.com", "rawpw1234", "나야나", SEOUL, Role.USER, today());
        registrationService.register("api-other@booktimer.com", "rawpw1234", "남이야", SEOUL, Role.USER, today());

        mockMvc.perform(get("/api/garden")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("api-me@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("나야나"));
    }

    // ── POST /api/garden/layout 은퇴(PR-2) ──────────────────────────────────────

    @Test
    @DisplayName("POST /api/garden/layout: 배치 엔진 은퇴로 엔드포인트 제거 → 인증·CSRF 갖춰도 404")
    void saveLayout_endpointRemoved_returns404() throws Exception {
        registrationService.register("api-save@booktimer.com", "rawpw1234", "저장사용자", SEOUL, Role.USER, today());

        mockMvc.perform(post("/api/garden/layout")
                        .with(user("api-save@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plants\":[]}"))
                .andExpect(status().isNotFound());
    }

    // ── S3: 유지 DTO 직렬화 단언 ────────────────────────────────────────────────

    @Test
    @DisplayName("S3: AuthorCharacterDto 직렬화에 matchName 포함")
    void s3_authorCharacterDto_hasMatchName() throws Exception {
        var dto = new GardenApiResponse.AuthorCharacterDto("aut-01", "🧑", "한강캐릭터", null, false, "한강", 0, 0, "");
        String json = objectMapper.writeValueAsString(dto);
        assertThat(json).contains("\"matchName\"");
    }

    @Test
    @DisplayName("S3: AuthorCharacterDto 직렬화에 affection 포함 (먹이주기 루프)")
    void s3_authorCharacterDto_hasAffection() throws Exception {
        var dto = new GardenApiResponse.AuthorCharacterDto("aut-01", "🧑", "한강캐릭터", null, false, "한강", 5, 3, "친한 사이");
        String json = objectMapper.writeValueAsString(dto);
        assertThat(json).contains("\"affection\"").contains("5");
    }

    @Test
    @DisplayName("S3: AuthorCharacterDto 직렬화에 level·title 포함 (affection 진화)")
    void s3_authorCharacterDto_hasLevelAndTitle() throws Exception {
        var dto = new GardenApiResponse.AuthorCharacterDto("aut-01", "🧑", "한강캐릭터", null, false, "한강", 7, 3, "친한 사이");
        String json = objectMapper.writeValueAsString(dto);
        assertThat(json).contains("\"level\"").contains("\"title\"").contains("친한 사이");
    }
}
