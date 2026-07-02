package com.booktimer.story;

import java.util.List;

/**
 * 홈 스트립 피드 응답 — 내 스토리는 별도 필드(맨 앞 고정), 팔로잉 작성자 그룹은 미열람 우선 정렬(sns-design §13.4).
 *
 * <p>§3.4 화이트리스트: 노출 필드는 이 record 트리에 정의된 것뿐 — 이메일·타이머 내부값 등은 설계적 차단.
 */
public record StoryFeedResponse(AuthorStories mine, List<AuthorStories> groups) {
}
