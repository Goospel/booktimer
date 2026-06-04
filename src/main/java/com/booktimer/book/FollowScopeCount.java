package com.booktimer.book;

/**
 * 팔로우 스코프 인기 카운트 집계 프로젝션 (sns-design §7.4, 요구사항 3).
 *
 * <p>한 isbn13에 대해 <b>내가 팔로우한 사용자</b> 중 그 책을 PUBLIC으로 가진 사람을 status 버킷별
 * distinct user count로 센 결과. 검색 페이지의 여러 isbn을 한 번의 group by로 받아 N+1을 피한다.
 */
public interface FollowScopeCount {

    String getIsbn();

    /** 원함 = WANT_TO_READ 인 distinct 사용자 수. */
    long getWantCount();

    /** 읽음 = READING ∪ FINISHED 인 distinct 사용자 수. */
    long getReadCount();
}
