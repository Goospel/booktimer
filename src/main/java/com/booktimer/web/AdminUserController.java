package com.booktimer.web;

import com.booktimer.admin.AdminDebtView;
import com.booktimer.admin.AdminUserDetail;
import com.booktimer.admin.AdminUserRow;
import com.booktimer.admin.AdminUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

/**
 * 관리자 데이터 조회 — 사용자 목록 + 드릴다운 (admin-data-lookup-design §2).
 *
 * <p>접근 제어는 {@link com.booktimer.config.SecurityConfig}가 {@code /admin/**} → {@code hasRole("ADMIN")}로
 * 강제한다(여기서 다시 검사하지 않음 — 보안 경계는 필터에서 한 번만). 전부 읽기 전용(GET)이라 이 화면엔
 * 수정·삭제·CSRF 표면이 없다. 개인정보 최소 노출: email은 마스킹본을 기본 표시하고 원문은 토글로만 본다(§3).
 */
@Controller
public class AdminUserController {

    /** 목록 기본 페이지 크기. */
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping("/admin/users")
    public String users(@RequestParam(value = "q", required = false) String q,
                        @RequestParam(value = "page", defaultValue = "0") int page,
                        @RequestParam(value = "size", defaultValue = "20") int size,
                        Model model) {
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : size;
        // 가입일 내림차순(최근 가입 먼저).
        PageRequest pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AdminUserRow> userPage = adminUserService.listUsers(q, pageable);
        model.addAttribute("userPage", userPage);
        model.addAttribute("q", q == null ? "" : q);
        return "admin-users";
    }

    @GetMapping("/admin/users/{loginId}")
    public String userDetail(@PathVariable("loginId") String loginId, Model model) {
        AdminUserDetail detail = adminUserService.userDetail(loginId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다"));
        model.addAttribute("detail", detail);
        return "admin-user-detail";
    }

    @GetMapping("/admin/users/{loginId}/debt")
    public String userDebt(@PathVariable("loginId") String loginId,
                           @RequestParam(value = "asOf", required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
                           Model model) {
        AdminDebtView debt = adminUserService.debtTrace(loginId, asOf)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다"));
        model.addAttribute("debt", debt);
        return "admin-user-debt";
    }
}
