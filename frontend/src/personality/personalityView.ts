/**
 * 책BTI 페이지 순수 표시 로직 — 컴포넌트 mount 없이 vitest(node)로 검증 가능.
 * 시각 재스킨이라 도메인 로직은 적지만, 데이터 바인딩·캐러셀 인덱스에 끼는
 * distinct 실패(구분자·3상태·0 나눗셈·clamp)를 단위로 못박는다.
 */

/** 장르·저자 라벨을 시안 구분자 ' · '로 잇는다. 빈 배열 → ''. */
export function joinLabels(items: { label: string }[]): string {
    return items.map(i => i.label).join(' · ');
}

/**
 * '다시 분석' 버튼 시각 상태. 진행 중이면(refreshing) remaining과 무관하게 refreshing,
 * 그다음 한도 소진(remaining<=0)이면 exhausted, 아니면 ready.
 * → 아이콘(회전/모래시계)·비활성·회색 처리를 이 한 값으로 분기한다.
 */
export function refreshState(remaining: number, refreshing: boolean): 'refreshing' | 'exhausted' | 'ready' {
    if (refreshing) return 'refreshing';
    if (remaining <= 0) return 'exhausted';
    return 'ready';
}

/**
 * 캐러셀에서 현재 중앙에 온 슬라이드 인덱스(도트 활성용). scroll-snap-center라
 * scrollLeft ≈ i·step 이므로 round(scrollLeft/step)로 역산하고 [0, count-1]로 clamp한다.
 * count·step이 0 이하면 0(빈 목록·미측정 가드, 0 나눗셈 방지).
 */
export function dotIndex(scrollLeft: number, step: number, count: number): number {
    if (count <= 0 || step <= 0) return 0;
    const i = Math.round(scrollLeft / step);
    return Math.min(count - 1, Math.max(0, i));
}
