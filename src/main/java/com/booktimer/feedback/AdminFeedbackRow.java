package com.booktimer.feedback;

import com.booktimer.user.User;

/**
 * 관리자 문의함 표시용 행 — 트랜잭션 안에서 엔티티(LAZY {@code author} 포함)를 평면 값으로 추출한다.
 *
 * <p>뷰에서 detached 엔티티의 LAZY 연관을 건드리지 않게(OSIV 비의존) 서비스가 미리 조립한다.
 * 작성자 식별은 운영자가 맥락·재현을 위해 본다(loginId 미설정 OAuth 온보딩 전이면 null일 수 있어 닉네임 폴백).
 */
public record AdminFeedbackRow(
        Long id,
        String authorLoginId,
        String authorNickname,
        FeedbackType type,
        String title,
        String content,
        FeedbackStatus status) {

    static AdminFeedbackRow from(Feedback feedback) {
        User author = feedback.getAuthor();
        return new AdminFeedbackRow(
                feedback.getId(),
                author.getLoginId(),
                author.getNickname(),
                feedback.getType(),
                feedback.getTitle(),
                feedback.getContent(),
                feedback.getStatus());
    }
}
