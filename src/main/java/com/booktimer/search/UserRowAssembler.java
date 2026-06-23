package com.booktimer.search;

import com.booktimer.book.BookRepository;
import com.booktimer.book.UserPublicBookCount;
import com.booktimer.follow.FollowService;
import com.booktimer.user.User;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 사용자 요약 행({@link UserSearchResult}) 조립 — 닉네임 검색·팔로워/팔로잉 목록·차단 목록이 공유한다.
 *
 * <p>대상 사용자 목록(targets)을 보는 주체(viewer) 기준으로 공개 책수·팔로우 여부·본인 여부를 채운다.
 * 공개 책수·팔로우 여부를 각각 <b>1쿼리로 배치 조회</b>해 행당 N+1을 없앤다(관계 행의 lazy User 제거는
 * 호출부 리포지토리의 {@code @EntityGraph}가 담당).
 */
@Component
public class UserRowAssembler {

    private final BookRepository bookRepository;
    private final FollowService followService;

    public UserRowAssembler(BookRepository bookRepository, FollowService followService) {
        this.bookRepository = bookRepository;
        this.followService = followService;
    }

    /**
     * 사용자 행 목록을 배치로 조립 — 공개책수·팔로우 여부를 각각 1쿼리로(행당 N+1 제거).
     *
     * @param viewer  목록을 보는 주체(self·following 판정 기준)
     * @param targets 행으로 펼칠 대상 사용자들(필터·정렬은 호출부가 이미 끝낸 상태)
     */
    public List<UserSearchResult> toRows(User viewer, List<User> targets) {
        if (targets.isEmpty()) {
            return List.of();
        }
        List<Long> ids = targets.stream().map(User::getId).toList();
        Map<Long, Long> publicCounts = bookRepository.countPublicByUsers(ids).stream()
                .collect(Collectors.toMap(UserPublicBookCount::getUserId, UserPublicBookCount::getPublicCount));
        Set<Long> followed = followService.followingAmong(viewer, ids);
        return targets.stream().map(t -> {
            boolean self = t.getId() != null && t.getId().equals(viewer.getId());
            long publicBooks = publicCounts.getOrDefault(t.getId(), 0L); // 공개책 0권 = 결과 누락 → 0 디폴트(중요)
            boolean following = !self && followed.contains(t.getId());
            return new UserSearchResult(t.getLoginId(), t.getNickname(), publicBooks, following, self);
        }).toList();
    }
}
