package com.booktimer.web.api;

import com.booktimer.profile.ProfileService;
import com.booktimer.report.ReportReason;
import com.booktimer.report.ReportRepository;
import com.booktimer.report.ReportService;
import com.booktimer.security.CurrentUserService;
import com.booktimer.security.RateLimitAction;
import com.booktimer.security.RateLimitService;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

/**
 * 신고 JSON API (선별 SPA 단계 2d).
 *
 * <p>SSR {@code ReportController}(POST /report)는 유지, 이 API는 Vue 섬용 fetch.
 *
 * <p>보안: 차단된 상대·ADMIN·미존재 → 404(조회 API와 차단 경계 일치). 자기신고 → 400(User.id 동일성).
 * 레이트리밋 초과 → 무음(200), 응답은 현재 reported 상태 반환.
 */
@RestController
public class ReportApiController {

    private final ProfileService profileService;
    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;
    private final ReportService reportService;
    private final ReportRepository reportRepository;
    private final RateLimitService rateLimitService;

    public ReportApiController(ProfileService profileService,
                               CurrentUserService currentUserService,
                               UserRepository userRepository,
                               ReportService reportService,
                               ReportRepository reportRepository,
                               RateLimitService rateLimitService) {
        this.profileService = profileService;
        this.currentUserService = currentUserService;
        this.userRepository = userRepository;
        this.reportService = reportService;
        this.reportRepository = reportRepository;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping("/api/report")
    public ResponseEntity<ReportResult> report(@RequestBody ReportRequest req, Principal principal) {
        User me = currentUserService.resolve(principal);

        // 차단 게이트: resolveVisibleTarget 3중 가드 — 차단·ADMIN·미존재 모두 404(조회 API와 경계 일치)
        profileService.profileOf(me, req.loginId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다"));

        // 가드 통과 후 엔티티 확보
        User target = userRepository.findByLoginId(req.loginId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다"));

        // 자기신고 판정: User.getId() 동일성(loginId 문자열 비교 금지 — 대소문자 우회 차단)
        if (target.getId().equals(me.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "자기 자신은 신고할 수 없습니다");
        }

        if (rateLimitService.allow(RateLimitAction.REPORT, me.getId())) {
            try {
                reportService.report(me, target, ReportReason.from(req.reason()), req.detail());
            } catch (IllegalArgumentException ignored) {
                // 방어용: ReportReason.from()이 항상 non-null, 자기신고는 위 400에서 이미 차단
            }
        }

        return ResponseEntity.ok(new ReportResult(reportRepository.existsByReporterAndReported(me, target)));
    }

    public record ReportRequest(String loginId, String reason, String detail) {}

    /** 멱등 — "내가 이 사람을 신고한 상태인가". */
    public record ReportResult(boolean reported) {}
}
