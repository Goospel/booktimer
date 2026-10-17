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
// coverUrl 이 없을 때 천 장정 색 + 미색 이니셜로 표지를 대신한다(에디토리얼 — 「표지가 색의 주인공」).
// 같은 책은 언제 어디서 그려도 같은 색이어야 해서 **제목**을 해시해 고정 팔레트에서 결정적으로 고른다.
// 팔레트·해시·이니셜은 미니앱 ui.tsx와 같다(순서까지 — editorialTheme.test.ts가 그 파일을 읽어 대조한다).
// 순서: oxblood · navy · forest · plum · teal · sienna · slate · umber · olive · wine · indigo · moss.

export const COVER_PALETTE: readonly string[] = [
    '#7A2E28', '#233A57', '#2F4B3A', '#5B3A55', '#2C5C5E', '#A0522D',
    '#3E4A56', '#5A4632', '#4F5A2E', '#6B2C3E', '#2E3F66', '#3F5B45',
];

/** 천 표지 위 이니셜 — 미색 리터럴(최저 대비 sienna 4.94). 표지는 물건이라 다크에서도 안 뒤집는다. */
export const COVER_FG = '#F5F0E6';

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

/** 무표지 자리 표지의 인라인 색 — 해시 입력은 **제목**(미니앱 ui.tsx와 같다 — isbn을 쓰면 같은 책이 채널마다 다른 색이 된다). */
export function coverStyle(title: string): { background: string; color: string } {
    const c = coverColor(title);
    return { background: c.bg, color: c.fg };
}

// ── 상태 배지 색 ──────────────────────────────────────────────────────────
// status(BookStatus.name())별 배지 톤 — 에디토리얼 3톤(미니앱 피드 배지와 같은 역할):
// 다 읽음 = 잉크 채움 · 읽는 중 = 가는 선 · 읽고 싶어요 = 옅은 면. 상태색을 한 색으로 모은 재테마(D8=①)에서
// 세 배지가 같은 회색 칩이 되지 않게 모양으로 가른다.

export interface StatusBadge {
    bg: string;
    fg: string;
    /** 테 — 「읽는 중」만 가는 선. 소비처(statusStyle)가 이 값을 버리면 화면에 선이 안 선다. */
    border?: string;
}

// 값은 hex가 아니라 **토큰 참조**다. 이 색은 `:style`로 인라인 박히므로 hex를 그대로 두면
// 다크에서 그 배지만 라이트 값으로 남는다 — 인라인 스타일은 CSS의 어떤 테마 규칙보다 세다.
const READING_BADGE: StatusBadge = { bg: 'transparent', fg: 'var(--muted)', border: '1px solid var(--border)' };
const STATUS_BADGE: Record<string, StatusBadge> = {
    READING: READING_BADGE,                                           // 읽는 중 — 가는 선
    FINISHED: { bg: 'var(--accent-fill)', fg: 'var(--on-accent)' },   // 다 읽음 — 잉크 채움
    WANT_TO_READ: { bg: 'var(--sage-soft)', fg: 'var(--ink-2)' },     // 읽고 싶음 — 옅은 면
};

/** 알 수 없는 상태 — 읽는 중과 같은 꼴(가장 조용한 톤). */
export const STATUS_BADGE_FALLBACK: StatusBadge = READING_BADGE;

export function statusBadge(status: string): StatusBadge {
    return STATUS_BADGE[status] ?? STATUS_BADGE_FALLBACK;
}

// ── 「여백」 손잡이 라벨 ────────────────────────────────────────────────────
// 글이 있으면 개수를 붙인다 — 책장은 글 0건·비공개 책까지 전부 진열하므로, 개수가 있어야
// 「내 글이 있는 책」을 훑어 찾는다. storyCount 없는 옛 응답(undefined)·0은 「여백」
// (공개 전환 고지의 `?? 0` 방어와 같은 관례 — 「여백 0」은 말만 남는다).

export function marginHandleLabel(storyCount: number | undefined): string {
    return storyCount && storyCount > 0 ? `여백 ${storyCount}` : '여백';
}
