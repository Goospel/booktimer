package com.booktimer.writing;

import java.time.LocalDate;
import java.util.List;

/**
 * 재료 하나 = 그 책의 여백 한 개(v1 §4.1 — 개정 2부터 여백만).
 *
 * @param date   작성일(사용자 타임존)
 * @param quote  옮긴 문장 — 없으면 {@code null}
 * @param text   생각(메모). 비었거나 공백·문장부호뿐이면 재료 블록에 메모 줄을 싣지 않는다(REQ-97 — DB 행은 그대로)
 * @param linked 이 쪽지에 건 블로그 문장 글(label 순) — 지금 쪽지에만 싣는다(REQ-208 · 처리방침 6-3 :305)
 */
public record MaterialItem(LocalDate date, String quote, String text, List<String> linked) {

    /** 건 문장 없는 여백 — 다른 쪽지 · 시험 도우미. */
    public MaterialItem(LocalDate date, String quote, String text) {
        this(date, quote, text, List.of());
    }
}
