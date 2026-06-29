package com.booktimer.follow;

/**
 * 친구의 친구(FoF) 추천 후보 집계 프로젝션 — 친구 추천 하이브리드 1단계.
 *
 * <p>한 후보(userId)에 대해 <b>나와 공통으로 팔로우하는 사람 수</b>(= 내 팔로이 중 그 후보를 팔로우하는
 * 사람 수)를 센 결과. 추천 정렬(공통 친구 많은 순)과 "공통 친구 N명" 이유 칩의 N에 쓰인다.
 * 하우스 스타일은 {@link com.booktimer.book.FollowScopeCount}와 동일 — 인터페이스 프로젝션 + JPQL {@code as} 별칭.
 */
public interface FriendOfFriendCount {

    Long getUserId();

    /** 나와 이 후보를 잇는 공통 친구(내 팔로이이면서 이 후보를 팔로우하는 사람) 수. */
    long getCommonFollowCount();
}
