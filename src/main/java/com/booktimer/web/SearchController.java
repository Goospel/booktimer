package com.booktimer.web;

import com.booktimer.search.UserSearchService;
import com.booktimer.security.RateLimitAction;
import com.booktimer.security.RateLimitService;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
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

    private final UserRepository userRepository;
    private final UserSearchService searchService;
    private final RateLimitService rateLimitService;

    public SearchController(UserRepository userRepository, UserSearchService searchService,
                            RateLimitService rateLimitService) {
        this.userRepository = userRepository;
        this.searchService = searchService;
        this.rateLimitService = rateLimitService;
    }

    @GetMapping("/search")
    public String search(@RequestParam(value = "q", required = false) String q,
                         Principal principal, Model model) {
        User me = currentUser(principal);
        model.addAttribute("q", q);
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
        return userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new IllegalStateException("authenticated user not found: " + principal.getName()));
    }
}
