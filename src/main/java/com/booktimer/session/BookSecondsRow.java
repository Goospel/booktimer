package com.booktimer.session;

/** 책별 누적 독서 시간(초) — GROUP BY 집계 projection. */
public record BookSecondsRow(Long bookId, Long seconds) {}
