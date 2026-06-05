package com.booktimer.web;

import com.booktimer.search.UserSearchService;
import com.booktimer.security.RateLimitAction;
import com.booktimer.security.CurrentUserService;
import com.booktimer.security.RateLimitService;
import com.booktimer.user.User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.util.List;

/**
 * 닉네임 검색 화면 (SNS 3단계, sns-design §7.3, 요구사항 6).
 *
 * <p>{@code GET /search?q=} — 닉네임 부분일치로 사용자를 찾아 결과(닉네임·공개책수·팔로우버튼)를 SSR로 그린다.
 * 검색 로직·가드(최소 2글자·상한 20)는 {@link UserSearchService}가 담당한다. 로그인 사용자만(default-deny).
 *
 * <p>크롤링·열거 완화로 사용자별 레이트리밋({@link RateLimitAction#SEARCH})을 건다 — 한도 초과 시 검색을
 * 돌리지 않고 안내만 그린다(§7.5·§9).
 */
@Controller
public class SearchController {

    /** 친구 추천에 노출하는 최대 인원 — 현재는 단순 무작위 N명(요구사항: 우선 단순하게). */
    private static final int RECOMMEND_COUNT = 10;

    private final CurrentUserService currentUserService;
    private final UserSearchService searchService;
    private final RateLimitService rateLimitService;

    public SearchController(CurrentUserService currentUserService, UserSearchService searchService,
                            RateLimitService rateLimitService) {
        this.currentUserService = currentUserService;
        this.searchService = searchService;
        this.rateLimitService = rateLimitService;
    }

    @GetMapping("/search")
    public String search(@RequestParam(value = "q", required = false) String q,
                         Principal principal, Model model) {
        User me = currentUser(principal);
        model.addAttribute("q", q);
        // 친구 추천·내 공개 책장 링크는 검색 레이트리밋과 무관하게 페이지 기본 구성으로 항상 싣는다.
        model.addAttribute("recommendations", searchService.recommend(me, RECOMMEND_COUNT));
        model.addAttribute("myLoginId", me.getLoginId());
        if (!rateLimitService.allow(RateLimitAction.SEARCH, me.getId())) {
            model.addAttribute("results", List.of());
            model.addAttribute("rateLimited", true);
            return "search";
        }
        model.addAttribute("results", searchService.search(me, q));
        model.addAttribute("rateLimited", false);
        return "search";
    }

    private User currentUser(Principal principal) {
        return currentUserService.resolve(principal);
    }
}
