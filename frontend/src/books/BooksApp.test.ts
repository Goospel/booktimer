// @vitest-environment jsdom
// 공개 전환 고지 — 비공개 책을 공개로 바꿀 때 여백 글이 팔로워에게 보인다는 사실을 확인받는다.
// 핵심 계측: confirm() 이 낙관 갱신보다 **앞**에 있어야 「확인 전에 화면이 먼저 공개로 바뀌는 창」이 없다.
//           취소 케이스가 그 배치를 못 박는 계측기다(뒤에 두면 isPublic 이 이미 true 라 깨진다).
import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, type VueWrapper } from '@vue/test-utils';
import BooksApp from './BooksApp.vue';

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

describe('BooksApp 공개 전환 고지', () => {
    test('여백 글이 있는 비공개 책을 공개로 바꾸면 개수를 담아 확인을 묻는다', async () => {
        await mountAndToggle(book({ storyCount: 3 }));

        expect(vi.mocked(confirm)).toHaveBeenCalledTimes(1);
        expect(vi.mocked(confirm).mock.calls[0][0]).toContain('3');
        expect(vi.mocked(confirm).mock.calls[0][0]).toContain('팔로워에게 보여요');
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
