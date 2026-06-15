package com.booktimer.garden;

/**
 * 작가 캐릭터 도감 한 칸의 보유 상태 — 카탈로그 메타 + 보유 여부.
 *
 * @param character 카탈로그 캐릭터(matchName·이름·이모지 등 불변 메타)
 * @param owned     보유 여부 — {@code matchName}을 정규화한 결과가 완독책 작가 정규화 결과에 contains되면 true
 */
public record AuthorCharacterState(AuthorCharacter character, boolean owned) {
}
