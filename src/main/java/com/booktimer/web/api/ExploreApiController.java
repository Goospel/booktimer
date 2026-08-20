package com.booktimer.web.api;

import com.booktimer.search.ExploreBook;
import com.booktimer.search.ExploreUser;
import com.booktimer.search.UserSearchService;
import com.booktimer.security.CurrentUserService;
import com.booktimer.security.RateLimitAction;
import com.booktimer.security.RateLimitService;
import com.booktimer.user.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

/**
 * 미니앱 「둘러보기」 JSON API — 아이디를 모르는 사람에게 <b>볼 것을 먼저 준다</b>.
 *
 * <p><b>{@code /api/search}와 따로 낸 이유</b>: 그 응답은 웹 프론트({@code UserSearchPanel.vue})가 함께
 * 쓰고 있어, 셔플·책 4권·공개책 0권 제외 같은 미니앱 전용 규칙을 얹으면 웹 동작까지 바뀐다. 검색(입력이
 * 있을 때)은 지금처럼 {@code /api/search}가 그대로 맡고, 이 경로는 <b>입력이 없을 때 보여줄 것</b>만 맡는다.
 *
 * <p>레이트리밋은 추천과 같은 {@link RateLimitAction#RECOMMEND}를 쓴다 — 같은 계산을 하는 같은 성격의
 * 요청이라 한도를 따로 두면 우회로가 생긴다. 한도에 닿으면 빈 목록 + {@code rateLimited=true}로
 * 응답한다(클라이언트가 「잠시 후 다시」를 말할 수 있게 — 조용한 빈 화면과 구분된다).
 */
@RestController
public class ExploreApiController {

    /** 한 번에 내려보내는 카드 수. 서비스는 이 수의 몇 배로 후보를 모아 그중 무작위로 고른다. */
    private static final int EXPLORE_COUNT = 10;

    private final CurrentUserService currentUserService;
    private final UserSearchService searchService;
    private final RateLimitService rateLimitService;

    public ExploreApiController(CurrentUserService currentUserService,
                                UserSearchService searchService,
                                RateLimitService rateLimitService) {
        this.currentUserService = currentUserService;
        this.searchService = searchService;
        this.rateLimitService = rateLimitService;
    }

    @GetMapping("/api/explore")
    public ExploreResponse explore(Principal principal) {
        User me = currentUserService.resolve(principal);
        if (!rateLimitService.allow(RateLimitAction.RECOMMEND, me.getId())) {
            return new ExploreResponse(List.of(), true);
        }
        return new ExploreResponse(toDto(searchService.explore(me, EXPLORE_COUNT)), false);
    }

    private List<ExploreUserDto> toDto(List<ExploreUser> users) {
        return users.stream()
                .map(u -> new ExploreUserDto(
                        u.row().loginId(), u.row().nickname(), u.row().publicBookCount(),
                        u.row().following(), u.row().self(),
                        u.books().stream().map(ExploreApiController::toDto).toList()))
                .toList();
    }

    private static ExploreBookDto toDto(ExploreBook book) {
        return new ExploreBookDto(book.title(), book.coverUrl());
    }

    public record ExploreResponse(List<ExploreUserDto> users, boolean rateLimited) {
    }

    /**
     * 카드 한 장. {@code SearchApiController.RecommendedUserDto}와 달리 <b>이유 칩이 없다</b> —
     * 「같이 읽은 책」은 겹친 책을 앞에 세우는 것으로 말하고, 「공통 친구」·「나를 팔로우함」은
     * 책방(프로필) 응답이 맡는다(사용자 결정 2026-08-20).
     */
    public record ExploreUserDto(String loginId, String nickname, long publicBookCount,
                                 boolean following, boolean self, List<ExploreBookDto> books) {
    }

    /** ⚠️ 누적 초·마지막 독서 시각을 <b>일부러 담지 않는다</b>(시점 비노출). 필드를 늘리기 전에 그 결정을 먼저 보라. */
    public record ExploreBookDto(String title, String coverUrl) {
    }
}
