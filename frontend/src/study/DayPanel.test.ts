// @vitest-environment jsdom
// 하루 패널의 [필기]/[백지노트] 탭.
//
// 탭은 `RecallPanel`이 소유한다(2026-09-11 번복) — 여기서 재는 것은 그 탭이 `DayPanel` 합성을
// 통과해 도는가, 즉 **/study 회귀 가드**다. 옛 규칙은 정반대였다: 「탭을 `RecallPanel`에 넣으면
// 홈(`dashboard/RecallCard.vue`)에 샌다」. 그 샘이 이제 의도다 — 홈이 타이머가 도는 화면이라
// 거기서 필기에 못 닿으면 「공부 도중에 그때그때」라는 기능의 요구가 반쯤 죽는다.
// 두 패널이 동시에 살아 있으면 Tiptap 편집기가 둘 마운트되는 제약은 그대로다(설계 U-4의 `.ProseMirror` 1개).
import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils';
import { h } from 'vue';

import DayPanel from './DayPanel.vue';

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
        setup: () => () => h('textarea', { 'data-testid': 'recall-body' }),
    },
}));

const BOOKS = [{ id: 7, title: '정보처리기사 실기' }, { id: 9, title: '토익 보카' }];

function notFound() {
    return { ok: false, status: 404, text: async () => '', json: async () => ({}) } as Response;
}

function okJson(body: object) {
    return { ok: true, status: 200, json: async () => body, text: async () => '' } as Response;
}

async function mountDay(props: Partial<Record<string, unknown>> = {}): Promise<VueWrapper> {
    const wrapper = mount(DayPanel, {
        attachTo: document.body,
        props: {
            date: '2026-09-10',
            today: '2026-09-10',
            items: [{ id: 1, date: '2026-09-10', bookId: 9, subject: '토익', task: 'p.30-50' }],
            books: BOOKS,
            aiAccess: 'APPROVED',
            aiAccessAt: null,
            aiEnabled: true,
            aiBusy: false,
            remainingAnalyze: 1,
            remainingTranscribe: 3,
            remainingPlan: 1,
            hasYesterdayQuestions: false,
            ...props,
        },
    });
    await flushPromises();
    return wrapper;
}

beforeEach(() => {
    document.body.innerHTML = '<div></div>';
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(notFound()));
    document.head.innerHTML = '<meta name="_csrf" content="tok">';
});

afterEach(() => {
    vi.unstubAllGlobals();
    document.body.innerHTML = '';
    document.head.innerHTML = '';
});

describe('하루 패널 — 필기 / 백지노트 탭', () => {
    test('기본은 백지노트이고 필기 패널은 뜨지 않는다', async () => {
        const wrapper = await mountDay();

        expect(wrapper.find('[data-testid="recall-book"]').exists()).toBe(true);
        expect(wrapper.find('[data-testid="notes-book"]').exists()).toBe(false);
        expect(wrapper.findAll('[data-testid="recall-body"]')).toHaveLength(1);
    });

    test('[필기]를 누르면 필기 패널로 갈리고 편집기는 여전히 하나다', async () => {
        const wrapper = await mountDay();
        vi.mocked(fetch).mockResolvedValue(okJson({ notes: [] }));

        await wrapper.find('[data-testid="tab-notes"]').trigger('click');
        await flushPromises();

        expect(wrapper.find('[data-testid="notes-book"]').exists()).toBe(true);
        expect(wrapper.find('[data-testid="recall-book"]').exists()).toBe(false);
        // 두 패널이 겹쳐 있으면 Tiptap이 둘 마운트된다 — 그러면 안 된다.
        expect(wrapper.findAll('[data-testid="recall-body"]')).toHaveLength(1);
    });

    test('그날 일정의 책이 필기 패널의 기본 선택으로 내려간다', async () => {
        const wrapper = await mountDay();
        vi.mocked(fetch).mockResolvedValue(okJson({ notes: [] }));

        await wrapper.find('[data-testid="tab-notes"]').trigger('click');
        await flushPromises();

        expect((wrapper.find('[data-testid="notes-book"]').element as HTMLSelectElement).value).toBe('9');
    });

    // role=tab만 붙이고 패널을 연결하지 않으면 스크린리더에 「탭이라는데 여는 곳이 없다」로 읽힌다.
    test('탭이 자기 패널을 가리키고, 그 패널이 지금 탭의 이름을 달고 있다', async () => {
        const wrapper = await mountDay();
        vi.mocked(fetch).mockResolvedValue(okJson({ notes: [] }));

        const target = wrapper.find('[data-testid="tab-notes"]').attributes('aria-controls');
        expect(target).toBeTruthy();
        const panel = wrapper.find(`#${target}`);
        expect(panel.attributes('role')).toBe('tabpanel');

        await wrapper.find('[data-testid="tab-notes"]').trigger('click');
        await flushPromises();
        expect(wrapper.find(`#${target}`).attributes('aria-labelledby'))
            .toBe(wrapper.find('[data-testid="tab-notes"]').attributes('id'));
    });

    test('날짜를 옮겨도 고른 탭은 그대로다', async () => {
        const wrapper = await mountDay();
        vi.mocked(fetch).mockResolvedValue(okJson({ notes: [] }));
        await wrapper.find('[data-testid="tab-notes"]').trigger('click');
        await flushPromises();

        await wrapper.setProps({ date: '2026-09-11' });
        await flushPromises();

        expect(wrapper.find('[data-testid="notes-book"]').exists()).toBe(true);
    });
});
