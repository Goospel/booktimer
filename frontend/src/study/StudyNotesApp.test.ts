// @vitest-environment jsdom
// 필기 화면(/study/notes) — 책별 목록과 「홈 편집기로 열기」 링크의 배선.
//
// 순수 함수(pure.test.ts의 notesBookParam)가 답하지 못하는 절반이다: 그 id로 실제 목록 요청이 나가는지,
// 행 링크가 홈 핸드오프 형식(`/?note=<id>`)인지, 서재 0권·실패 경로가 막다른 길이 아닌지는 마운트해서 잰다.
import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils';

import StudyNotesApp from './StudyNotesApp.vue';

const AT = new Date(2026, 8, 10, 14, 32).toISOString();

const book = (id: number, title: string) => ({
    id, title, author: null, coverUrl: null, isbn13: null, readCount: 0,
    purchaseLink: null, totalSeconds: 0, sessionGoalSeconds: null, linkUrl: null,
});

const BOOKS = [book(7, '정보처리기사 실기'), book(9, '토익 보카')];

function okJson(body: object) {
    return { ok: true, status: 200, json: async () => body, text: async () => '' } as Response;
}

function fail(status: number) {
    return { ok: false, status, json: async () => ({}), text: async () => '' } as Response;
}

/**
 * URL별 응답 — 호출 순서가 아니라 주소로 답한다(순서에 기대면 「어느 책 목록을 불렀나」를 못 가른다).
 * `notes`는 bookId → 응답.
 */
function stubFetch(books: unknown[], notes: Record<number, Response | (() => Response)>) {
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        if (url === '/api/study/books') return okJson({ books });
        const m = /\/api\/study\/notes\?bookId=(\d+)$/.exec(url);
        if (m) {
            const r = notes[Number(m[1])];
            if (!r) return okJson({ notes: [] });
            return typeof r === 'function' ? r() : r;
        }
        throw new Error(`예상 밖 요청: ${url}`);
    }));
}

const listCalls = () => vi.mocked(fetch).mock.calls
    .map((c) => String(c[0]))
    .filter((u) => u.startsWith('/api/study/notes'));

async function mountAt(search: string): Promise<VueWrapper> {
    window.history.replaceState(null, '', `/study/notes${search}`);
    const wrapper = mount(StudyNotesApp, { attachTo: document.body });
    await flushPromises();
    return wrapper;
}

beforeEach(() => {
    document.body.innerHTML = '<div></div>';
});

afterEach(() => {
    vi.unstubAllGlobals();
    document.body.innerHTML = '';
    window.history.replaceState(null, '', '/');
});

describe('필기 화면', () => {
    test('서재 0권 — 목록을 안 부르고 공부 서재로 가는 길을 준다', async () => {
        stubFetch([], {});
        const w = await mountAt('');

        expect(listCalls()).toHaveLength(0);
        expect(w.find('a[href="/study/books"]').exists()).toBe(true);
    });

    test('?bookId=9 — 그 책 목록을 1회 부르고, 행은 홈 편집기로 여는 링크다', async () => {
        stubFetch(BOOKS, {
            9: okJson({ notes: [{ id: 5, title: '3장 정리', chars: 12, preview: '본문', updatedAt: AT }] }),
        });
        const w = await mountAt('?bookId=9');

        expect(listCalls()).toEqual(['/api/study/notes?bookId=9']);
        const items = w.findAll('[data-testid="notes-page-item"]');
        expect(items).toHaveLength(1);
        expect(items[0].attributes('href')).toBe('/?note=5');
        expect((w.find('[data-testid="notes-page-book"]').element as HTMLSelectElement).value).toBe('9');
    });

    test('책을 바꾸면 새 책 목록을 1회 더 부른다', async () => {
        stubFetch(BOOKS, {});
        const w = await mountAt('');
        expect(listCalls()).toEqual(['/api/study/notes?bookId=7']);

        await w.find('[data-testid="notes-page-book"]').setValue('9');
        await flushPromises();

        expect(listCalls()).toEqual(['/api/study/notes?bookId=7', '/api/study/notes?bookId=9']);
    });

    test('목록 실패 — 오류 문구와 「다시 시도」, 누르면 다시 부른다', async () => {
        let first = true;
        stubFetch(BOOKS, {
            7: () => {
                if (first) { first = false; return fail(500); }
                return okJson({ notes: [{ id: 3, title: null, chars: 1, preview: 'ㄱ', updatedAt: AT }] });
            },
        });
        const w = await mountAt('');

        expect(w.find('.study-error').exists()).toBe(true);
        const retry = w.findAll('button').find((b) => b.text() === '다시 시도');
        expect(retry).toBeDefined();

        await retry!.trigger('click');
        await flushPromises();

        expect(listCalls()).toHaveLength(2);
        expect(w.find('.study-error').exists()).toBe(false);
        expect(w.findAll('[data-testid="notes-page-item"]')).toHaveLength(1);
    });

    test('라벨 — 제목이 있으면 제목, 없으면 본문 첫 줄(preview)', async () => {
        stubFetch(BOOKS, {
            7: okJson({ notes: [
                { id: 1, title: '3장 정리', chars: 10, preview: '무시될 첫 줄', updatedAt: AT },
                { id: 2, title: null, chars: 10, preview: '## 정규화 요약', updatedAt: AT },
            ] }),
        });
        const w = await mountAt('');

        const labels = w.findAll('.study-notes-label').map((l) => l.text());
        expect(labels).toEqual(['3장 정리', '정규화 요약']);
    });

    test('필기 0장 — 빈 안내', async () => {
        stubFetch(BOOKS, {});
        const w = await mountAt('');

        expect(w.text()).toContain('이 책의 필기가 아직 없어요.');
        expect(w.findAll('[data-testid="notes-page-item"]')).toHaveLength(0);
    });
});
