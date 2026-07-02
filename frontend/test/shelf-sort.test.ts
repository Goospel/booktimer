// @vitest-environment jsdom
// 책방 공개 책 정렬 — 기본 이름순 + 완독 한정 완독시각 정렬(최신순/오래된순).
// 핵심 불변식:
//   1) 정렬 컨트롤은 완독(FINISHED) 필터에서만 뜬다 — 전체/읽는중/읽고싶음엔 새지 않는다.
//   2) 정렬 선택은 서버 재조회(/api/profile/books?sort=...)로 이어진다(서버 정렬 — 상태필터와 동일 패턴).
//   3) 완독 외 필터로 바꾸면 정렬은 이름순(기본)으로 리셋된다 — 죽은 sort 파라미터가 남지 않는다.
//   4) 딥링크(?status=...&sort=...)는 초기 로드에서 실제 필터·정렬이 적용된 목록을 부른다.
// 동작·노출 여부 위주(브리틀한 정확 문자열 회피 — profile-app.test.ts와 동일 방침).
import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount } from '@vue/test-utils';
import ProfileApp from '../src/profile/ProfileApp.vue';
import ShelfPanel from '../src/profile/ShelfPanel.vue';

// ── ShelfPanel 단독: 컨트롤 노출·emit ────────────────────────────────────

function mountShelf(shelfFilter: string | null, shelfSort: string | null = null) {
    return mount(ShelfPanel, {
        props: {
            books: [], shelfFilter, shelfSort, self: false,
            coupangEnabled: false, yes24Enabled: false, loginId: 'owner',
        },
    });
}

function sortNav(wrapper: ReturnType<typeof mountShelf>) {
    return wrapper.find('[aria-label="완독 정렬"]');
}

describe('ShelfPanel 완독 정렬 컨트롤', () => {
    test('완독(FINISHED) 필터에서만 정렬 컨트롤이 뜬다', () => {
        expect(sortNav(mountShelf('FINISHED')).exists()).toBe(true);
    });

    test('전체·다른 상태 필터에선 정렬 컨트롤이 새지 않는다', () => {
        expect(sortNav(mountShelf(null)).exists()).toBe(false);
        expect(sortNav(mountShelf('READING')).exists()).toBe(false);
        expect(sortNav(mountShelf('WANT_TO_READ')).exists()).toBe(false);
    });

    test('최신순/오래된순/이름순 선택이 selectSort로 emit된다', async () => {
        const wrapper = mountShelf('FINISHED');
        const buttons = sortNav(wrapper).findAll('button');
        expect(buttons.length).toBe(3); // 이름순 · 최신순 · 오래된순

        await buttons[1].trigger('click'); // 최신순
        await buttons[2].trigger('click'); // 오래된순
        await buttons[0].trigger('click'); // 이름순(기본)

        expect(wrapper.emitted('selectSort')).toEqual([
            ['finished_desc'], ['finished_asc'], [null],
        ]);
    });

    test('활성 정렬 버튼에 active 클래스가 붙는다', () => {
        const wrapper = mountShelf('FINISHED', 'finished_desc');
        const buttons = sortNav(wrapper).findAll('button');
        expect(buttons[1].classes()).toContain('active');
        expect(buttons[0].classes()).not.toContain('active');
    });
});

// ── ProfileApp 통합: 재조회·URL·리셋·딥링크 ──────────────────────────────

const PROFILE_JSON = {
    loginId: 'owner', nickname: '주인', profileCharacterCode: null,
    followerCount: 0, followingCount: 0,
    following: false, self: false,
    personality: null, personalityTags: [],
    books: [], coupangEnabled: false, yes24Enabled: false,
};

function setupDom() {
    document.body.innerHTML = `
        <meta name="_csrf" content="csrf-token-test">
        <div id="profile-app" data-login-id="owner" data-my-login-id="me"></div>
    `;
}

function mockFetch() {
    vi.stubGlobal('fetch', vi.fn().mockImplementation((url: string) => {
        if (url.includes('/api/profile/books')) {
            return Promise.resolve({ ok: true, json: async () => ({ books: [] }) });
        }
        if (url.includes('/api/stories')) {
            return Promise.resolve({ ok: true, json: async () => ({ stories: [] }) });
        }
        return Promise.resolve({ ok: true, status: 200, json: async () => ({ ...PROFILE_JSON }) });
    }));
}

