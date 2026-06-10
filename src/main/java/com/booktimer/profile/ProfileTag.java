package com.booktimer.profile;

/**
 * 책방 헤더에 노출되는 책BTI 태그 칩 한 개 — Phase 6b 3단계.
 *
 * <p>라벨이 곧 근거 책 드릴다운 토큰(htmx {@code ?tag=label}). 클릭 가능 여부는 부분집합이 정의되는
 * 태그(장르 종족·한우물형)만 true — 폭 태그(외길형·잡식가·균형형)는 분포 전체에서 나와 false.
 *
 * @param label     화면 표시·드릴다운 토큰(예 "이야기파", "한우물형")
 * @param clickable true면 칩 클릭 시 근거 책 드릴다운 가능
 */
public record ProfileTag(String label, boolean clickable) {
}
