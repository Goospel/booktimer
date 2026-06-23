package com.booktimer.book;

/**
 * 여러 사용자의 PUBLIC 책 수를 한 번의 group by로 받는 집계 프로젝션(사용자 행 목록 조립의 N+1 회피).
 *
 * <p>하우스 스타일은 {@link FollowScopeCount}와 동일 — 인터페이스 프로젝션 + JPQL의 {@code as} 별칭으로
 * 매핑한다. <b>공개 책이 0권인 사용자는 결과에 행이 없으니</b> 호출부에서 0으로 디폴트한다.
 */
public interface UserPublicBookCount {

    Long getUserId();

    /** PUBLIC 가시성 책 수. */
    long getPublicCount();
}
