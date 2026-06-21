// @vitest-environment jsdom
// PersonalityApp 동작 위주 테스트 — fetch mock으로 API 호출 검증.
// 캐러셀 scroll-snap·scrollBy는 jsdom 레이아웃 미지원이라 버튼 존재·핸들러 호출만 확인.
// 실 브라우저 게이트(캐러셀 시각·CSRF·LLM 경로)는 Chrome 확장으로 별도 검증(N-083/T-053).
import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount } from '@vue/test-utils';
import PersonalityApp from '../src/personality/PersonalityApp.vue';

const MOCK_PROFILE = {
    totalBooks: 10,
    finishedBooks: 5,
    readingBooks: 2,
    wantToReadBooks: 3,
    finishedRatio: 0.5,
    totalReadingSeconds: 36000,
    finishedSessionCount: 20,
    avgSessionSeconds: 1800,
    distinctAuthors: 4,
    topAuthors: [{ label: '한강', count: 3 }],
    distinctGenres: 3,
    topGenres: [{ label: '소설', count: 5 }],
};

const MOCK_ENTRY = {
    id: 101,
    narrative: '완독형 독자입니다.',
    tags: ['완독러'],
    generatedAt: '2026-06-21T10:00:00Z',
    generatedAtLabel: '2026-06-21 19:00',
    selected: true,
    stale: false,
};

const MOCK_READY: object = {
    nickname: '테스터',
    loginId: 'testid',
    view: { state: 'READY', narrative: '완독형 독자입니다.', tags: ['완독러'], profile: MOCK_PROFILE, coldStartMinBooks: 1, entries: [MOCK_ENTRY] },
    refreshRemaining: 3,
    refreshLimit: 3,
};

const MOCK_COLD_START: object = {
    nickname: '테스터',
    loginId: 'testid',
    view: { state: 'COLD_START', narrative: null, tags: [], profile: { ...MOCK_PROFILE, finishedBooks: 0 }, coldStartMinBooks: 1, entries: [] },
    refreshRemaining: 3,
    refreshLimit: 3,
};

const MOCK_FALLBACK: object = {
    nickname: '테스터',
    loginId: 'testid',
    view: { state: 'FALLBACK', narrative: null, tags: [], profile: MOCK_PROFILE, coldStartMinBooks: 1, entries: [] },
    refreshRemaining: 3,
    refreshLimit: 3,
};

function setupDom() {
    document.body.innerHTML = `
        <meta name="_csrf" content="csrf-test-token">
        <div id="personality-app" data-nickname="테스터" data-login-id="testid"></div>
    `;
}

beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ ...MOCK_READY }),
    }));
});

afterEach(() => {
    vi.unstubAllGlobals();
    document.body.innerHTML = '';
});

