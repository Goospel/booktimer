// @vitest-environment jsdom
// ProfileApp 내 책방 검색 흡수 — 탐색(/search 사용자검색)을 내 책방(self) 상단으로 흡수.
// 핵심 불변식: 검색 패널은 self(내 책방)에서만 뜨고, 남의 책방엔 새지 않는다.
// 동작·노출 여부 위주(브리틀한 정확 문자열 회피). 실 렌더/로드순서는 크롬 확장 별도 게이트.
import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount } from '@vue/test-utils';
import ProfileApp from '../src/profile/ProfileApp.vue';

function profileJson(self: boolean) {
    return {
        loginId: 'owner', nickname: '주인',
        followerCount: 0, followingCount: 0,
        following: false, self,
        personality: null, personalityTags: [],
        books: [], coupangEnabled: false,
    };
}
const SEARCH_JSON = { q: null, results: [], recommendations: [], myLoginId: 'me', rateLimited: false };

function setupDom(loginId = 'owner', myLoginId = 'me') {
    document.body.innerHTML = `
        <meta name="_csrf" content="csrf-token-test">
        <div id="profile-app" data-login-id="${loginId}" data-my-login-id="${myLoginId}"></div>
    `;
}

function mockFetch(self: boolean) {
    vi.stubGlobal('fetch', vi.fn().mockImplementation((url: string) => {
        if (url.includes('/api/search')) {
            return Promise.resolve({ ok: true, json: async () => ({ ...SEARCH_JSON }) });
        }
        if (url.includes('/api/profile/books')) {
            return Promise.resolve({ ok: true, json: async () => ({ books: [] }) });
        }
        // /api/profile (and /api/profile/personality-tag) fallthrough
        return Promise.resolve({ ok: true, status: 200, json: async () => profileJson(self) });
    }));
}

function searchCalled(): boolean {
    const calls = (fetch as ReturnType<typeof vi.fn>).mock.calls;
    return calls.some((c) => String(c[0]).includes('/api/search'));
}

beforeEach(() => {
    // ProfileApp onMounted가 matchMedia 구독 — jsdom엔 없어 스텁 필요
    vi.stubGlobal('matchMedia', vi.fn().mockReturnValue({
        matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn(),
    }));
});
afterEach(() => {
    vi.unstubAllGlobals();
    document.body.innerHTML = '';
});

describe('ProfileApp 내 책방 검색 흡수', () => {
    test('self=true(내 책방): 다른 책방 찾기 검색 패널이 보인다', async () => {
        setupDom();
        mockFetch(true);
        const wrapper = mount(ProfileApp, { attachTo: document.body });

        await vi.waitFor(() => expect(wrapper.text()).toContain('다른 책방 찾기'));
        // 사용자 검색 입력칸이 존재
        expect(wrapper.find('input[type="text"]').exists()).toBe(true);
        // 패널이 /api/search 를 호출(추천/검색 로드)
        await vi.waitFor(() => expect(searchCalled()).toBe(true));
    });

    test('self=false(남의 책방): 검색 패널이 새지 않는다', async () => {
        setupDom('other', 'me');
        mockFetch(false);
        const wrapper = mount(ProfileApp, { attachTo: document.body });

        // 헤더가 렌더될 때까지 대기(프로필 로드 완료 신호)
        await vi.waitFor(() => expect(wrapper.text()).toContain('주인'));
        expect(wrapper.text()).not.toContain('다른 책방 찾기');
        expect(wrapper.find('input[type="text"]').exists()).toBe(false);
        // 남의 책방에선 /api/search 를 아예 호출하지 않는다
        expect(searchCalled()).toBe(false);
    });
});
