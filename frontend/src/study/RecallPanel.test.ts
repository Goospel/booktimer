// @vitest-environment jsdom
// 백지복습 패널 — 「고른 값이 실제로 요청에 실리는가」와 「못 쓰는 이유가 화면에 보이는가」.
//
// 이 둘은 순수 함수로 뺄 수 없어(컴포넌트 상태 ↔ fetch 본문의 연결이 곧 규칙이다) 마운트해서 잰다.
// 특히 책 선택은 서버의 소유권 검사(ownedBookOrNull)·연쇄 삭제(unlinkBook)·탈퇴 순서가 전부
// `bookId`가 실려 와야 도달하는 코드라, 여기가 비면 그 아래 전부가 죽은 길이 된다.
import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, type VueWrapper } from '@vue/test-utils';
import { h } from 'vue';

import RecallPanel from './RecallPanel.vue';

// 본문 편집기는 얇은 textarea 대역으로 바꾼다. 이 파일의 관심은 「고른 값이 요청에 실리는가」이지
// 편집기가 아니고(그건 editor/RecallEditor.test.ts가 실제 Tiptap을 마운트해 잰다), 대역이 없으면
// 여기 열두 개 테스트가 편집기 사정으로 흔들린다.
vi.mock('./editor/RecallEditor.vue', () => ({
    default: {
        name: 'RecallEditorStub',
        props: { modelValue: { type: String, default: '' }, placeholder: { type: String, default: '' }, disabled: Boolean },
        emits: ['update:modelValue'],
        setup(props: { modelValue: string }, { emit }: { emit: (e: 'update:modelValue', v: string) => void }) {
            return () => h('textarea', {
                'class': 'study-recall-body',
                'data-testid': 'recall-body',
                'value': props.modelValue,
                'onInput': (e: Event) => emit('update:modelValue', (e.target as HTMLTextAreaElement).value),
            });
        },
    },
}));

// canvas는 node 하니스에 없다 — 축소 자체(1568px·품질 0.85)는 preview 게이트(U-3·U-12)가 재고,
// 여기서는 「축소한 결과가 미리보기와 요청으로 흘러가는가」라는 배선만 잰다.
vi.mock('./image', () => ({
    shrinkForUpload: vi.fn(async (file: File) => ({
        blob: new Blob([new Uint8Array([1, 2, 3])], { type: 'image/jpeg' }),
        dataUrl: 'data:image/jpeg;base64,AQID',
        name: file.name,
    })),
}));

const PHOTO = new File([new Uint8Array([9])], 'memo.jpg', { type: 'image/jpeg' });

const BOOKS = [
    { id: 7, title: '정보처리기사 실기' },
    { id: 9, title: '토익 보카' },
];

function notFound() {
    return { ok: false, status: 404, text: async () => '', json: async () => ({}) } as Response;
}

function okJson(body: object) {
    return { ok: true, status: 200, json: async () => body, text: async () => '' } as Response;
}

async function mountPanel(props: Partial<Record<string, unknown>> = {}): Promise<VueWrapper> {
    vi.mocked(fetch).mockResolvedValueOnce(notFound()); // 그날은 아직 쓴 글이 없다
    const wrapper = mount(RecallPanel, {
        attachTo: document.body,
        props: {
            date: '2026-09-03',
            today: '2026-09-03',
            items: [],
            books: BOOKS,
            aiEnabled: true,
            remainingAnalyze: 1,
            remainingTranscribe: 3,
            hasYesterdayQuestions: false,
            ...props,
        },
    });
    await vi.waitFor(() => expect(wrapper.find('[data-testid="recall-body"]').exists()).toBe(true));
    return wrapper;
}

beforeEach(() => {
    document.body.innerHTML = '<div></div>';
    vi.stubGlobal('fetch', vi.fn());
    document.head.innerHTML = '<meta name="_csrf" content="tok">';
});

afterEach(() => {
    vi.unstubAllGlobals();
    document.body.innerHTML = '';
    document.head.innerHTML = '';
});

