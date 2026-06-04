package com.booktimer.popularity;

/**
 * 한 책(isbn13)에 대한 팔로우 스코프 인기 카운트 (sns-design §7.4).
 *
 * @param wantCount 내 팔로우 중 이 책을 "원함"(WANT_TO_READ)으로 가진 distinct 사용자 수
 * @param readCount 내 팔로우 중 이 책을 "읽음"(READING∪FINISHED)으로 가진 distinct 사용자 수
 */
public record FollowScopePopularity(long wantCount, long readCount) {

    /** 표시할 게 하나라도 있는가(둘 다 0이면 화면에서 숨김). */
    public boolean hasAny() {
        return wantCount > 0 || readCount > 0;
    }
}
