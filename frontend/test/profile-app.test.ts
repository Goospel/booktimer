// @vitest-environment jsdom
// ProfileApp 내 책방 검색 진입점 — 탐색(/search 사용자검색)을 책방 self 상단의 '슬림 진입 링크'로.
// 핵심 불변식: 검색 진입점(→/search)은 self(내 책방)에서만 뜨고, 남의 책방엔 새지 않는다.
//   + 책방은 진입점만 소유 — 무거운 검색 본체/추천은 /search가 가짐 → 책방에서 /api/search 호출 0.
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

// /api/search 도 모킹은 해두되(혹시 호출되면 가짜 응답), 책방은 이를 호출하지 않아야 한다.
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
    window.history.pushState({}, '', '/');   // URL(tab/status) 복원 — 다른 테스트 onMounted 파싱 오염 방지
});

describe('ProfileApp 내 책방 검색 진입점', () => {
    test('self=true(내 책방): /search로 가는 검색 진입 링크가 뜨고, 책방에서 라이브 검색은 안 한다', async () => {
        setupDom();
        mockFetch(true);
        const wrapper = mount(ProfileApp, { attachTo: document.body });

        // 진입점은 profile 로드 후 self일 때만 렌더 → /search 링크가 등장
        await vi.waitFor(() => expect(wrapper.find('a[href="/search"]').exists()).toBe(true));
        expect(wrapper.find('a[href="/search"]').text()).toContain('다른 책방 찾기');
        // 책방은 진입점만 — 무거운 검색 패널(입력칸)이 책방에 없다
        expect(wrapper.find('input[type="text"]').exists()).toBe(false);
        // 라이브 검색 본체가 사라져 책방에선 /api/search 를 호출하지 않는다(회귀 가드)
        expect(searchCalled()).toBe(false);
    });

    test('self=false(남의 책방): 검색 진입점이 새지 않는다', async () => {
        setupDom('other', 'me');
        mockFetch(false);
        const wrapper = mount(ProfileApp, { attachTo: document.body });

        // 헤더가 렌더될 때까지 대기(프로필 로드 완료 신호)
        await vi.waitFor(() => expect(wrapper.text()).toContain('주인'));
        expect(wrapper.find('a[href="/search"]').exists()).toBe(false);
        expect(wrapper.text()).not.toContain('다른 책방 찾기');
        expect(searchCalled()).toBe(false);
    });
});

describe('ProfileApp 신고 모달', () => {
    test('other: 기존 details(.shop-report) 신고박스가 더 이상 없다', async () => {
        setupDom('other', 'me');
        mockFetch(false);
        const wrapper = mount(ProfileApp, { attachTo: document.body });
        await vi.waitFor(() => expect(wrapper.text()).toContain('주인'));
        expect(wrapper.find('.shop-report').exists()).toBe(false);
    });

    test('other: 케밥→신고 흐름 → ReportModal 오픈, 사유 select·상세 textarea 존재', async () => {
        setupDom('other', 'me');
        mockFetch(false);
        const wrapper = mount(ProfileApp, { attachTo: document.body });
        await vi.waitFor(() => expect(wrapper.find('.shop-menu-btn').exists()).toBe(true));
        await wrapper.find('.shop-menu-btn').trigger('click');
        const reportItem = wrapper.findAll('.shop-menu-item').find(item => item.text().includes('신고'));
        await reportItem!.trigger('click');
        await vi.waitFor(() => expect(wrapper.find('select').exists()).toBe(true));
        expect(wrapper.find('textarea').exists()).toBe(true);
    });

    test('self: 신고 모달·케밥 안 뜸', async () => {
        setupDom('owner', 'owner');
        mockFetch(true);
        const wrapper = mount(ProfileApp, { attachTo: document.body });
        await vi.waitFor(() => expect(wrapper.text()).toContain('주인'));
        expect(wrapper.find('.shop-menu-btn').exists()).toBe(false);
        expect(wrapper.find('.shop-report').exists()).toBe(false);
        expect(wrapper.find('select').exists()).toBe(false);
    });
});

// 모바일(좁은 화면)에선 BtiPanel이 activeTab==='bti'일 때만 렌더된다. 공개 책장(shelf) 탭에서
// 헤더의 책BTI 태그칩을 눌러도 BTI 탭으로 넘어가지 않으면 드릴다운이 DOM에 없어 화면이 그대로다.
// → 태그를 누르면 자동으로 BTI 탭으로 전환되어 드릴다운이 보여야 한다(회귀 가드).
describe('ProfileApp 책BTI 태그 드릴다운', () => {
    const TAG_BOOKS = [
        { id: 1, title: '안나 카레니나', author: '톨스토이', coverUrl: null,
          status: 'DONE', seconds: 0, purchaseLink: null },
    ];
    function mockTagFetch() {
        vi.stubGlobal('fetch', vi.fn().mockImplementation((url: string) => {
            if (url.includes('/api/profile/personality-tag')) {
                return Promise.resolve({ ok: true, json: async () => ({ books: TAG_BOOKS }) });
            }
            if (url.includes('/api/profile/books')) {
                return Promise.resolve({ ok: true, json: async () => ({ books: [] }) });
            }
            return Promise.resolve({ ok: true, status: 200, json: async () => ({
                loginId: 'owner', nickname: '주인',
                followerCount: 0, followingCount: 0,
                following: false, self: true,
                personality: '깊이 있는 사유를 즐기는 독자',
                personalityTags: [{ label: '이야기파', clickable: true }],
                books: [], coupangEnabled: false,
            }) });
        }));
    }

    test('공개 책장 탭에서 태그칩을 누르면 BTI 탭으로 전환되어 드릴다운이 보인다', async () => {
        setupDom('owner', 'owner');
        window.history.pushState({}, '', '/u/owner?tab=shelf');   // 공개 책장 탭으로 진입
        mockTagFetch();
        const wrapper = mount(ProfileApp, { attachTo: document.body });

        // 로드 완료 → 헤더의 clickable 태그칩 등장 대기
        await vi.waitFor(() => expect(wrapper.find('.shop-tag-click').exists()).toBe(true));
        // 진입 직후엔 공개 책장 탭이라 드릴다운이 아직 없다
        expect(wrapper.find('.shop-drill').exists()).toBe(false);

        await wrapper.find('.shop-tag-click').trigger('click');

        // 태그 클릭 → BTI 탭 전환 → 드릴다운(근거책)이 화면에 나타난다
        await vi.waitFor(() => expect(wrapper.find('.shop-drill').exists()).toBe(true));
        expect(wrapper.text()).toContain('「이야기파」를 만든 책');
        expect(wrapper.text()).toContain('안나 카레니나');
    });
});
