package com.booktimer.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 차단 목록 Vue 셸 (SNS 5단계, sns-design §7.5).
 *
 * <p>GET /me/blocks — Vue 섬 셸. 목록 데이터는 {@link com.booktimer.web.api.BlockApiController}가 제공.
 * POST /block·/unblock은 SPA 전환 후 BlockApiController(/api/block·/api/unblock)가 담당.
 */
@Controller
public class BlockController {

    /** 셸만 — 목록은 API, 내 책방 링크는 양옆 바(RailModelAdvice)가 싣는다. */
    @GetMapping("/me/blocks")
    public String blocks() {
        return "block-list";
    }
}
