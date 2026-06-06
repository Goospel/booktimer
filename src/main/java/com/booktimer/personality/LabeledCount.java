package com.booktimer.personality;

/**
 * 라벨별 집계 수 — 분포(저자·장르·출간연대)의 한 항목.
 *
 * @param label 항목 이름(저자명/장르명/연대 문자열 등)
 * @param count 그 라벨에 해당하는 책 수
 */
public record LabeledCount(String label, int count) {
}
