// @vitest-environment jsdom
// 공부 서재의 「인강」 배선 — 링크 한 칸으로 인강을 담고, 「강의 열기」로 열고, 「링크」로 갈아끼운다.
//
// 순수 함수(pure.test.ts의 sameTitleExists)가 답하지 못하는 절반이다: 그 판정이 실제로 confirm에
// 걸리는지, 링크 칸의 빈 값이 `null`로 실리는지(`''`로 실으면 서버가 400을 준다), 앵커에 안전 속성이
// 붙는지는 컴포넌트 ↔ api의 연결이 곧 규칙이라 마운트해서 잰다.
import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils';

import StudyBooksApp from './StudyBooksApp.vue';
import { addStudyBook, fetchStudyShelf, setStudyLink, type StudyBookRow } from './api';

vi.mock('./api', () => ({
    fetchStudyShelf: vi.fn(),
    addStudyBook: vi.fn(),
    setStudyLink: vi.fn(),
    setStudyReadCount: vi.fn(),
    deleteStudyBook: vi.fn(),
    searchBooks: vi.fn(),
}));

const book = (id: number, title: string, linkUrl: string | null): StudyBookRow => ({
    id,
    title,
    author: null,
    coverUrl: null,
    isbn13: null,
    readCount: 0,
    purchaseLink: null,
    totalSeconds: 0,
    sessionGoalSeconds: null,
    linkUrl,
});

async function mountShelf(books: StudyBookRow[] = []): Promise<VueWrapper> {
    vi.mocked(fetchStudyShelf).mockResolvedValue({ searchEnabled: true, books });
    vi.mocked(addStudyBook).mockResolvedValue(book(99, '새 책', null));
    vi.mocked(setStudyLink).mockResolvedValue(book(1, '수학 뉴런', null));
    const wrapper = mount(StudyBooksApp, { attachTo: document.body });
    await flushPromises();
    return wrapper;
}

/** 직접 추가 폼에 제목·링크를 적고 제출한다. */
async function submitManual(w: VueWrapper, title: string, link: string) {
    await w.find('.book-manual-form input[type="text"]').setValue(title);
    await w.find('.book-manual-form input[type="url"]').setValue(link);
    await w.find('.book-manual-form').trigger('submit');
    await flushPromises();
}

const lectureLinks = (w: VueWrapper) =>
    w.findAll('.shelf-list a[target="_blank"][rel="noopener noreferrer"]');

beforeEach(() => {
    document.body.innerHTML = '<div></div>';
    vi.clearAllMocks();
});

afterEach(() => {
    vi.unstubAllGlobals();
    document.body.innerHTML = '';
});

describe('강의 열기 — 링크가 있는 행만 새 탭으로 연다', () => {
    test('링크가 있으면 그 주소로 가는 앵커가 선다', async () => {
        const links = lectureLinks(await mountShelf([book(1, '수학 뉴런', 'https://lec.example/c/1')]));

        expect(links).toHaveLength(1);
        expect(links[0].attributes('href')).toBe('https://lec.example/c/1');
        expect(links[0].text()).toBe('강의 열기');
    });

    test('링크가 없는 책엔 앵커가 없다 — 눌러도 갈 곳 없는 손잡이를 안 남긴다', async () => {
        expect(lectureLinks(await mountShelf([book(1, '기본서', null)]))).toHaveLength(0);
    });
});

describe('직접 추가 — 링크 칸', () => {
    // ⚠️ 이 테스트는 addManual의 `.trim()`을 잠그지 **않는다** — jsdom(과 실브라우저)이 `type=url` 값의 앞뒤 공백을
    // 먼저 떼서, trim을 지워도 초록이다(리뷰 N3 돌연변이). 서버도 strip하므로 실해는 없다. 여기서 재는 것은
    // 「링크 칸의 값이 linkUrl 키로 실린다」뿐이다. trim이 실제로 의미 있는 경로(prompt)는 study-books-app (k)가 잰다.
    test('링크를 적으면 그 문자열이 linkUrl로 실린다', async () => {
        await submitManual(await mountShelf(), '수학 뉴런', '  https://lec.example/c/1 ');

        expect(vi.mocked(addStudyBook).mock.calls[0][0]).toMatchObject({
            title: '수학 뉴런',
            linkUrl: 'https://lec.example/c/1',
        });
    });

    // `''`를 실으면 서버가 blank → null로 모으긴 하지만, 「링크 없음」의 표기는 한 가지여야 한다.
    test('링크 칸이 비면 null로 실린다', async () => {
        await submitManual(await mountShelf(), '기본서', '');

        expect(vi.mocked(addStudyBook).mock.calls[0][0].linkUrl).toBeNull();
    });
});

describe('같은 제목 재등록 — 한 번 묻는다', () => {
    test('취소하면 담지 않는다 — 누적 시간이 두 행으로 갈리는 것을 막는 유일한 자리다', async () => {
        const w = await mountShelf([book(1, '수학 뉴런', null)]);
        vi.stubGlobal('confirm', vi.fn(() => false));

        await submitManual(w, '수학 뉴런', '');

        expect(addStudyBook).not.toHaveBeenCalled();
    });

    test('확인하면 담는다 — 「수학 1권」/「수학 2권」류를 영영 못 담게 하지는 않는다', async () => {
        const w = await mountShelf([book(1, '수학 뉴런', null)]);
        vi.stubGlobal('confirm', vi.fn(() => true));

        await submitManual(w, '수학 뉴런', '');

        expect(addStudyBook).toHaveBeenCalledTimes(1);
    });

    test('다른 제목이면 묻지 않는다', async () => {
        const w = await mountShelf([book(1, '수학 뉴런', null)]);
        const ask = vi.fn(() => true);
        vi.stubGlobal('confirm', ask);

        await submitManual(w, '영어 뉴런', '');

        expect(ask).not.toHaveBeenCalled();
        expect(addStudyBook).toHaveBeenCalledTimes(1);
    });
});

describe('링크 편집 — 죽은 링크를 갈아끼우거나 지운다', () => {
    const editButton = (w: VueWrapper) =>
        w.findAll('.shelf-list button').find((b) => b.text() === '링크')!;

    test('빈 문자열을 넣으면 해제를 보낸다(null)', async () => {
        const w = await mountShelf([book(1, '수학 뉴런', 'https://old.example/c')]);
        vi.stubGlobal('prompt', vi.fn(() => ''));

        await editButton(w).trigger('click');
        await flushPromises();

        expect(setStudyLink).toHaveBeenCalledWith(1, null);
    });

    test('새 주소를 넣으면 그 값을 보낸다', async () => {
        const w = await mountShelf([book(1, '수학 뉴런', 'https://old.example/c')]);
        vi.stubGlobal('prompt', vi.fn(() => ' https://new.example/c '));

        await editButton(w).trigger('click');
        await flushPromises();

        expect(setStudyLink).toHaveBeenCalledWith(1, 'https://new.example/c');
    });

    test('취소(null)면 아무것도 보내지 않는다 — 취소가 「해제」로 둔갑하면 안 된다', async () => {
        const w = await mountShelf([book(1, '수학 뉴런', 'https://old.example/c')]);
        vi.stubGlobal('prompt', vi.fn(() => null));

        await editButton(w).trigger('click');
        await flushPromises();

        expect(setStudyLink).not.toHaveBeenCalled();
    });
});
