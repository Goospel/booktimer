package com.booktimer.garden;

/**
 * 정원 배치(꾸미기)에서 한 식물이 어느 수집축 소속인지 구분하는 축 식별자.
 *
 * <p>식물 {@code code}가 축 간 전역 유니크가 아니므로(시간축 {@code uk_plant_code}·장르축
 * {@code uk_genre_plant_code}·다양성축 {@code uk_diversity_plant_code}·레시피축 {@code uk_user_discovered_plant}가
 * 각자 테이블 안에서만 유니크) 배치 저장 키는 <b>(axis, code) 복합</b>이어야 한다(설계 §1·§2.2). 이 enum이
 * 그 축 절반을 맡는다 — 같은 {@code "lotus"} code라도 TIME 'lotus'와 RECIPE 'lotus'는 다른 식물이다.
 */
public enum PlacementAxis {
    /** 시간축({@link Plant}) — 누적 목표 달성일로 해금. */
    TIME,
    /** 장르축({@link GenrePlant}) — 장르 완독으로 해금. */
    GENRE,
    /** 작가·출판사 다양성축({@link DiversityPlant}) — distinct 완독 수로 해금. */
    DIVERSITY,
    /** 레시피축({@link RecipePlant}) — 책 조합 발견으로 보유. */
    RECIPE
}
