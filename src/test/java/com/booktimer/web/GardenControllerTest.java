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
 * 독서 마을 페이지({@code /village}) 컨트롤러 통합 테스트.
 *
 * <p>마을 컨셉 전환 후: 식물·소품 sprite 주입 제거. 작가·건물·여백 sprite는 유지.
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
    @DisplayName("GET /village: 미인증이면 로그인 페이지로 리다이렉트된다 (기본 잠김)")
    void village_unauthenticated_redirectsToLogin() throws Exception {
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
    @DisplayName("GET /village: principal의 유저로 view를 싣는다 (현재 유저 해소 — 닉네임이 그 유저)")
    void village_loadsCurrentUserView() throws Exception {
        registrationService.register("me@booktimer.com", "rawpw1234", "나야", SEOUL, Role.USER, today());
        registrationService.register("other@booktimer.com", "rawpw1234", "남이야", SEOUL, Role.USER, today());

        mockMvc.perform(get("/village").with(user("me@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("nickname", "나야"));
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
    @DisplayName("POST /village/layout: 빈 배치 저장은 성공한다 (캔버스 비우기)")
    void saveLayout_emptyBody_ok() throws Exception {
        registrationService.register("garden-save@booktimer.com", "rawpw1234", "정원사", SEOUL, Role.USER, today());

        mockMvc.perform(post("/village/layout").with(user("garden-save@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"plants\":[]}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /village/layout: 미보유 건물 배치 요청은 4xx로 거부된다 (H2엔 카탈로그가 비어 무엇이든 미보유)")
    void saveLayout_unownedBuilding_rejected() throws Exception {
        registrationService.register("garden-reject@booktimer.com", "rawpw1234", "정원사", SEOUL, Role.USER, today());

        mockMvc.perform(post("/village/layout").with(user("garden-reject@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plants\":[{\"axis\":\"BUILDING\",\"code\":\"minumsa\",\"x\":0.5,\"y\":0.5,\"z\":0,\"rotation\":0,\"scale\":1}]}"))
                .andExpect(status().isBadRequest());
    }
}
