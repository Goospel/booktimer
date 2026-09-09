// @vitest-environment jsdom
// 백지복습 편집기 — 「마크다운으로 들어가 마크다운으로 나온다」와 「글의 뼈대를 만드는 세 입구」.
//
// 세 입구(툴바·Tab·슬래시)는 전부 같은 명령을 부르지만 <b>도달 경로가 다르다</b> — 하나가 끊겨도
// 나머지가 초록이라, 셋을 따로 잰다. 실제 Tiptap을 마운트하는 것이 요점이다(명령 존재만 재는
// 목이라면 키맵이 안 걸린 것도 통과한다).
import { describe, test, expect, beforeEach, vi } from 'vitest';
import { mount, type VueWrapper } from '@vue/test-utils';

import RecallEditor from './RecallEditor.vue';

// ProseMirror는 좌표를 재려고 Range를 만든다 — jsdom엔 구현이 없어 마운트 자체가 깨진다.
beforeEach(() => {
    document.body.innerHTML = '';
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    (document as any).createRange = () => ({
        setStart: () => {}, setEnd: () => {},
        commonAncestorContainer: document.body,
        getBoundingClientRect: () => ({ top: 0, bottom: 0, left: 0, right: 0, width: 0, height: 0 }),
        getClientRects: () => ({ length: 0, item: () => null, [Symbol.iterator]: function* () {} }),
    });
});

// eslint-disable-next-line @typescript-eslint/no-explicit-any
type AnyEditor = any;

async function mountEditor(modelValue = ''): Promise<VueWrapper> {
    const wrapper = mount(RecallEditor, {
        attachTo: document.body,
        props: { modelValue, placeholder: '기억나는 것을 적어 보세요.' },
    });
    await vi.waitFor(() => expect(editorOf(wrapper)).toBeTruthy());
    return wrapper;
}

function editorOf(wrapper: VueWrapper): AnyEditor {
    return (wrapper.vm as AnyEditor).editor;
}

/** 밖으로 나갈 값 — 저장 버튼이 받는 것과 같은 함수를 탄다. */
function markdown(wrapper: VueWrapper): string {
    return (wrapper.vm as AnyEditor).markdown();
}

/** 마지막으로 밖에 알린 값 — 저장 버튼이 받는 것이 이것이다. */
function lastEmitted(wrapper: VueWrapper): string | undefined {
    const events = wrapper.emitted('update:modelValue') as string[][] | undefined;
    return events?.[events.length - 1]?.[0];
}

describe('마크다운 왕복', () => {
    const SAMPLE = '# 제목\n\n- 하나\n  - 둘\n\n- [ ] 할 일\n\n**굵게** ++밑줄++ ==형광==';

    test('넣은 마크다운이 그대로 나온다 — 제목·중첩목록·체크박스·굵게·밑줄·형광', async () => {
        const wrapper = await mountEditor(SAMPLE);
        expect(markdown(wrapper)).toBe(SAMPLE);
    });

    test('열기만 해서는 밖으로 아무것도 알리지 않는다 — 안 그러면 저장 안 한 글이 「고쳤다」가 된다', async () => {
        const wrapper = await mountEditor(SAMPLE);
        expect(wrapper.emitted('update:modelValue')).toBeUndefined();
    });

    test('밖에서 다른 글이 오면 문서가 바뀐다 — 날짜 이동·사진 전사가 이 길이다', async () => {
        const wrapper = await mountEditor(SAMPLE);
        await wrapper.setProps({ modelValue: '사진에서 읽은 글' });
        await vi.waitFor(() => expect(markdown(wrapper)).toBe('사진에서 읽은 글'));
    });
});

describe('옛 평문 줄바꿈', () => {
    test('한 줄 바꿈은 한 문단 안의 줄바꿈으로 살아남는다 — 옛 글이 통째로 붙어 버리면 안 된다', async () => {
        const html = editorOf(await mountEditor('줄1\n줄2')).getHTML();
        expect(html.match(/<br/g) ?? []).toHaveLength(1);
        expect(html.match(/<p/g) ?? []).toHaveLength(1);
    });

    test('빈 줄이 있으면 문단이 갈린다 — 위 단언이 「전부 한 문단」이라서 통과한 것이 아니다', async () => {
        const html = editorOf(await mountEditor('줄1\n\n줄2')).getHTML();
        expect(html.match(/<p/g) ?? []).toHaveLength(2);
    });
});

