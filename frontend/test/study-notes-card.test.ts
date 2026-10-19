// @vitest-environment jsdom
// 홈 필기 카드 — 공부 모드 홈의 카드는 필기 하나다(2026-09-15, 설계 2026-09-15-study-focus-lamp 결정 3).
//
// 옛 홈 카드는 [필기]/[백지노트] 탭을 담고 오늘 일정(agenda)을 1~2회 불러 필기의 기본 책을 골랐다. 이제
// 백지노트는 /study/recall로 떠났고, 필기 책은 **지금 공부하는 책**(부모가 이미 아는 값)이다 — 그래서
// 이 카드가 재는 것은 둘이다: 부모가 준 책이 필기에 닿는가, agenda 왕복이 정말 사라졌는가.
import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils';

vi.mock('../src/study/image', () => ({ shrinkForUpload: vi.fn(async (file: File) => ({ blob: new Blob([new Uint8Array([0xFF, 0xD8, 0xFF])], { type: 'image/jpeg' }), dataUrl: '', name: file.name })) }));

import StudyNotesCard from '../src/dashboard/StudyNotesCard.vue';

const BOOKS = [
    { id: 7, title: '정보처리기사 실기', author: null, coverUrl: null, isbn13: null, readCount: 0, purchaseLink: null, totalSeconds: 0 },
    { id: 9, title: '토익 보카', author: null, coverUrl: null, isbn13: null, readCount: 0, purchaseLink: null, totalSeconds: 0 },
];

const calls = () => vi.mocked(fetch).mock.calls.map(c => String(c[0]));

async function mountCard(defaultBookId: number | null = 9): Promise<VueWrapper> {
    const wrapper = mount(StudyNotesCard, { attachTo: document.body, props: { books: BOOKS, defaultBookId } });
    await flushPromises();
    await vi.waitFor(() => expect(wrapper.find('[data-testid="notes-book"]').exists()).toBe(true));
    return wrapper;
}

beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn((url: string) => Promise.resolve(
        url.includes('/api/study/notes?')
            ? { ok: true, status: 200, json: async () => ({ notes: [] }), text: async () => '' }
            : { ok: false, status: 404, json: async () => ({}), text: async () => '' })));
});

afterEach(() => {
    vi.unstubAllGlobals();
    document.body.innerHTML = '';
});

describe('홈 필기 카드', () => {
    test('pill은 「필기」다', async () => {
        expect((await mountCard()).find('.dash-pill').text()).toBe('필기');
    });

    test('공부 잉크 카드다(.dash-recall-card.is-study)', async () => {
        expect((await mountCard()).find('.dash-recall-card').classes()).toContain('is-study');
    });

    // ⚠️ 서재 첫 책(7)이 아닌 9를 쓴다 — 7이면 「prop이 닿았다」와 「그냥 첫 책」이 같은 값이다.
    test('부모가 준 책(지금 공부하는 책)이 필기의 선택이다', async () => {
        const w = await mountCard(9);
        expect(w.find('[data-testid="notes-book"]').attributes('data-book-id')).toBe('9');
        expect(calls()).toContain('/api/study/notes?bookId=9');
    });

    test('탭이 없다 — 백지노트는 이 카드에 없다', async () => {
        const w = await mountCard();
        expect(w.find('[data-testid="tab-notes"]').exists()).toBe(false);
        expect(w.find('[data-testid="recall-book"]').exists()).toBe(false);
    });

    test('오늘 일정(agenda)을 부르지 않는다', async () => {
        await mountCard();
        expect(calls().filter(u => u.includes('/api/study/agenda'))).toHaveLength(0);
        // 양성 대조 — 같은 fetch 스텁이 이 카드의 실제 왕복(필기 목록)은 잡는다.
        expect(calls().filter(u => u.includes('/api/study/notes?'))).toHaveLength(1);
    });
});

// 캐럿 중계 — DashboardApp은 카드의 focusEnd만 안다. 카드 → NotesPanel → RecallEditor로 닿아야 한다(§2-6).
// 끊기면 브라우저에선 에러 없이 캐럿만 안 온다.
describe('홈 필기 카드 — focusEnd 중계', () => {
    test('카드의 focusEnd가 편집기 포커스를 문서 끝에 둔다', async () => {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        (document as any).createRange = () => ({
            setStart: () => {}, setEnd: () => {}, commonAncestorContainer: document.body,
            getBoundingClientRect: () => ({ top: 0, bottom: 0, left: 0, right: 0, width: 0, height: 0 }),
            getClientRects: () => ({ length: 0, item: () => null, [Symbol.iterator]: function* () {} }),
        });
        const w = await mountCard(9);
        await vi.waitFor(() => expect(w.find('.ProseMirror').exists()).toBe(true));
        expect(document.activeElement).not.toBe(w.find('.ProseMirror').element);   // 음성 쌍 — 처음엔 포커스가 없다

        (w.vm as unknown as { focusEnd: () => void }).focusEnd();
        await vi.waitFor(() => expect(document.activeElement).toBe(w.find('.ProseMirror').element));
    });
});

