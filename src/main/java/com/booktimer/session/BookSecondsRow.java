package com.booktimer.session;

/**
 * 책별 누적 시간(초) — GROUP BY 집계 projection.
 *
 * <p>독서({@code reading_session} × {@code book})와 공부({@code study_session} × {@code study_book})가
 * <b>같은 모양</b>이라 이 record 하나를 함께 쓴다 — 담는 것이 id와 초뿐이라 도메인 중립이다.
 */
public record BookSecondsRow(Long bookId, Long seconds) {}
