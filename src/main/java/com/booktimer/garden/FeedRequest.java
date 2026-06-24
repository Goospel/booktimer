package com.booktimer.garden;

/**
 * POST /api/garden/feed 요청 바디.
 *
 * @param characterCode 먹일 작가 캐릭터 코드
 */
public record FeedRequest(String characterCode) {}