// 필기 사진(설계 2026-10-19 T12 ①′) — 실제 NotesPanel·RecallEditor로 「빈 장 → 빈 장」 전환을 잰다. 대역으로는 못 잰다:
// 편집기는 장 사이에 재사용되고 '' → ''는 modelValue watch가 안 본다 — 세대를 watch에 둔 구현이 여기서만 빨강이다(검토 FE-1).
describe('홈 필기 카드 — 사진을 올리는 사이 새 필기', () => {
    test('REQ-16 · 빈 새 필기에 사진 세 장을 넣고 첫 장이 올라가는 사이 「새 필기」를 누르면 어느 장에도 안 들어간다 — 그 뒤 넣은 한 장은 들어간다(대조군)', async () => {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        (document as any).createRange = () => ({
            setStart: () => {}, setEnd: () => {}, commonAncestorContainer: document.body,
            getBoundingClientRect: () => ({ top: 0, bottom: 0, left: 0, right: 0, width: 0, height: 0 }),
            getClientRects: () => ({ length: 0, item: () => null, [Symbol.iterator]: function* () {} }),
        });
        let first!: (r: Response) => void;
        let uploads = 0;
        vi.stubGlobal('fetch', vi.fn((url: string, init?: RequestInit) => {
            const ok = (body: object) => ({ ok: true, status: 200, json: async () => body, text: async () => '' }) as Response;
            if (url === '/api/study/notes/images') {
                uploads += 1;
                const src = `/api/study/notes/images/${100 + uploads}`;
                return uploads === 1 ? new Promise<Response>((r) => { first = () => r(ok({ src })); }) : Promise.resolve(ok({ src }));
            }
            if (url === '/api/study/notes' && init?.method === 'POST') {
                const body = JSON.parse(String(init.body)) as { body: string };
                return Promise.resolve(ok({ id: 50, bookId: 9, title: null, body: body.body, revision: 0, updatedAt: '2026-10-19T00:00:00Z' }));
            }
            return Promise.resolve(url.includes('/api/study/notes?') ? ok({ notes: [] }) : ({ ok: false, status: 404, json: async () => ({}), text: async () => '' } as Response));
        }));
        const creates = () => vi.mocked(fetch).mock.calls
            .filter((c) => String(c[0]) === '/api/study/notes' && (c[1] as RequestInit | undefined)?.method === 'POST')
            .map((c) => (JSON.parse(String((c[1] as RequestInit).body)) as { body: string }).body);
        const pick = async (w: VueWrapper, n: number) => {
            const input = w.find('[data-testid="editor-image-input"]');
            const files = Array.from({ length: n }, (_, i) => new File(['x'], `${i}.jpg`, { type: 'image/jpeg' }));
            Object.defineProperty(input.element, 'files', { value: files, configurable: true });
            await input.trigger('change');
        };

        const w = await mountCard(9);
        await vi.waitFor(() => expect(w.find('.ProseMirror').exists()).toBe(true));
        await pick(w, 3);
        await vi.waitFor(() => expect(uploads).toBe(1));
        await w.find('[data-testid="notes-new"]').trigger('click');
        await flushPromises();
        first(undefined as unknown as Response);
        await vi.waitFor(() => expect(w.find('[data-testid="editor-image-status"]').text()).toContain('필기가 바뀌어 넣지 않았어요'));
        await new Promise((r) => setTimeout(r, 1700));   // 디바운스(1.5초)를 넘겨도
        expect(uploads).toBe(1);        // 쌓여 있던 2·3번째 장은 올리지도 않는다
        expect(creates()).toEqual([]);  // 빈 장은 저장하지 않는다 — 어느 장에도 사진이 안 들어갔다

        await pick(w, 1);
        await vi.waitFor(() => expect(creates()).toHaveLength(1), { timeout: 4000 });
        const saved = creates()[0];
        expect(saved.match(/!\[\]\(\/api\/study\/notes\/images\/\d+\)/g)).toEqual(['![](/api/study/notes/images/102)']);
    }, 10_000);
});
