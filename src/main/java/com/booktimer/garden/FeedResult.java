package com.booktimer.garden;

/**
 * POST /api/garden/feed 성공 응답 — 먹이기 후 갱신된 상태.
 *
 * @param foodBalance   먹이기 후 남은 먹이 잔액(≥ 0)
 * @param characterCode 먹인 작가 캐릭터 코드
 * @param affection     먹이기 후 해당 캐릭터의 누적 feed_count(정 레벨)
 */
public record FeedResult(int foodBalance, String characterCode, int affection) {}
