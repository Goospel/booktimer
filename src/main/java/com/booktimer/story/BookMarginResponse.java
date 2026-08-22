package com.booktimer.story;

import java.util.List;

/**
 * 「이 책의 여백」 — 책 한 권(isbn13)에 함께 걸린 글 전부 (2026-08-22 책축 개방).
 *
 * <p>사람축 {@link MarginResponse}와 달리 <b>사람 좌표가 없다</b>: 진입이 책 검색·내 서재라
 * 주인이라는 개념 자체가 없고, 여러 사람의 글이 한 목록에 섞인다.
 *
 * <p><b>자기완결</b>이라 화면이 다른 요청 없이 그려진다(사람축과 같은 관례).
 *
 * @param book       헤더 라벨. viewer 본인 책 행이 있으면 그것, 없으면 첫 공유 글의 책 행
 * @param myBookId   viewer가 이 책을 서재에 가졌으면 그 책 id — 「내 여백」 탭·글쓰기의 존재 조건이자,
 *                   {@code null}이면 화면이 「내 서재에 담기」 안내로 갈린다
 * @param totalCount 「함께 걸린 글 N」 — <b>상한과 무관한 진짜 값</b>. entries는 100장에서 잘린다
 * @param entries    최신순 글 목록(상한 {@code StoryService.MAX_MARGIN_ENTRIES})
 */
public record BookMarginResponse(BookMarginLabel book, Long myBookId, long totalCount,
                                 List<SharedMarginEntry> entries) {
}
