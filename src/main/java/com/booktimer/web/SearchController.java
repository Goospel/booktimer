package com.booktimer.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * search 페이지 얇은 셸 컨트롤러 (선별 SPA 단계 1a).
 *
 * <p>검색 로직·추천·레이트리밋은 {@link com.booktimer.web.api.SearchApiController}(JSON API)로 이관.
 * 이 컨트롤러는 Vue 섬이 마운트할 셸 HTML만 내려준다 — model에는 초기 q만 담는다(내 책방 링크는 양옆 바 RailModelAdvice).
 */
@Controller
public class SearchController {

    @GetMapping("/search")
    public String search(@RequestParam(value = "q", required = false) String q, Model model) {
        model.addAttribute("q", q);
        return "search";
    }
}
