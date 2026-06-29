package com.booktimer.book;

/**
 * "같은 책" 추천 후보 집계 프로젝션 — 친구 추천 하이브리드 1단계.
 *
 * <p>한 후보(userId)에 대해 <b>나와 겹치는 책 수</b>(내가 읽은/완독한 책 isbn13 ∩ 그 후보가 PUBLIC으로
 * 읽은/완독한 책)를 센 결과. 추천 정렬(겹친 권수 많은 순)과 "같이 읽은 책 N권" 이유 칩의 N에 쓰인다.
 * 하우스 스타일은 {@link FollowScopeCount}·{@link UserPublicBookCount}와 동일.
 */
public interface CoReadCount {

    Long getUserId();

    /** 나와 이 후보가 함께 읽은(겹치는) 책 수. */
    long getSharedBookCount();
}
