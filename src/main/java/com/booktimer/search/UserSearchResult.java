package com.booktimer.search;

/**
 * 닉네임 검색 결과 한 줄 (sns-design §7.3).
 *
 * @param nickname        닉네임(프로필 링크 핸들)
 * @param publicBookCount 공개(PUBLIC)한 책 수 — "누구인지" 감 잡기용
 * @param following       검색한 사람(viewer)이 이미 이 사용자를 팔로우 중인지 — 버튼을 팔로우/언팔로 분기
 * @param self            이 사용자가 viewer 본인인지 — 그러면 팔로우 버튼 대신 "나" 표시
 */
public record UserSearchResult(
        String nickname,
        long publicBookCount,
        boolean following,
        boolean self) {
}
