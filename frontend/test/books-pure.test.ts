// pure.ts를 직접 ESM import — 함수 하나 누락/시그니처 불일치 시 import 실패로 즉시 RED.
import { describe, test, expect } from 'vitest';
import {
    summarize, type ShelfSummary,
    initialOf,
    coverColor, COVER_PALETTE, COVER_FG,
    byline,
    statusBadge, STATUS_BADGE_FALLBACK,
    booksNavLinks,
    marginHandleLabel,
} from '../src/books/pure';

// 백엔드 BookStatus.name() = WANT_TO_READ / READING / FINISHED
const want = (n = 1) => Array.from({ length: n }, () => ({ status: 'WANT_TO_READ' }));
const reading = (n = 1) => Array.from({ length: n }, () => ({ status: 'READING' }));
const finished = (n = 1) => Array.from({ length: n }, () => ({ status: 'FINISHED' }));

describe('summarize — 책장 상태 집계', () => {
    test('빈 배열 → 전부 0', () => {
        expect(summarize([])).toEqual<ShelfSummary>({ total: 0, reading: 0, finished: 0, want: 0 });
    });

    test('한 상태만 (읽는 중 3권)', () => {
        expect(summarize(reading(3))).toEqual<ShelfSummary>({ total: 3, reading: 3, finished: 0, want: 0 });
    });

    test('혼합 — 시안 mock과 동일 분포(총 13 · 읽는중 5 · 완독 4 · 읽고싶음 4)', () => {
        const books = [...reading(5), ...finished(4), ...want(4)];
        expect(summarize(books)).toEqual<ShelfSummary>({ total: 13, reading: 5, finished: 4, want: 4 });
    });

    test('total 은 항상 books.length (알 수 없는 상태도 총합엔 포함, 세부엔 미포함)', () => {
        const books = [...reading(2), { status: 'PAUSED' }];
        const s = summarize(books);
        expect(s.total).toBe(3);
        expect(s.reading).toBe(2);
        expect(s.finished + s.want).toBe(0);
    });
});

describe('initialOf — 표지 이니셜 추출', () => {
    test('일반 제목 → 첫 글자', () => {
        expect(initialOf('불안')).toBe('불');
        expect(initialOf('Atomic Habits')).toBe('A');
    });

    test('앞 공백 trim 후 첫 글자', () => {
        expect(initialOf('  데미안')).toBe('데');
    });

    test('빈 제목 → 폴백 ?', () => {
        expect(initialOf('')).toBe('?');
    });

    test('공백뿐인 제목 → 폴백 ?', () => {
        expect(initialOf('   ')).toBe('?');
    });

    test('null/undefined 방어 → 폴백 ?', () => {
        expect(initialOf(null as unknown as string)).toBe('?');
        expect(initialOf(undefined as unknown as string)).toBe('?');
    });

    test('특수문자/이모지로 시작 → 그 문자', () => {
        expect(initialOf('#1 베스트셀러')).toBe('#');
    });
});

describe('coverColor — 무표지 플레이스홀더 색(결정적 해시 매핑)', () => {
    test('같은 seed → 항상 같은 색 (결정적·순수)', () => {
        expect(coverColor('불안')).toEqual(coverColor('불안'));
        expect(coverColor('9788937473135')).toEqual(coverColor('9788937473135'));
    });

    test('결과 bg 는 항상 시안 팔레트 안의 색', () => {
        for (const seed of ['불안', '데미안', '코스모스', '사피엔스', '', 'x']) {
            expect(COVER_PALETTE).toContain(coverColor(seed).bg);
        }
    });

    test('fg 는 시안 cover-present 톤으로 고정', () => {
        expect(coverColor('불안').fg).toBe(COVER_FG);
    });

    test('빈/누락 seed 도 폴백 색 반환(throw 안 함)', () => {
        expect(COVER_PALETTE).toContain(coverColor('').bg);
        expect(COVER_PALETTE).toContain(coverColor(null as unknown as string).bg);
    });

    test('서로 다른 seed 는 색이 분산된다(전부 같은 색 아님)', () => {
        const seeds = ['불안', '아주 작은 습관의 힘', '데미안', '미드나잇 라이브러리', '코스모스',
            '나미야 잡화점의 기적', '사피엔스', '작별인사', '달러구트 꿈 백화점', '트렌드 코리아 2026'];
        const distinct = new Set(seeds.map(s => coverColor(s).bg));
        expect(distinct.size).toBeGreaterThan(1);
    });

    test('팔레트는 시안 mock의 12색', () => {
        expect(COVER_PALETTE).toHaveLength(12);
    });
});

describe('byline — 검색 결과 저자·출판사 한 줄', () => {
    test('둘 다 있으면 "저자 · 출판사"', () => {
        expect(byline('알랭 드 보통', '은행나무')).toBe('알랭 드 보통 · 은행나무');
    });

    test('저자만 → 저자', () => {
        expect(byline('김영하', null)).toBe('김영하');
    });

    test('출판사만 → 출판사', () => {
        expect(byline(null, '문학동네')).toBe('문학동네');
    });

    test('둘 다 없음 → 빈 문자열', () => {
        expect(byline(null, null)).toBe('');
    });

    test('빈 문자열도 없음 취급', () => {
        expect(byline('', '')).toBe('');
    });

    test('공백뿐인 값은 trim 후 없음 취급', () => {
        expect(byline('   ', '문학동네')).toBe('문학동네');
        expect(byline('김영하', '   ')).toBe('김영하');
    });
});

