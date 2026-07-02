package com.booktimer.story;

import java.time.Instant;

/** 열람자 목록 한 줄 — 작성자 본인만 조회 가능(sns-design §13.4). */
public record StoryViewerEntry(String loginId, String nickname, String profileCharacterCode, Instant viewedAt) {
}
