// @vitest-environment jsdom
// 필기 패널 — 자동저장의 배선. 「언제 보내는가 / 무엇을 싣는가 / 실패를 어떻게 가르는가」.
//
// 순수 상태기계(notes.test.ts)가 답하지 못하는 절반이다: 디바운스가 실제로 묶는지, `revision`이
// 요청에 <b>숫자로</b> 실리는지(빠지면 서버가 500 + HTML을 준다), 400 뒤에 왕복이 늘지 않는지는
// 컴포넌트 ↔ fetch의 연결이 곧 규칙이라 마운트해서 잰다.
import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils';
import { h } from 'vue';

import NotesPanel from './NotesPanel.vue';

// 본문 편집기는 얇은 textarea 대역 — 이 파일의 관심은 편집기가 아니라 자동저장의 배선이다
// (편집기 자체는 editor/RecallEditor.test.ts가 실제 Tiptap을 마운트해 잰다).
vi.mock('./editor/RecallEditor.vue', () => ({
    default: {
        name: 'RecallEditorStub',
        props: {
            modelValue: { type: String, default: '' },
            placeholder: { type: String, default: '' },
            ariaLabel: { type: String, default: '' },
            disabled: Boolean,
        },
        emits: ['update:modelValue'],
        setup(props: { modelValue: string; disabled: boolean; ariaLabel: string },
              { emit }: { emit: (e: 'update:modelValue', v: string) => void }) {
            return () => h('textarea', {
                'data-testid': 'recall-body',
                'aria-label': props.ariaLabel,
                'disabled': props.disabled,
                'value': props.modelValue,
                'onInput': (e: Event) => emit('update:modelValue', (e.target as HTMLTextAreaElement).value),
            });
        },
    },
}));

const BOOKS = [
    { id: 7, title: '정보처리기사 실기' },
    { id: 9, title: '토익 보카' },
];

const AT = new Date(2026, 8, 10, 14, 32).toISOString();

function okJson(body: object) {
    return { ok: true, status: 200, json: async () => body, text: async () => '' } as Response;
}

function fail(status: number, text: string) {
    return { ok: false, status, json: async () => ({}), text: async () => text } as Response;
}

function noteOf(over: Partial<Record<string, unknown>> = {}) {
    return { id: 5, bookId: 7, title: null, body: 'ㄱ', revision: 0, updatedAt: AT, ...over };
}

/** 저장 왕복만 센다 — 목록·본문 조회(GET)는 이 파일의 관심이 아니다. */
function posts(): { url: string; body: Record<string, unknown> }[] {
    return vi.mocked(fetch).mock.calls
        .filter((call) => (call[1] as RequestInit | undefined)?.method === 'POST')
        .map((call) => ({
            url: String(call[0]),
            body: JSON.parse(String((call[1] as RequestInit).body ?? '{}')) as Record<string, unknown>,
        }));
}

async function mountPanel(props: Partial<Record<string, unknown>> = {}): Promise<VueWrapper> {
    vi.mocked(fetch).mockResolvedValueOnce(okJson({ notes: [] }));
    const wrapper = mount(NotesPanel, {
        attachTo: document.body,
        props: { books: BOOKS, defaultBookId: null, ...props },
    });
    await flushPromises();
    return wrapper;
}

/** 한 글자 치는 것과 같다 — v-model이 dirty를 켜고 디바운스가 걸린다. */
async function type(wrapper: VueWrapper, text: string): Promise<void> {
    await wrapper.find('[data-testid="recall-body"]').setValue(text);
}

/** 디바운스(1.5초)를 넘기고 왕복까지 끝낸다. */
async function settle(ms = 1500): Promise<void> {
    await vi.advanceTimersByTimeAsync(ms);
    await flushPromises();
}

beforeEach(() => {
    vi.useFakeTimers();
    document.body.innerHTML = '<div></div>';
    vi.stubGlobal('fetch', vi.fn());
    document.head.innerHTML = '<meta name="_csrf" content="tok">';
});

afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
    document.body.innerHTML = '';
    document.head.innerHTML = '';
});