describe('백지복습 — 책 선택', () => {
    test('공부 서재가 선택지로 뜨고 기본값은 「책 없이」다', async () => {
        const options = (await mountPanel()).find('[data-testid="recall-book"]').findAll('option');

        expect(options.map((o) => o.text())).toEqual(['책 없이 (직접 입력)', '정보처리기사 실기', '토익 보카']);
        expect((options[0].element as HTMLOptionElement).selected).toBe(true);
    });

    test('고른 책이 저장 요청의 bookId로 실린다 — 안 실리면 서버의 소유권·연쇄 삭제가 죽은 길이 된다', async () => {
        const wrapper = await mountPanel();
        await wrapper.find('[data-testid="recall-body"]').setValue('오늘 배운 것');
        await wrapper.find('[data-testid="recall-book"]').setValue('7');

        vi.mocked(fetch).mockResolvedValueOnce(okJson({
            date: '2026-09-03', bookId: 7, subject: '', scope: '', body: '오늘 배운 것',
            source: 'TEXT', summary: null, holes: [], questions: [], model: null, analyzedAt: null,
        }));
        await wrapper.find('[data-testid="recall-save"]').trigger('click');
        await vi.waitFor(() => expect(vi.mocked(fetch).mock.calls.length).toBe(2));

        const [url, init] = vi.mocked(fetch).mock.calls[1];
        expect(url).toBe('/api/study/recall');
        expect(JSON.parse(String((init as RequestInit).body))).toMatchObject({ bookId: 7, body: '오늘 배운 것' });
    });

    test('책을 안 고르면 bookId는 null로 간다 — 자유 제목도 정당한 사용이다', async () => {
        const wrapper = await mountPanel();
        await wrapper.find('[data-testid="recall-body"]').setValue('책 없이 쓴 글');

        vi.mocked(fetch).mockResolvedValueOnce(okJson({
            date: '2026-09-03', bookId: null, subject: '', scope: '', body: '책 없이 쓴 글',
            source: 'TEXT', summary: null, holes: [], questions: [], model: null, analyzedAt: null,
        }));
        await wrapper.find('[data-testid="recall-save"]').trigger('click');
        await vi.waitFor(() => expect(vi.mocked(fetch).mock.calls.length).toBe(2));

        expect(JSON.parse(String((vi.mocked(fetch).mock.calls[1][1] as RequestInit).body)))
            .toMatchObject({ bookId: null });
    });
});

