package com.booktimer.personality;

import java.time.Instant;
import java.util.List;

/**
 * 책BTI 분석 히스토리 한 줄의 표시 모델 — 화면(전용 페이지)이 최대 3개의 과거 분석을 카드로 그릴 때 쓴다.
 *
 * <p>엔티티의 자기 컬럼만 평면화해 담는다(LAZY 연관 없음) — 트랜잭션 밖(뷰)에서 안전하게 쓰인다(OSIV 비의존).
 *
 * @param id        행 식별자 — "이걸로 대표 선택" 폼의 대상
 * @param narrative 성향 서술(한 문단)
 * @param tags      태그(있으면)
 * @param generatedAt 분석 생성 시각 — 카드에 "분석 시각"으로 표시(과거 분석 구분의 핵심)
 * @param selected  대표 여부(공개 책방에 노출되는 그 행) — true면 "대표" 뱃지
 * @param stale     이 분석 이후 책장이 바뀌었는가 — true면 "분석 이후 책장이 바뀜" 안내(다시 분석 유도)
 */
public record PersonalityHistoryEntry(Long id, String narrative, List<String> tags,
                                      Instant generatedAt, boolean selected, boolean stale) {

    public PersonalityHistoryEntry {
        tags = (tags == null) ? List.of() : List.copyOf(tags);
    }
}
