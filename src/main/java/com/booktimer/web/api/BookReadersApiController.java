package com.booktimer.web.api;

import com.booktimer.popularity.FollowScopeReaders;
import com.booktimer.popularity.FollowScopeReadersService;
import com.booktimer.search.UserSearchResult;
import com.booktimer.security.CurrentUserService;
import com.booktimer.user.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

/**
 * GET /api/book-readers — "내 팔로우 중 이 책 보는 사람" drill-down JSON API (Vue 섬용).
 *
 * <p>뮤테이션은 기존 {@code /api/follow}·{@code /api/unfollow}를 그대로 재사용(신설 없음).
 * 스코프 게이트(팔로우·PUBLIC·distinct)는 서비스가 담당 — 임의 isbn을 넣어도 내 팔로우의 공개 책만 나와 IDOR 없음.
 */
@RestController
public class BookReadersApiController {

    private final CurrentUserService currentUserService;
    private final FollowScopeReadersService readersService;

    public BookReadersApiController(CurrentUserService currentUserService,
                                    FollowScopeReadersService readersService) {
        this.currentUserService = currentUserService;
        this.readersService = readersService;
    }

    @GetMapping("/api/book-readers")
    public BookReadersResponse readers(@RequestParam(value = "isbn", required = false) String isbn,
                                       Principal principal) {
        User me = currentUserService.resolve(principal);
        FollowScopeReaders readers = readersService.readers(me, isbn);
        return new BookReadersResponse(readers.reading(), readers.wanting());
    }

    public record BookReadersResponse(List<UserSearchResult> reading, List<UserSearchResult> wanting) {}
}
