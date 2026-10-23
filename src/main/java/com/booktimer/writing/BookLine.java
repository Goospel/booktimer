package com.booktimer.writing;

import com.booktimer.book.BookStatus;

/**
 * 재료 화면 머리의 책 한 줄(v1 §5.1.3 — 필드 이름이 앱 types.ts의 단일 출처).
 *
 * <p>{@code readSeconds}·{@code sessions}는 <b>화면용</b>이다 — AI 재료 블록({@link WritingMaterial#text})에는 싣지 않는다
 * (v1 §4.1 · 처리방침 6-3절 「그 밖의 이용 기록은 보내지 않습니다」).
 */
public record BookLine(Long id, String title, String author, String coverUrl, BookStatus status,
                       long readSeconds, int sessions, boolean isPublic) {
}
