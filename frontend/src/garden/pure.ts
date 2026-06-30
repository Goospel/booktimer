// PortraitVillage(다마고치 돌봄 뷰) 순수 함수 — DOM·Phaser 의존 0.
//
// CoC 가로 무대 은퇴로 좌표·줌·격자·아이소(normToIso 등)·배회 AI(wanderStep)·
// 걷기 pose(walkPose·idlePose)·여백 앰비언트(AMBIENT_DECOR) 순수함수는 모두 삭제됐다.
// 남은 것은 돌봄 뷰가 쓰는 "오늘의 작가" 선정 + 정(affection) 미터 진행도뿐.

export function clampNorm(v: number): number {
    return Math.min(1, Math.max(0, v));
}

// "오늘의 작가" 선정 — 보유 작가 중 정 레벨 최저(키울 여지 큰 순). 동률은 첫(입력 순서 보존). 빈 목록 → null.
// 제네릭: { level } 만 요구 — OwnedCharacterDto 등 어떤 객체든 그대로 반환(참조 유지로 컴포넌트가 선택 객체 활용).
export function pickTodayAuthor<T extends { level: number }>(owned: readonly T[]): T | null {
    if (owned.length === 0) return null;
    let best = owned[0];
    for (let i = 1; i < owned.length; i++) {
        if (owned[i].level < best.level) best = owned[i];   // strict< → 동률 시 첫 유지
    }
    return best;
}

// 정(affection=feedCount) 레벨 진입 임계 — AffectionLevel.of 단일출처의 프론트 복제(PoC).
// 인덱스 i = level (i+1) 진입에 필요한 누적 feedCount: Lv1=1, Lv2=3, Lv3=7, Lv4=15, Lv5=30.
export const AFFECTION_THRESHOLDS: readonly number[] = [1, 3, 7, 15, 30];

export interface AffectionProgress {
    level: number;               // 0–5 (AffectionLevel과 동일 유도)
    prevThreshold: number;       // 현 레벨 진입 feedCount(Lv0=0)
    nextThreshold: number | null; // 다음 레벨 진입 feedCount(만렙=null)
    progress: number;            // 현 레벨 구간 진행도 [0,1] (만렙=1)
    isMax: boolean;              // Lv5 도달
}

// feedCount → 다음 임계까지 진행도. level·title은 API가 주므로 프론트는 progress(미터 채움)만 계산.
// nextThreshold를 API가 안 줘 임계 테이블을 복제(plan 2-3 허용).
export function affectionProgress(affection: number): AffectionProgress {
    const f = Math.max(0, Math.floor(affection));   // 음수·소수 방어
    const t = AFFECTION_THRESHOLDS;
    let level = 0;
    for (let i = 0; i < t.length; i++) {
        if (f >= t[i]) level = i + 1; else break;
    }
    const isMax = level >= t.length;                // Lv5
    const prevThreshold = level === 0 ? 0 : t[level - 1];
    const nextThreshold = isMax ? null : t[level];
    const progress = isMax || nextThreshold === null
        ? 1
        : clampNorm((f - prevThreshold) / (nextThreshold - prevThreshold));
    return { level, prevThreshold, nextThreshold, progress, isMax };
}
