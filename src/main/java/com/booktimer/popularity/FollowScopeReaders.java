package com.booktimer.popularity;

import com.booktimer.search.UserSearchResult;

import java.util.List;

/**
 * 팔로우 스코프 인기 카운트 drill-down 결과 — "이 책을 원함/읽음인 내 팔로우 명단".
 *
 * <p>카운트 배지("👥 팔로우 중 N명 원함 · M명 읽음")의 두 버킷을 그대로 신원 명단으로 펼친 것이다.
 * 행은 검색·팔로우 목록과 같은 {@link UserSearchResult}(닉네임·공개책수·팔로우버튼)로 화면을 일관시킨다.
 *
 * @param wanting 원함(WANT_TO_READ) 버킷의 팔로우 사용자들
 * @param reading 읽음(READING∪FINISHED) 버킷의 팔로우 사용자들
 */
public record FollowScopeReaders(List<UserSearchResult> wanting, List<UserSearchResult> reading) {

    /** 양쪽 다 비면 화면에서 "없음"으로 안내한다. */
    public boolean isEmpty() {
        return wanting.isEmpty() && reading.isEmpty();
    }
}
