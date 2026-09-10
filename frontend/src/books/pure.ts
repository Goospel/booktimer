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

/** 표지 주소가 실제로 있는가 — null·빈문자열·공백은 false. 구현은 profile/format.ts 하나다
 *  (두 벌이 되면 「공백은 표지인가」가 화면마다 갈린다). */
export { hasCover } from '../profile/format'

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

// 값은 hex가 아니라 **토큰 참조**다. 이 색은 `:style`로 인라인 박히므로 hex를 그대로 두면
// 다크에서 그 배지만 크림색으로 남는다 — 인라인 스타일은 CSS의 어떤 테마 규칙보다 세다.
// 토큰 정의는 app.css 한 곳(라이트 `:root` + 다크 블록)이고, 값은 옮기기 전 hex와 같다
// (읽고싶음 배지만 설정 배너와 같은 --warn-bg로 합쳐졌다 — #F0E8DB → #F6ECD9).
const STATUS_BADGE: Record<string, StatusBadge> = {
    READING: { bg: 'var(--sage-soft)', fg: 'var(--accent-hover)' },  // 읽는 중 — 세이지
    FINISHED: { bg: 'var(--ok-soft)', fg: 'var(--ok)' },             // 완독 — 선명한 초록
    WANT_TO_READ: { bg: 'var(--warn-bg-4)', fg: 'var(--warn-fg)' },    // 읽고 싶음 — 베이지
};

export const STATUS_BADGE_FALLBACK: StatusBadge = { bg: 'var(--line-1-4)', fg: 'var(--muted)' };

export function statusBadge(status: string): StatusBadge {
    return STATUS_BADGE[status] ?? STATUS_BADGE_FALLBACK;
}

// ── 하단 네비 링크 ────────────────────────────────────────────────────────
// 책장(/books) 하단 타일(NavLinks). 책방 하단엔 '내 책장' 타일이 있는데 책장 하단엔
// '내 책방'이 없어 동선이 비대칭이었다(책장→책방은 본문 힌트 문구로만) → '내 책방'
// (/u/{myLoginId}) 타일을 더해 양방향 대칭화. icon='user'(사람)은 NAV_ICONS·QuickNav
// '내 책방' 타일과 동일. myLoginId가 비면(dataset 미주입 등) /u/ 로 끝나는 깨진 링크가
// 새지 않게 책방 타일을 뺀다(가드). 구조는 shared/NavLinks.vue 의 NavLink 와 동일.

export interface NavLinkSpec {
    href: string;
    icon: string;
    label: string;
}

export function booksNavLinks(myLoginId: string): NavLinkSpec[] {
    const links: NavLinkSpec[] = [
        { href: '/', icon: 'home', label: '홈' },
        { href: '/history', icon: 'history', label: '독서 기록' },
    ];
    if ((myLoginId ?? '').trim()) {
        links.push({ href: `/u/${myLoginId}`, icon: 'user', label: '내 책방' });
    }
    return links;
}

// ── 「여백」 손잡이 라벨 ────────────────────────────────────────────────────
// 글이 있으면 개수를 붙인다 — 책장은 글 0건·비공개 책까지 전부 진열하므로, 개수가 있어야
// 「내 글이 있는 책」을 훑어 찾는다. storyCount 없는 옛 응답(undefined)·0은 「여백」
// (공개 전환 고지의 `?? 0` 방어와 같은 관례 — 「여백 0」은 말만 남는다).

export function marginHandleLabel(storyCount: number | undefined): string {
    return storyCount && storyCount > 0 ? `여백 ${storyCount}` : '여백';
}
