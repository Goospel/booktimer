package com.booktimer.quote;

/**
 * 작가 격언 한 줄 — 대시보드 인사말 자리에 랜덤 노출하는 단위.
 *
 * @param text   격언 문장(출처 표기를 위해 작가와 한 쌍으로 다룬다)
 * @param author 작가명
 */
public record Quote(String text, String author) {
}
