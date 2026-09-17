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

const { focusEndSpy } = vi.hoisted(() => ({ focusEndSpy: vi.fn() }));

// 본문 편집기는 얇은 textarea 대역 — 이 파일의 관심은 편집기가 아니라 자동저장의 배선이다
// (편집기 자체는 editor/RecallEditor.test.ts가 실제 Tiptap을 마운트해 잰다).
// ⚠️ 빈자리 슬롯(`empty`)을 실제 편집기와 같은 자리 조건(글이 없을 때)으로 그린다 — 대역이 슬롯을 안 그리면
// 아래 「이어 쓰기」 칩 테스트가 전부 공허하게 0개를 센다. `focusEnd`도 스파이로 노출한다.
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
              { emit, slots, expose }: {
                  emit: (e: 'update:modelValue', v: string) => void;
                  slots: Record<string, (() => unknown) | undefined>;
                  expose: (e: Record<string, unknown>) => void;
              }) {
            expose({ focusEnd: focusEndSpy });
            return () => h('div', [
                h('textarea', {
                    'data-testid': 'recall-body',
                    'aria-label': props.ariaLabel,
                    'disabled': props.disabled,
                    'value': props.modelValue,
                    'onInput': (e: Event) => emit('update:modelValue', (e.target as HTMLTextAreaElement).value),
                }),
                props.modelValue === '' && slots.empty
                    ? h('div', { 'data-testid': 'editor-empty-slot' }, slots.empty() as never)
                    : null,
            ]);
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

/** 응답을 손에 쥔 채 붙잡아 두는 왕복 — 「저장 중에 다른 장으로 갈아탄다」를 재려면 이게 있어야 한다. */
function deferred(): { promise: Promise<Response>; resolve: (r: Response) => void } {
    let resolve!: (r: Response) => void;
    const promise = new Promise<Response>((r) => { resolve = r; });
    return { promise, resolve };
}

async function mountPanel(props: Partial<Record<string, unknown>> = {},
                          rows: unknown[] = []): Promise<VueWrapper> {
    vi.mocked(fetch).mockResolvedValueOnce(okJson({ notes: rows }));
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

/** 책 이름 줄이 가리키는 책 — select를 걷은 뒤(설계 D7) 필기 책의 유일한 표시다. */
function bookOf(wrapper: VueWrapper): string | undefined {
    return wrapper.find('[data-testid="notes-book"]').attributes('data-book-id');
}

/** 「최근 필기 ▾」를 연다 — 목록 행(notes-item)은 이 팝오버 안에만 있다. */
async function openMenu(wrapper: VueWrapper): Promise<void> {
    if (wrapper.find('.study-notes-recent-menu').exists()) return;
    await wrapper.find('[data-testid="notes-recent"]').trigger('click');
}

function rowOf(id: number, title: string | null, preview = '') {
    return { id, title, chars: 1, preview, updatedAt: AT };
}

beforeEach(() => {
    focusEndSpy.mockClear();
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

    test('부모가 준 책이 필기 책이고, 첫 줄에 그 책 이름이 뜬다', async () => {
        const wrapper = await mountPanel({ defaultBookId: 9 });
        expect(bookOf(wrapper)).toBe('9');
        expect(wrapper.find('[data-testid="notes-book"]').text()).toBe('토익 보카');
        expect(String(vi.mocked(fetch).mock.calls[0][0])).toBe('/api/study/notes?bookId=9');
    });
});

// 홈에서 측정 중 「책 바꾸기」로 A→B가 되면 필기도 B로 가야 한다 — 「지금 공부하는 책: B」와 필기 select 「A」가
// 한 화면에서 갈리면 안 된다(설계 2026-09-15 결정 4). 옮기기 전에 A에 쓰던 것을 먼저 보낸다.
describe('필기 패널 — 기본 책이 바뀌면 따라간다', () => {
    test('defaultBookId 7→9: 쓰던 7의 초안을 먼저 보내고, 9로 옮겨 목록을 부른다', async () => {
        const wrapper = await mountPanel({ defaultBookId: 7 });
        await type(wrapper, '7번 책 필기');   // 디바운스 중 — 아직 안 나갔다

        vi.mocked(fetch)
            .mockResolvedValueOnce(okJson(noteOf({ id: 8, bookId: 7, body: '7번 책 필기' })))
            .mockResolvedValueOnce(okJson({ notes: [] }));
        await wrapper.setProps({ defaultBookId: 9 });
        await flushPromises();

        expect(bookOf(wrapper)).toBe('9');
        const urls = vi.mocked(fetch).mock.calls.map((c) => String(c[0]));
        const saveAt = urls.indexOf('/api/study/notes');
        const listAt = urls.indexOf('/api/study/notes?bookId=9');
        expect(saveAt).toBeGreaterThan(-1);
        expect(listAt).toBeGreaterThan(saveAt);   // flush가 목록 조회보다 먼저
        expect(posts()).toEqual([
            { url: '/api/study/notes', body: { bookId: 7, title: '', body: '7번 책 필기' } },
        ]);
    });

    test('null로 바뀌면 그대로 둔다 — 필기는 책이 필수라 빈 상태를 만들지 않는다', async () => {
        const wrapper = await mountPanel({ defaultBookId: 9 });
        await wrapper.setProps({ defaultBookId: null });
        await flushPromises();
        expect(bookOf(wrapper)).toBe('9');
    });

    // 리뷰 Important-1 — 서재 0권으로 마운트된 패널에 책이 늦게 온다(다른 탭에서 담고 돌아와 재조회).
    // onMounted는 책이 없어 초기화를 건너뛰었으므로 초안이 책 없이(bookId null) 남으면 자동저장이 **무음 스킵**된다.
    test('서재 0권으로 마운트 → 책이 늦게 오면 그 책으로 목록을 부르고, 쓴 글이 그 책에 저장된다', async () => {
        const wrapper = mount(NotesPanel, { attachTo: document.body, props: { books: [], defaultBookId: null } });
        await flushPromises();

        vi.mocked(fetch).mockResolvedValueOnce(okJson({ notes: [] }));
        await wrapper.setProps({ books: [BOOKS[1]], defaultBookId: 9 });
        await flushPromises();
        expect(vi.mocked(fetch).mock.calls.map((c) => String(c[0]))).toEqual(['/api/study/notes?bookId=9']);

        vi.mocked(fetch).mockResolvedValueOnce(okJson(noteOf({ id: 8, bookId: 9, body: '늦게 온 책' })));
        await type(wrapper, '늦게 온 책');
        await settle();
        expect(posts()).toEqual([{ url: '/api/study/notes', body: { bookId: 9, title: '', body: '늦게 온 책' } }]);
    });

    // (옛 「select로 직접 고른 경로」 테스트는 select와 함께 걷었다 — 설계 2026-09-17 D7·R2. 필기 책은 이제 prop 단일 출처다.)

    // 서재에서 지운 책 id로 옮기면 fetchNotes가 404를 맞는다.
    test('서재에 없는 id면 그대로 둔다', async () => {
        const wrapper = await mountPanel({ defaultBookId: 9 });
        await wrapper.setProps({ defaultBookId: 999 });
        await flushPromises();
        expect(bookOf(wrapper)).toBe('9');
        expect(vi.mocked(fetch).mock.calls.map((c) => String(c[0]))).not.toContain('/api/study/notes?bookId=999');
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

    test('429 — 레이트리밋에 걸린 초안은 스스로 레이트리밋을 다시 때리지 않는다', async () => {
        const wrapper = await mountPanel();
        vi.mocked(fetch).mockResolvedValueOnce(fail(429, '한 시간에 30장까지 만들 수 있어요'));

        await type(wrapper, '새 장');
        await settle();
        expect(posts()).toHaveLength(1);
        expect(wrapper.find('[data-testid="notes-status"]').text()).toContain('30장까지');

        // 초안에 id가 없어 매 편집이 또 생성 시도가 된다 — 여기가 열리면 1.5초마다 한 시간을 두드린다.
        await type(wrapper, '새 장을 더 쓴다');
        await settle(5000);
        await type(wrapper, '새 장을 더더 쓴다');
        await settle(5000);
        expect(posts()).toHaveLength(1);
    });

    test('404 — 다른 곳에서 지워진 필기는 편집할 때마다 다시 두드리지 않는다', async () => {
        const wrapper = await withNote();
        vi.mocked(fetch).mockResolvedValueOnce(fail(404, '<!DOCTYPE html><html>...</html>'));

        await type(wrapper, 'ㄱㄴ');
        await settle();
        expect(posts()).toHaveLength(2);
        expect(wrapper.find('[data-testid="notes-status"]').text()).toContain('필기');

        await type(wrapper, 'ㄱㄴㄷ');
        await settle(5000);
        expect(posts()).toHaveLength(2);
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
        const wrapper = await mountPanel({}, [rowOf(6, '다른')]);
        vi.mocked(fetch).mockResolvedValueOnce(okJson(noteOf()));
        await type(wrapper, 'ㄱ');
        await settle();

        // 아직 디바운스 중인 편집 하나를 남겨 둔 채 다른 필기를 연다.
        await type(wrapper, 'ㄱㄴ');
        vi.mocked(fetch)
            .mockResolvedValueOnce(okJson(noteOf({ revision: 1 })))
            .mockResolvedValueOnce(okJson(noteOf({ id: 6, body: '다른 필기', revision: 3 })));

        await openMenu(wrapper);
        await wrapper.findAll('[data-testid="notes-item"]').find((b) => b.text().includes('다른'))!.trigger('click');
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

describe('필기 패널 — 왕복 중에 갈아타도 남의 자리에 쓰지 않는다', () => {
    // 자동저장은 사용자가 인지할 계기가 없고 휴지통·버전도 없다 — 여기서 잘못 쓰면 글이 조용히 사라진다.
    // 네 시나리오 모두 뿌리가 하나다: 왕복이 날아가 있는 동안 편집 대상이 바뀌는데,
    // ⓐ 플러시가 그냥 반환하고 ⓑ 응답이 지금 어느 장인지 안 보고 id·revision을 되쓰고
    // ⓒ 초안의 책을 전송 시점에 읽는다.

    /** 응답 전에 붙잡아 둔 갱신 왕복 — 「저장 중」 상태를 시험대에 세운다. */
    async function inflightUpdate(wrapper: VueWrapper, body: string) {
        const pending = deferred();
        vi.mocked(fetch).mockReturnValueOnce(pending.promise);
        await type(wrapper, body);
        await vi.advanceTimersByTimeAsync(1500);
        return pending;
    }

    /** 5번 장을 하나 만들어 둔다(이후 저장은 갱신이다). */
    async function withNote(rows: unknown[] = []): Promise<VueWrapper> {
        const wrapper = await mountPanel({}, rows);
        vi.mocked(fetch).mockResolvedValueOnce(okJson(noteOf({ body: 'A', revision: 0 })));
        await type(wrapper, 'A');
        await settle();
        return wrapper;
    }

    // C-1 ① — 「＋ 새 필기」가 왕복 중에 눌리면 돌아온 응답이 **빈 초안에 앞 장의 id**를 붙였다.
    // 그 뒤의 입력은 생성이 아니라 A에 대한 갱신으로 나가 A의 본문을 지운다.
    test('저장 왕복 중에 「새 필기」를 눌러도 새 글은 생성으로 나간다', async () => {
        const wrapper = await withNote();
        const pending = await inflightUpdate(wrapper, 'A2');
        expect(posts()).toHaveLength(2);

        await wrapper.find('[data-testid="notes-new"]').trigger('click');
        await flushPromises();
        pending.resolve(okJson(noteOf({ id: 5, body: 'A2', revision: 1 }))); // 앞선 왕복이 이제야 돌아온다
        await flushPromises();

        vi.mocked(fetch).mockResolvedValueOnce(okJson(noteOf({ id: 6, body: '완전히 새 글', revision: 0 })));
        await type(wrapper, '완전히 새 글');
        await settle();

        const last = posts()[posts().length - 1];
        expect(last.url).toBe('/api/study/notes');
        expect(last.body).toEqual({ bookId: 7, title: '', body: '완전히 새 글' });
    });

    // C-1 ② — 목록에서 다른 장을 열었는데 화면만 B이고 draft.id는 A였다. 이어 쓰면 B의 글이 A에 기록된다.
    test('저장 왕복 중에 다른 필기를 열면 이어 쓴 글이 그 필기에 저장된다', async () => {
        const wrapper = await withNote([{ id: 6, title: 'B', chars: 1, updatedAt: AT }]);
        const pending = await inflightUpdate(wrapper, 'A2');

        await openMenu(wrapper);
        const rowB = wrapper.findAll('[data-testid="notes-item"]')
            .find((li) => li.text().includes('B'))!;
        vi.mocked(fetch).mockResolvedValueOnce(okJson(noteOf({ id: 6, title: 'B', body: 'B본문', revision: 4 })));
        await rowB.trigger('click');
        await flushPromises();
        pending.resolve(okJson(noteOf({ id: 5, body: 'A2', revision: 1 })));
        await flushPromises();

        vi.mocked(fetch).mockResolvedValueOnce(okJson(noteOf({ id: 6, title: 'B', body: 'B본문+', revision: 5 })));
        await type(wrapper, 'B본문+');
        await settle();

        const last = posts()[posts().length - 1];
        expect(last.url).toBe('/api/study/notes/6');
        expect(last.body).toEqual({ title: 'B', body: 'B본문+', revision: 4 });
    });

    // C-1 ③ — 지우기는 플러시하지 않는다. 앞선 왕복이 돌아와 **지운 장의 id**를 새 초안에 붙이면
    // 다음 입력이 없는 행에 대한 갱신(404)이 된다. 세대 대조가 없으면 여기가 안 막힌다.
    test('지우기 직후 앞선 저장이 돌아와도 새 초안에 지운 장의 id가 붙지 않는다', async () => {
        const wrapper = await withNote();
        const pending = await inflightUpdate(wrapper, 'A2');

        vi.stubGlobal('confirm', () => true);
        vi.mocked(fetch).mockResolvedValueOnce(okJson({}));
        await wrapper.find('[data-testid="notes-delete"]').trigger('click');
        await flushPromises();
        pending.resolve(okJson(noteOf({ id: 5, body: 'A2', revision: 1 })));
        await flushPromises();

        vi.mocked(fetch).mockResolvedValueOnce(okJson(noteOf({ id: 6, body: '새 글', revision: 0 })));
        await type(wrapper, '새 글');
        await settle();

        expect(posts()[posts().length - 1].url).toBe('/api/study/notes');
    });

    // I-1 — 왕복 중에 친 글자는 어디에도 도달하지 못했다. flush()가 즉시 반환하고, 갈아타기가
    // state를 idle로 되돌려 finally의 큐(다음 왕복)까지 끊었다.
    test('왕복 중에 친 글자가 전환 때 사라지지 않는다', async () => {
        const wrapper = await withNote();
        const pending = await inflightUpdate(wrapper, 'A2');

        await type(wrapper, 'A2A3');
        vi.mocked(fetch).mockResolvedValueOnce(okJson(noteOf({ id: 5, body: 'A2A3', revision: 2 })));
        await wrapper.find('[data-testid="notes-new"]').trigger('click');
        await flushPromises();
        pending.resolve(okJson(noteOf({ id: 5, body: 'A2', revision: 1 })));
        await flushPromises();

        expect(posts().map((p) => p.body.body)).toContain('A2A3');
    });

    // I-1의 변종 — 앞선 왕복이 **실패**로 끝나면 state가 error라 finally의 큐(dirty만 본다)가 안 돈다.
    // 플러시가 기다리기만 하고 스스로 다시 보내지 않으면, 그 사이에 친 글자가 여기서 사라진다.
    test('앞선 왕복이 5xx로 끝나도 그 사이에 친 글자는 전환 때 나간다', async () => {
        const wrapper = await withNote();
        const pending = await inflightUpdate(wrapper, 'A2');

        await type(wrapper, 'A2A3');
        vi.mocked(fetch).mockResolvedValueOnce(okJson(noteOf({ id: 5, body: 'A2A3', revision: 1 })));
        await wrapper.find('[data-testid="notes-new"]').trigger('click');
        await flushPromises();
        pending.resolve(fail(500, ''));
        await flushPromises();

        expect(posts().map((p) => p.body.body)).toContain('A2A3');
    });

    // I-2 — save()가 전송 시점에 bookId.value를 읽어, 책을 바꾸는 순간 앞 책의 초안이 **새 책에** 생겼다.
    // 설계가 필기 이동을 비목표로 못 박아 복구 수단이 없다.
    test('초안은 쓰던 그 책에 생성된다 — 책을 바꿔도 따라가지 않는다', async () => {
        const wrapper = await mountPanel();
        await type(wrapper, '정처기 필기');

        vi.mocked(fetch)
            .mockResolvedValueOnce(okJson(noteOf({ id: 8, bookId: 7, body: '정처기 필기' })))
            .mockResolvedValueOnce(okJson({ notes: [] }));
        // 책을 바꾸는 입구는 이제 부모 prop 하나다(select를 걷었다 — 설계 D7).
        await wrapper.setProps({ defaultBookId: 9 });
        await flushPromises();

        expect(posts()).toEqual([
            { url: '/api/study/notes', body: { bookId: 7, title: '', body: '정처기 필기' } },
        ]);
    });
});

// PR-3 — 목록 라벨의 근본 처방. 제목을 안 적는 것이 이 기능의 기본 사용법이라, 서버가 본문 첫 줄
// (`preview`)을 함께 싣지 않으면 화면은 <b>지금 열어 둔 장 말고 전부</b>를 「제목 없음」으로 그린다.
describe('필기 패널 — 목록 라벨', () => {
    test('제목 없는 장은 서버가 준 본문 첫 줄로 이름을 얻는다 — 마크다운 표식은 벗긴다', async () => {
        const wrapper = await mountPanel({}, [
            { id: 6, title: null, chars: 20, preview: '# 미분계수', updatedAt: AT },
            { id: 7, title: '3장 함수', chars: 10, preview: '매개변수는', updatedAt: AT },
        ]);

        await openMenu(wrapper);
        const labels = wrapper.findAll('[data-testid="notes-item"]').map((li) => li.text());
        expect(labels[0]).toContain('미분계수');
        expect(labels[0]).not.toContain('제목 없음');
        expect(labels[1]).toContain('3장 함수'); // 제목이 있으면 제목이 이긴다
    });

    test('방금 저장한 장도 목록에서 첫 줄 이름을 유지한다 — 응답으로 만든 행에도 preview가 실린다', async () => {
        const wrapper = await mountPanel();
        vi.mocked(fetch).mockResolvedValueOnce(okJson(noteOf({ id: 8, body: '# 새 장\n내용' })));
        await type(wrapper, '# 새 장\n내용');
        await settle();

        // 「새 필기」로 갈아타 <b>그 행이 더 이상 열려 있는 초안이 아니게</b> 만든다 — 안 그러면
        // 라벨이 draft.body에서 나와, 목록 행이 이름을 못 얻는 구현도 통과한다(공허한 단언).
        await wrapper.find('[data-testid="notes-new"]').trigger('click');
        await flushPromises();

        await openMenu(wrapper);
        expect(wrapper.find('[data-testid="notes-item"]').text()).toContain('새 장');
    });
});

// 이어 쓰기 칩 — 빈 새 필기일 때만 편집기 빈자리에 최근 3장(설계 2026-09-17 목표 4 · §4-2 chipsOn).
// 조건 3항(서버에 없는 장 · 제목 비었음 · 고를 것 있음)과 편집기 쪽 empty가 AND로 겹친다 — 항마다 따로 죽인다.
describe('필기 패널 — 이어 쓰기 칩', () => {
    const FOUR = [rowOf(5, '가'), rowOf(6, '나'), rowOf(8, '다'), rowOf(10, '라')];
    const chips = (w: VueWrapper) => w.findAll('[data-testid="editor-empty-slot"] [data-testid="notes-continue"]');
    const chipAll = (w: VueWrapper) => w.find('[data-testid="editor-empty-slot"] [data-testid="notes-all"]');

    test('필기 3장 → 칩 3개 + 「전체」(이 책의 필기 화면)', async () => {
        const wrapper = await mountPanel({ defaultBookId: 9 }, FOUR.slice(0, 3));
        expect(chips(wrapper).map((c) => c.text())).toEqual(['가', '나', '다']);
        expect(chipAll(wrapper).attributes('href')).toBe('/study/notes?bookId=9');
    });

    test('4장이어도 3개까지', async () => {
        expect(chips(await mountPanel({}, FOUR))).toHaveLength(3);
    });

    test('필기 0장이면 칩이 없다', async () => {
        const wrapper = await mountPanel({}, []);
        expect(wrapper.find('[data-testid="editor-empty-slot"]').exists()).toBe(false);
    });

    test('목록 500이면 칩이 없고 오류를 말한다', async () => {
        vi.mocked(fetch).mockResolvedValueOnce(fail(500, ''));
        const wrapper = mount(NotesPanel, { attachTo: document.body, props: { books: BOOKS, defaultBookId: null } });
        await flushPromises();
        expect(chips(wrapper)).toHaveLength(0);
        expect(wrapper.text()).toContain('필기 목록을 불러오지 못했어요.');
    });

    // 리뷰 M-2 — 책을 바꿔 목록을 다시 받는 동안 옛 책의 칩이 남으면, 누르는 순간 다른 책의 장이 열린다.
    test('책이 바뀌어 목록을 다시 받는 동안엔 옛 책의 칩이 없다', async () => {
        const wrapper = await mountPanel({ defaultBookId: 7 }, FOUR);
        expect(chips(wrapper)).toHaveLength(3);   // 전제 — 7의 칩이 떠 있었다

        const pending = deferred();
        vi.mocked(fetch).mockReturnValueOnce(pending.promise);
        await wrapper.setProps({ defaultBookId: 9 });
        await flushPromises();
        expect(chips(wrapper)).toHaveLength(0);

        pending.resolve(okJson({ notes: [rowOf(20, '토익')] }));
        await flushPromises();
        expect(chips(wrapper).map((c) => c.text())).toEqual(['토익']);
    });

    test('제목만 쳐도 사라진다', async () => {
        const wrapper = await mountPanel({}, FOUR);
        await wrapper.find('[data-testid="notes-title"]').setValue('3장');
        expect(chips(wrapper)).toHaveLength(0);
    });

    test('본문을 치면 사라진다', async () => {
        const wrapper = await mountPanel({}, FOUR);
        await type(wrapper, 'ㄱ');
        expect(chips(wrapper)).toHaveLength(0);
    });

    test('「＋ 새 필기」로 빈 필기가 되면 다시 뜬다', async () => {
        const wrapper = await mountPanel({}, FOUR);
        vi.mocked(fetch).mockResolvedValueOnce(okJson(noteOf({ id: 12, body: 'ㄱ' })));
        await type(wrapper, 'ㄱ');
        await settle();
        expect(chips(wrapper)).toHaveLength(0);

        await wrapper.find('[data-testid="notes-new"]').trigger('click');
        await flushPromises();
        expect(chips(wrapper)).toHaveLength(3);
    });

    // 저장돼 id가 붙은 장은 「새 필기」가 아니다 — 본문을 다 지워도 그 장을 쓰는 중이다.
    test('저장된 장에서 본문을 지워도 칩이 안 뜬다', async () => {
        const wrapper = await mountPanel({}, FOUR);
        vi.mocked(fetch).mockResolvedValueOnce(okJson(noteOf({ id: 12, body: 'ㄱ' })));
        await type(wrapper, 'ㄱ');
        await settle();
        await type(wrapper, '');
        // 전제 확인 — 본문은 비었고(편집기 쪽 조건은 켜질 자리) 이 장은 저장된 장이다(지우기 버튼 = id 있음).
        expect((wrapper.find('[data-testid="recall-body"]').element as HTMLTextAreaElement).value).toBe('');
        expect(wrapper.find('[data-testid="notes-delete"]').exists()).toBe(true);
        expect(chips(wrapper)).toHaveLength(0);
    });

    // 갈아타기는 기존 openNote 경로다 — 새 전환 경로를 만들지 않는다(설계 §2 상태기계).
    test('칩을 누르면 그 장이 열리고 캐럿이 끝으로 가며, 이어 쓴 글은 그 장의 갱신이다', async () => {
        const wrapper = await mountPanel({}, FOUR);
        vi.mocked(fetch).mockResolvedValueOnce(okJson(noteOf({ id: 5, title: '가', body: '가 본문', revision: 2 })));
        await chips(wrapper)[0].trigger('click');
        await flushPromises();

        expect(vi.mocked(fetch).mock.calls.map((c) => String(c[0]))).toContain('/api/study/notes/5');
        expect((wrapper.find('[data-testid="recall-body"]').element as HTMLTextAreaElement).value).toBe('가 본문');
        expect(focusEndSpy).toHaveBeenCalledTimes(1);

        vi.mocked(fetch).mockResolvedValueOnce(okJson(noteOf({ id: 5, title: '가', body: '가 본문+', revision: 3 })));
        await type(wrapper, '가 본문+');
        await settle();
        expect(posts()).toEqual([{ url: '/api/study/notes/5', body: { title: '가', body: '가 본문+', revision: 2 } }]);
    });
});

// 「최근 필기 ▾」 — 디스클로저 팝오버(설계 D6). 닫힘 세 경로(고르기·Esc·바깥 클릭)와 리스너 수명을 잰다.
describe('필기 패널 — 최근 필기 팝오버', () => {
    const SIX = [rowOf(5, '가'), rowOf(6, '나'), rowOf(8, '다'), rowOf(10, '라'), rowOf(11, '마'), rowOf(12, '바')];
    const trigger = (w: VueWrapper) => w.find('[data-testid="notes-recent"]');
    const menu = (w: VueWrapper) => w.find('.study-notes-recent-menu');

    test('처음엔 닫혀 있고, 누르면 5장까지 + 「전체 필기 보기」', async () => {
        const wrapper = await mountPanel({}, SIX);
        expect(trigger(wrapper).attributes('aria-expanded')).toBe('false');
        // aria-haspopup="true"는 ARIA에서 menu와 같은 뜻이다 — role="menu"를 안 쓰는 이유와 같이 붙이지 않는다(리뷰 M-4).
        expect(trigger(wrapper).attributes('aria-haspopup')).toBeUndefined();
        expect(menu(wrapper).exists()).toBe(false);

        await trigger(wrapper).trigger('click');
        expect(trigger(wrapper).attributes('aria-expanded')).toBe('true');
        expect(trigger(wrapper).attributes('aria-controls')).toBe(menu(wrapper).attributes('id'));
        expect(menu(wrapper).findAll('[data-testid="notes-item"]').map((b) => b.text().slice(0, 1))).toEqual(['가', '나', '다', '라', '마']);
        expect(menu(wrapper).find('[data-testid="notes-all"]').attributes('href')).toBe('/study/notes?bookId=7');
    });

    test('항목을 고르면 닫히고 그 장이 열린다', async () => {
        const wrapper = await mountPanel({}, SIX);
        await openMenu(wrapper);
        vi.mocked(fetch).mockResolvedValueOnce(okJson(noteOf({ id: 6, title: '나', body: '나 본문' })));
        await menu(wrapper).findAll('[data-testid="notes-item"]')[1].trigger('click');
        await flushPromises();

        expect(menu(wrapper).exists()).toBe(false);
        expect((wrapper.find('[data-testid="recall-body"]').element as HTMLTextAreaElement).value).toBe('나 본문');
        expect(focusEndSpy).toHaveBeenCalledTimes(1);
    });

    test('Esc로 닫히고 포커스가 트리거로 돌아온다', async () => {
        const wrapper = await mountPanel({}, SIX);
        await openMenu(wrapper);
        (menu(wrapper).find('[data-testid="notes-item"]').element as HTMLElement).focus();

        document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
        await wrapper.vm.$nextTick();
        expect(menu(wrapper).exists()).toBe(false);
        expect(document.activeElement).toBe(trigger(wrapper).element);
    });

    // Esc가 포커스를 옮기는 것은 포커스가 팝오버 **안**에 있을 때뿐이다(리뷰 M-4) — 안 그러면 제목을 치다 누른 Esc가 캐럿을 뺏는다.
    test('포커스가 팝오버 밖이면 Esc는 닫기만 하고 포커스를 옮기지 않는다', async () => {
        const wrapper = await mountPanel({}, SIX);
        await openMenu(wrapper);
        const title = wrapper.find('[data-testid="notes-title"]').element as HTMLInputElement;
        title.focus();

        document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
        await wrapper.vm.$nextTick();
        expect(menu(wrapper).exists()).toBe(false);
        expect(document.activeElement).toBe(title);
    });

    test('팝오버가 닫혀 있으면 Esc는 아무것도 안 한다 — 포커스 그대로', async () => {
        const wrapper = await mountPanel({}, SIX);
        const title = wrapper.find('[data-testid="notes-title"]').element as HTMLInputElement;
        title.focus();

        document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
        await wrapper.vm.$nextTick();
        expect(document.activeElement).toBe(title);
    });

    // 바깥 판정은 pointerdown이다 — iOS 사파리는 클릭할 수 없는 영역을 탭하면 document까지 click을 안 보낸다(아이패드가 주 기기).
    test('바깥을 누르면(pointerdown) 닫힌다 — 팝오버 안을 누르는 것은 아니다', async () => {
        const wrapper = await mountPanel({}, SIX);
        await openMenu(wrapper);
        menu(wrapper).element.dispatchEvent(new Event('pointerdown', { bubbles: true }));   // 양성 대조 — 안쪽엔 안 닫힌다
        await wrapper.vm.$nextTick();
        expect(menu(wrapper).exists()).toBe(true);

        document.body.dispatchEvent(new Event('pointerdown', { bubbles: true }));
        await wrapper.vm.$nextTick();
        expect(menu(wrapper).exists()).toBe(false);
    });

    test('지금 열린 장은 표시되고, 눌러도 다시 부르지 않고 닫기만 한다', async () => {
        const wrapper = await mountPanel({}, SIX);
        vi.mocked(fetch).mockResolvedValueOnce(okJson(noteOf({ id: 6, title: '나', body: '나 본문' })));
        await openMenu(wrapper);
        await menu(wrapper).findAll('[data-testid="notes-item"]')[1].trigger('click');
        await flushPromises();
        const callsBefore = vi.mocked(fetch).mock.calls.length;

        await openMenu(wrapper);
        const current = menu(wrapper).findAll('[data-testid="notes-item"]')[1];
        expect(current.classes()).toContain('is-active');
        expect(current.attributes('aria-current')).toBe('true');
        expect(menu(wrapper).findAll('[data-testid="notes-item"]')[0].attributes('aria-current')).toBeUndefined();

        await current.trigger('click');
        await flushPromises();
        expect(vi.mocked(fetch).mock.calls.length).toBe(callsBefore);
        expect(menu(wrapper).exists()).toBe(false);
    });

    test('사라질 때 document 리스너를 남기지 않는다', async () => {
        const added = vi.spyOn(document, 'addEventListener');
        const removed = vi.spyOn(document, 'removeEventListener');
        const wrapper = await mountPanel({}, SIX);
        const mine = added.mock.calls.filter(([type]) => type === 'pointerdown' || type === 'keydown');
        expect(mine.map(([type]) => type).sort()).toEqual(['keydown', 'pointerdown']);   // 양성 대조 — 리스너를 실제로 건다

        wrapper.unmount();
        for (const [type, fn] of mine) {
            expect(removed.mock.calls.some(([t, f]) => t === type && f === fn)).toBe(true);
        }
        added.mockRestore();
        removed.mockRestore();
    });
});

// `/study/notes`의 행 → `/?note=<id>` → 홈 편집기가 그 장을 연다(설계 D2). ⚠️ R1 — 마운트 경로는 초안에 책을
// 먼저 채워 watch(bookId)의 첫 확정 분기를 통과시킨다. 순서가 틀어지면 방금 연 장이 빈 초안으로 지워진다.
describe('필기 패널 — ?note= 핸드오프', () => {
    async function mountWith(initialNoteId: number, noteRes: Response, rows: unknown[] = []): Promise<VueWrapper> {
        vi.mocked(fetch).mockResolvedValueOnce(noteRes).mockResolvedValueOnce(okJson({ notes: rows }));
        const wrapper = mount(NotesPanel, {
            attachTo: document.body,
            props: { books: BOOKS, defaultBookId: 9, initialNoteId },
        });
        await flushPromises();
        return wrapper;
    }
    const urls = () => vi.mocked(fetch).mock.calls.map((c) => String(c[0]));

    // 기본 책(9)과 필기의 책(7)을 일부러 가른다 — 같으면 「필기의 책을 따랐다」와 「기본 책 그대로」가 같은 값이다.
    test('다른 책의 필기면 그 책으로 옮겨 열고, 이어 쓴 글이 그 장에 저장된다', async () => {
        const wrapper = await mountWith(5, okJson(noteOf({ id: 5, bookId: 7, title: '3장', body: '열린 글', revision: 4 })));

        expect(urls()).toEqual(['/api/study/notes/5', '/api/study/notes?bookId=7']);
        expect(bookOf(wrapper)).toBe('7');
        expect((wrapper.find('[data-testid="recall-body"]').element as HTMLTextAreaElement).value).toBe('열린 글');
        expect((wrapper.find('[data-testid="notes-title"]').element as HTMLInputElement).value).toBe('3장');

        vi.mocked(fetch).mockResolvedValueOnce(okJson(noteOf({ id: 5, bookId: 7, body: '열린 글+', revision: 5 })));
        await type(wrapper, '열린 글+');
        await settle();
        expect(posts()).toEqual([{ url: '/api/study/notes/5', body: { title: '3장', body: '열린 글+', revision: 4 } }]);
    });

    test('404면 기본 책의 빈 새 필기 + 오류 문구', async () => {
        const wrapper = await mountWith(5, fail(404, ''));

        expect(urls()).toEqual(['/api/study/notes/5', '/api/study/notes?bookId=9']);
        expect(bookOf(wrapper)).toBe('9');
        expect((wrapper.find('[data-testid="recall-body"]').element as HTMLTextAreaElement).value).toBe('');
        expect(wrapper.text()).toContain('필기를 불러오지 못했어요.');
    });

    // 리뷰 M-1 — 조회 중에 친 글자의 디바운스가 살아 있으면, 장이 열리자마자 **고치지도 않은 그 장**에 갱신이 나간다.
    test('조회 중에 쳤던 입력의 디바운스가 연 장으로 새지 않는다', async () => {
        const pending = deferred();
        vi.mocked(fetch).mockReturnValueOnce(pending.promise).mockResolvedValueOnce(okJson({ notes: [] }));
        const wrapper = mount(NotesPanel, { attachTo: document.body, props: { books: BOOKS, defaultBookId: 9, initialNoteId: 5 } });
        await flushPromises();
        await type(wrapper, '조회 중 입력');

        pending.resolve(okJson(noteOf({ id: 5, bookId: 7, body: '열린 글', revision: 4 })));
        await flushPromises();
        await settle(5000);

        expect((wrapper.find('[data-testid="recall-body"]').element as HTMLTextAreaElement).value).toBe('열린 글');   // 전제 — 장이 열렸다
        expect(posts()).toHaveLength(0);
    });

    // 리뷰 M-3 — 조회를 붙잡은 사이에 기본 책 prop이 먼저 책을 정하면, 늦게 온 장이 그 초기화를 덮지 않는다.
    // 기본 책(7)과 장의 책(9)을 가른다 — 같으면 「덮었다」와 「안 덮었다」가 같은 값이다.
    test('조회 중에 기본 책이 먼저 정해지면 늦게 온 장이 그 자리를 덮지 않는다', async () => {
        const pending = deferred();
        vi.mocked(fetch).mockReturnValueOnce(pending.promise).mockResolvedValueOnce(okJson({ notes: [] }));
        const wrapper = mount(NotesPanel, { attachTo: document.body, props: { books: BOOKS, defaultBookId: null, initialNoteId: 5 } });
        await flushPromises();
        await wrapper.setProps({ defaultBookId: 7 });
        await flushPromises();
        expect(bookOf(wrapper)).toBe('7');   // 전제 — 기본 책 watch가 먼저 초기화했다

        pending.resolve(okJson(noteOf({ id: 5, bookId: 9, body: '늦게 온 장', revision: 4 })));
        await flushPromises();

        expect(bookOf(wrapper)).toBe('7');
        expect((wrapper.find('[data-testid="recall-body"]').element as HTMLTextAreaElement).value).toBe('');
        expect(urls()).toEqual(['/api/study/notes/5', '/api/study/notes?bookId=7']);   // 목록을 두 번 부르지 않는다
        expect(wrapper.text()).not.toContain('필기를 불러오지 못했어요.');
    });

    test('서재에 없는 책의 필기면 기본 책의 빈 새 필기', async () => {
        const wrapper = await mountWith(5, okJson(noteOf({ id: 5, bookId: 99, body: '지운 책' })));

        expect(urls()).toEqual(['/api/study/notes/5', '/api/study/notes?bookId=9']);
        expect(bookOf(wrapper)).toBe('9');
        expect((wrapper.find('[data-testid="recall-body"]').element as HTMLTextAreaElement).value).toBe('');
    });
});
