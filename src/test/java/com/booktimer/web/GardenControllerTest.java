package com.booktimer.web;

import com.booktimer.user.Role;
import com.booktimer.user.UserRegistrationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import org.springframework.http.MediaType;

/**
 * 독서 정원 도감 전용 페이지({@code /garden}) 컨트롤러 통합 테스트 (MockMvc + 실제 빈·H2).
 *
 * <p>1차 핵심 가치는 "전용 페이지 + 축 탭 + 보유/미보유 필터"이고, 데이터는 대시보드와 같은
 * {@link com.booktimer.garden.GardenService#view}를 재사용한다. 그래서 여기선 라우팅·인증·모델 적재
 * (배선)만 못 박는다 — 탭·필터·상세는 순수 클라이언트(Alpine) 렌더라 단위테스트가 무의미해 preview로 검증한다.
 * 식물 카탈로그(V35~V38 시드)는 H2 테스트엔 비어 있어, 내용이 아니라 GardenView 타입·비-null만 본다
 * ({@code DashboardControllerTest.dashboard_includesGardenView}와 동일 관례).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GardenControllerTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRegistrationService registrationService;

    @Autowired
    private Clock clock;

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    @Test
    @DisplayName("GET /garden → 302 /village: 레거시 리다이렉트 — 인증 사용자의 옛 북마크를 새 URL로 안내한다")
    void garden_redirectsToVillage() throws Exception {
        registrationService.register("redir@booktimer.com", "rawpw1234", "리다이렉트", SEOUL, Role.USER, today());
        mockMvc.perform(get("/garden").with(user("redir@booktimer.com")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/village"));
    }

    @Test
    @DisplayName("GET /village: 미인증이면 로그인 페이지로 리다이렉트된다 (기본 잠김이 실제로 막는다)")
    void village_unauthenticated_redirectsToLogin() throws Exception {
        // /village은 permitAll 목록에 없어 default-deny에 걸려야 한다(SecurityConfig.anyRequest().authenticated()).
        mockMvc.perform(get("/village"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("GET /village: SPA 셸 — nickname만 모델에 담고 garden·placedItems는 모델에 없다 (S4 컷오버)")
    void village_spaShell_hasNicknameOnly() throws Exception {
        registrationService.register("spa@booktimer.com", "rawpw1234", "SPA사용자", SEOUL, Role.USER, today());

        mockMvc.perform(get("/village").with(user("spa@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("nickname", "SPA사용자"))
                .andExpect(model().attributeDoesNotExist("garden"))
                .andExpect(model().attributeDoesNotExist("placedItems"));
    }

    @Test
    @DisplayName("GET /village: 인증 사용자에게 garden 뷰를 그리고 nickname을 모델에 싣는다 (S4: SPA 셸)")
    void village_rendersForLoggedInUser() throws Exception {
        registrationService.register("garden@booktimer.com", "rawpw1234", "정원사", SEOUL, Role.USER, today());

        mockMvc.perform(get("/village").with(user("garden@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("garden"))
                .andExpect(model().attribute("nickname", "정원사"));
        // S4 컷오버: garden·placedItems 등 SSR 모델은 제거 — API(/api/garden)로 분리됨.
    }

    @Test
    @DisplayName("GET /village: 식물 SVG 스프라이트 정의가 페이지에 주입된다 (A2 — th:block replace + 14종 symbol 파싱)")
    void village_injectsSvgSpriteDefs() throws Exception {
        registrationService.register("garden-sprites@booktimer.com", "rawpw1234", "정원사", SEOUL, Role.USER, today());

        // 화면 밖 <symbol> 정의가 렌더된 HTML에 실제로 들어오는지 — 프래그먼트 replace·SVG 마크업 파싱 배선 확인.
        // (보유 종의 <use href> 분기는 카탈로그 의존이라 preview 시각 게이트로 검증 — 좌표·색은 비검증.)
        String html = mockMvc.perform(get("/village").with(user("garden-sprites@booktimer.com")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("id=\"sprite-sprout\"");          // 첫 종
        assertThat(html).contains("id=\"sprite-deciduous_tree\""); // 마지막 종(14종 전체가 주입됨)
    }

    @Test
    @DisplayName("GET /village: principal의 유저로 view를 싣는다 (현재 유저 해소 — 닉네임이 그 유저)")
    void village_loadsCurrentUserView() throws Exception {
        registrationService.register("me@booktimer.com", "rawpw1234", "나야", SEOUL, Role.USER, today());
        // 다른 유저도 존재하지만, principal=me의 닉네임이 실려야 한다(교차 유저 데이터 아님).
        registrationService.register("other@booktimer.com", "rawpw1234", "남이야", SEOUL, Role.USER, today());

        mockMvc.perform(get("/village").with(user("me@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("nickname", "나야"));
    }

    @Test
    @DisplayName("GET /village: SPA 셸에 #village-app 마운트 포인트가 존재한다 (S4 컷오버)")
    void village_spaShell_hasVillageAppMount() throws Exception {
        registrationService.register("garden-layout@booktimer.com", "rawpw1234", "정원사", SEOUL, Role.USER, today());

        String html = mockMvc.perform(get("/village").with(user("garden-layout@booktimer.com")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("id=\"village-app\"");
    }

    @Test
    @DisplayName("GET /village: 소품 SVG 스프라이트 정의가 페이지에 주입된다 (Phase 3 — decor 프래그먼트 replace)")
    void village_injectsDecorSpriteDefs() throws Exception {
        registrationService.register("garden-decor-sprites@booktimer.com", "rawpw1234", "정원사", SEOUL, Role.USER, today());

        // 소품 <symbol> 정의는 카탈로그 의존 없이 정적이라 항상 주입된다(보유 무관). 첫·끝 종 id로 확인.
        String html = mockMvc.perform(get("/village").with(user("garden-decor-sprites@booktimer.com")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("id=\"sprite-stone_path\""); // 첫 소품
        assertThat(html).contains("id=\"sprite-mushrooms\"");  // 마지막 소품(13종 전체 주입)
    }

    @Test
    @DisplayName("POST /village/layout: 미인증이면 로그인으로 막힌다 (기본 잠김)")
    void saveLayout_unauthenticated_blocked() throws Exception {
        mockMvc.perform(post("/village/layout").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("POST /village/layout: 빈 배치 저장은 성공한다 (캔버스 비우기 — 식물·소품 둘 다 빈 래퍼)")
    void saveLayout_emptyBody_ok() throws Exception {
        registrationService.register("garden-save@booktimer.com", "rawpw1234", "정원사", SEOUL, Role.USER, today());

        // 래퍼 {plants, decorations} — null 종은 빈 리스트로 본다(plantsOrEmpty/decorationsOrEmpty).
        mockMvc.perform(post("/village/layout").with(user("garden-save@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"plants\":[],\"decorations\":[]}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /village/layout: 미보유 식물 배치 요청은 4xx로 거부된다 (H2엔 카탈로그가 비어 무엇이든 미보유)")
    void saveLayout_unownedPlant_rejected() throws Exception {
        registrationService.register("garden-reject@booktimer.com", "rawpw1234", "정원사", SEOUL, Role.USER, today());

        mockMvc.perform(post("/village/layout").with(user("garden-reject@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plants\":[{\"axis\":\"TIME\",\"code\":\"sprout\",\"x\":0.5,\"y\":0.5,\"z\":0,\"rotation\":0,\"scale\":1}],\"decorations\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /village/layout: 카탈로그에 없는 소품 배치 요청은 4xx로 거부된다 (H2엔 소품 카탈로그가 비어 무엇이든 미지)")
    void saveLayout_unknownDecoration_rejected() throws Exception {
        registrationService.register("garden-decor-reject@booktimer.com", "rawpw1234", "정원사", SEOUL, Role.USER, today());

        mockMvc.perform(post("/village/layout").with(user("garden-decor-reject@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plants\":[],\"decorations\":[{\"code\":\"bench\",\"x\":0.5,\"y\":0.5,\"z\":0,\"rotation\":0,\"scale\":1}]}"))
                .andExpect(status().isBadRequest());
    }
}
