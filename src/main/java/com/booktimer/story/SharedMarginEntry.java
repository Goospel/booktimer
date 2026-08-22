package com.booktimer.story;

import java.time.Instant;

/**
 * 책축 여백의 글 카드 — 사람축 {@link MarginEntry}에 <b>작성자 줄</b>을 더한 모양.
 *
 * <p>작성자를 싣는 이유: 사람축은 진입 자체가 「누구의 여백」이라 카드마다 이름을 반복할 이유가 없지만,
 * 책축은 여러 사람의 글이 한 목록에 섞이므로 누가 썼는지가 카드의 정보다. 핸들({@code authorLoginId})은
 * 탭했을 때 그의 책방으로 가는 좌표이기도 하다.
 *
 * <p>{@code shared}는 담지 않는다 — 이 목록에 실렸다는 것 자체가 켜져 있다는 뜻이다.
 *
 * <p>§3.4 화이트리스트: 노출 필드는 여기 정의된 것뿐. 핸들 없는 작성자는 쿼리가 이미 걸러 내므로
 * ({@code StoryRepository.sharedByIsbn}) {@code authorLoginId}는 언제나 값이 있다.
 */
public record SharedMarginEntry(Long id, String text, String quote, String bgCode, Instant createdAt,
                                long likeCount, boolean liked,
                                String authorLoginId, String authorNickname) {

    public static SharedMarginEntry of(Story story, long likeCount, boolean liked) {
        return new SharedMarginEntry(story.getId(), story.getText(), story.getQuote(),
                story.getBgCode(), story.getCreatedAt(), likeCount, liked,
                story.getUser().getLoginId(), story.getUser().getNickname());
    }
}
