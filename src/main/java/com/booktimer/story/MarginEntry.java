package com.booktimer.story;

import java.time.Instant;

/**
 * 여백에 남긴 글 한 장 — 목록 카드의 표시 단위 (2026-08-16 재설계, 옛 {@code StoryCard} 후계).
 *
 * <p>책 라벨(제목·표지)이 없다: 진입 자체가 「그 책의 여백」이라 카드마다 같은 책 이름을 반복할 이유가 없고,
 * 책 정보는 응답 헤더({@code MarginResponse.book})에 한 번만 실린다. 열람 여부(옛 {@code viewed})도 없다 —
 * 미열람 링·「본 사람」이 사라지면서 열람 개념 자체가 은퇴했다(V71이 story_view를 드롭).
 *
 * <p>§3.4 화이트리스트: 노출 필드는 여기 정의된 것뿐 — 작성자 이메일·내부값은 설계적 차단.
 *
 * @param likeCount 이 글에 달린 좋아요 수. <b>누가</b> 눌렀는지는 담지 않는다 — 카드는 개수만으로 그려지고,
 *                  명단은 눌러야 열리는 별도 조회다({@code GET /api/stories/{id}/likes}). 목록 응답에 글마다
 *                  명단을 실으면 100장 × N명이 한 번에 날아간다
 * @param liked     viewer 본인이 눌렀는가. <b>자기 글도 true가 될 수 있다</b>(자기 좋아요 허용 — 2026-08-20)
 */
public record MarginEntry(Long id, String text, String bgCode, Instant createdAt, long likeCount, boolean liked) {

    /** 방금 만든 글 — 좋아요가 달릴 시간이 없었다({@code StoryApiController.create}의 응답). */
    public static MarginEntry of(Story story) {
        return of(story, 0L, false);
    }

    public static MarginEntry of(Story story, long likeCount, boolean liked) {
        return new MarginEntry(story.getId(), story.getText(), story.getBgCode(), story.getCreatedAt(),
                likeCount, liked);
    }
}
