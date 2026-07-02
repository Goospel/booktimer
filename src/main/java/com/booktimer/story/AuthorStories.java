package com.booktimer.story;

import java.util.List;

/** 작성자 한 명의 활성 스토리 묶음 — 스트립 아바타 링 하나. 내부는 작성순 asc(뷰어 재생 순서). */
public record AuthorStories(String loginId, String nickname, String profileCharacterCode,
                            boolean allViewed, List<StoryCard> stories) {
}