describe('백지복습 — 사진 전사', () => {
    function bodyValue(wrapper: VueWrapper): string {
        return (wrapper.find('[data-testid="recall-body"]').element as HTMLTextAreaElement).value;
    }

    async function pickPhoto(wrapper: VueWrapper, files: File[] = [PHOTO]): Promise<void> {
        await wrapper.find('[data-testid="recall-tab-photo"]').trigger('click');
        const input = wrapper.find('[data-testid="recall-photo-input"]').element as HTMLInputElement;
        // jsdom의 files는 읽기 전용이라 값을 심어 주고 change를 직접 쏜다(브라우저와 같은 경로).
        Object.defineProperty(input, 'files', { value: files, configurable: true });
        await wrapper.find('[data-testid="recall-photo-input"]').trigger('change');
        await vi.waitFor(() => expect(wrapper.findAll('[data-testid="recall-photo-preview"]').length)
            .toBe(files.length));
    }

    test('고른 사진이 data URL 미리보기로 뜬다 — CSP에 blob:이 없어 objectURL은 못 쓴다', async () => {
        const wrapper = await mountPanel();
        await pickPhoto(wrapper);

        const src = wrapper.find('[data-testid="recall-photo-preview"]').attributes('src');
        expect(src?.startsWith('data:image/jpeg;base64,')).toBe(true);
    });

    test('「읽어 오기」가 축소한 사진을 multipart로 보내고, 읽은 글이 textarea에 들어온다', async () => {
        const wrapper = await mountPanel();
        await pickPhoto(wrapper);

        vi.mocked(fetch).mockResolvedValueOnce(okJson({ text: '1. 함수의 정의\n2. 호출 [?]', unreadable: false }));
        await wrapper.find('[data-testid="recall-transcribe"]').trigger('click');
        await vi.waitFor(() => expect(vi.mocked(fetch).mock.calls.length).toBe(2));

        const [url, init] = vi.mocked(fetch).mock.calls[1];
        expect(url).toBe('/api/study/recall/transcribe');
        const body = (init as RequestInit).body as FormData;
        expect(body).toBeInstanceOf(FormData);
        expect(body.getAll('images')).toHaveLength(1);
        // Content-Type을 손으로 넣으면 boundary가 빠져 서버가 파트를 못 읽는다 — 브라우저에 맡긴다.
        expect((init as RequestInit).headers).not.toHaveProperty('Content-Type');

        await vi.waitFor(() => expect(bodyValue(wrapper)).toBe('1. 함수의 정의\n2. 호출 [?]'));
    });

    test('전사 뒤에는 확인 안내가 뜨고, 분석은 <b>자동으로 돌지 않는다</b>', async () => {
        const wrapper = await mountPanel();
        await pickPhoto(wrapper);

        vi.mocked(fetch).mockResolvedValueOnce(okJson({ text: '읽은 글', unreadable: false }));
        await wrapper.find('[data-testid="recall-transcribe"]').trigger('click');
        await vi.waitFor(() => expect(wrapper.find('[data-testid="recall-transcribed"]').exists()).toBe(true));

        expect(wrapper.find('[data-testid="recall-transcribed"]').text())
            .toContain('틀린 곳을 고친 뒤');
        // 요청은 전사 하나뿐이다 — 저장도 분석도 사용자가 눌러야 일어난다
        expect(vi.mocked(fetch).mock.calls.map((c) => c[0]))
            .toEqual(['/api/study/recall/2026-09-03', '/api/study/recall/transcribe']);
    });

    test('전사한 글을 저장하면 source=PHOTO로 간다 — 어떻게 쓴 글인지가 원장에 남는다', async () => {
        const wrapper = await mountPanel();
        await pickPhoto(wrapper);
        vi.mocked(fetch).mockResolvedValueOnce(okJson({ text: '읽은 글', unreadable: false }));
        await wrapper.find('[data-testid="recall-transcribe"]').trigger('click');
        await vi.waitFor(() => expect(bodyValue(wrapper)).toBe('읽은 글'));

        vi.mocked(fetch).mockResolvedValueOnce(okJson({
            date: '2026-09-03', bookId: null, subject: '', scope: '', body: '읽은 글',
            source: 'PHOTO', summary: null, holes: [], questions: [], model: null, analyzedAt: null,
        }));
        await wrapper.find('[data-testid="recall-save"]').trigger('click');
        await vi.waitFor(() => expect(vi.mocked(fetch).mock.calls.length).toBe(3));

        expect(JSON.parse(String((vi.mocked(fetch).mock.calls[2][1] as RequestInit).body)))
            .toMatchObject({ source: 'PHOTO', body: '읽은 글' });
    });

    test('글씨를 못 읽었으면 그렇게 말한다 — 빈 textarea를 「다 읽었다」고 하지 않는다', async () => {
        const wrapper = await mountPanel();
        await pickPhoto(wrapper);

        vi.mocked(fetch).mockResolvedValueOnce(okJson({ text: '', unreadable: true }));
        await wrapper.find('[data-testid="recall-transcribe"]').trigger('click');
        await vi.waitFor(() => expect(wrapper.find('[data-testid="recall-photo-error"]').exists()).toBe(true));

        expect(wrapper.find('[data-testid="recall-photo-error"]').text()).toContain('읽지 못했어요');
        expect(wrapper.find('[data-testid="recall-transcribed"]').exists()).toBe(false);
    });

    test('4장을 고르면 서버에 보내기 전에 화면이 막는다 — 왕복 없이 이유를 말한다', async () => {
        const wrapper = await mountPanel();
        await pickPhoto(wrapper, [PHOTO, PHOTO, PHOTO]);
        const input = wrapper.find('[data-testid="recall-photo-input"]').element as HTMLInputElement;
        Object.defineProperty(input, 'files', { value: [PHOTO], configurable: true });
        await wrapper.find('[data-testid="recall-photo-input"]').trigger('change');
        await vi.waitFor(() => expect(wrapper.find('[data-testid="recall-photo-error"]').exists()).toBe(true));

        expect(wrapper.find('[data-testid="recall-photo-error"]').text()).toContain('3장');
        expect(vi.mocked(fetch).mock.calls).toHaveLength(1); // 첫 로드뿐
    });

    test('서버 실패 문구가 그대로 화면에 온다 — errorMessage 경로를 탄다', async () => {
        const wrapper = await mountPanel();
        await pickPhoto(wrapper);

        vi.mocked(fetch).mockResolvedValueOnce({
            ok: false, status: 413, text: async () => '사진은 3MB 이하로 올려 주세요', json: async () => ({}),
        } as Response);
        await wrapper.find('[data-testid="recall-transcribe"]').trigger('click');
        await vi.waitFor(() => expect(wrapper.find('[data-testid="recall-photo-error"]').exists()).toBe(true));

        expect(wrapper.find('[data-testid="recall-photo-error"]').text()).toBe('사진은 3MB 이하로 올려 주세요');
    });

    test('불러오기가 실패해도 source는 TEXT로 돌아온다 — 전날의 PHOTO가 새 글에 눌어붙지 않는다', async () => {
        const wrapper = await mountPanel();
        await pickPhoto(wrapper);
        vi.mocked(fetch).mockResolvedValueOnce(okJson({ text: '사진에서 읽은 글', unreadable: false }));
        await wrapper.find('[data-testid="recall-transcribe"]').trigger('click');
        await vi.waitFor(() => expect(bodyValue(wrapper)).toBe('사진에서 읽은 글'));

        // 날짜를 옮기는데 그날 글 조회가 실패한다(서버 장애·네트워크) — 여기서 source가 안 씻기면
        // 손으로 새로 친 글이 PHOTO로 저장돼 원장이 거짓말을 한다.
        vi.mocked(fetch).mockRejectedValueOnce(new Error('boom'));
        await wrapper.setProps({ date: '2026-09-04', today: '2026-09-04' });
        await vi.waitFor(() => expect(bodyValue(wrapper)).toBe(''));

        await wrapper.find('[data-testid="recall-body"]').setValue('손으로 친 글');
        vi.mocked(fetch).mockResolvedValueOnce(okJson({
            date: '2026-09-04', bookId: null, subject: '', scope: '', body: '손으로 친 글',
            source: 'TEXT', summary: null, holes: [], questions: [], model: null, analyzedAt: null,
        }));
        await wrapper.find('[data-testid="recall-save"]').trigger('click');
        await vi.waitFor(() => expect(vi.mocked(fetch).mock.calls.length).toBe(4));

        expect(JSON.parse(String((vi.mocked(fetch).mock.calls[3][1] as RequestInit).body)))
            .toMatchObject({ source: 'TEXT', body: '손으로 친 글' });
    });

    test('AI가 꺼져 있으면 사진 탭 자체가 없다 — 못 쓰는 버튼을 보여 주지 않는다', async () => {
        const wrapper = await mountPanel({ aiEnabled: false });

        expect(wrapper.find('[data-testid="recall-tab-photo"]').exists()).toBe(false);
    });

    test('오늘 전사 몫이 0이면 「읽어 오기」가 잠기고 이유가 뜬다', async () => {
        const wrapper = await mountPanel({ remainingTranscribe: 0 });
        await wrapper.find('[data-testid="recall-tab-photo"]').trigger('click');

        expect(wrapper.find('[data-testid="recall-transcribe"]').attributes('disabled')).toBeDefined();
        expect(wrapper.find('[data-testid="recall-photo-cap-spent"]').exists()).toBe(true);
    });
});

