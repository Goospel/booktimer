package com.booktimer.book;

/**
 * 뉴스 수집 대상 프로젝션 — 한 ISBN에 대한 검색 입력(제목·저자).
 *
 * <p>같은 책을 여러 사람이 완독해도 <b>isbn당 한 행</b>으로 모은다(외부 호출 1회 = 쿼터·중복 절약).
 * 제목·저자는 표기가 사용자마다 미세하게 다를 수 있어 그룹의 대표값 하나를 쓴다.
 * 하우스 스타일은 {@link CoReadCount}·{@link FollowScopeCount}와 동일.
 */
public interface BookNewsTarget {

    String getIsbn13();

    String getTitle();

    String getAuthor();
}
