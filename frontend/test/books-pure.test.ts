// pure.ts를 직접 ESM import — 함수 하나 누락/시그니처 불일치 시 import 실패로 즉시 RED.
import { describe, test, expect } from 'vitest';
import {
    summarize, type ShelfSummary,
    initialOf,
    coverColor, COVER_PALETTE, COVER_FG,
    byline,
    statusBadge, STATUS_BADGE_FALLBACK,
    booksNavLinks,
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

describe('statusBadge — 상태배지 색 매핑(시안 STATUS)', () => {
    test('읽는 중 → 세이지', () => {
        expect(statusBadge('READING')).toEqual({ bg: '#E7EEE2', fg: '#4F6B4C' });
    });

    test('완독 → 선명한 초록', () => {
        expect(statusBadge('FINISHED')).toEqual({ bg: '#E0EFE6', fg: '#2F8F6B' });
    });

    test('읽고 싶음 → 베이지', () => {
        expect(statusBadge('WANT_TO_READ')).toEqual({ bg: '#F0E8DB', fg: '#8A6D3B' });
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
    test('myLoginId 있으면 대시보드·독서기록·내 책방(/u/{id}) 순으로 포함', () => {
        const links = booksNavLinks('alice');
        expect(links.map(l => l.href)).toEqual(['/', '/history', '/u/alice']);
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
