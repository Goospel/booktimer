package com.booktimer.garden;

/**
 * 배회용 보유 캐릭터 단일 표현 — {@link OwnedPlant}와 달리 axis·배치 의미가 없다.
 * {@link GardenView#ownedCharacters()}가 이 형태로 내준다.
 *
 * @param code     캐릭터 code (카탈로그 유니크)
 * @param emoji    비주얼 이모지(SVG 미적용 종 폴백)
 * @param name     표시 이름(한글)
 * @param spriteId SVG 스프라이트 식별자. null이면 이모지 폴백
 */
public record OwnedCharacter(String code, String emoji, String name, String spriteId) {
}
