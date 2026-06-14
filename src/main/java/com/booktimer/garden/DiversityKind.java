package com.booktimer.garden;

/**
 * 다양성 수집축 식물의 종류 — 무엇의 <b>다양성</b>을 세느냐.
 *
 * <p>작가·출판사는 "완독한 서로 다른 N개"라는 <b>같은 카운트 메커닉</b>이라 한 카탈로그
 * ({@link DiversityPlant})에 담고 이 종류로만 가른다(설계 §2.2 — 통합 테이블). 판정 시
 * 작가 식물은 distinct 작가 수로, 출판사 식물은 distinct 출판사 수로 본다(교차 없음).
 */
public enum DiversityKind {
    AUTHOR,
    PUBLISHER
}
