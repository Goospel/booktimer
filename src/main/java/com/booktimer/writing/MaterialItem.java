package com.booktimer.writing;

import java.time.LocalDate;

/**
 * 재료 하나 = 그 책의 여백 한 개(v1 §4.1 — 개정 2부터 여백만).
 *
 * @param date  작성일(사용자 타임존)
 * @param quote 옮긴 문장 — 없으면 {@code null}
 * @param text  생각(메모). 비었거나 공백·문장부호뿐이면 재료 블록에 메모 줄을 싣지 않는다(REQ-97 — DB 행은 그대로)
 */
public record MaterialItem(LocalDate date, String quote, String text) {
}
