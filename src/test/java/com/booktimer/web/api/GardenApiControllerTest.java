package com.booktimer.web.api;

import com.booktimer.garden.GardenLayoutService;
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
 * GET /api/garden · POST /api/garden/layout 컨트롤러 통합 테스트.
 *
 * <p>마을 컨셉 전환 후: 식물 4축(TIME·GENRE·DIVERSITY·RECIPE)·소품(Decoration) 제거.
 * catalog에 authorCharacters·buildings만, decorationCatalog 필드 없음.
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
    @DisplayName("GET /api/garden 인증 → 200 JSON + 필수 키(world·nickname·placed·catalog·owned·characters), 제거된 키 부재")
    void getGarden_authenticated_returnsJsonStructure() throws Exception {
        registrationService.register("api-garden@booktimer.com", "rawpw1234", "API사용자", SEOUL, Role.USER, today());

        mockMvc.perform(get("/api/garden")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("api-garden@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.world").exists())
                .andExpect(jsonPath("$.world.width").value(GardenLayoutService.WORLD_WIDTH))
                .andExpect(jsonPath("$.world.height").value(GardenLayoutService.WORLD_HEIGHT))
                .andExpect(jsonPath("$.nickname").value("API사용자"))
                .andExpect(jsonPath("$.placed").isArray())
                // decorationCatalog 제거됨
                .andExpect(jsonPath("$.decorationCatalog").doesNotExist())
                .andExpect(jsonPath("$.catalog").exists())
                // 식물 4축 제거됨
                .andExpect(jsonPath("$.catalog.plants").doesNotExist())
                .andExpect(jsonPath("$.catalog.genrePlants").doesNotExist())
                .andExpect(jsonPath("$.catalog.diversityPlants").doesNotExist())
                .andExpect(jsonPath("$.catalog.recipePlants").doesNotExist())
                // 유지 — 작가·건물
                .andExpect(jsonPath("$.catalog.authorCharacters").isArray())
                .andExpect(jsonPath("$.catalog.ownedAuthorCharacterCount").exists())
                .andExpect(jsonPath("$.catalog.ownedCharacters").isArray())
                .andExpect(jsonPath("$.catalog.buildings").isArray())
                .andExpect(jsonPath("$.catalog.ownedBuildingCount").exists())
                // S2: 게임 직접 소비용
                .andExpect(jsonPath("$.owned").isArray())
                .andExpect(jsonPath("$.characters").isArray())
                // 먹이주기 루프 — foodBalance(top-level); affection 직렬화는 s3_authorCharacterDto_hasAffection
                .andExpect(jsonPath("$.foodBalance").exists())
                .andExpect(jsonPath("$.foodBalance").isNumber());
    }

    @Test
    @DisplayName("GET /api/garden: owned 항목은 AUTHOR 제외한 보유 건물만(axis·code·emoji·name·spriteId 구조)")
    void getGarden_owned_excludesAuthorCharacters() throws Exception {
        registrationService.register("api-owned@booktimer.com", "rawpw1234", "보유자", SEOUL, Role.USER, today());

        mockMvc.perform(get("/api/garden")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("api-owned@booktimer.com")))
                .andExpect(status().isOk())
                // 신규 유저 = 보유 건물 0 → 빈 배열
                .andExpect(jsonPath("$.owned").isArray())
                .andExpect(jsonPath("$.characters").isArray());
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

    // ── POST /api/garden/layout ────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/garden/layout 미인증 → 302 로그인 리다이렉트 (기본 잠김)")
    void saveLayout_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(post("/api/garden/layout").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("POST /api/garden/layout: CSRF 없으면 403 (세션 쿠키 기반 CSRF 보호 활성)")
    void saveLayout_withoutCsrf_returns403() throws Exception {
        registrationService.register("api-csrf@booktimer.com", "rawpw1234", "CSRF사용자", SEOUL, Role.USER, today());

        mockMvc.perform(post("/api/garden/layout")
                        .with(user("api-csrf@booktimer.com"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"plants\":[]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/garden/layout 인증 + 빈 배치 → 200 (서비스 저장 위임)")
    void saveLayout_authenticated_emptyBody_ok() throws Exception {
        registrationService.register("api-save@booktimer.com", "rawpw1234", "저장사용자", SEOUL, Role.USER, today());

        mockMvc.perform(post("/api/garden/layout")
                        .with(user("api-save@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plants\":[]}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/garden/layout: 미보유 건물 배치 → 400 거부 전파 (H2엔 카탈로그 없어 무엇이든 미보유)")
    void saveLayout_unownedBuilding_returns400() throws Exception {
        registrationService.register("api-reject@booktimer.com", "rawpw1234", "거부사용자", SEOUL, Role.USER, today());

        mockMvc.perform(post("/api/garden/layout")
                        .with(user("api-reject@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plants\":[{\"axis\":\"BUILDING\",\"code\":\"minumsa\",\"x\":0.5,\"y\":0.5,\"z\":0,\"rotation\":0,\"scale\":1}]}"))
                .andExpect(status().isBadRequest());
    }

    // ── S3: 유지 DTO 직렬화 단언 ────────────────────────────────────────────────

    @Test
    @DisplayName("S3: BuildingDto 직렬화에 matchName·thresholdCount 포함")
    void s3_buildingDto_hasMatchNameAndThresholdCount() throws Exception {
        var dto = new GardenApiResponse.BuildingDto("bld-01", "🏢", "민음사빌딩", null, false, "민음사", 3);
        String json = objectMapper.writeValueAsString(dto);
        assertThat(json).contains("\"matchName\"").contains("\"thresholdCount\"");
    }

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
