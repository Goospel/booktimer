package com.booktimer.story;

import com.booktimer.book.Book;

import java.time.Instant;

/** 스토리 카드 한 장 — 책 라벨(제목·표지)은 표시 시점 재검사를 통과한 공개 책만(sns-design §13.2). */
public record StoryCard(Long id, String text, String bgCode, String bookTitle, String bookCoverUrl,
                        Instant createdAt, boolean viewed) {

    /** 책 라벨은 표시 시점 재검사 — 첨부 후 비공개 전환이면 라벨만 숨기고 문장은 유지(§13.2). */
    public static StoryCard of(Story story, boolean viewed) {
        Book book = story.getBook();
        boolean labeled = book != null && book.isPublic();
        return new StoryCard(story.getId(), story.getText(), story.getBgCode(),
                labeled ? book.getTitle() : null, labeled ? book.getCoverUrl() : null,
                story.getCreatedAt(), viewed);
    }
}
