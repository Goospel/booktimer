// @vitest-environment jsdom
// 공개 전환 고지 — 비공개 책을 공개로 바꿀 때 여백 글이 팔로워에게 보인다는 사실을 확인받는다.
// 핵심 계측: confirm() 이 낙관 갱신보다 **앞**에 있어야 「확인 전에 화면이 먼저 공개로 바뀌는 창」이 없다.
//           취소 케이스가 그 배치를 못 박는 계측기다(뒤에 두면 isPublic 이 이미 true 라 깨진다).
import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { nextTick } from 'vue';
import { mount, type VueWrapper } from '@vue/test-utils';
import BooksApp from './BooksApp.vue';
import MarginPanel from '../shared/story/MarginPanel.vue';

type Book = {
    id: number; title: string; author: string | null; coverUrl: string | null;
    isbn13: string | null; status: string; statusLabel: string;
    visibility: string; visibilityLabel: string; isPublic: boolean;
    seconds: number; purchaseLink: string | null; storyCount?: number;
};

function book(overrides: Partial<Book> = {}): Book {
    return {
        id: 1, title: '책', author: null, coverUrl: null, isbn13: null,
        status: 'READING', statusLabel: '읽는 중',
        visibility: 'PRIVATE', visibilityLabel: '비공개', isPublic: false,
        seconds: 0, purchaseLink: null,
        ...overrides,
    };
}

function okJson(body: object) {
    return { ok: true, json: async () => body } as Response;
}

/** 책 1권을 실은 책장을 띄우고, 공개 토글 칩을 누른다. */
async function mountAndToggle(b: Book): Promise<VueWrapper> {
    vi.mocked(fetch).mockResolvedValueOnce(okJson({ books: [b] }));
    const wrapper = mount(BooksApp, { attachTo: document.body });
    await vi.waitFor(() => expect(wrapper.find('.vis-chip').exists()).toBe(true));

    // 전환 응답(확인 케이스에서만 소비된다)
    vi.mocked(fetch).mockResolvedValueOnce(okJson({
        ...b, isPublic: !b.isPublic,
        visibility: b.isPublic ? 'PRIVATE' : 'PUBLIC',
        visibilityLabel: b.isPublic ? '비공개' : '공개',
    }));
    await wrapper.find('.vis-chip').trigger('click');
    return wrapper;
}

/** 화면에 보이는 그 책의 현재 공개 상태(칩 라벨) */
function chipLabel(wrapper: VueWrapper): string {
    return wrapper.find('.vis-chip').text();
}

beforeEach(() => {
    document.body.innerHTML = '<div></div>';
    vi.stubGlobal('fetch', vi.fn());
    vi.stubGlobal('confirm', vi.fn(() => true));
});

afterEach(() => {
    vi.unstubAllGlobals();
    document.body.innerHTML = '';
});

/**
 * 검색 결과의 「이미 있는 책」 배지 — 미니앱과 같은 언어로 맞춘다(2026-08-19).
 *
 * ⚠️ **문구는 「서재」가 아니라 「책장」이다.** 미니앱에서는 책 목록이 「서재」지만, 웹은 「책장」으로
 * 부른다. 맞추는 것은 **표기 방식(이모지 대신 체크 · 칩 모양 · -어요체)**이지 명칭이 아니다.
 * (「서재」가 작가 캐릭터 마을을 뜻해 충돌하던 사정은 2026-09-09 그 기능이 폐기되며 사라졌다.)
 */
describe('BooksApp 검색 결과 — 이미 책장에 있는 책', () => {
    const searchRow = (title: string, owned: boolean) => ({
        title, author: '지은이', isbn13: title, coverUrl: null,
        publisher: null, purchaseLink: null, category: null, pubDate: null, owned,
    });

    /** 담긴 책 한 권 + 아직 없는 책 한 권을 나란히 띄운다. */
    async function mountAndSearch(): Promise<VueWrapper> {
        vi.mocked(fetch).mockResolvedValueOnce(okJson({ books: [], searchEnabled: true }));
        const wrapper = mount(BooksApp, { attachTo: document.body });
        await vi.waitFor(() => expect(wrapper.find('.book-search-form').exists()).toBe(true));

        vi.mocked(fetch).mockResolvedValueOnce(okJson({
            results: [searchRow('미움받을 용기', true), searchRow('역행자', false)],
        }));
        await wrapper.find('.book-search-form input').setValue('책');
        await wrapper.find('.book-search-form').trigger('submit');
        await vi.waitFor(() => expect(wrapper.findAll('.book-row').length).toBe(2));
        return wrapper;
    }

    test('배지는 「책장에 있어요」 — 기본 이모지를 쓰지 않는다', async () => {
        const wrapper = await mountAndSearch();

        // 정확 일치라 `📚`가 남아 있으면 여기서 깨진다(문구 확인과 이모지 가드를 한 단언이 겸한다).
        expect(wrapper.find('.shelf-owned-badge').text()).toBe('책장에 있어요');
    });

    test('배지 대신 담을 길을 잃지 않는다 — 아직 없는 책엔 추가 폼이 그대로다', async () => {
        const rows = (await mountAndSearch()).findAll('.book-row');

        expect(rows[0].find('.shelf-owned-badge').exists()).toBe(true);
        expect(rows[0].find('.book-add-form').exists()).toBe(false);
        expect(rows[1].find('.shelf-owned-badge').exists()).toBe(false);
        expect(rows[1].find('.book-add-form').exists()).toBe(true);
    });
});