// 편집기엔 maxlength가 없다(textarea 시절엔 브라우저가 막아 줬다) — 상한을 넘긴 글로 저장을 누르면
// 서버가 400을 주고 사용자는 이유를 왕복 뒤에야 듣는다. 화면이 먼저 막는다.
describe('백지복습 — 본문 상한', () => {
    test('8000자를 넘으면 저장이 잠긴다', async () => {
        const wrapper = await mountPanel();
        await wrapper.find('[data-testid="recall-body"]').setValue('가'.repeat(8001));

        expect(wrapper.find('[data-testid="recall-save"]').attributes('disabled')).toBeDefined();
    });

    test('딱 8000자면 저장할 수 있다 — 서버가 받는 값을 화면이 더 좁히지 않는다', async () => {
        const wrapper = await mountPanel();
        await wrapper.find('[data-testid="recall-body"]').setValue('가'.repeat(8000));

        expect(wrapper.find('[data-testid="recall-save"]').attributes('disabled')).toBeUndefined();
    });
});

describe('백지복습 — 오늘 몫 소진', () => {
    test('남은 몫이 0이면 왜 못 누르는지 문구로 말한다 — 비활성 버튼만으론 이유가 화면에 없다', async () => {
        const wrapper = await mountPanel({ remainingAnalyze: 0 });

        expect(wrapper.find('[data-testid="recall-cap-spent"]').text())
            .toBe('오늘 몫을 다 썼어요 — 내일 다시 해 주세요.');
        expect(wrapper.find('[data-testid="recall-analyze"]').attributes('disabled')).toBeDefined();
    });

    test('몫이 남아 있으면 그 문구는 안 뜬다', async () => {
        const wrapper = await mountPanel({ remainingAnalyze: 1 });

        expect(wrapper.find('[data-testid="recall-cap-spent"]').exists()).toBe(false);
    });
});

