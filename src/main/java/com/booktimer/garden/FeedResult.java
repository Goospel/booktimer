package com.booktimer.garden;

/**
 * POST /api/garden/feed 성공 응답 — 먹이기 후 갱신된 상태.
 *
 * @param foodBalance   먹이기 후 남은 먹이 잔액(≥ 0)
 * @param characterCode 먹인 작가 캐릭터 코드
 * @param affection     먹이기 후 해당 캐릭터의 누적 feed_count
 * @param level         affection에서 유도된 현재 레벨(0–5)
 * @param title         현재 레벨 칭호(level 0이면 빈 문자열)
 * @param leveledUp     이번 먹이기로 레벨이 올랐으면 {@code true}
 */
public record FeedResult(
        int foodBalance,
        String characterCode,
        int affection,
        int level,
        String title,
        boolean leveledUp
) {}