describe('PersonalityApp', () => {
    test('마운트 시 /api/personality 를 호출한다', async () => {
        setupDom();
        mount(PersonalityApp, { attachTo: document.body });
        await vi.waitFor(() => expect(fetch).toHaveBeenCalled());
        const url = (fetch as ReturnType<typeof vi.fn>).mock.calls[0][0] as string;
        expect(url).toContain('/api/personality');
    });

    test('READY: 캐러셀 래퍼(.personality-carousel-wrap)가 렌더된다', async () => {
        setupDom();
        const wrapper = mount(PersonalityApp, { attachTo: document.body });
        await vi.waitFor(() => expect(wrapper.find('.personality-carousel-wrap').exists()).toBe(true));
    });

    test('COLD_START: 안내 문구 보임, refresh 버튼 없음', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
            ok: true,
            json: async () => ({ ...MOCK_COLD_START }),
        }));
        setupDom();
        const wrapper = mount(PersonalityApp, { attachTo: document.body });
        await vi.waitFor(() => expect(wrapper.text()).toContain('조금 더 읽으면'));
        expect(wrapper.find('.refresh-form').exists()).toBe(false);
    });

    test('FALLBACK: 안내 문구 보임, refresh 버튼 있음', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
            ok: true,
            json: async () => ({ ...MOCK_FALLBACK }),
        }));
        setupDom();
        const wrapper = mount(PersonalityApp, { attachTo: document.body });
        await vi.waitFor(() => expect(wrapper.text()).toContain('잠시 후 다시 분석'));
        expect(wrapper.find('.refresh-form').exists()).toBe(true);
    });

    test('refresh 클릭 → POST /api/personality/refresh + X-CSRF-TOKEN 헤더', async () => {
        const refreshResult = {
            view: { state: 'READY', narrative: '새 서술.', tags: ['태그'], profile: MOCK_PROFILE, coldStartMinBooks: 1, entries: [{ ...MOCK_ENTRY, narrative: '새 서술.' }] },
            refreshRemaining: 2,
            refreshLimit: 3,
        };
        vi.stubGlobal('fetch', vi.fn()
            .mockResolvedValueOnce({ ok: true, json: async () => ({ ...MOCK_READY }) })
            .mockResolvedValueOnce({ ok: true, json: async () => ({ ...refreshResult }) }),
        );
        setupDom();
        const wrapper = mount(PersonalityApp, { attachTo: document.body });
        await vi.waitFor(() => expect(wrapper.find('.refresh-form').exists()).toBe(true));

        const refreshBtn = wrapper.find('.refresh-form button');
        await refreshBtn.trigger('click');

        await vi.waitFor(() => {
            const calls = (fetch as ReturnType<typeof vi.fn>).mock.calls;
            expect(calls.length).toBeGreaterThanOrEqual(2);
        });

        const postCall = (fetch as ReturnType<typeof vi.fn>).mock.calls[1];
        expect(postCall[0]).toContain('/api/personality/refresh');
        const opts = postCall[1] as RequestInit;
        expect((opts.headers as Record<string, string>)['X-CSRF-TOKEN']).toBe('csrf-test-token');
        expect(opts.method).toBe('POST');
    });

    test('refresh 응답으로 view 교체 · refreshRemaining 감소', async () => {
        const refreshResult = {
            view: { state: 'READY', narrative: '새 서술.', tags: [], profile: MOCK_PROFILE, coldStartMinBooks: 1, entries: [{ ...MOCK_ENTRY, narrative: '새 서술.' }] },
            refreshRemaining: 2,
            refreshLimit: 3,
        };
        vi.stubGlobal('fetch', vi.fn()
            .mockResolvedValueOnce({ ok: true, json: async () => ({ ...MOCK_READY }) })
            .mockResolvedValueOnce({ ok: true, json: async () => ({ ...refreshResult }) }),
        );
        setupDom();
        const wrapper = mount(PersonalityApp, { attachTo: document.body });
        await vi.waitFor(() => expect(wrapper.find('.refresh-form').exists()).toBe(true));

        await wrapper.find('.refresh-form button').trigger('click');

        await vi.waitFor(() => expect(wrapper.text()).toContain('2/3'));
    });

    test('refreshRemaining=0 이면 refresh 버튼이 disabled', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
            ok: true,
            json: async () => ({ ...MOCK_READY, refreshRemaining: 0 }),
        }));
        setupDom();
        const wrapper = mount(PersonalityApp, { attachTo: document.body });
        await vi.waitFor(() => expect(wrapper.find('.refresh-form button').exists()).toBe(true));
        const btn = wrapper.find('.refresh-form button').element as HTMLButtonElement;
        expect(btn.disabled).toBe(true);
    });

    test('select 클릭 → POST /api/personality/select/{id} + X-CSRF-TOKEN 헤더', async () => {
        const candidate: object = { ...MOCK_ENTRY, id: 202, selected: false, narrative: '후보 서술.' };
        const initial = {
            ...MOCK_READY,
            view: { ...(MOCK_READY as { view: object }).view, entries: [MOCK_ENTRY, candidate] },
        };
        const selectResult = {
            view: { state: 'READY', narrative: '후보 서술.', tags: [], profile: MOCK_PROFILE, coldStartMinBooks: 1, entries: [{ ...MOCK_ENTRY, selected: false }, { ...candidate, selected: true }] },
            refreshRemaining: 3,
            refreshLimit: 3,
        };
        vi.stubGlobal('fetch', vi.fn()
            .mockResolvedValueOnce({ ok: true, json: async () => initial })
            .mockResolvedValueOnce({ ok: true, json: async () => selectResult }),
        );
        setupDom();
        const wrapper = mount(PersonalityApp, { attachTo: document.body });
        await vi.waitFor(() => expect(wrapper.find('.personality-carousel-wrap').exists()).toBe(true));

        // 미선택 카드의 "이걸로 대표 선택" 버튼 클릭
        const selectBtn = wrapper.findAll('button').find(b => b.text().includes('이걸로 대표 선택'));
        expect(selectBtn).toBeDefined();
        await selectBtn!.trigger('click');

        await vi.waitFor(() => {
            const calls = (fetch as ReturnType<typeof vi.fn>).mock.calls;
            expect(calls.length).toBeGreaterThanOrEqual(2);
        });

        const postCall = (fetch as ReturnType<typeof vi.fn>).mock.calls[1];
        expect(postCall[0]).toContain('/api/personality/select/202');
        const opts = postCall[1] as RequestInit;
        expect((opts.headers as Record<string, string>)['X-CSRF-TOKEN']).toBe('csrf-test-token');
    });

    test('캐러셀 이전/다음 버튼이 존재한다(jsdom은 스크롤 동작 미검증 — 실 브라우저 게이트)', async () => {
        setupDom();
        const wrapper = mount(PersonalityApp, { attachTo: document.body });
        await vi.waitFor(() => expect(wrapper.find('.carousel-nav-prev').exists()).toBe(true));
        expect(wrapper.find('.carousel-nav-next').exists()).toBe(true);
    });
});