describe('필기 패널 — 책이 있어야 쓴다', () => {
    test('공부 서재가 비면 편집기 대신 안내와 서재 링크가 뜨고, 왕복이 없다', async () => {
        const wrapper = mount(NotesPanel, { props: { books: [], defaultBookId: null } });
        await flushPromises();

        expect(wrapper.find('[data-testid="recall-body"]').exists()).toBe(false);
        expect(wrapper.find('a[href="/study/books"]').exists()).toBe(true);
        expect(fetch).not.toHaveBeenCalled();
    });

    test('그날 일정의 책이 기본 선택이다', async () => {
        const wrapper = await mountPanel({ defaultBookId: 9 });
        expect((wrapper.find('[data-testid="notes-book"]').element as HTMLSelectElement).value).toBe('9');
        expect(String(vi.mocked(fetch).mock.calls[0][0])).toBe('/api/study/notes?bookId=9');
    });
});

describe('필기 패널 — 자동저장', () => {
    test('빈 초안은 서버에 행을 만들지 않는다', async () => {
        const wrapper = await mountPanel();
        await wrapper.find('[data-testid="notes-new"]').trigger('click');
        await type(wrapper, '   \n  ');
        await settle(5000);

        expect(posts()).toHaveLength(0);
    });

    test('디바운스가 연속 입력을 한 번으로 묶고, 첫 저장은 생성이다', async () => {
        const wrapper = await mountPanel();
        vi.mocked(fetch).mockResolvedValueOnce(okJson(noteOf({ body: '미분계수' })));

        await type(wrapper, '미');
        await vi.advanceTimersByTimeAsync(1000);
        await type(wrapper, '미분');
        await vi.advanceTimersByTimeAsync(1000);
        await type(wrapper, '미분계수');
        expect(posts()).toHaveLength(0); // 아직 1.5초가 안 지났다

        await settle();
        expect(posts()).toEqual([
            { url: '/api/study/notes', body: { bookId: 7, title: '', body: '미분계수' } },
        ]);
        expect(wrapper.find('[data-testid="notes-status"]').text()).toContain('14:32');
    });

    test('두 번째 저장부터는 갱신이고 revision이 숫자로 실린다', async () => {
        const wrapper = await mountPanel();
        vi.mocked(fetch).mockResolvedValueOnce(okJson(noteOf({ revision: 0 })));
        await type(wrapper, 'ㄱ');
        await settle();

        vi.mocked(fetch).mockResolvedValueOnce(okJson(noteOf({ revision: 1, body: 'ㄱㄴ' })));
        await type(wrapper, 'ㄱㄴ');
        await settle();

        const second = posts()[1];
        expect(second.url).toBe('/api/study/notes/5');
        // 빠지거나 null이면 서버 `int`가 못 받아 500 + HTML 본문이 온다 — 자동저장이 통째로 죽는 자리.
        expect(typeof second.body.revision).toBe('number');
        expect(second.body).toEqual({ title: '', body: 'ㄱㄴ', revision: 0 });

        // 세 번째는 서버가 준 새 판 번호(1)를 되싣는다 — 손으로 세지 않는다.
        vi.mocked(fetch).mockResolvedValueOnce(okJson(noteOf({ revision: 2 })));
        await type(wrapper, 'ㄱㄴㄷ');
        await settle();
        expect(posts()[2].body.revision).toBe(1);
    });
});

