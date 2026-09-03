// @vitest-environment jsdom
// AI 일정 폼 — 「미리보기가 그대로 적용되는가」와 「몇 개가 바뀌는지 화면이 말하는가」.
//
// 순수 함수로 뺄 수 없는 배선이라 마운트해서 잰다. 특히 적용 요청의 본문은 서버가 「오늘 이후 전부
// 교체」에 쓰는 값이라, 여기가 어긋나면 잘못된 일정 하나로 남은 일정이 통째로 지워진다.
import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, type VueWrapper } from '@vue/test-utils';

import PlanForm from './PlanForm.vue';

function okJson(body: object) {
    return { ok: true, status: 200, json: async () => body, text: async () => '' } as Response;
}

function errorText(status: number, body: string) {
    return { ok: false, status, json: async () => ({}), text: async () => body } as Response;
}

const DRAFT = {
    days: [
        { date: '2026-09-04', task: '1장 접근통제 p.10-30' },
        { date: '2026-09-07', task: '2장 암호학 p.31-60' },
    ],
    replaceCount: 4,
};

function mountForm(props: Partial<Record<string, unknown>> = {}): VueWrapper {
    return mount(PlanForm, {
        attachTo: document.body,
        props: {
            today: '2026-09-03',
            items: [],
            books: [{ id: 7, title: '정보보안기사 필기' }],
            remainingPlan: 3,
            ...props,
        },
    });
}

async function fillAndGenerate(wrapper: VueWrapper): Promise<void> {
    await wrapper.find('[data-testid="plan-subject"]').setValue('정보보안기사');
    await wrapper.find('[data-testid="plan-scope"]').setValue('1장 접근통제\n2장 암호학');
    await wrapper.find('[data-testid="plan-exam-date"]').setValue('2026-10-03');
    await wrapper.find('[data-testid="plan-generate"]').trigger('click');
    await vi.waitFor(() => expect(wrapper.find('[data-testid="plan-preview"]').exists()).toBe(true));
}

beforeEach(() => {
    document.body.innerHTML = '<div></div>';
    vi.stubGlobal('fetch', vi.fn());
    document.head.innerHTML = '<meta name="_csrf" content="tok">';
});

afterEach(() => {
    vi.unstubAllGlobals();
});

describe('PlanForm', () => {
    test('생성한 미리보기를 그대로 적용 요청에 싣는다 — 고른 책·과목과 함께', async () => {
        vi.mocked(fetch).mockResolvedValueOnce(okJson(DRAFT));
        const wrapper = mountForm();
        await wrapper.find('[data-testid="plan-book"]').setValue(7);
        await fillAndGenerate(wrapper);

        vi.mocked(fetch).mockResolvedValueOnce(okJson({ applied: 2, removed: 4 }));
        await wrapper.find('[data-testid="plan-apply"]').trigger('click');
        await vi.waitFor(() => expect(vi.mocked(fetch).mock.calls).toHaveLength(2));

        const [url, init] = vi.mocked(fetch).mock.calls[1] as [string, RequestInit];
        expect(url).toBe('/api/study/plan/apply');
        expect(JSON.parse(init.body as string)).toEqual({
            bookId: 7,
            // 책을 고르면 과목이 그 제목으로 채워지지만, 그 뒤에 직접 친 값이 이긴다(프리필은 힌트다)
            subject: '정보보안기사',
            days: DRAFT.days,
        });
    });

    test('적용 전에 「몇 개가 바뀌는지」를 말하고, 적용 뒤에는 서버가 준 실제 숫자를 말한다', async () => {
        vi.mocked(fetch).mockResolvedValueOnce(okJson(DRAFT));
        const wrapper = mountForm();
        await fillAndGenerate(wrapper);

        expect(wrapper.find('[data-testid="plan-replace"]').text()).toContain('4개');

        // 미리보기를 읽는 동안 일정이 늘어 실제로는 5개가 지워졌다 — 화면은 서버 값을 말해야 한다.
        vi.mocked(fetch).mockResolvedValueOnce(okJson({ applied: 2, removed: 5 }));
        await wrapper.find('[data-testid="plan-apply"]').trigger('click');
        await vi.waitFor(() => expect(wrapper.find('[data-testid="plan-notice"]').exists()).toBe(true));

        expect(wrapper.find('[data-testid="plan-notice"]').text()).toContain('5개');
        expect(wrapper.find('[data-testid="plan-preview"]').exists()).toBe(false); // 적용한 초안은 치운다
    });

    test('폼이 잘못되면 요청을 보내지 않는다 — 헛왕복으로 오늘 몫을 태우지 않는다', async () => {
        const wrapper = mountForm();
        await wrapper.find('[data-testid="plan-subject"]').setValue('   ');
        await wrapper.find('[data-testid="plan-generate"]').trigger('click');

        expect(vi.mocked(fetch)).not.toHaveBeenCalled();
        expect(wrapper.find('[data-testid="plan-error"]').text()).toBe('과목을 입력해 주세요.');
    });

    test('서버가 거절하면 그 한국어 사유가 그대로 뜬다(403 승인 필요 · 429 상한)', async () => {
        vi.mocked(fetch).mockResolvedValueOnce(errorText(403, 'AI 기능은 승인 후 쓸 수 있어요'));
        const wrapper = mountForm();
        await wrapper.find('[data-testid="plan-subject"]').setValue('정보보안기사');
        await wrapper.find('[data-testid="plan-exam-date"]').setValue('2026-10-03');
        await wrapper.find('[data-testid="plan-generate"]').trigger('click');

        await vi.waitFor(() => expect(wrapper.find('[data-testid="plan-error"]').exists()).toBe(true));
        expect(wrapper.find('[data-testid="plan-error"]').text()).toBe('AI 기능은 승인 후 쓸 수 있어요');
    });

    test('오늘 몫을 다 썼으면 버튼을 잠그고 이유를 적는다', () => {
        const wrapper = mountForm({ remainingPlan: 0 });

        expect(wrapper.find('[data-testid="plan-generate"]').attributes('disabled')).toBeDefined();
        expect(wrapper.find('[data-testid="plan-cap-spent"]').exists()).toBe(true);
    });
});
