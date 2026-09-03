package com.booktimer.web;

import com.booktimer.study.StudyAiAccessService;
import com.booktimer.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Clock;
import java.util.Optional;
import java.util.function.Function;

/**
 * AI 기능 승인 — 관리자가 대기 중인 신청을 수락·거절하고, 이미 준 승인을 회수한다.
 *
 * <p>접근 제어는 {@link com.booktimer.config.SecurityConfig}가 {@code /admin/**} → {@code hasRole("ADMIN")}로
 * 강제한다(여기서 재검사하지 않음 — 경계는 필터에서 한 번만). <b>관리자 우회는 없다</b>: 관리자 자신도
 * {@code /study}에서 신청한 뒤 여기서 승인해야 AI를 쓴다(설계 §2.6).
 *
 * <p>목록 렌더는 {@link AdminController#dashboard}가 맡는다 — 이 화면은 대시보드의 섹션 하나라 별도
 * GET이 없다. 목록이 페이지네이션을 요구할 만큼 길어지는 날 전용 페이지로 분리하면 되고, 그때 이 POST
 * 셋은 그대로 옮겨진다.
 */
@Controller
public class AdminStudyAiController {

    private final StudyAiAccessService accessService;
    private final Clock clock;

    public AdminStudyAiController(StudyAiAccessService accessService, Clock clock) {
        this.accessService = accessService;
        this.clock = clock;
    }

    @PostMapping("/admin/study-ai/{loginId}/approve")
    public String approve(@PathVariable("loginId") String loginId, RedirectAttributes redirectAttributes) {
        return apply(loginId, redirectAttributes, "승인",
                id -> accessService.approve(id, clock.instant()));
    }

    @PostMapping("/admin/study-ai/{loginId}/reject")
    public String reject(@PathVariable("loginId") String loginId, RedirectAttributes redirectAttributes) {
        return apply(loginId, redirectAttributes, "거절",
                id -> accessService.reject(id, clock.instant()));
    }

    @PostMapping("/admin/study-ai/{loginId}/revoke")
    public String revoke(@PathVariable("loginId") String loginId, RedirectAttributes redirectAttributes) {
        return apply(loginId, redirectAttributes, "승인 회수",
                id -> accessService.revoke(id, clock.instant()));
    }

    /**
     * 셋의 공통 마무리 — <b>없는 사람과 잘못된 전이를 다르게 다룬다.</b>
     *
     * <p>없는 아이디는 404다(경로가 가리키는 것이 없다). 반면 「신청한 적 없는 사람을 승인」처럼 사람은
     * 있는데 상태가 안 맞는 것은 500도 404도 아니라 <b>대시보드로 돌아가 플래시 오류</b>다 — 목록을 띄워
     * 놓은 사이 사용자가 상태를 바꿨거나(재신청 취소는 없지만 신청은 있다) 두 탭에서 두 번 눌렀을 때가
     * 정확히 이 경로이고, 그건 운영자의 실수가 아니라 정상적인 경합이다.
     */
    private String apply(String loginId, RedirectAttributes redirectAttributes, String label,
                         Function<String, Optional<User>> action) {
        try {
            action.apply(loginId).orElseThrow(
                    () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다"));
            redirectAttributes.addFlashAttribute("message", "@" + loginId + " " + label);
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", "이미 처리된 신청이에요");
        }
        return "redirect:/admin";
    }
}