describe('BooksApp 공개 전환 고지', () => {
    test('여백 글이 있는 비공개 책을 공개로 바꾸면 개수를 담아 확인을 묻는다', async () => {
        await mountAndToggle(book({ storyCount: 3 }));

        expect(vi.mocked(confirm)).toHaveBeenCalledTimes(1);
        expect(vi.mocked(confirm).mock.calls[0][0]).toContain('3');
        // 2026-08-22 — 옛 문구 「팔로워에게 보여요」는 실제보다 좁은 고지였다(팔로우 축 제거).
        expect(vi.mocked(confirm).mock.calls[0][0]).toContain('누구에게나 보여요');
        expect(vi.mocked(confirm).mock.calls[0][0]).not.toContain('팔로워');
    });

    test('취소하면 화면이 공개로 바뀌지 않고 서버 요청도 안 나간다', async () => {
        vi.mocked(confirm).mockReturnValue(false);

        const wrapper = await mountAndToggle(book({ storyCount: 3 }));

        // 낙관 갱신이 confirm 보다 앞서면 여기서 깨진다 — 삽입 위치를 못 박는 계측기.
        expect(chipLabel(wrapper)).toContain('비공개');
        // 목록 로드 1회뿐 — visibility POST 는 나가지 않았다.
        expect(vi.mocked(fetch)).toHaveBeenCalledTimes(1);
    });

    test('확인하면 기존 경로대로 공개로 전환된다', async () => {
        const wrapper = await mountAndToggle(book({ storyCount: 3 }));

        await vi.waitFor(() => expect(vi.mocked(fetch)).toHaveBeenCalledTimes(2));
        expect(vi.mocked(fetch).mock.calls[1][0]).toBe('/api/books/1/visibility');
        expect(chipLabel(wrapper)).toContain('공개');
    });

    test('여백 글이 0개면 묻지 않는다', async () => {
        await mountAndToggle(book({ storyCount: 0 }));

        expect(vi.mocked(confirm)).not.toHaveBeenCalled();
        await vi.waitFor(() => expect(vi.mocked(fetch)).toHaveBeenCalledTimes(2));
    });

    test('공개 → 비공개 방향은 글이 있어도 묻지 않는다', async () => {
        await mountAndToggle(book({
            storyCount: 3, isPublic: true, visibility: 'PUBLIC', visibilityLabel: '공개',
        }));

        expect(vi.mocked(confirm)).not.toHaveBeenCalled();
        await vi.waitFor(() => expect(vi.mocked(fetch)).toHaveBeenCalledTimes(2));
    });

    test('storyCount 없는 옛 응답에서는 묻지 않는다(고지 UX — fail-open)', async () => {
        await mountAndToggle(book());

        expect(vi.mocked(confirm)).not.toHaveBeenCalled();
        await vi.waitFor(() => expect(vi.mocked(fetch)).toHaveBeenCalledTimes(2));
    });
});

/**
 * 책장 여백 진입 — 「여백 N」 손잡이가 책방과 같은 공용 MarginPanel을 연다 (2026-09-10).
 *
 * 책장은 언제나 본인이라 글 0건·비공개 책에도 손잡이가 선다(2026-08-16 결정 2 — 비공개 책 여백은
 * 「나만 보는 메모」). 그 「전부에 선다」가 이 기능의 핵심이라 B1이 좁히는 회귀를 겨눈다.
 */
