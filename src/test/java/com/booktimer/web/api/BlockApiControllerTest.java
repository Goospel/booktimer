package com.booktimer.web.api;

import com.booktimer.block.BlockService;
import com.booktimer.user.Role;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * GET /api/blocks · POST /api/block · /api/unblock 컨트롤러 통합 테스트.
 *
 * <p>①미인증 302, ②조회 구조, ③차단→blocked:true+목록반영, ④해제→blocked:false+사라짐,
 * ⑤자기차단 400, ⑥미존재 404, ⑦양방향 비노출(단방향만), ⑧loginId:null 없음(N-055 가드).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BlockApiControllerTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRegistrationService registrationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BlockService blockService;

    @Autowired
    private Clock clock;

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    @Test
    @DisplayName("GET /api/blocks 미인증 → 302 로그인 리다이렉트")
    void blocks_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/api/blocks"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("POST /api/block 미인증 → 302 로그인 리다이렉트")
    void block_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(post("/api/block").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"someone\"}"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("GET /api/blocks → {blocked[], myLoginId} 구조 반환")
    void blocks_authenticated_returnsBlockedListAndMyLoginId() throws Exception {
        registrationService.register("blk-me@booktimer.com", "pw1234qwer!!", "blkmeid", "차단자", SEOUL, Role.USER, today());
        registrationService.register("blk-tgt@booktimer.com", "pw1234qwer!!", "blktgtid", "차단대상", SEOUL, Role.USER, today());
        User me = userRepository.findByLoginId("blkmeid").orElseThrow();
        User target = userRepository.findByLoginId("blktgtid").orElseThrow();
        blockService.block(me, target);

        mockMvc.perform(get("/api/blocks")
                        .with(user("blk-me@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myLoginId").value("blkmeid"))
                .andExpect(jsonPath("$.blocked[0].loginId").value("blktgtid"));
    }

    @Test
    @DisplayName("POST /api/block → blocked:true, 이후 GET에서 목록 반영")
    void block_validTarget_returnsBlockedTrue_andAppearsInList() throws Exception {
        registrationService.register("blk-actor@booktimer.com", "pw1234qwer!!", "blkactorid", "차단행위자", SEOUL, Role.USER, today());
        registrationService.register("blk-victim@booktimer.com", "pw1234qwer!!", "blkvictimid", "차단피해자", SEOUL, Role.USER, today());

        mockMvc.perform(post("/api/block")
                        .with(user("blk-actor@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"blkvictimid\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked").value(true));

        mockMvc.perform(get("/api/blocks")
                        .with(user("blk-actor@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked[0].loginId").value("blkvictimid"));
    }

    @Test
    @DisplayName("POST /api/unblock → blocked:false, GET 목록에서 사라짐")
    void unblock_afterBlock_returnsBlockedFalse_andDisappearsFromList() throws Exception {
        registrationService.register("ublk-me@booktimer.com", "pw1234qwer!!", "ublkmeid", "해제자", SEOUL, Role.USER, today());
        registrationService.register("ublk-tgt@booktimer.com", "pw1234qwer!!", "ublktgtid", "해제대상", SEOUL, Role.USER, today());
        User me = userRepository.findByLoginId("ublkmeid").orElseThrow();
        User target = userRepository.findByLoginId("ublktgtid").orElseThrow();
        blockService.block(me, target);

        mockMvc.perform(post("/api/unblock")
                        .with(user("ublk-me@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"ublktgtid\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked").value(false));

        mockMvc.perform(get("/api/blocks")
                        .with(user("ublk-me@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked", hasSize(0)));
    }

    @Test
    @DisplayName("POST /api/block 자기 자신 → 400")
    void block_self_returns400() throws Exception {
        registrationService.register("blk-self@booktimer.com", "pw1234qwer!!", "blkselfid", "자기차단시도자", SEOUL, Role.USER, today());

        mockMvc.perform(post("/api/block")
                        .with(user("blk-self@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"blkselfid\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/block 없는 loginId → 404")
    void block_nonexistentLoginId_returns404() throws Exception {
        registrationService.register("blk-nf@booktimer.com", "pw1234qwer!!", "blknfid", "존재확인자", SEOUL, Role.USER, today());

        mockMvc.perform(post("/api/block")
                        .with(user("blk-nf@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"zz_nonexistent_block_xyz\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("상대가 나를 차단해도 내 GET /api/blocks에는 나타나지 않는다(단방향 비노출)")
    void blocks_onlyShowsMyBlocks_notCounterpartBlocks() throws Exception {
        registrationService.register("blk-a@booktimer.com", "pw1234qwer!!", "blkaid", "A유저", SEOUL, Role.USER, today());
        registrationService.register("blk-b@booktimer.com", "pw1234qwer!!", "blkbid", "B유저", SEOUL, Role.USER, today());
        User userA = userRepository.findByLoginId("blkaid").orElseThrow();
        User userB = userRepository.findByLoginId("blkbid").orElseThrow();
        blockService.block(userB, userA); // B가 A를 차단 — A는 차단하지 않음

        mockMvc.perform(get("/api/blocks")
                        .with(user("blk-a@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/blocks 응답에 loginId:null 없음(N-055 회귀 가드)")
    void blocks_noNullLoginIdInResponse() throws Exception {
        registrationService.register("blk-gd@booktimer.com", "pw1234qwer!!", "blkgdid", "가드테스터", SEOUL, Role.USER, today());
        registrationService.register("blk-gdtgt@booktimer.com", "pw1234qwer!!", "blkgdtid", "가드대상", SEOUL, Role.USER, today());
        User me = userRepository.findByLoginId("blkgdid").orElseThrow();
        User target = userRepository.findByLoginId("blkgdtid").orElseThrow();
        blockService.block(me, target);

        mockMvc.perform(get("/api/blocks")
                        .with(user("blk-gd@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("\"loginId\":null"))));
    }
}
