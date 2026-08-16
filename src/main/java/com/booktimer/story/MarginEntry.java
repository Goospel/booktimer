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
 */
public record MarginEntry(Long id, String text, String bgCode, Instant createdAt) {

    public static MarginEntry of(Story story) {
        return new MarginEntry(story.getId(), story.getText(), story.getBgCode(), story.getCreatedAt());
    }
}