// 색은 hex가 아니라 **토큰 참조**로 돌려준다. 이 값은 `:style`로 인라인 박히므로 hex면
// 다크에서 그 배지만 크림색으로 남는다 — CSS 쪽 래칫(darkTokens.test.ts)이 못 보는 사각이라
// 여기서 따로 못 박는다. `var(--…)` 형태 자체를 단언해야 「다크에서 갈리는가」가 걸린다.
describe('statusBadge — 상태배지 색 매핑(시안 STATUS)', () => {
    test('읽는 중 → 세이지', () => {
        expect(statusBadge('READING')).toEqual({ bg: 'var(--sage-soft)', fg: 'var(--accent-hover)' });
    });

    test('완독 → 선명한 초록', () => {
        expect(statusBadge('FINISHED')).toEqual({ bg: 'var(--ok-soft)', fg: 'var(--ok)' });
    });

    test('읽고 싶음 → 베이지', () => {
        // `--warn-bg-4`는 설정 배너(`--warn-bg`)와 **다른 라이트 값**이다(#F0E8DB vs #F6ECD9).
        // 근접값이라 한 토큰으로 합칠 뻔했으나, 라이트 픽셀을 origin/main 그대로 두기로 해서 갈랐다.
        expect(statusBadge('WANT_TO_READ')).toEqual({ bg: 'var(--warn-bg-4)', fg: 'var(--warn-fg)' });
    });

    test('모든 상태가 토큰 참조다 — hex가 하나라도 섞이면 그 배지만 다크에서 라이트로 남는다', () => {
        for (const s of ['READING', 'FINISHED', 'WANT_TO_READ', 'PAUSED', '']) {
            const b = statusBadge(s);
            expect(b.bg, `${s} bg`).toMatch(/^var\(--[\w-]+\)$/);
            expect(b.fg, `${s} fg`).toMatch(/^var\(--[\w-]+\)$/);
        }
    });

    test('알 수 없는 상태 → 중립 폴백', () => {
        expect(statusBadge('PAUSED')).toEqual(STATUS_BADGE_FALLBACK);
        expect(statusBadge('')).toEqual(STATUS_BADGE_FALLBACK);
    });
});

// 책장(/books) 하단 네비 — 책방 하단엔 '내 책장' 타일이 있는데 책장 하단엔 '내 책방'이
// 없어 동선이 비대칭이었다(책장→책방은 본문 힌트 문구로만). 양방향 대칭화로 '내 책방'
// 타일을 추가하되, myLoginId가 비면(dataset 미주입 등) /u/ 로 끝나는 깨진 링크가 새지
// 않게 책방 타일을 뺀다(N-055 정신 — null-state가 노출되지 않는가).
describe('booksNavLinks — 책장 하단 네비 링크', () => {
    test('myLoginId 있으면 홈·독서기록·내 책방(/u/{id}) 순으로 포함', () => {
        const links = booksNavLinks('alice');
        expect(links.map(l => l.href)).toEqual(['/', '/history', '/u/alice']);
        // 메인(루트) 타일 라벨은 '홈'(이 웹의 메인 페이지 명칭 = 홈, 옛 '대시보드' 폐기).
        expect(links.find(l => l.href === '/')).toEqual({
            href: '/', icon: 'home', label: '홈',
        });
        expect(links.find(l => l.label === '내 책방')).toEqual({
            href: '/u/alice', icon: 'user', label: '내 책방',
        });
    });

    test('myLoginId 비면 내 책방 타일 제외 (/u/ 깨진 링크 방지)', () => {
        const links = booksNavLinks('');
        expect(links.map(l => l.href)).toEqual(['/', '/history']);
        expect(links.some(l => l.label === '내 책방')).toBe(false);
    });

    test('공백뿐인 myLoginId도 제외(방어)', () => {
        expect(booksNavLinks('   ').some(l => l.href.startsWith('/u/'))).toBe(false);
    });

    test('null/undefined myLoginId도 제외(방어 — dataset 미주입)', () => {
        expect(booksNavLinks(null as unknown as string).some(l => l.href.startsWith('/u/'))).toBe(false);
        expect(booksNavLinks(undefined as unknown as string).some(l => l.href.startsWith('/u/'))).toBe(false);
    });
});

// ── 「여백」 손잡이 라벨 ────────────────────────────────────────────────────
// 책장은 글 0건·비공개 책까지 전부 진열하므로, 개수가 붙어야 「내 글이 있는 책」을 훑어 찾는다.
describe('marginHandleLabel — 책장 여백 손잡이 라벨', () => {
    test('글이 있으면 개수를 붙인다', () => {
        expect(marginHandleLabel(3)).toBe('여백 3');
    });

    test('0건이면 개수를 안 붙인다 — 「여백 0」은 말만 남는다', () => {
        expect(marginHandleLabel(0)).toBe('여백');
    });

    test('storyCount 없는 옛 응답도 「여백」 (undefined 노출·throw 방지)', () => {
        expect(marginHandleLabel(undefined)).toBe('여백');
    });
});
