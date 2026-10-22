package com.booktimer.writing;

/**
 * AI 질문 하나.
 *
 * @param anchor 재료·사용자 글에서 그대로 따온 조각(없으면 {@code null})
 */
public record Question(String text, String anchor) {
}