describe('툴바', () => {
    test('「글머리」를 누르면 글머리 목록이 된다', async () => {
        const wrapper = await mountEditor('하나');
        await wrapper.find('[data-testid="editor-btn-bulletList"]').trigger('click');
        expect(lastEmitted(wrapper)).toBe('- 하나');
    });

    test('「체크」를 누르면 체크 목록이 된다', async () => {
        const wrapper = await mountEditor('할 일');
        await wrapper.find('[data-testid="editor-btn-taskList"]').trigger('click');
        expect(lastEmitted(wrapper)).toBe('- [ ] 할 일');
    });

    test('「들여쓰기」는 목록 안에서만 눌린다 — 문단에서 눌러도 할 일이 없다', async () => {
        const wrapper = await mountEditor('그냥 문단');
        expect(wrapper.find('[data-testid="editor-btn-sink"]').attributes('disabled')).toBeDefined();

        await wrapper.find('[data-testid="editor-btn-bulletList"]').trigger('click');
        await wrapper.vm.$nextTick();
        expect(wrapper.find('[data-testid="editor-btn-sink"]').attributes('disabled')).toBeUndefined();
    });

    test('활성 상태가 버튼에 보인다 — 지금 무엇으로 쓰고 있는지', async () => {
        const wrapper = await mountEditor('하나');
        expect(wrapper.find('[data-testid="editor-btn-bulletList"]').attributes('aria-pressed')).toBe('false');
        await wrapper.find('[data-testid="editor-btn-bulletList"]').trigger('click');
        await wrapper.vm.$nextTick();
        expect(wrapper.find('[data-testid="editor-btn-bulletList"]').attributes('aria-pressed')).toBe('true');
    });
});

describe('Tab 들여쓰기', () => {
    function pressTab(wrapper: VueWrapper, shift = false): void {
        editorOf(wrapper).view.dom.dispatchEvent(
            new KeyboardEvent('keydown', { key: 'Tab', shiftKey: shift, bubbles: true, cancelable: true }));
    }

    test('목록 둘째 항목에서 Tab을 누르면 한 단 들어가고, Shift+Tab이면 되돌아온다', async () => {
        const wrapper = await mountEditor('- 하나\n- 둘');
        editorOf(wrapper).commands.focus('end');

        pressTab(wrapper);
        expect(markdown(wrapper)).toBe('- 하나\n  - 둘');

        pressTab(wrapper, true);
        expect(markdown(wrapper)).toBe('- 하나\n- 둘');
    });

    test('그냥 문단에서 Tab을 누르면 글머리 목록이 된다 — 빈 곳의 들여쓰기는 의미가 없다', async () => {
        const wrapper = await mountEditor('그냥 문단');
        editorOf(wrapper).commands.focus('end');

        pressTab(wrapper);
        expect(markdown(wrapper)).toBe('- 그냥 문단');
    });
});

describe('슬래시 메뉴', () => {
    async function openSlash(wrapper: VueWrapper, query: string): Promise<void> {
        editorOf(wrapper).commands.focus('end');
        editorOf(wrapper).commands.insertContent(`/${query}`);
        await vi.waitFor(() => expect(wrapper.find('[data-testid="slash-menu"]').exists()).toBe(true));
    }

    test('`/제`를 치면 제목 둘이 뜨고, 고르면 그 블록이 제목이 되고 슬래시 텍스트는 사라진다', async () => {
        const wrapper = await mountEditor('');
        await openSlash(wrapper, '제');
        expect(wrapper.findAll('[data-testid="slash-item"]')).toHaveLength(2);
        expect(wrapper.findAll('[data-testid="slash-item"]').map((b) => b.text())).toEqual(['큰 제목', '작은 제목']);

        await wrapper.findAll('[data-testid="slash-item"]')[0].trigger('click');
        await vi.waitFor(() => expect(editorOf(wrapper).getHTML()).toContain('<h1'));
        expect(editorOf(wrapper).getText()).not.toContain('/제');
        expect(wrapper.find('[data-testid="slash-menu"]').exists()).toBe(false);
    });

    test('Esc는 팝업만 닫는다 — 친 글자는 남는다(그냥 `/`를 쓰려던 것일 수 있다)', async () => {
        const wrapper = await mountEditor('');
        await openSlash(wrapper, '제');

        editorOf(wrapper).view.dom.dispatchEvent(
            new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }));
        await vi.waitFor(() => expect(wrapper.find('[data-testid="slash-menu"]').exists()).toBe(false));
        expect(editorOf(wrapper).getText()).toContain('/제');
    });

    test('안 맞는 질의엔 팝업을 안 그린다 — 빈 상자를 띄우지 않는다', async () => {
        const wrapper = await mountEditor('');
        editorOf(wrapper).commands.focus('end');
        editorOf(wrapper).commands.insertContent('/zzz');
        await wrapper.vm.$nextTick();
        expect(wrapper.find('[data-testid="slash-menu"]').exists()).toBe(false);
    });
});

describe('본문 상한', () => {
    test('8000자를 넘으면 넘었다고 말한다 — 서버 400을 만나기 전에', async () => {
        expect((await mountEditor('가'.repeat(8001))).find('[data-testid="editor-over"]').exists()).toBe(true);
        expect((await mountEditor('가'.repeat(8000))).find('[data-testid="editor-over"]').exists()).toBe(false);
    });
});
