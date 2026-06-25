// 내 책장(/books) 순수 로직 — 뷰(BooksApp.vue)에서 분리해 vitest(node env)로 단위 검증.
// 시안: private-docs/standardHTML/My Shelf.decoded.html (DCLogic mock).

// ── 요약 통계 집계 ────────────────────────────────────────────────────────
// 인사말 아래 "총 N권 · 읽는 중 N · 완독 N · 읽고 싶음 N" 한 줄.
// status 는 백엔드 BookStatus.name() (WANT_TO_READ / READING / FINISHED).

export interface ShelfBookLike {
    status: string;
}

export interface ShelfSummary {
    total: number;
    reading: number;
    finished: number;
    want: number;
}

export function summarize(books: readonly ShelfBookLike[]): ShelfSummary {
    const s: ShelfSummary = { total: books.length, reading: 0, finished: 0, want: 0 };
    for (const b of books) {
        if (b.status === 'READING') s.reading++;
        else if (b.status === 'FINISHED') s.finished++;
        else if (b.status === 'WANT_TO_READ') s.want++;
        // 그 외(알 수 없는 상태)는 total 에만 포함 — 세부 카운트는 방어적으로 제외.
    }
    return s;
}

// ── 표지 이니셜 ───────────────────────────────────────────────────────────
// 시안: b.t.charAt(0). 앞 공백은 trim, 빈/공백 제목은 폴백 문자로.

export function initialOf(title: string): string {
    const t = (title ?? '').trim();
    return t ? t.charAt(0) : '?';
}

// ── 검색 결과 byline(저자 · 출판사) ───────────────────────────────────────
// 시안: r.a + ' · ' + r.p. 둘 중 없는 값(null·빈·공백)은 빼고, 남은 것만 ' · '로 잇는다.

export function byline(author: string | null, publisher: string | null): string {
    const a = (author ?? '').trim();
    const p = (publisher ?? '').trim();
    return [a, p].filter(Boolean).join(' · ');
}

// ── 무표지 플레이스홀더 색(결정적 해시 매핑) ──────────────────────────────
// coverUrl 이 없을 때 색 배경 + 이니셜로 표지를 대신한다. 같은 책은 항상 같은 색이
// 되도록 seed(isbn13 우선, 없으면 title)를 해시해 고정 팔레트에서 결정적으로 고른다.
// 팔레트·fg 는 시안 mock 의 cov 색 12종과 cover-present fg 를 그대로 사용.

export const COVER_PALETTE: readonly string[] = [
    '#B8C2A6', '#D6C3B0', '#C7B89B', '#A9B9A0', '#B0B7A8', '#CBB9A3',
    '#C3CBC0', '#BFC8B4', '#CFC0AE', '#D9C8A9', '#C2BBA8', '#B5A98F',
];

export const COVER_FG = 'rgba(44,42,36,0.5)';

export interface CoverColor {
    bg: string;
    fg: string;
}

export function coverColor(seed: string): CoverColor {
    const key = seed ?? '';
    let h = 0;
    for (let i = 0; i < key.length; i++) {
        h = (h * 31 + key.charCodeAt(i)) >>> 0; // 결정적 해시(부호 없는 32bit)
    }
    return { bg: COVER_PALETTE[h % COVER_PALETTE.length], fg: COVER_FG };
}

// ── 상태 배지 색 ──────────────────────────────────────────────────────────
// status(BookStatus.name())별 배지 배경·글자색. 시안 My Shelf 의 STATUS 매핑 그대로.

export interface StatusBadge {
    bg: string;
    fg: string;
}

const STATUS_BADGE: Record<string, StatusBadge> = {
    READING: { bg: '#E7EEE2', fg: '#4F6B4C' },      // 읽는 중 — 세이지
    FINISHED: { bg: '#E0EFE6', fg: '#2F8F6B' },     // 완독 — 선명한 초록
    WANT_TO_READ: { bg: '#F0E8DB', fg: '#8A6D3B' }, // 읽고 싶음 — 베이지
};

export const STATUS_BADGE_FALLBACK: StatusBadge = { bg: '#EFEADD', fg: '#6F6A5E' };

export function statusBadge(status: string): StatusBadge {
    return STATUS_BADGE[status] ?? STATUS_BADGE_FALLBACK;
}
