package com.booktimer.follow;

import com.booktimer.search.UserRowAssembler;
import com.booktimer.search.UserSearchResult;
import com.booktimer.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 본인 팔로워/팔로잉 목록 유스케이스.
 *
 * <p>남의 목록은 노출하지 않는다(요구사항·privacy) — 호출부({@code /me/**})가 항상 본인을 넘긴다.
 * 검색과 같은 사용자 행({@link UserSearchResult}, {@link UserRowAssembler})으로 매핑해 화면을 일관시킨다.
 */
@Service
@Transactional(readOnly = true)
public class FollowListService {

    private final FollowRepository followRepository;
    private final UserRowAssembler rowAssembler;

    public FollowListService(FollowRepository followRepository, UserRowAssembler rowAssembler) {
        this.followRepository = followRepository;
        this.rowAssembler = rowAssembler;
    }

    /** 나(viewer)를 팔로우한 사람들 — 각 행의 following은 "내가 그를 (맞)팔로우 중인지". */
    public List<UserSearchResult> followers(User viewer) {
        return followRepository.findByFolloweeOrderByCreatedAtDesc(viewer).stream()
                .map(Follow::getFollower)
                .filter(u -> u.getLoginId() != null) // N-055: 온보딩 전(login_id=null) 사용자 제외
                .map(u -> rowAssembler.toRow(viewer, u))
                .toList();
    }

    /** 내(viewer)가 팔로우한 사람들 — following은 모두 true(언팔로 버튼으로 관리). */
    public List<UserSearchResult> following(User viewer) {
        return followRepository.findByFollowerOrderByCreatedAtDesc(viewer).stream()
                .map(Follow::getFollowee)
                .filter(u -> u.getLoginId() != null) // N-055: 온보딩 전(login_id=null) 사용자 제외
                .map(u -> rowAssembler.toRow(viewer, u))
                .toList();
    }
}
