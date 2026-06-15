package com.booktimer.web;

import com.booktimer.garden.GardenLayoutService;
import com.booktimer.garden.GardenView;
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
    @DisplayName("GET /garden: 미인증이면 로그인 페이지로 리다이렉트된다 (기본 잠김이 실제로 막는다)")
    void garden_unauthenticated_redirectsToLogin() throws Exception {
        // /garden은 permitAll 목록에 없어 default-deny에 걸려야 한다(SecurityConfig.anyRequest().authenticated()).
        mockMvc.perform(get("/garden"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("GET /garden: 인증 사용자에게 garden 뷰를 그리고 GardenView를 모델에 싣는다")
    void garden_rendersForLoggedInUser() throws Exception {
        registrationService.register("garden@booktimer.com", "rawpw1234", "정원사", SEOUL, Role.USER, today());

        var result = mockMvc.perform(get("/garden").with(user("garden@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("garden"))
                .andExpect(model().attribute("nickname", "정원사"))
                .andExpect(model().attributeExists("garden"))
                .andReturn();

        Object garden = result.getModelAndView().getModel().get("garden");
        assertThat(garden).isInstanceOf(GardenView.class);
        // 4축 슬롯(시간·장르·작가출판사·레시피)이 모두 배선된다(카탈로그는 V35~V38 시드, H2엔 비어 non-null만 확인).
        assertThat(((GardenView) garden).plants()).isNotNull();
        assertThat(((GardenView) garden).genrePlants()).isNotNull();
        assertThat(((GardenView) garden).diversityPlants()).isNotNull();
        assertThat(((GardenView) garden).recipePlants()).isNotNull();
    }

    @Test
    @DisplayName("GET /garden: 식물 SVG 스프라이트 정의가 페이지에 주입된다 (A2 — th:block replace + 14종 symbol 파싱)")
    void garden_injectsSvgSpriteDefs() throws Exception {
        registrationService.register("garden-sprites@booktimer.com", "rawpw1234", "정원사", SEOUL, Role.USER, today());

        // 화면 밖 <symbol> 정의가 렌더된 HTML에 실제로 들어오는지 — 프래그먼트 replace·SVG 마크업 파싱 배선 확인.
        // (보유 종의 <use href> 분기는 카탈로그 의존이라 preview 시각 게이트로 검증 — 좌표·색은 비검증.)
        String html = mockMvc.perform(get("/garden").with(user("garden-sprites@booktimer.com")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("id=\"sprite-sprout\"");          // 첫 종
        assertThat(html).contains("id=\"sprite-deciduous_tree\""); // 마지막 종(14종 전체가 주입됨)
    }

    @Test
    @DisplayName("GET /garden: principal의 유저로 view를 싣는다 (현재 유저 해소 — 닉네임이 그 유저)")
    void garden_loadsCurrentUserView() throws Exception {
        registrationService.register("me@booktimer.com", "rawpw1234", "나야", SEOUL, Role.USER, today());
        // 다른 유저도 존재하지만, principal=me의 닉네임이 실려야 한다(교차 유저 데이터 아님).
        registrationService.register("other@booktimer.com", "rawpw1234", "남이야", SEOUL, Role.USER, today());

        mockMvc.perform(get("/garden").with(user("me@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("nickname", "나야"));
    }

    @Test
    @DisplayName("GET /garden: 배치 렌더 모델(placedPlants·월드 크기)이 함께 실린다")
    void garden_includesLayoutModel() throws Exception {
        registrationService.register("garden-layout@booktimer.com", "rawpw1234", "정원사", SEOUL, Role.USER, today());

        mockMvc.perform(get("/garden").with(user("garden-layout@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("placedPlants"))
                .andExpect(model().attribute("worldWidth", GardenLayoutService.WORLD_WIDTH))
                .andExpect(model().attribute("worldHeight", GardenLayoutService.WORLD_HEIGHT));
    }

    @Test
    @DisplayName("POST /garden/layout: 미인증이면 로그인으로 막힌다 (기본 잠김)")
    void saveLayout_unauthenticated_blocked() throws Exception {
        mockMvc.perform(post("/garden/layout").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("POST /garden/layout: 빈 배치 저장은 성공한다 (캔버스 비우기)")
    void saveLayout_emptyBody_ok() throws Exception {
        registrationService.register("garden-save@booktimer.com", "rawpw1234", "정원사", SEOUL, Role.USER, today());

        mockMvc.perform(post("/garden/layout").with(user("garden-save@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /garden/layout: 미보유 식물 배치 요청은 4xx로 거부된다 (H2엔 카탈로그가 비어 무엇이든 미보유)")
    void saveLayout_unownedPlant_rejected() throws Exception {
        registrationService.register("garden-reject@booktimer.com", "rawpw1234", "정원사", SEOUL, Role.USER, today());

        mockMvc.perform(post("/garden/layout").with(user("garden-reject@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"axis\":\"TIME\",\"code\":\"sprout\",\"x\":0.5,\"y\":0.5,\"z\":0,\"rotation\":0,\"scale\":1}]"))
                .andExpect(status().isBadRequest());
    }
}
