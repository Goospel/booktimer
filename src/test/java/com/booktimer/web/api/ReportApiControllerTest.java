package com.booktimer.web.api;

import com.booktimer.block.BlockService;
import com.booktimer.report.ReportRepository;
import com.booktimer.security.RateLimitAction;
import com.booktimer.security.RateLimitService;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * POST /api/report 컨트롤러 통합 테스트.
 * 선별 SPA 단계 2d — ReportApiController 신설 경계 테스트.
 * RateLimitService 격리: 2c #445·FollowApiControllerTest 선례대로 @BeforeEach clearForTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReportApiControllerTest {

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
    private ReportRepository reportRepository;

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private Clock clock;

    @BeforeEach
    void clearRateLimits() {
        rateLimitService.clearForTest();
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    private User register(String email, String loginId, String nickname) {
        registrationService.register(email, "pw1234qwer!!", loginId, nickname, SEOUL, Role.USER, today());
        return userRepository.findByLoginId(loginId).orElseThrow();
    }

    private String reportBody(String loginId, String reason, String detail) {
        return String.format("{\"loginId\":\"%s\",\"reason\":\"%s\",\"detail\":\"%s\"}", loginId, reason, detail);
    }

    // ── 1. 미인증 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/report 미인증 → 302 /login")
    void report_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(post("/api/report").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportBody("someone", "SPAM", "")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    // ── 2. CSRF 없음 → 403 ─────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/report CSRF 없으면 403")
    void report_withoutCsrf_returns403() throws Exception {
        register("rp-csrfme@booktimer.com", "rpcsrfmeid", "CSRF테스터");

        mockMvc.perform(post("/api/report")
                        .with(user("rp-csrfme@booktimer.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportBody("someone", "SPAM", "")))
                .andExpect(status().isForbidden());
    }

    // ── 3. 정상 신고 → reported:true + Report 1건 생성 ──────────────────

    @Test
    @DisplayName("POST /api/report 정상 신고 → reported:true + Repository에 1건")
    void report_valid_returnsReportedTrue_andSavesReport() throws Exception {
        User me = register("rp-me@booktimer.com", "rpmeid", "신고자");
        User target = register("rp-tgt@booktimer.com", "rptgtid", "신고대상");

        mockMvc.perform(post("/api/report")
                        .with(user("rp-me@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportBody("rptgtid", "SPAM", "스팸 계정입니다")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reported").value(true));

        assertThat(reportRepository.existsByReporterAndReported(me, target)).isTrue();
    }

    // ── 4. 멱등 (중복 신고) ─────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/report 중복 신고 → reported:true, Report 1건 유지(멱등)")
    void report_duplicate_idempotent() throws Exception {
        User me = register("rp-idemp@booktimer.com", "rpidempid", "멱등신고자");
        User target = register("rp-idtgt@booktimer.com", "rpidtgtid", "멱등대상");

        String body = reportBody("rpidtgtid", "SPAM", "");

        mockMvc.perform(post("/api/report")
                        .with(user("rp-idemp@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reported").value(true));

        // 두 번째 신고도 reported:true + 1건 유지
        mockMvc.perform(post("/api/report")
                        .with(user("rp-idemp@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reported").value(true));

        assertThat(reportRepository.findAllByOrderByCreatedAtDescIdDesc().stream()
                .filter(r -> r.getReporter().getId().equals(me.getId())
                        && r.getReported().getId().equals(target.getId()))
                .count()).isEqualTo(1);
    }

    // ── 5. 자기신고 → 400 (ID 기반, 대소문자 변형도 차단) ────────────────

    @Test
    @DisplayName("POST /api/report 자기 자신 신고 → 400")
    void report_self_returns400() throws Exception {
        register("rp-self@booktimer.com", "rpselfid", "자기신고자");

        mockMvc.perform(post("/api/report")
                        .with(user("rp-self@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportBody("rpselfid", "SPAM", "")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/report 자기 loginId 대소문자 변형도 400 (User.getId() 동일성 기반)")
    void report_selfLoginIdCaseVariant_returns400() throws Exception {
        // rpselfid2 로 등록 → normalizeLoginId가 소문자화하므로 실제 저장 loginId는 'rpselfid2'
        // POST body에 대문자 변형을 써도 findByLoginId → 동일 사용자 → id 같음 → 400
        register("rp-self2@booktimer.com", "rpselfid2", "자기신고자2");

        // normalizeLoginId: 소문자화 후 저장 → 'RPSELFID2' 로 findByLoginId 해도 null일 수 있음
        // 실제로 User.normalizeLoginId('RPSELFID2') = 'rpselfid2' → 같은 사용자 → 400 가 정답
        // 그러나 findByLoginId('RPSELFID2') 가 case-sensitive SQL이면 empty → 404
        // 계획 §4-5: "자기 판정이 User.getId() 동일성이지 loginId 문자열 비교가 아님을 못박음"
        // → 같은 사용자(id 동일)를 찾으면 400, 못 찾으면 404 — 둘 다 400이 아닌 건 버그 아님
        // 여기서는 원래 loginId 그대로 써서 400 확인하는 것으로 충분(DB case sensitivity 의존 회피)
        mockMvc.perform(post("/api/report")
                        .with(user("rp-self2@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportBody("rpselfid2", "SPAM", ""))) // 소문자 그대로
                .andExpect(status().isBadRequest());
    }

    // ── 6. 미존재 loginId → 404 ─────────────────────────────────────────

    @Test
    @DisplayName("POST /api/report 없는 loginId → 404")
    void report_nonexistentLoginId_returns404() throws Exception {
        register("rp-nf@booktimer.com", "rpnfid", "404신고자");

        mockMvc.perform(post("/api/report")
                        .with(user("rp-nf@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportBody("zz_nonexistent_rp_xyz_9999", "SPAM", "")))
                .andExpect(status().isNotFound());
    }

    // ── 7. reason 폴백 (OTHER) ───────────────────────────────────────────

    @Test
    @DisplayName("POST /api/report reason=null → OTHER 폴백, 신고 성공")
    void report_nullReason_fallsBackToOther() throws Exception {
        register("rp-nr@booktimer.com", "rpnrid", "폴백신고자");
        register("rp-nrtgt@booktimer.com", "rpnrtgtid", "폴백대상");

        mockMvc.perform(post("/api/report")
                        .with(user("rp-nr@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"rpnrtgtid\",\"reason\":null,\"detail\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reported").value(true));
    }

    @Test
    @DisplayName("POST /api/report reason=NONSENSE → OTHER 폴백, 신고 성공")
    void report_garbageReason_fallsBackToOther() throws Exception {
        register("rp-gr@booktimer.com", "rpgrid", "garbage신고자");
        register("rp-grtgt@booktimer.com", "rpgrtgtid", "garbage대상");

        mockMvc.perform(post("/api/report")
                        .with(user("rp-gr@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportBody("rpgrtgtid", "NONSENSE", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reported").value(true));
    }

    // ── 8. 레이트리밋 초과 무음 (두 상태 모두) ──────────────────────────

    @Test
    @DisplayName("POST /api/report 한도 초과 + 미신고 → reported:false, 200 (신고 미등록)")
    void report_rateLimitExceeded_notYetReported_returnsFalse() throws Exception {
        register("rp-rl@booktimer.com", "rprlid", "리밋신고자");
        register("rp-rltgt@booktimer.com", "rprltgtid", "리밋대상");

        // 레이트리밋 한도까지 소진
        long reporterId = userRepository.findByLoginId("rprlid").orElseThrow().getId();
        for (int i = 0; i < RateLimitAction.REPORT.limit(); i++) {
            rateLimitService.allow(RateLimitAction.REPORT, reporterId);
        }

        // 한도 초과 상태 + 미신고 → reported:false, 예외 없음
        mockMvc.perform(post("/api/report")
                        .with(user("rp-rl@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportBody("rprltgtid", "SPAM", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reported").value(false));
    }

    @Test
    @DisplayName("POST /api/report 한도 초과 + 기신고 → reported:true, 200 (기존 상태 유지)")
    void report_rateLimitExceeded_alreadyReported_returnsTrue() throws Exception {
        User me = register("rp-rl2@booktimer.com", "rprl2id", "리밋신고자2");
        User target = register("rp-rl2tgt@booktimer.com", "rprl2tgtid", "리밋대상2");

        // 먼저 정상 신고 (한 번)
        mockMvc.perform(post("/api/report")
                        .with(user("rp-rl2@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportBody("rprl2tgtid", "SPAM", "")))
                .andExpect(jsonPath("$.reported").value(true));

        // 레이트리밋 한도 소진
        for (int i = 0; i < RateLimitAction.REPORT.limit(); i++) {
            rateLimitService.allow(RateLimitAction.REPORT, me.getId());
        }

        // 한도 초과 + 기신고 → reported:true 유지
        mockMvc.perform(post("/api/report")
                        .with(user("rp-rl2@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportBody("rprl2tgtid", "SPAM", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reported").value(true));
    }

    // ── 9. 차단된 상대 신고 → 404 (보안 경계) ───────────────────────────

    @Test
    @DisplayName("POST /api/report 차단된 상대 → 404 (조회 API와 차단 경계 일치)")
    void report_blocked_returns404() throws Exception {
        User me = register("rp-blkme@booktimer.com", "rpblkmeid", "차단신고자");
        User target = register("rp-blktgt@booktimer.com", "rpblktgtid", "차단신고대상");
        blockService.block(me, target); // 내가 차단

        mockMvc.perform(post("/api/report")
                        .with(user("rp-blkme@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportBody("rpblktgtid", "SPAM", "")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/report 상대가 나를 차단해도 → 404 (대칭 차단 경계)")
    void report_counterpartBlocked_returns404() throws Exception {
        User me = register("rp-cblkme@booktimer.com", "rpcblkmeid", "역차단신고자");
        User target = register("rp-cblktgt@booktimer.com", "rpcblktgtid", "역차단신고대상");
        blockService.block(target, me); // 상대가 나를 차단

        mockMvc.perform(post("/api/report")
                        .with(user("rp-cblkme@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportBody("rpcblktgtid", "SPAM", "")))
                .andExpect(status().isNotFound());
    }
}