describe('필기 패널 — 실패는 셋으로 갈린다', () => {
    async function withNote(): Promise<VueWrapper> {
        const wrapper = await mountPanel();
        vi.mocked(fetch).mockResolvedValueOnce(okJson(noteOf()));
        await type(wrapper, 'ㄱ');
        await settle();
        return wrapper;
    }

    test('400 — 서버가 준 평문을 그대로 띄우고, 더 쳐도 다시 보내지 않는다', async () => {
        const wrapper = await withNote();
        vi.mocked(fetch).mockResolvedValueOnce(fail(400, '쓴 내용은 8000자까지 쓸 수 있어요'));

        await type(wrapper, 'ㄱ'.repeat(8001));
        await settle();
        expect(posts()).toHaveLength(2);
        expect(wrapper.find('[data-testid="notes-status"]').text()).toContain('쓴 내용은 8000자까지 쓸 수 있어요');

        // 여기가 이 테스트의 요점 — 400은 사용자가 줄이기 전엔 영원히 400이라 재시도가 무한 왕복이 된다.
        await type(wrapper, 'ㄱ'.repeat(8100));
        await settle(5000);
        await type(wrapper, 'ㄱ'.repeat(8200));
        await settle(5000);
        expect(posts()).toHaveLength(2);
        // 편집은 계속 된다 — 줄여야 고칠 수 있으므로 잠그면 안 된다.
        expect(wrapper.find('[data-testid="recall-body"]').attributes('disabled')).toBeUndefined();
    });

    test('400 뒤에 글을 줄이면 자동저장이 되살아난다', async () => {
        const wrapper = await withNote();
        vi.mocked(fetch).mockResolvedValueOnce(fail(400, '쓴 내용은 8000자까지 쓸 수 있어요'));
        await type(wrapper, 'ㄱ'.repeat(8001));
        await settle();

        vi.mocked(fetch).mockResolvedValueOnce(okJson(noteOf({ revision: 1 })));
        await type(wrapper, '줄였다');
        await settle();

        expect(posts()).toHaveLength(3);
        expect(posts()[2].body.body).toBe('줄였다');
    });

    test('409 — 자동저장이 멈추고 편집기가 잠긴다', async () => {
        const wrapper = await withNote();
        vi.mocked(fetch).mockResolvedValueOnce(fail(409, '다른 곳에서 고쳐진 필기예요 — 새로고침한 뒤 이어서 써 주세요'));

        await type(wrapper, 'ㄱㄴ');
        await settle();
        expect(wrapper.find('[data-testid="notes-status"]').text()).toContain('다른 곳에서 고쳐진');
        expect(wrapper.find('[data-testid="notes-reload"]').exists()).toBe(true);
        expect(wrapper.find('[data-testid="recall-body"]').attributes('disabled')).toBeDefined();

        await type(wrapper, 'ㄱㄴㄷ');
        await settle(5000);
        expect(posts()).toHaveLength(2); // 더 두드리지 않는다 — 두드리면 남의 판을 덮는다
    });

    test('5xx — 문구를 띄우고 본문을 지키되, 다음 입력에 재시도한다', async () => {
        const wrapper = await withNote();
        vi.mocked(fetch).mockResolvedValueOnce(fail(500, ''));

        await type(wrapper, 'ㄱㄴ');
        await settle();
        expect(wrapper.find('[data-testid="notes-status"]').text()).toContain('저장하지 못했어요');
        expect((wrapper.find('[data-testid="recall-body"]').element as HTMLTextAreaElement).value).toBe('ㄱㄴ');

        vi.mocked(fetch).mockResolvedValueOnce(okJson(noteOf({ revision: 1 })));
        await type(wrapper, 'ㄱㄴㄷ');
        await settle();
        expect(posts()).toHaveLength(3);
        expect(wrapper.find('[data-testid="notes-status"]').text()).toContain('저장됨');
    });
});

describe('필기 패널 — 즉시 플러시', () => {
    test('다른 필기를 열면 디바운스를 기다리지 않고 먼저 저장한다', async () => {
        const wrapper = await mountPanel();
        vi.mocked(fetch).mockResolvedValueOnce(okJson(noteOf()));
        await type(wrapper, 'ㄱ');
        await settle();

        // 아직 디바운스 중인 편집 하나를 남겨 둔 채 다른 필기를 연다.
        await type(wrapper, 'ㄱㄴ');
        vi.mocked(fetch)
            .mockResolvedValueOnce(okJson(noteOf({ revision: 1 })))
            .mockResolvedValueOnce(okJson(noteOf({ id: 6, body: '다른 필기', revision: 3 })));

        await wrapper.findAll('[data-testid="notes-item"]')[0].trigger('click');
        await flushPromises();

        expect(posts()).toHaveLength(2);
        expect(posts()[1].body.body).toBe('ㄱㄴ');
        expect((wrapper.find('[data-testid="recall-body"]').element as HTMLTextAreaElement).value).toBe('다른 필기');
    });

    test('패널이 사라질 때(탭 전환) 남은 편집을 마저 보낸다', async () => {
        const wrapper = await mountPanel();
        vi.mocked(fetch).mockResolvedValueOnce(okJson(noteOf()));
        await type(wrapper, 'ㄱ');
        await settle();

        await type(wrapper, 'ㄱㄴ');
        vi.mocked(fetch).mockResolvedValueOnce(okJson(noteOf({ revision: 1 })));
        wrapper.unmount();
        await flushPromises();

        expect(posts()).toHaveLength(2);
        expect(posts()[1].body.body).toBe('ㄱㄴ');
    });
});