describe('BooksApp 책장 여백 진입', () => {
    /** 여백 응답(self·글 0건) — 책장은 self 축이라 남의 여백 경로엔 닿지 않는다. */
    function marginJson(entries: object[] = []) {
        return okJson({
            book: { id: 1, title: '책', author: null, coverUrl: null },
            ownerNickname: '닉', self: true, entries,
        });
    }

    /** 책장 응답을 갈아끼울 수 있게 둔다 — B5의 재조회가 새 storyCount를 받아야 한다. */
    let shelfBody: object;

    /**
     * 책 1권을 실은 책장을 띄운다. `loginId`를 비우면 dataset 자체를 안 넣는다
     * (온보딩 전 = BookController가 loginId null을 그대로 싣는 경로).
     */
    async function mountShelf(b: Book, loginId = 'me'): Promise<VueWrapper> {
        document.body.innerHTML = loginId
            ? `<div id="books-app" data-my-login-id="${loginId}"></div>`
            : '<div id="books-app"></div>';
        shelfBody = { books: [b], nickname: '닉' };
        vi.mocked(fetch).mockImplementation(async (url: RequestInfo | URL) =>
            String(url).startsWith('/api/stories/of/') ? marginJson() : okJson(shelfBody));
        const wrapper = mount(BooksApp, { attachTo: document.body });
        await vi.waitFor(() => expect(wrapper.find('.book-row').exists()).toBe(true));
        return wrapper;
    }

    /** 손잡이를 눌러 패널을 연다. */
    async function open(wrapper: VueWrapper): Promise<void> {
        await wrapper.find('.shop-margin-btn').trigger('click');
        await vi.waitFor(() => expect(wrapper.find('.margin-overlay').exists()).toBe(true));
    }

    test('글 0건·비공개 책에도 손잡이가 선다 — 라벨은 「여백」', async () => {
        const wrapper = await mountShelf(book({ storyCount: 0 }));

        const handles = wrapper.findAll('.shop-margin-btn');
        expect(handles.length).toBe(1);
        expect(handles[0].text()).toBe('여백');
    });

    test('글이 있으면 개수를 붙인다 — 「여백 3」', async () => {
        const wrapper = await mountShelf(book({ storyCount: 3 }));

        expect(wrapper.find('.shop-margin-btn').text()).toBe('여백 3');
    });

    test('읽어 주는 이름이 보이는 글자를 품는다 — 음성 조작이 「여백 3」으로 닿는다', async () => {
        // WCAG 2.5.3 Label in Name. 책방 칩은 보이는 글자가 「여백」이라 `제목 + ' 여백 보기'`로 포함관계가
        // 성립했는데, 개수를 붙이는 순간 그 관계가 깨진다 — 화면엔 「여백 3」인데 이름엔 그 말이 없어진다.
        const wrapper = await mountShelf(book({ title: '사피엔스', storyCount: 3 }));

        const handle = wrapper.find('.shop-margin-btn');
        expect(handle.text()).toBe('여백 3');
        // 보이는 글자가 이름 안에 있어야 음성 조작이 매치된다.
        expect(handle.attributes('aria-label')).toContain('여백 3');
        // 그렇다고 이름이 보이는 글자와 같기만 하면 안 된다 — 행이 여럿이라 어느 책인지가 이름에 있어야 한다.
        expect(handle.attributes('aria-label')).toContain('사피엔스');
    });

    test('누르면 내 여백을 연다 — 요청 URL이 loginId·bookId를 그대로 싣는다', async () => {
        const wrapper = await mountShelf(book({ id: 1, storyCount: 3 }));

        await open(wrapper);

        // 대상 축을 URL로 못 박는다 — nickname 등 다른 값으로 새면 여기서 깨진다.
        expect(vi.mocked(fetch).mock.calls[1][0]).toBe('/api/stories/of/me?bookId=1');
    });

    test('패널이 close 하면 닫힌다', async () => {
        const wrapper = await mountShelf(book({ storyCount: 1 }));
        await open(wrapper);

        wrapper.findComponent(MarginPanel).vm.$emit('close');
        await vi.waitFor(() => expect(wrapper.find('.margin-overlay').exists()).toBe(false));
    });

    test('글을 쓰면 라벨만 조용히 갱신된다 — 패널이 죽지도, 스켈레톤이 번쩍이지도 않는다', async () => {
        const wrapper = await mountShelf(book({ storyCount: 0 }));
        await open(wrapper);

        shelfBody = { books: [book({ storyCount: 1 })], nickname: '닉' };
        wrapper.findComponent(MarginPanel).vm.$emit('changed');

        // 재조회 **도중**을 본다 — loading을 켜면 이 틱에 이미 스켈레톤 분기라 패널이 언마운트된다.
        // (왕복이 끝난 뒤에만 보면 패널이 되살아나 있어 갈아끼움이 안 보인다 — 사후 단언은 공허하다.)
        await nextTick();
        expect(wrapper.find('.shelf-skeleton').exists()).toBe(false);
        expect(wrapper.find('.margin-overlay').exists()).toBe(true);

        await vi.waitFor(() => expect(wrapper.find('.shop-margin-btn').text()).toBe('여백 1'));
        expect(wrapper.find('.margin-overlay').exists()).toBe(true);
        expect(vi.mocked(fetch).mock.calls.filter(c => c[0] === '/api/books').length).toBe(2);
        // 패널이 다시 마운트됐다면 여백을 한 번 더 불러온다 — 재마운트의 흔적을 개수로 못 박는다.
        expect(vi.mocked(fetch).mock.calls
            .filter(c => String(c[0]).startsWith('/api/stories/of/')).length).toBe(1);
    });

    test('loginId가 없으면(온보딩 전) 손잡이를 아예 안 그린다', async () => {
        const wrapper = await mountShelf(book({ storyCount: 3 }), '');

        expect(wrapper.findAll('.shop-margin-btn').length).toBe(0);
    });
});
