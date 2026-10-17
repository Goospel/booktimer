// pure.ts를 직접 ESM import — 함수 하나 누락/시그니처 불일치 시 import 실패로 즉시 RED.
import { describe, test, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import {
    summarize, type ShelfSummary,
    initialOf,
    coverColor, coverStyle, COVER_PALETTE, COVER_FG,
    byline,
    statusBadge, STATUS_BADGE_FALLBACK,
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

    test('fg 는 미색 이니셜로 고정(밤에도 안 뒤집는다 — 표지는 물건이다)', () => {
        expect(coverColor('불안').fg).toBe(COVER_FG);
        expect(COVER_FG).toBe('#F5F0E6');
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

    test('팔레트는 천 장정 12색(미니앱 ui.tsx와 같은 순서 — editorialTheme.test.ts가 파일을 읽어 대조한다)', () => {
        expect(COVER_PALETTE).toHaveLength(12);
        expect(COVER_PALETTE[0]).toBe('#7A2E28');
    });
});

// 해시 입력은 **제목** 하나다 — isbn을 쓰면 같은 책이 웹(isbn)과 미니앱(제목)에서 다른 칸이 된다(설계 §5-1).
describe('REQ-14 · 무표지 표지는 제목 하나로 칠한다', () => {
    test('coverStyle(제목) = 그 칸의 천 색 + 미색 이니셜 — 「미움받을 용기」는 칸 2', () => {
        expect(coverStyle('미움받을 용기')).toEqual({ background: COVER_PALETTE[2], color: '#F5F0E6' });
    });

    test('같은 제목이면 isbn이 달라도 같은 색이다(입력이 제목뿐이라 isbn을 받을 자리가 없다)', () => {
        expect(coverStyle('데미안')).toEqual(coverStyle('데미안'));
        expect(coverStyle.length).toBe(1);
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
// 에디토리얼(D8=①)은 상태색을 한 색으로 모은다 — 그대로 두면 세 배지가 같은 회색 칩이 된다. 그래서 **역할 3톤**으로 가른다
// (미니앱 HomeFeed 배지와 같은 역할): 다 읽음 = 잉크 채움 · 읽는 중 = 가는 선 · 읽고 싶어요 = 옅은 면.
describe('REQ-08 · 상태 배지 3톤', () => {
    test('다 읽음 → 잉크 채움 + 종이 글자', () => {
        expect(statusBadge('FINISHED')).toEqual({ bg: 'var(--accent-fill)', fg: 'var(--on-accent)' });
    });

    test('읽는 중 → 채움 없이 가는 선 + 흐린 글자', () => {
        expect(statusBadge('READING')).toEqual({ bg: 'transparent', fg: 'var(--muted)', border: '1px solid var(--border)' });
    });

    test('읽고 싶음 → 옅은 면 + 보조 글자', () => {
        expect(statusBadge('WANT_TO_READ')).toEqual({ bg: 'var(--sage-soft)', fg: 'var(--ink-2)' });
    });

    test('모든 상태가 토큰 참조(또는 투명)다 — hex가 하나라도 섞이면 그 배지만 다크에서 라이트로 남는다', () => {
        for (const s of ['READING', 'FINISHED', 'WANT_TO_READ', 'PAUSED', '']) {
            const b = statusBadge(s);
            expect(b.bg, `${s} bg`).toMatch(/^(var\(--[\w-]+\)|transparent)$/);
            expect(b.fg, `${s} fg`).toMatch(/^var\(--[\w-]+\)$/);
            if (b.border) expect(b.border, `${s} border`).toMatch(/^1px solid var\(--[\w-]+\)$/);
        }
    });

    test('알 수 없는 상태 → 읽는 중과 같은 꼴(가는 선)', () => {
        expect(statusBadge('PAUSED')).toEqual(STATUS_BADGE_FALLBACK);
        expect(statusBadge('')).toEqual(STATUS_BADGE_FALLBACK);
        expect(STATUS_BADGE_FALLBACK).toEqual(statusBadge('READING'));
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

// 무표지 칸을 그리는 Vue 9컴포넌트가 한 함수를 쓴다 — 지역 함수가 한 벌이라도 남으면 그 화면만 isbn으로 칠해 채널·화면마다 색이 갈린다.
describe('REQ-14 · 무표지 칸 9컴포넌트가 공용 coverStyle(제목)을 쓴다', () => {
    const FILES = ['books/BooksApp.vue', 'dashboard/BookPickForm.vue', 'dashboard/BookPickSheet.vue', 'dashboard/StudyBookSheet.vue',
        'dashboard/StudyTimerCard.vue', 'study/StudyBooksApp.vue', 'profile/ShelfPanel.vue', 'profile/BtiPanel.vue', 'shared/story/MarginPanel.vue'];
    const src = (f: string) => readFileSync(fileURLToPath(new URL(`../src/${f}`, import.meta.url)), 'utf8');

    test('지역 coverStyle 0 · isbn 해시 입력 0 · 공용 함수 호출은 9파일 전부', () => {
        expect(FILES.filter((f) => /function coverStyle/.test(src(f)))).toEqual([]);
        expect(FILES.filter((f) => /isbn13\s*\|\|/.test(src(f)) && /coverColor\(/.test(src(f)))).toEqual([]);
        expect(FILES.filter((f) => !/:style="coverStyle\(/.test(src(f)))).toEqual([]);
    });
});
