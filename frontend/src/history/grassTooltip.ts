// 잔디 셀 hover 다크 툴팁 텍스트 — 순수함수(렌더링/DOM 비의존).
// 시안(HistoryPage.dc.html) 규칙:
//   placeholder(date=null, future·선행 빈칸) → 툴팁 없음(null) → 컴포넌트가 핸들러를 안 붙인다.
//   level==0(기록 없는 날)                  → "{날짜} · 기록 없음"
//   level>0(읽은 날)                         → "{날짜} · {N}분"  (manual이면 " · 직접 채움")
//   N = floor(totalSeconds / 60)

export interface GrassCellLike {
    date: string | null;
    totalSeconds: number;
    level: number;
    manual: boolean;
}

export function cellTooltip(cell: GrassCellLike): string | null {
    if (cell.date === null) return null;            // placeholder/future — hover 대상 아님
    if (cell.level <= 0) return `${cell.date} · 기록 없음`;
    const minutes = Math.floor(cell.totalSeconds / 60);
    return `${cell.date} · ${minutes}분${cell.manual ? ' · 직접 채움' : ''}`;
}
