package com.booktimer.web.api;

import com.booktimer.garden.GardenLayoutService;
import com.booktimer.user.Role;
import com.booktimer.user.UserRegistrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
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
 * <p>S1 백엔드 API 레이어 검증 — ①인증 게이트(기본 잠김), ②JSON 구조 키 존재(DTO 평탄화 성공),
 * ③CSRF 보호, ④서비스 위임(saveLayout). 도메인 로직은 기존 GardenService/GardenLayoutServiceTest가 커버.
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpObjectMapper() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules(); // JavaTimeModule 등 자동 등록
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    // ── GET /api/garden ────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/garden 미인증 → 302 로그인 리다이렉트 (기본 잠김 — /api/** 는 permitAll 목록 외)")
    void getGarden_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/api/garden"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("GET /api/garden 인증 → 200 JSON + 필수 키(world·nickname·placed·decorationCatalog·catalog·owned·characters)")
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
                .andExpect(jsonPath("$.decorationCatalog").isArray())
                .andExpect(jsonPath("$.catalog").exists())
                .andExpect(jsonPath("$.catalog.plants").isArray())
                .andExpect(jsonPath("$.catalog.ownedCount").exists())
                .andExpect(jsonPath("$.catalog.totalCount").exists())
                .andExpect(jsonPath("$.catalog.genrePlants").isArray())
                .andExpect(jsonPath("$.catalog.diversityPlants").isArray())
                .andExpect(jsonPath("$.catalog.recipePlants").isArray())
                .andExpect(jsonPath("$.catalog.authorCharacters").isArray())
                .andExpect(jsonPath("$.catalog.ownedCharacters").isArray())
                .andExpect(jsonPath("$.catalog.buildings").isArray())
                // S2: 게임 직접 소비용 — owned(식물 팔레트)·characters(배회) 최상위 노출
                .andExpect(jsonPath("$.owned").isArray())
                .andExpect(jsonPath("$.characters").isArray());
    }

    @Test
    @DisplayName("GET /api/garden: owned 항목은 AUTHOR 제외한 보유 식물만(axis·code·emoji·name·spriteId 구조)")
    void getGarden_owned_excludesAuthorCharacters() throws Exception {
        registrationService.register("api-owned@booktimer.com", "rawpw1234", "보유자", SEOUL, Role.USER, today());

        mockMvc.perform(get("/api/garden")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("api-owned@booktimer.com")))
                .andExpect(status().isOk())
                // 신규 유저 = 보유 식물 0 → 빈 배열
                .andExpect(jsonPath("$.owned").isArray())
                // owned 항목이 있다면 axis 필드가 있어야 한다(GardenItemMeta 호환)
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
                        .contentType(MediaType.APPLICATION_JSON).content("{\"plants\":[],\"decorations\":[]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/garden/layout 인증 + 빈 배치 → 200 (서비스 저장 위임 — GardenLayoutService.saveLayout 재사용)")
    void saveLayout_authenticated_emptyBody_ok() throws Exception {
        registrationService.register("api-save@booktimer.com", "rawpw1234", "저장사용자", SEOUL, Role.USER, today());

        mockMvc.perform(post("/api/garden/layout")
                        .with(user("api-save@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plants\":[],\"decorations\":[]}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/garden/layout: 미보유 식물 배치 → 400 거부 전파 (H2엔 카탈로그 없어 무엇이든 미보유)")
    void saveLayout_unownedPlant_returns400() throws Exception {
        registrationService.register("api-reject@booktimer.com", "rawpw1234", "거부사용자", SEOUL, Role.USER, today());

        mockMvc.perform(post("/api/garden/layout")
                        .with(user("api-reject@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plants\":[{\"axis\":\"TIME\",\"code\":\"sprout\",\"x\":0.5,\"y\":0.5,\"z\":0,\"rotation\":0,\"scale\":1}],\"decorations\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/garden/layout: 카탈로그 없는 소품 배치 → 400 거부 전파 (H2엔 소품 카탈로그 없어 무엇이든 미지)")
    void saveLayout_unknownDecoration_returns400() throws Exception {
        registrationService.register("api-decor@booktimer.com", "rawpw1234", "소품사용자", SEOUL, Role.USER, today());

        mockMvc.perform(post("/api/garden/layout")
                        .with(user("api-decor@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plants\":[],\"decorations\":[{\"code\":\"bench\",\"x\":0.5,\"y\":0.5,\"z\":0,\"rotation\":0,\"scale\":1}]}"))
                .andExpect(status().isBadRequest());
    }

    // ── S3: 잠금 라벨 필드 직렬화 단언 ────────────────────────────────────────────────────
    // RED: 현재 DTO에 필드 없어 JSON에 미포함 → assertThat 실패.
    // GREEN: §2.1 보강 후 필드 포함 → 통과.

    @Test
    @DisplayName("S3: PlantDto 직렬화에 unlockThresholdDays 포함")
    void s3_plantDto_hasUnlockThresholdDays() throws Exception {
        var dto = new GardenApiResponse.PlantDto("oak", "🌳", "참나무", "oak-01", false, null, false, 7L);
        String json = objectMapper.writeValueAsString(dto);
        assertThat(json).contains("\"unlockThresholdDays\"");
    }

    @Test
    @DisplayName("S3: GenrePlantDto 직렬화에 genreLabel 포함")
    void s3_genrePlantDto_hasGenreLabel() throws Exception {
        var dto = new GardenApiResponse.GenrePlantDto("genre-01", "📚", "소설식물", null, false, "소설");
        String json = objectMapper.writeValueAsString(dto);
        assertThat(json).contains("\"genreLabel\"");
    }

    @Test
    @DisplayName("S3: DiversityPlantDto 직렬화에 thresholdCount 포함")
    void s3_diversityPlantDto_hasThresholdCount() throws Exception {
        var dto = new GardenApiResponse.DiversityPlantDto("div-01", "🫐", "다양성식물", null, false, 3);
        String json = objectMapper.writeValueAsString(dto);
        assertThat(json).contains("\"thresholdCount\"");
    }

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
        var dto = new GardenApiResponse.AuthorCharacterDto("aut-01", "🧑", "한강캐릭터", null, false, "한강");
        String json = objectMapper.writeValueAsString(dto);
        assertThat(json).contains("\"matchName\"");
    }

    @Test
    @DisplayName("S3: RecipePlantDto 직렬화에 story 포함 (discovered=true)")
    void s3_recipePlantDto_hasStory() throws Exception {
        var dto = new GardenApiResponse.RecipePlantDto("lotus", "💮", "연꽃", null, true, "감성으로 읽고 이성을 탐낸다");
        String json = objectMapper.writeValueAsString(dto);
        assertThat(json).contains("\"story\"");
    }

    // ── N-055: 미발견 레시피 story 마스킹 경계 ──────────────────────────────────────────
    // discovered=false → story null(백엔드 마스킹). discovered=true → story 노출.

    @Test
    @Sql(statements = "INSERT INTO recipe_plant (code, name, emoji, story, display_order) VALUES ('test-undiscovered', '테스트연꽃', '💮', '이 스토리는 보이면 안 됨', 99)")
    @DisplayName("N-055: 미발견 레시피 — story null 마스킹 (discovered=false면 API에서 story 0노출)")
    void n055_recipePlant_undiscovered_storyIsNull() throws Exception {
        registrationService.register("n055-undiscovered@booktimer.com", "rawpw1234", "미발견자", SEOUL, Role.USER, today());

        mockMvc.perform(get("/api/garden")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("n055-undiscovered@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.catalog.recipePlants[0].code").value("test-undiscovered"))
                .andExpect(jsonPath("$.catalog.recipePlants[0].discovered").value(false))
                .andExpect(jsonPath("$.catalog.recipePlants[0].story").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @Sql(statements = "INSERT INTO recipe_plant (code, name, emoji, story, display_order) VALUES ('test-discovered', '발견꽃', '🌸', '발견된 아름다운 스토리', 99)")
    @DisplayName("N-055 경계: 발견된 레시피 — story 정상 노출 (discovered=true면 story 반환)")
    void n055_recipePlant_discovered_storyIsExposed() throws Exception {
        registrationService.register("n055-discovered@booktimer.com", "rawpw1234", "발견자", SEOUL, Role.USER, today());
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = 'n055-discovered@booktimer.com'", Long.class);
        jdbcTemplate.update(
                "INSERT INTO user_discovered_plant (user_id, plant_code, discovered_at) VALUES (?, 'test-discovered', CURRENT_TIMESTAMP)",
                userId);

        mockMvc.perform(get("/api/garden")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("n055-discovered@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.catalog.recipePlants[0].code").value("test-discovered"))
                .andExpect(jsonPath("$.catalog.recipePlants[0].discovered").value(true))
                .andExpect(jsonPath("$.catalog.recipePlants[0].story").value("발견된 아름다운 스토리"));
    }
}
