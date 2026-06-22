package com.booktimer.web;

import com.booktimer.block.BlockService;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * GET /u/{loginId} 셸 컨트롤러 통합 테스트 (선별 SPA 단계 2d).
 *
 * <p>셸은 선가드(resolveVisibleTarget 3중)만 수행하고 loginId·myLoginId를 모델에 실은 뒤 Vue 마운트 포인트를 반환한다.
 * 데이터(책 목록·팔로우·성향 등)는 {@link com.booktimer.web.api.ProfileApiController}에서 별도 테스트.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private BlockService blockService;

    private User newUser(String email, String loginId, String nickname) {
        User u = User.of(email, passwordEncoder.encode("rawpw1234"), nickname, "Asia/Seoul", Role.USER);
        u.assignLoginId(loginId);
        return userRepository.save(u);
    }

    private User newAdmin(String email, String loginId, String nickname) {
        User u = User.of(email, passwordEncoder.encode("rawpw1234"), nickname, "Asia/Seoul", Role.ADMIN);
        u.assignLoginId(loginId);
        return userRepository.save(u);
    }

    // ── 1. 404 가드 ────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /u/{loginId}: 없는 아이디면 404")
    void profile_unknownLoginId_404() throws Exception {
        newUser("viewer@booktimer.com", "viewer", "뷰어");
        mockMvc.perform(get("/u/{loginId}", "nosuchid").with(user("viewer@booktimer.com")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /u/{loginId}: 운영자(ADMIN) 프로필은 핸들을 직접 알아도 404 (존재 누설 회피)")
    void profile_admin_404() throws Exception {
        newUser("viewer@booktimer.com", "viewer", "뷰어");
        newAdmin("admin@booktimer.com", "adminhandle", "운영자");

        mockMvc.perform(get("/u/{loginId}", "adminhandle").with(user("viewer@booktimer.com")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /u/{loginId}: 운영자 본인이 자기 프로필을 조회해도 404 (소셜 프로필 비대상)")
    void profile_adminSelf_404() throws Exception {
        newAdmin("admin@booktimer.com", "adminhandle", "운영자");

        mockMvc.perform(get("/u/{loginId}", "adminhandle").with(user("adminhandle")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /u/{loginId}: 차단된 상대 → 404 (조회 불가)")
    void profile_blocked_404() throws Exception {
        User viewer = newUser("viewer@booktimer.com", "viewer", "뷰어");
        User owner = newUser("owner@booktimer.com", "openking", "공개왕");
        blockService.block(owner, viewer); // 주인이 viewer를 차단 — 대칭

        mockMvc.perform(get("/u/{loginId}", "openking").with(user("viewer@booktimer.com")))
                .andExpect(status().isNotFound());
    }

    // ── 2. 인증 가드 ──────────────────────────────────────────────────

    @Test
    @DisplayName("GET /u/{loginId}: 비로그인 → /login 리다이렉트")
    void profile_anonymous_redirectsToLogin() throws Exception {
        newUser("owner@booktimer.com", "openking", "공개왕");

        mockMvc.perform(get("/u/{loginId}", "openking"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    // ── 3. 셸 반환 ────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /u/{loginId}: 유효한 프로필 → Vue 셸 반환(loginId·myLoginId 모델에 실림)")
    void profile_valid_returnsShell() throws Exception {
        newUser("viewer@booktimer.com", "viewerid", "뷰어");
        newUser("owner@booktimer.com", "openking", "공개왕");

        mockMvc.perform(get("/u/{loginId}", "openking").with(user("viewer@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("profile"))
                .andExpect(model().attribute("loginId", "openking"))
                .andExpect(model().attribute("myLoginId", "viewerid"));
    }

    @Test
    @DisplayName("GET /u/{loginId}: 본인 프로필 → loginId == myLoginId (Vue가 self 판정)")
    void profile_self_myLoginIdMatchesLoginId() throws Exception {
        newUser("me@booktimer.com", "myself", "나자신");

        mockMvc.perform(get("/u/{loginId}", "myself").with(user("me@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("profile"))
                .andExpect(model().attribute("loginId", "myself"))
                .andExpect(model().attribute("myLoginId", "myself"));
    }
}
