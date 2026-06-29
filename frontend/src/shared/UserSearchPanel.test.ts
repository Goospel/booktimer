// @vitest-environment jsdom
// UserSearchPanel 동작 테스트 — fetch mock으로 추천 분리 동작 검증.
// 핵심: q 있는 검색 응답이 진입 때 받은 추천을 덮어쓰지 않아야 한다.
import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount } from '@vue/test-utils';
import UserSearchPanel from './UserSearchPanel.vue';

const INIT_RECS = [
    { loginId: 'recuser1', nickname: '추천1', publicBookCount: 3, following: false, self: false, reasons: ['나를 팔로우함'] },
];

function makeResp(overrides: object) {
    return {
        ok: true,
        json: async () => ({ q: null, results: [], recommendations: [], myLoginId: 'testuser', rateLimited: false, ...overrides }),
    } as Response;
}

beforeEach(() => {
    document.body.innerHTML = '<div></div>';
    vi.stubGlobal('fetch', vi.fn());
});

afterEach(() => {
    vi.unstubAllGlobals();
    document.body.innerHTML = '';
});

describe('UserSearchPanel', () => {
    test('진입(q="") fetch 응답의 recommendations가 화면에 반영된다', async () => {
        vi.mocked(fetch).mockResolvedValue(makeResp({ recommendations: INIT_RECS }));

        const wrapper = mount(UserSearchPanel, { attachTo: document.body });
        await vi.waitFor(() => {
            expect(wrapper.text()).toContain('추천1');
        });
    });

    test('검색(q 있음) fetch 후에도 진입 때 받은 recommendations가 유지된다(미덮어씀)', async () => {
        vi.mocked(fetch)
            // 진입(q='') → 추천 있음
            .mockResolvedValueOnce(makeResp({ recommendations: INIT_RECS }))
            // 검색(q='ab') → 서버는 빈 추천 반환(q 있을 땐 서버가 빈 배열)
            .mockResolvedValueOnce(makeResp({ q: 'ab', recommendations: [] }));

        const wrapper = mount(UserSearchPanel, { attachTo: document.body });
        // 진입 추천 로딩 대기
        await vi.waitFor(() => expect(wrapper.text()).toContain('추천1'));

        // 검색어 입력 + form submit
        await wrapper.find('input').setValue('ab');
        await wrapper.find('form').trigger('submit');

        // 2번째 fetch 완료 대기
        await vi.waitFor(() => expect(vi.mocked(fetch)).toHaveBeenCalledTimes(2));

        // 추천이 빈 배열로 덮어써지지 않아야 함
        expect(wrapper.text()).toContain('추천1');
    });
});
