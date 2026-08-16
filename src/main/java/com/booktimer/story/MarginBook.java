package com.booktimer.story;

import com.booktimer.book.Book;

/** 여백 헤더에 그릴 책 라벨 — PUBLIC 재검사를 통과한 책만 여기 담긴다(§13.2). */
public record MarginBook(Long id, String title, String author, String coverUrl) {

    public static MarginBook of(Book book) {
        return new MarginBook(book.getId(), book.getTitle(), book.getAuthor(), book.getCoverUrl());
    }
}
