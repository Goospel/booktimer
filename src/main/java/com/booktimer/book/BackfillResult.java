package com.booktimer.book;

/**
 * 카탈로그 백필 한 번의 결과 요약(책BTI Phase 1b) — 관리자 화면에 무엇이 처리됐는지 투명하게 보여준다.
 *
 * @param scanned   이번에 조회 시도한 후보 권수(limit 이하)
 * @param updated   실제로 장르/출간일을 채운 권수
 * @param notFound  조회했으나 알라딘이 메타를 못 준 권수(다음 실행에서 재시도됨)
 * @param remaining 아직 백필이 남은 책 수(장르 비었고 isbn 있는) — 0이면 더 채울 것 없음(notFound 제외)
 */
public record BackfillResult(int scanned, int updated, int notFound, long remaining) {
}
