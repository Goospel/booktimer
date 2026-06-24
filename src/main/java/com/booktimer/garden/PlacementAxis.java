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
    /** @deprecated 마을 컨셉 전환으로 제거됨. DB 직렬화 호환 보존. */
    @Deprecated
    TIME,
    /** @deprecated 마을 컨셉 전환으로 제거됨. DB 직렬화 호환 보존. */
    @Deprecated
    GENRE,
    /** @deprecated 마을 컨셉 전환으로 제거됨. DB 직렬화 호환 보존. */
    @Deprecated
    DIVERSITY,
    /** @deprecated 마을 컨셉 전환으로 제거됨. DB 직렬화 호환 보존. */
    @Deprecated
    RECIPE,
    /**
     * 작가 캐릭터축({@link AuthorCharacter}) — 특정 작가 완독으로 해금.
     * 배회 전용(배치 좌표 저장 없음) — ownedCharacters()로 노출.
     */
    AUTHOR,
    /**
     * 출판사 건물축({@link Building}) — 특정 출판사 N권 완독으로 해금.
     * 마을 유일 배치 축.
     */
    BUILDING
}