function booksCalls(): string[] {
    return (fetch as ReturnType<typeof vi.fn>).mock.calls
        .map((c) => String(c[0]))
        .filter((u) => u.includes('/api/profile/books'));
}

beforeEach(() => {
    vi.stubGlobal('matchMedia', vi.fn().mockReturnValue({
        matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn(),
    }));
});
afterEach(() => {
    vi.unstubAllGlobals();
    document.body.innerHTML = '';
    window.history.pushState({}, '', '/'); // URL(tab/status/sort) 복원 — 타 테스트 onMounted 파싱 오염 방지
});

async function mountApp() {
    setupDom();
    mockFetch();
    const wrapper = mount(ProfileApp, { attachTo: document.body });
    // 프로필 로드 완료(셸프 렌더) 대기
    await vi.waitFor(() => expect(wrapper.find('.shop-filter').exists()).toBe(true));
    return wrapper;
}

function filterButton(wrapper: Awaited<ReturnType<typeof mountApp>>, label: string) {
    const btn = wrapper.findAll('.shop-filter button').find((b) => b.text() === label);
    if (!btn) throw new Error(`filter button not found: ${label}`);
    return btn;
}

describe('ProfileApp 완독 정렬 동작', () => {
    test('완독 필터에서 최신순 선택 → sort=finished_desc로 재조회하고 URL에도 반영된다', async () => {
        const wrapper = await mountApp();

        await filterButton(wrapper, '완독').trigger('click');
        await vi.waitFor(() => expect(wrapper.find('[aria-label="완독 정렬"]').exists()).toBe(true));

        const sortButtons = wrapper.find('[aria-label="완독 정렬"]').findAll('button');
        await sortButtons[1].trigger('click'); // 최신순

        await vi.waitFor(() => {
            const withSort = booksCalls().filter((u) => u.includes('sort=finished_desc'));
            expect(withSort.length).toBeGreaterThan(0);
        });
        expect(location.search).toContain('sort=finished_desc');
        expect(location.search).toContain('status=FINISHED');
    });

    test('완독 외 필터로 바꾸면 정렬이 이름순으로 리셋된다(죽은 sort 잔존 금지)', async () => {
        const wrapper = await mountApp();

        await filterButton(wrapper, '완독').trigger('click');
        await vi.waitFor(() => expect(wrapper.find('[aria-label="완독 정렬"]').exists()).toBe(true));
        await wrapper.find('[aria-label="완독 정렬"]').findAll('button')[1].trigger('click');
        await vi.waitFor(() => expect(location.search).toContain('sort=finished_desc'));

        await filterButton(wrapper, '읽는 중').trigger('click');

        await vi.waitFor(() => expect(location.search).not.toContain('sort='));
        const lastCall = booksCalls().at(-1)!;
        expect(lastCall).not.toContain('sort=');
        expect(lastCall).toContain('status=READING');
    });

    test('딥링크 ?status=FINISHED&sort=finished_asc → 초기 로드가 필터·정렬 적용 목록을 부른다', async () => {
        window.history.pushState({}, '', '/u/owner?status=FINISHED&sort=finished_asc&tab=shelf');
        setupDom();
        mockFetch();
        mount(ProfileApp, { attachTo: document.body });

        await vi.waitFor(() => {
            const applied = booksCalls().filter(
                (u) => u.includes('status=FINISHED') && u.includes('sort=finished_asc'));
            expect(applied.length).toBeGreaterThan(0);
        });
    });

    test('딥링크 ?status=READING(정렬 없음) → 초기 로드가 상태필터 적용 목록을 부른다(기존 딥링크 사각 수리)', async () => {
        window.history.pushState({}, '', '/u/owner?status=READING&tab=shelf');
        setupDom();
        mockFetch();
        mount(ProfileApp, { attachTo: document.body });

        await vi.waitFor(() => {
            const applied = booksCalls().filter((u) => u.includes('status=READING'));
            expect(applied.length).toBeGreaterThan(0);
        });
    });
});
