package com.booktimer.web.api;

import com.booktimer.search.RecommendedUser;
import com.booktimer.search.UserSearchResult;
import com.booktimer.search.UserSearchService;
import com.booktimer.security.CurrentUserService;
import com.booktimer.security.RateLimitAction;
import com.booktimer.security.RateLimitService;
import com.booktimer.user.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

/**
 * search 페이지 Vue 섬용 JSON API (선별 SPA 단계 1a).
 *
 * <p>기존 {@link com.booktimer.web.SearchController}가 SSR로 하던 검색 로직을 JSON으로 반환한다.
 * 서비스 재사용 — 새 도메인 로직 없음. SecurityConfig default-deny로 자동 인증 보호.
 */
@RestController
@RequestMapping("/api/search")
public class SearchApiController {

    private static final int RECOMMEND_COUNT = 10;

    private final CurrentUserService currentUserService;
    private final UserSearchService searchService;
    private final RateLimitService rateLimitService;

    public SearchApiController(CurrentUserService currentUserService,
                               UserSearchService searchService,
                               RateLimitService rateLimitService) {
        this.currentUserService = currentUserService;
        this.searchService = searchService;
        this.rateLimitService = rateLimitService;
    }

    @GetMapping
    public SearchResponse search(@RequestParam(value = "q", required = false) String q,
                                 Principal principal) {
        User me = currentUserService.resolve(principal);
        String myLoginId = me.getLoginId();
        boolean isSearch = q != null && !q.isBlank();

        // 추천은 q와 무관 → 검색(q 있음)일 땐 재계산하지 않고 빈 배열. 진입(q 없음)에서만, RECOMMEND 한도 내에서 계산.
        List<RecommendedUserDto> recommendations =
                (!isSearch && rateLimitService.allow(RateLimitAction.RECOMMEND, me.getId()))
                        ? toRecommendDto(searchService.recommend(me, RECOMMEND_COUNT))
                        : List.of();

        if (isSearch && !rateLimitService.allow(RateLimitAction.SEARCH, me.getId())) {
            return new SearchResponse(q, List.of(), recommendations, myLoginId, true);
        }
        return new SearchResponse(q, toDto(searchService.search(me, q)), recommendations, myLoginId, false);
    }

    private List<UserSearchResultDto> toDto(List<UserSearchResult> list) {
        return list.stream()
                .map(r -> new UserSearchResultDto(r.loginId(), r.nickname(), r.publicBookCount(), r.following(), r.self()))
                .toList();
    }

    private List<RecommendedUserDto> toRecommendDto(List<RecommendedUser> list) {
        return list.stream()
                .map(r -> new RecommendedUserDto(r.row().loginId(), r.row().nickname(), r.row().publicBookCount(),
                        r.row().following(), r.row().self(), r.reasons()))
                .toList();
    }

    public record SearchResponse(
            String q,
            List<UserSearchResultDto> results,
            List<RecommendedUserDto> recommendations,
            String myLoginId,
            boolean rateLimited) {
    }

    public record UserSearchResultDto(
            String loginId,
            String nickname,
            long publicBookCount,
            boolean following,
            boolean self) {
    }

    /** 친구 추천 한 줄 — 검색 결과 행({@link UserSearchResultDto})에 "추천 이유" 칩을 더한다(검색 행은 무변경). */
    public record RecommendedUserDto(
            String loginId,
            String nickname,
            long publicBookCount,
            boolean following,
            boolean self,
            List<String> reasons) {
    }
}
