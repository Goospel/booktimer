// @vitest-environment jsdom
// SearchApp 동작 위주 테스트 — fetch mock으로 API 호출 검증.
// 브리틀한 정확 문자열 단언은 피함; 동작·호출 여부를 확인한다.
// 실 브라우저 게이트(실제 렌더링·CSRF·로드순서)는 Chrome 확장으로 별도 검증(N-083/T-053).
import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount } from '@vue/test-utils';
import SearchApp from '../src/search/SearchApp.vue';

const MOCK_RESPONSE = {
    q: null,
    results: [],
    recommendations: [
        { loginId: 'recuser1', nickname: '추천1', publicBookCount: 3, following: false, self: false },
    ],
    myLoginId: 'testuser',
    rateLimited: false,
};

function setupDom(initialQ = '', myLoginId = 'testuser') {
    document.body.innerHTML = `
        <meta name="_csrf" content="csrf-token-test">
        <div id="search-app"
             data-initial-q="${initialQ}"
             data-my-login-id="${myLoginId}">
        </div>
    `;
}

beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ ...MOCK_RESPONSE }),
    }));
});

afterEach(() => {
    vi.unstubAllGlobals();
    document.body.innerHTML = '';
});

describe('SearchApp', () => {
    test('마운트 시 /api/search 를 호출한다', async () => {
        setupDom();
        mount(SearchApp, { attachTo: document.body });
        await vi.waitFor(() => expect(fetch).toHaveBeenCalled());
        const url = (fetch as ReturnType<typeof vi.fn>).mock.calls[0][0] as string;
        expect(url).toContain('/api/search');
    });

    test('initialQ 있을 때 자동으로 q 파라미터로 조회한다', async () => {
        setupDom('abc');
        mount(SearchApp, { attachTo: document.body });
        await vi.waitFor(() => expect(fetch).toHaveBeenCalled());
        const url = (fetch as ReturnType<typeof vi.fn>).mock.calls[0][0] as string;
        expect(url).toContain('q=abc');
    });

    test('추천 결과가 있으면 렌더링된다', async () => {
        setupDom();
        const wrapper = mount(SearchApp, { attachTo: document.body });
        await vi.waitFor(() => {
            expect(wrapper.text()).toContain('추천1');
        });
    });

    test('rateLimited=true 응답 시 레이트리밋 안내가 노출된다', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
            ok: true,
            json: async () => ({ ...MOCK_RESPONSE, rateLimited: true, results: [] }),
        }));
        setupDom('ab');
        const wrapper = mount(SearchApp, { attachTo: document.body });
        await vi.waitFor(() => {
            expect(wrapper.text()).toContain('너무 잦습니다');
        });
    });

    test('팔로우 버튼 클릭 시 POST /api/follow 를 X-CSRF-TOKEN 헤더와 함께 호출한다', async () => {
        vi.stubGlobal('fetch', vi.fn()
            // 첫 번째 호출: GET /api/search (마운트 시 초기 로딩)
            .mockResolvedValueOnce({
                ok: true,
                json: async () => ({
                    ...MOCK_RESPONSE,
                    results: [
                        { loginId: 'tgtuser', nickname: '대상', publicBookCount: 1, following: false, self: false },
                    ],
                }),
            })
            // 두 번째 호출: POST /api/follow
            .mockResolvedValueOnce({
                ok: true,
                json: async () => ({ following: true }),
            }),
        );

        setupDom('tg');
        const wrapper = mount(SearchApp, { attachTo: document.body });
        // 검색 결과 렌더링 대기
        await vi.waitFor(() => expect(wrapper.text()).toContain('대상'));

        // 팔로우 버튼 클릭
        const followBtn = wrapper.findAll('button').find(b => b.text() === '팔로우');
        expect(followBtn).toBeDefined();
        await followBtn!.trigger('click');

        await vi.waitFor(() => {
            const calls = (fetch as ReturnType<typeof vi.fn>).mock.calls;
            expect(calls.length).toBeGreaterThanOrEqual(2);
        });

        const postCall = (fetch as ReturnType<typeof vi.fn>).mock.calls[1];
        expect(postCall[0]).toContain('/api/follow');
        const opts = postCall[1] as RequestInit;
        expect((opts.headers as Record<string, string>)['X-CSRF-TOKEN']).toBe('csrf-token-test');
    });
});
