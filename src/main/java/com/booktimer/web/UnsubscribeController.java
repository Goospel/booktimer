package com.booktimer.web;

import com.booktimer.retention.UnsubscribeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 마케팅 메일 구독해지 화면/처리(이메일 인프라 2단계 PR-3). 공개 경로 — 토큰이 신원 증명이라 로그인 불필요.
 *
 * <p>{@code GET /unsubscribe?token=}은 <b>토큰을 소비하지 않고</b> 확인 페이지(버튼 폼)만 렌더한다. 일회용 토큰
 * 소비를 POST로 미룬 이유는 가입 인증과 동일하다 — 메일 클라이언트 링크 프리페치(Outlook SafeLinks·백신)가 GET을
 * 자동으로 날려, GET에서 소비하면 사용자가 클릭하기도 전에 해지·토큰 소진이 일어난다. 사람이 버튼을 눌러야 하는
 * POST에서만 소비·해지한다.
 */
@Controller
public class UnsubscribeController {

    private final UnsubscribeService unsubscribeService;

    public UnsubscribeController(UnsubscribeService unsubscribeService) {
        this.unsubscribeService = unsubscribeService;
    }

    /** 수신거부 링크 진입(공개) — 토큰을 소비하지 않고 확인 버튼 폼만 보여준다(프리페치 안전). */
    @GetMapping("/unsubscribe")
    public String unsubscribeForm(@RequestParam(name = "token", required = false) String token, Model model) {
        model.addAttribute("token", token == null ? "" : token);
        return "unsubscribe-confirm";
    }

    /** 수신거부 확정(공개·CSRF 보호) — 버튼 클릭 시에만 토큰을 소비해 동의를 끄고 결과 페이지를 렌더한다. */
    @PostMapping("/unsubscribe")
    public String confirm(@RequestParam(name = "token", required = false) String token, Model model) {
        boolean unsubscribed = unsubscribeService.unsubscribe(token);
        model.addAttribute("unsubscribed", unsubscribed);
        return "unsubscribe-done";
    }
}
