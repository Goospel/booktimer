package com.booktimer.search;

import com.booktimer.book.BookRepository;
import com.booktimer.book.BookVisibility;
import com.booktimer.follow.FollowService;
import com.booktimer.user.User;
import org.springframework.stereotype.Component;

/**
 * 사용자 요약 행({@link UserSearchResult}) 조립 — 닉네임 검색·팔로워/팔로잉 목록이 공유한다.
 *
 * <p>한 대상 사용자(target)를 보는 주체(viewer) 기준으로 공개 책수·팔로우 여부·본인 여부를 채운다.
 * 행 1건당 카운트·팔로우 조회가 따르므로 큰 목록에선 N개의 쿼리가 된다(현 규모에선 충분, 필요 시 일괄화).
 */
@Component
public class UserRowAssembler {

    private final BookRepository bookRepository;
    private final FollowService followService;

    public UserRowAssembler(BookRepository bookRepository, FollowService followService) {
        this.bookRepository = bookRepository;
        this.followService = followService;
    }

    public UserSearchResult toRow(User viewer, User target) {
        boolean self = target.getId() != null && target.getId().equals(viewer.getId());
        long publicBooks = bookRepository.countByUserAndVisibility(target, BookVisibility.PUBLIC);
        boolean following = !self && followService.isFollowing(viewer, target);
        return new UserSearchResult(target.getNickname(), publicBooks, following, self);
    }
}
