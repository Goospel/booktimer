package com.booktimer.web;

import com.booktimer.chat.ChatException;
import com.booktimer.chat.ChatSafetyService;
import com.booktimer.chat.ChatSanctionService;
import com.booktimer.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

/**
 * 관리자 대화 대본·조치(정책 문서 §2 확인·조치, §3 제재, §6 수사 협조).
 *
 * <p>접근 제어는 {@code /admin/**} → {@code hasRole("ADMIN")}(SecurityConfig). 대본은 <b>신고를 통해서만</b>
 * 연다 — 경로가 {@code /admin/reports/{id}/chat}이고, 방을 가리키지 않는 신고는 404다.
 */
@Controller
public class AdminChatController {

    private final ChatSafetyService safety;
    private final ChatSanctionService sanctions;
    private final UserRepository userRepository;

    public AdminChatController(ChatSafetyService safety, ChatSanctionService sanctions, UserRepository userRepository) {
        this.safety = safety;
        this.sanctions = sanctions;
        this.userRepository = userRepository;
    }

    @GetMapping("/admin/reports/{id}/chat")
    public String transcript(@PathVariable long id, HttpServletRequest request, Model model) {
        CsrfTokenUtil.precommit(request); // 대본이 길면 폼 렌더 전에 응답이 커밋된다(T-033·T-049)
        model.addAttribute("t", safety.transcript(id).orElseThrow(AdminChatController::notFound));
        model.addAttribute("actions", ChatSanctionService.Action.values());
        return "admin-chat";
    }

    /** 수사 협조용 텍스트 내보내기 — 파일로 받는다. */
    @GetMapping("/admin/reports/{id}/chat.txt")
    public ResponseEntity<String> export(@PathVariable long id) {
        String text = safety.exportText(id).orElseThrow(AdminChatController::notFound);
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("booktimer-chat-report-" + id + ".txt").build().toString())
                .body(text);
    }

    /** 처리 완료 + 조치(무혐의·경고·7일·영구). 방 없는 프로필 신고도 같은 문으로 닫는다. */
    @PostMapping("/admin/reports/{id}/resolve")
    public String resolve(@PathVariable long id, @RequestParam ChatSanctionService.Action action) {
        safety.resolve(id, action);
        return "redirect:/admin/reports";
    }

    @PostMapping("/admin/reports/{id}/legal-hold")
    public String legalHold(@PathVariable long id, @RequestParam boolean hold) {
        safety.setLegalHold(id, hold);
        return "redirect:/admin/reports/" + id + "/chat";
    }

    /** 신고와 무관한 제재 조정 — 해제(이의 인용)·연장 등. */
    @PostMapping("/admin/users/{loginId}/chat-sanction")
    public String sanction(@PathVariable String loginId, @RequestParam ChatSanctionService.Action action) {
        sanctions.apply(userRepository.findByLoginId(loginId).orElseThrow(AdminChatController::notFound), action);
        return "redirect:/admin/reports";
    }

    @ExceptionHandler(ChatException.class)
    public ResponseEntity<String> handle(ChatException e) {
        return ResponseEntity.status(e.getStatus()).body(e.getMessage());
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