// 2026-09-07 — 홈(대시보드)이 이 패널을 그대로 싣는다. 넓은 화면에서 본문을 왼쪽에 크게 두려면
// 「본문 묶음」과 「부가입력 묶음」이 DOM에서 갈려 있어야 한다(CSS만으로는 순서를 못 바꾼다).
// 갈라 놓고도 /study의 쓰는 순서는 그대로여야 해서, 두 단언을 한 쌍으로 둔다.
describe('백지복습 — 두 묶음', () => {
    test('본문·저장은 main, 책·과목·범위는 side로 갈린다', async () => {
        const wrapper = await mountPanel();
        expect(wrapper.find('.study-recall-main [data-testid="recall-body"]').exists()).toBe(true);
        expect(wrapper.find('.study-recall-main [data-testid="recall-save"]').exists()).toBe(true);
        expect(wrapper.find('.study-recall-side [data-testid="recall-book"]').exists()).toBe(true);
        expect(wrapper.find('.study-recall-side .study-recall-scope').exists()).toBe(true);
    });

    test('/study의 쓰는 순서는 그대로 — 과목·범위가 본문 앞이다', async () => {
        const html = (await mountPanel()).html();
        expect(html.indexOf('study-recall-scope')).toBeLessThan(html.indexOf('study-recall-body'));
    });
});


// 「과목」을 「주제」로 바꾸면서 함께 메운 구멍(2026-09-07) — 일정 화면·AI 일정 폼엔 「책을 고르면 그
// 제목으로 채우기」가 있는데 백지복습엔 없어서, 책을 골라 놓고도 같은 값을 손으로 다시 쳐야 했다.
// 바로 그 순간 「이게 주제인가 책 제목인가」 하는 고민이 생긴다.
describe('백지복습 — 책을 고르면 주제가 따라온다', () => {
    test('책을 고르면 주제 칸이 그 제목으로 채워진다', async () => {
        const wrapper = await mountPanel();
        await wrapper.find('[data-testid="recall-book"]').setValue('7');
        expect((wrapper.find('input[aria-label="주제"]').element as HTMLInputElement).value)
            .toBe('정보처리기사 실기');
    });

    // 진짜 경계 — 저장된 글을 불러오면 bookId도 함께 세팅된다. 그때 watch가 그대로 돌면 **사용자가
    // 적어 둔 주제가 책 제목으로 덮인다**(글은 그대로인데 라벨만 바뀌어 있어 눈치채기 어렵다).
    test('저장된 글을 불러올 때는 적어 둔 주제를 덮지 않는다', async () => {
        vi.mocked(fetch).mockResolvedValueOnce(okJson({
            date: '2026-09-03', bookId: 7, subject: '내가 적어 둔 주제', scope: '', body: '어제 쓴 글',
            source: 'TEXT', summary: null, holes: [], questions: [], analyzedAt: null,
        }));
        const wrapper = mount(RecallPanel, {
            attachTo: document.body,
            props: {
                date: '2026-09-03', today: '2026-09-03', items: [], books: BOOKS,
                aiEnabled: true, remainingAnalyze: 1, remainingTranscribe: 3, hasYesterdayQuestions: false,
            },
        });
        await vi.waitFor(() => expect(
            (wrapper.find('.study-recall-body').element as HTMLTextAreaElement)?.value).toBe('어제 쓴 글'));

        expect((wrapper.find('input[aria-label="주제"]').element as HTMLInputElement).value)
            .toBe('내가 적어 둔 주제');
    });
});
