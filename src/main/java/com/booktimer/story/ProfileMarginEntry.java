package com.booktimer.story;

import java.time.Instant;

/**
 * 사람축 <b>전체</b> 목록의 글 카드 — {@link MarginEntry}에 <b>책 라벨</b>을 더한 모양
 * ({@link SharedMarginEntry}가 작성자 줄을 더한 것과 같은 방향).
 *
 * <p>책방 「여백」 탭이 이 목록을 그린다: 한 사람의 글이 책 구분 없이 한 줄로 쌓이므로, 카드마다
 * 「어느 책의 여백인가」가 정보가 된다(한 책의 목록에선 헤더가 한 번만 말하면 되던 값이다).
 *
 * <p>{@code bookId}는 라벨을 눌렀을 때 그 책의 여백 전체 화면으로 가는 <b>좌표</b>다.
 * 표지는 싣지 않는다 — 카드 행 머리에 그림이 들어갈 자리가 없다(작성자 줄과 같은 한 줄 글자다).
 *
 * <p>§3.4 화이트리스트: 노출 필드는 여기 정의된 것뿐.
 */
public record ProfileMarginEntry(Long id, String text, String quote, String bgCode, Instant createdAt,
                                 long likeCount, boolean liked, boolean shared,
                                 Long bookId, String bookTitle) {

    public static ProfileMarginEntry of(Story story, long likeCount, boolean liked) {
        return new ProfileMarginEntry(story.getId(), story.getText(), story.getQuote(), story.getBgCode(),
                story.getCreatedAt(), likeCount, liked, story.isShared(),
                story.getBook().getId(), story.getBook().getTitle());
    }
}
