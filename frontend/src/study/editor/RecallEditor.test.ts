// @vitest-environment jsdom
// 백지복습 편집기 — 「마크다운으로 들어가 마크다운으로 나온다」와 「글의 뼈대를 만드는 세 입구」.
//
// 세 입구(툴바·Tab·슬래시)는 전부 같은 명령을 부르지만 <b>도달 경로가 다르다</b> — 하나가 끊겨도
// 나머지가 초록이라, 셋을 따로 잰다. 실제 Tiptap을 마운트하는 것이 요점이다(명령 존재만 재는
// 목이라면 키맵이 안 걸린 것도 통과한다).
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, test, expect, beforeEach, vi } from 'vitest';
import { mount, type VueWrapper } from '@vue/test-utils';

import { Slice } from '@tiptap/pm/model';
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

async function mountEditor(modelValue = '', extraProps: Record<string, unknown> = {}): Promise<VueWrapper> {
    const wrapper = mount(RecallEditor, {
        attachTo: document.body,
        props: { modelValue, placeholder: '기억나는 것을 적어 보세요.', ...extraProps },
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

    // 팝업이 떠 있을 때 Tab은 확장의 Tab 키맵(목록 만들기)보다 먼저 먹어야 한다 — 안 그러면
    // 고르려던 손이 `- /제`라는 목록을 만든다(리뷰 실측).
    test('Tab도 고른 항목을 확정한다 — Enter와 같은 자리', async () => {
        const wrapper = await mountEditor('');
        await openSlash(wrapper, '제');

        editorOf(wrapper).view.dom.dispatchEvent(
            new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true }));
        await vi.waitFor(() => expect(editorOf(wrapper).getHTML()).toContain('<h1'));
        expect(editorOf(wrapper).getText()).not.toContain('/제');
        expect(markdown(wrapper)).not.toContain('- ');
    });

    test('팝업은 목록상자로 읽힌다 — 화살표로 옮긴 자리가 스크린리더에 전해진다', async () => {
        const wrapper = await mountEditor('');
        await openSlash(wrapper, '제');
        expect(wrapper.find('[data-testid="slash-menu"]').attributes('role')).toBe('listbox');
        expect(editorOf(wrapper).view.dom.getAttribute('aria-expanded')).toBe('true');
        expect(wrapper.findAll('[role="option"]').map((o) => o.attributes('aria-selected'))).toEqual(['true', 'false']);

        editorOf(wrapper).view.dom.dispatchEvent(
            new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true, cancelable: true }));
        await wrapper.vm.$nextTick();
        expect(wrapper.findAll('[role="option"]').map((o) => o.attributes('aria-selected'))).toEqual(['false', 'true']);
    });

    test('안 맞는 질의엔 팝업을 안 그린다 — 빈 상자를 띄우지 않는다', async () => {
        const wrapper = await mountEditor('');
        editorOf(wrapper).commands.focus('end');
        editorOf(wrapper).commands.insertContent('/zzz');
        await wrapper.vm.$nextTick();
        expect(wrapper.find('[data-testid="slash-menu"]').exists()).toBe(false);
    });
});

// 2026-09-09 리뷰 실측 — 직렬화가 글자 `>`·`&`를 엔티티로 내보내던 자리. 저장값이 곧 DB·AI
// 프롬프트라, 사용자가 친 적 없는 `&gt;`가 거기 남으면 안 된다.
describe('저장값 엔티티', () => {
    test('글자로 친 `>`·`&`는 엔티티로 저장되지 않는다', async () => {
        const wrapper = await mountEditor('a > b, AT&T');
        expect(wrapper.emitted('update:modelValue')).toBeUndefined();

        editorOf(wrapper).commands.focus('end');
        editorOf(wrapper).commands.insertContent('!');
        await vi.waitFor(() => expect(lastEmitted(wrapper)).toBeDefined());
        expect(lastEmitted(wrapper)).toBe('a > b, AT&T!');
    });

    test('한 번 저장한 값을 다시 열어도 같은 문자열 — 열 때마다 글이 자라면 안 된다', async () => {
        const once = markdown(await mountEditor('a > b, AT&T'));
        expect(once).toBe('a > b, AT&T');
        expect(markdown(await mountEditor(once))).toBe(once);
    });
});

describe('본문 상한', () => {
    test('8000자를 넘으면 넘었다고 말한다 — 서버 400을 만나기 전에', async () => {
        expect((await mountEditor('가'.repeat(8001))).find('[data-testid="editor-over"]').exists()).toBe(true);
        expect((await mountEditor('가'.repeat(8000))).find('[data-testid="editor-over"]').exists()).toBe(false);
    });
});

// 공부 측정 시작 전환이 끝나면 캐럿을 필기 끝에 둔다(설계 2026-09-15-study-focus-lamp §2-6) — 부모가 이 문을 부른다.
describe('focusEnd — 전환 끝 캐럿', () => {
    test('포커스가 들어오고 선택이 문서 끝이다(맨 앞이 아니다)', async () => {
        const wrapper = await mountEditor('첫 문단\n\n둘째 문단');
        const e = editorOf(wrapper);
        (wrapper.vm as AnyEditor).focusEnd();
        await vi.waitFor(() => expect(e.isFocused).toBe(true));

        const { $to } = e.state.selection;
        expect($to.pos).toBe(e.state.doc.content.size - 1);
        expect($to.pos).toBeGreaterThan(2);   // 양성 대조 — 「그냥 1」(문서 맨 앞)과 갈린다
    });

    // 긴 필기에서 시작하면 캐럿으로 스크롤돼 막대(측정 종료·시간)가 화면 밖으로 밀린다 — 스크롤은 사용자가 치기 시작할 때
    // 브라우저가 한다(리뷰 Minor-4). Tiptap의 스크롤은 `scrolledIntoView` 트랜잭션으로 관측된다.
    test('캐럿만 옮기고 스크롤하지 않는다', async () => {
        const scrolled = async (run: (w: VueWrapper) => void) => {
            const wrapper = await mountEditor('첫 문단\n\n둘째 문단');
            const e = editorOf(wrapper);
            let hit = false;
            e.on('transaction', ({ transaction }: AnyEditor) => { if (transaction.scrolledIntoView) hit = true; });
            run(wrapper);
            await vi.waitFor(() => expect(e.isFocused).toBe(true));
            await new Promise((r) => setTimeout(r, 30));   // focus 명령은 다음 프레임에 스크롤을 건다
            wrapper.unmount();
            return hit;
        };
        expect(await scrolled((w) => editorOf(w).commands.focus('end'))).toBe(true);   // 양성 대조 — 기본값은 스크롤한다
        expect(await scrolled((w) => (w.vm as AnyEditor).focusEnd())).toBe(false);
    });
});

// 안내문구는 오버레이다(문서 위에 겹쳐 그린다) — 그래서 「언제 걷는가」가 곧 화면의 정확성이다.
// Tiptap의 `isEmpty`는 「글자가 없다」는 뜻이라 빈 목록·빈 제목에도 참이고, 그걸 그대로 쓰면
// 목록 버튼을 누른 순간 「1.」 위에 안내문구가 겹쳐 그려진다(태블릿 실측 2026-09-16).
describe('안내문구', () => {
    const placeholder = (wrapper: VueWrapper) => wrapper.find('.study-editor-placeholder');

    test('빈 편집기엔 보인다', async () => {
        // 양성 대조 — 이게 없으면 조건을 아무렇게나 좁혀도(늘 false) 아래 셋이 초록이다.
        expect(placeholder(await mountEditor()).exists()).toBe(true);
    });

    test('번호 목록 버튼을 누르면 사라진다 — 글자는 없지만 뼈대가 생겼다', async () => {
        const wrapper = await mountEditor();
        await wrapper.find('[data-testid="editor-btn-orderedList"]').trigger('click');
        expect(placeholder(wrapper).exists()).toBe(false);
    });

    test('큰 제목 버튼도 같다 — 목록만의 문제가 아니다', async () => {
        const wrapper = await mountEditor();
        await wrapper.find('[data-testid="editor-btn-h1"]').trigger('click');
        expect(placeholder(wrapper).exists()).toBe(false);
    });

    test('목록을 되돌리면 다시 보인다 — 빈 문단으로 돌아왔다', async () => {
        const wrapper = await mountEditor();
        await wrapper.find('[data-testid="editor-btn-orderedList"]').trigger('click');
        await wrapper.find('[data-testid="editor-btn-orderedList"]').trigger('click');
        expect(placeholder(wrapper).exists()).toBe(true);
    });
});

// 빈자리 슬롯 — 홈 필기의 「이어 쓰기」 칩이 여기 그려진다(설계 2026-09-17 D9). 안내문구와 **같은 조건**(empty)이라
// 한 글자·목록 버튼 하나에 걷히고, 슬롯을 안 넘기는 백지노트엔 상자 자체가 없다.
describe('빈자리 슬롯', () => {
    async function mountWithSlot(modelValue = ''): Promise<VueWrapper> {
        const wrapper = mount(RecallEditor, {
            attachTo: document.body,
            props: { modelValue, placeholder: '적어 보세요.' },
            slots: { empty: '<button data-testid="chip">이어 쓰기</button>' },
        });
        await vi.waitFor(() => expect(editorOf(wrapper)).toBeTruthy());
        return wrapper;
    }
    const chip = (wrapper: VueWrapper) => wrapper.find('[data-testid="editor-empty-slot"] [data-testid="chip"]');

    test('빈 편집기엔 슬롯 내용이 뜬다', async () => {
        expect(chip(await mountWithSlot()).exists()).toBe(true);
    });

    test('한 글자 치면 사라진다', async () => {
        const wrapper = await mountWithSlot();
        editorOf(wrapper).commands.insertContent('ㄱ');
        await wrapper.vm.$nextTick();
        expect(chip(wrapper).exists()).toBe(false);
    });

    test('목록 버튼만 눌러도 사라진다 — 뼈대가 생겼다', async () => {
        const wrapper = await mountWithSlot();
        await wrapper.find('[data-testid="editor-btn-orderedList"]').trigger('click');
        expect(chip(wrapper).exists()).toBe(false);
    });

    test('글이 있는 채로 열면 없다', async () => {
        expect(chip(await mountWithSlot('이미 쓴 글')).exists()).toBe(false);
    });

    test('슬롯을 안 넘기면(백지노트) 상자 자체가 없다', async () => {
        expect((await mountEditor()).find('.study-editor-empty-slot').exists()).toBe(false);
    });
});

/**
 * 화면 아래 가운데에 떠 있는 「독서|공부」 알약 밑에 <b>지금 쓰는 줄</b>과 <b>키보드 포커스</b>가 깔리지 않는다.
 *
 * <p>알약 띠는 창 아래에서 54.8px(힌트가 뜨면 91.6px)까지다(653a855c 실측). 맨 아래 정적 콘텐츠는
 * `body.has-rails`의 padding-bottom이 비켜 주지만, 스크롤을 <b>스스로</b> 정하는 두 길은 그 여백을 모른다 —
 * ProseMirror는 캐럿을 창 아래 5px(기본 scrollMargin)까지만 올리고 `scroll-padding`을 읽지 않으며,
 * 브라우저의 Tab 포커스 스크롤은 요소를 창 맨 아래 끝에 맞춘다. 그래서 세 자리가 같은 여백을 쓰는지 잰다.
 *
 * <p>⚠️ 값 검사라 <b>음성 판정 전용</b>이다 — 「실제로 안 깔린다」는 브라우저 실측(4폭)이 증명했다.
 */
describe('알약 밑에 지금 쓰는 줄·포커스가 깔리지 않는다', () => {
    const HERE = dirname(fileURLToPath(import.meta.url));
    const css = readFileSync(join(HERE, '..', '..', '..', '..', 'src', 'main', 'resources', 'static', 'css', 'app.css'), 'utf8');
    /** 맨 아래 정적 콘텐츠가 이미 쓰는 여백 — 세 자리의 기준값. */
    const clearance = Number(css.match(/^body\.has-rails\s*\{\s*padding-bottom:\s*calc\((\d+)px/m)?.[1]);

    test('기준 여백이 힌트까지 뜬 알약 띠(91.6px)보다 크다', () => {
        expect(clearance).toBeGreaterThan(91.6);
    });

    test('편집기 캐럿 스크롤 — scrollThreshold·scrollMargin의 아래가 기준 여백 이상', async () => {
        const view = editorOf(await mountEditor()).view;
        for (const prop of ['scrollThreshold', 'scrollMargin']) {
            const v = view.someProp(prop);
            expect(typeof v === 'number' ? v : v?.bottom, prop).toBeGreaterThanOrEqual(clearance);
        }
    });

    test('알약 화면의 스크롤 루트(html)에 scroll-padding-bottom이 기준 여백 이상', () => {
        const m = css.match(/^:root:has\(> body\.has-rails\)\s*\{[^}]*scroll-padding-bottom:\s*calc\((\d+)px/m);
        expect(m, ':root:has(> body.has-rails) { scroll-padding-bottom: calc(Npx + …) }').not.toBeNull();
        expect(Number(m![1])).toBeGreaterThanOrEqual(clearance);
    });
});

// ── 필기 사진(설계 2026-10-19 T10) — 자기 마크다운 토큰을 가진 인라인 노드 `noteImage` ──────────────
// view.pasteHTML이 ClipboardEvent를 만든다 — jsdom엔 없다.
// eslint-disable-next-line @typescript-eslint/no-explicit-any
(globalThis as any).ClipboardEvent ??= class extends Event { clipboardData = null; };

const IMG = (n: number): string => `![](/api/study/notes/images/${n})`;
/** 사진을 켠 편집기 — uploadImage를 받은 편집기만 사진을 안다. */
const withPhotos = (upload: (f: File) => Promise<string> = async () => '/api/study/notes/images/99') => ({ uploadImage: upload });

/** 숨은 파일 입력으로 고른다(RecallPanel.test 선례). */
async function pickFiles(wrapper: VueWrapper, files: File[]): Promise<void> {
    const input = wrapper.find('[data-testid="editor-image-input"]');
    Object.defineProperty(input.element, 'files', { value: files, configurable: true });
    await input.trigger('change');
}

function photoCount(wrapper: VueWrapper): number {
    let n = 0;
    editorOf(wrapper).state.doc.descendants((node: AnyEditor) => { if (node.type.name === 'noteImage') n += 1; });
    return n;
}

const jpgFile = (name = 'a.jpg'): File => new File(['x'], name, { type: 'image/jpeg' });

describe('필기 사진 노드', () => {
    const SHAPES: string[] = [
        IMG(1),                                       // 혼자 선 줄
        `앞 ${IMG(1)} 뒤`,                            // 글 사이
        `글 끝 ${IMG(1)}`,                            // 글 끝
        `${IMG(1)}${IMG(2)}`,                         // 연속
        `앞\n\n${IMG(1)}\n\n뒤`,                      // 문단 사이
        `# 제목 ${IMG(1)}`,                           // 제목
        `> ${IMG(1)}`,                                // 인용
        `- ${IMG(1)}`,                                // 글머리
        `- 하나\n  - ${IMG(1)}`,                      // 중첩
        `1. ${IMG(1)}`,                               // 번호
        `- [ ] ${IMG(1)}`,                            // 체크
        `- [ ] 할 일 ${IMG(1)}`,                      // 체크 + 글
    ];

    test('REQ-12 · 사진 표식이 혼자 선 줄·글 사이·연속·제목·인용·글머리·중첩·번호·체크에서 그대로 왕복하고 문서가 스키마에 맞다', async () => {
        for (const shape of SHAPES) {
            const wrapper = await mountEditor(shape, withPhotos());
            expect(markdown(wrapper), shape).toBe(shape);
            expect(() => editorOf(wrapper).state.doc.check(), shape).not.toThrow();
            expect(photoCount(wrapper), shape).toBeGreaterThan(0);
            wrapper.unmount();
        }
    });

    test('REQ-12 · 사진이 든 본문을 열고 한 글자 고쳐도 사진 줄이 저장값에 남는다', async () => {
        const body = `설명\n\n${IMG(7)}`;
        const wrapper = await mountEditor(body, withPhotos());
        editorOf(wrapper).commands.insertContentAt(3, '!');
        expect(lastEmitted(wrapper)).toBe(`설명!\n\n${IMG(7)}`);
    });

    // 회귀 가드 — Red 없음(음성 판정 전용): 구현 전엔 모르는 prop이 그냥 흘러내려 결과가 같다.
    test('REQ-12 · 사진을 켜도 글만 있는 본문은 지금과 똑같이 왕복한다', async () => {
        const texts = ['# 제목\n\n- 하나\n  - 둘\n\n- [ ] 할 일\n\n**굵게** ++밑줄++ ==형광==', '느낌표! 대괄호 [가] (괄호)', 'a > b & c', '줄1\n줄2'];
        for (const t of texts) {
            expect(markdown(await mountEditor(t, withPhotos())), t).toBe(markdown(await mountEditor(t)));
        }
    });

    test('REQ-13 · 붙여넣은 외부·다른 출처·data:·blob: 사진은 노드가 되지 않고 우리 주소는 된다 — 같은 출처 절대주소는 상대로 저장된다', async () => {
        const wrapper = await mountEditor('', withPhotos());
        const origin = window.location.origin;
        editorOf(wrapper).view.pasteHTML(
            '<p>가<img src="https://example.com/x.jpg">'
            + '<img src="data:image/png;base64,AAAA">'
            + '<img src="blob:http://localhost/abc">'
            + '<img src="http://other.test/api/study/notes/images/3">'
            + '<img src="/api/study/notes/images/4?x=1">'
            + '<img src="/api/study/notes/images/5">'
            + `<img src="${origin}/api/study/notes/images/6">나</p>`,
        );
        const md = markdown(wrapper);
        expect(md).toBe(`가${IMG(5)}${IMG(6)}나`);
        expect(md).not.toMatch(/example|data:|blob:|other\.test|images\/3|images\/4/);
    });

    test('REQ-13 · 저장값에 data: 주소가 생기지 않는다 — data: img를 붙여넣어도, 업로드가 data:를 돌려줘도 · 업로드가 우리 주소를 돌려주면 들어간다(대조군)', async () => {
        const replies = ['data:image/jpeg;base64,AAAA', '/api/study/notes/images/9'];
        const wrapper = await mountEditor('', withPhotos(async () => replies.shift()!));
        editorOf(wrapper).view.pasteHTML('<p><img src="data:image/png;base64,AAAA"></p>');

        await pickFiles(wrapper, [jpgFile()]);
        await vi.waitFor(() => expect(wrapper.find('[data-testid="editor-image-status"]').text()).toBe('사진을 넣지 못했어요.'));
        expect(markdown(wrapper)).not.toContain('data:');
        expect(photoCount(wrapper)).toBe(0);

        await pickFiles(wrapper, [jpgFile()]);
        await vi.waitFor(() => expect(photoCount(wrapper)).toBe(1));
        expect(markdown(wrapper)).toBe(IMG(9));
    });

    test('REQ-14 · uploadImage 없는 편집기(백지노트)는 사진 버튼도 사진 노드도 없다 — 있는 편집기는 있다(대조군)', async () => {
        const recall = await mountEditor(IMG(1));
        expect(recall.find('[data-testid="editor-btn-image"]').exists()).toBe(false);
        expect(recall.find('[data-testid="editor-image-input"]').exists()).toBe(false);
        expect(editorOf(recall).schema.nodes.noteImage).toBeUndefined();

        const notes = await mountEditor(IMG(1), withPhotos());
        expect(notes.find('[data-testid="editor-btn-image"]').exists()).toBe(true);
        expect(notes.find('[data-testid="editor-image-input"]').exists()).toBe(true);
        expect(editorOf(notes).schema.nodes.noteImage).toBeDefined();
    });
});

// ── 필기 사진 넣는 길(설계 2026-10-19 T11) ─────────────────────────────────────────────
// jsdom엔 DataTransfer·DragEvent·ClipboardEvent가 없다 — 핸들러는 someProp으로 직접 부르고 이벤트는 손으로 만든다.
// 「놓은 자리」는 단위테스트로 재지 않는다(posAtCoords가 스텁 위에서 {pos:0}) — 실 브라우저 U-6 ⓒ 몫.

interface Deferred<T> { promise: Promise<T>; resolve: (v: T) => void; reject: (e: unknown) => void }
function deferred<T>(): Deferred<T> {
    let resolve!: (v: T) => void;
    let reject!: (e: unknown) => void;
    const promise = new Promise<T>((res, rej) => { resolve = res; reject = rej; });
    return { promise, resolve, reject };
}

/** 부를 때마다 새 deferred를 내주는 업로드 대역 — calls[i]를 풀면 i번째 업로드가 끝난다. */
function heldUploads(): { upload: (f: File) => Promise<string>; calls: Deferred<string>[]; files: File[] } {
    const calls: Deferred<string>[] = [];
    const files: File[] = [];
    return {
        calls, files,
        upload: (f: File) => { const d = deferred<string>(); calls.push(d); files.push(f); return d.promise; },
    };
}

const statusOf = (wrapper: VueWrapper): string => wrapper.find('[data-testid="editor-image-status"]').text();
const pdfFile = (): File => new File(['x'], 'c.pdf', { type: 'application/pdf' });

function paste(wrapper: VueWrapper, files: File[], text = '', html = ''): boolean {
    const view = editorOf(wrapper).view;
    const fake = { clipboardData: { files, getData: (t: string) => (t === 'text/plain' ? text : t === 'text/html' ? html : '') }, preventDefault() {} };
    return !!view.someProp('handlePaste', (f: AnyEditor) => f(view, fake, Slice.empty));
}

function drop(wrapper: VueWrapper, files: File[], moved = false): boolean {
    const view = editorOf(wrapper).view;
    const fake = { dataTransfer: { files, types: ['Files'], getData: () => '' }, clientX: 1, clientY: 1, preventDefault() {} };
    return !!view.someProp('handleDrop', (f: AnyEditor) => f(view, fake, Slice.empty, moved));
}

/** DOM으로 쏘는 드래그 — 좌표가 없으면 ProseMirror의 drop이 posAtCoords에서 일찍 돌아가 handleDrop을 안 부른다. */
function fireDrag(target: Element, type: 'dragover' | 'drop', files: File[]): Event {
    const ev = new Event(type, { bubbles: true, cancelable: true });
    Object.defineProperty(ev, 'dataTransfer', { value: { files, types: ['Files'], getData: () => '' } });
    Object.defineProperty(ev, 'clientX', { value: 1 });
    Object.defineProperty(ev, 'clientY', { value: 1 });
    target.dispatchEvent(ev);
    return ev;
}

describe('필기 사진 넣는 길', () => {
    beforeEach(() => {
        // handleDrop이 부르는 view.posAtCoords가 이것을 직접 부른다(prosemirror-view) — 없으면 엉뚱한 이유의 빨강.
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        (document as any).elementFromPoint = () => null;
    });

    test('REQ-15 · 툴바 「사진 넣기」로 고른 두 장을 차례로 올려 넣는다', async () => {
        const h = heldUploads();
        const wrapper = await mountEditor('', { uploadImage: h.upload });
        const a = jpgFile('a.jpg');
        const b = jpgFile('b.jpg');
        await pickFiles(wrapper, [a, b, pdfFile()]);
        await vi.waitFor(() => expect(h.calls).toHaveLength(1));   // 한 장씩 — 둘째는 첫째가 끝난 뒤
        h.calls[0].resolve('/api/study/notes/images/101');
        await vi.waitFor(() => expect(h.calls).toHaveLength(2));
        h.calls[1].resolve('/api/study/notes/images/102');
        await vi.waitFor(() => expect(photoCount(wrapper)).toBe(2));
        expect(h.files).toEqual([a, b]);
        expect(markdown(wrapper)).toBe(`${IMG(101)}${IMG(102)}`);
    });

    test('REQ-15 · 붙여넣기에 사진 파일만 오면 올려 넣고 true, 글이 함께 오면 올리지 않고 false', async () => {
        const upload = vi.fn(async () => '/api/study/notes/images/7');
        const wrapper = await mountEditor('', { uploadImage: upload });
        expect(paste(wrapper, [jpgFile()], '워드에서 복사한 단락')).toBe(false);
        expect(upload).not.toHaveBeenCalled();
        expect(paste(wrapper, [jpgFile()])).toBe(true);
        await vi.waitFor(() => expect(photoCount(wrapper)).toBe(1));
        expect(upload).toHaveBeenCalledTimes(1);
    });

    test('REQ-15 · 끌어다 놓은 사진은 올려 넣고, 사진 아닌 파일은 올리지 않되 true(브라우저가 파일로 떠나지 않게)와 「사진 파일만」 문구, moved는 false', async () => {
        const upload = vi.fn(async () => '/api/study/notes/images/8');
        const wrapper = await mountEditor('', { uploadImage: upload });
        expect(drop(wrapper, [pdfFile()])).toBe(true);
        await wrapper.vm.$nextTick();
        expect(statusOf(wrapper)).toBe('사진 파일만 넣을 수 있어요.');
        expect(upload).not.toHaveBeenCalled();
        expect(drop(wrapper, [jpgFile()], true)).toBe(false);
        expect(upload).not.toHaveBeenCalled();
        expect(drop(wrapper, [jpgFile()])).toBe(true);
        await vi.waitFor(() => expect(photoCount(wrapper)).toBe(1));
        expect(upload).toHaveBeenCalledTimes(1);
    });

    test('REQ-15 · 편집기 상자의 본문 밖(툴바)에 놓은 사진도 받고 기본 동작을 막는다 — dragover도 막는다 · 본문 위 드롭은 한 번만 올린다', async () => {
        const upload = vi.fn(async () => '/api/study/notes/images/9');
        const wrapper = await mountEditor('', { uploadImage: upload });
        const toolbar = wrapper.find('.study-editor-toolbar').element;
        expect(fireDrag(toolbar, 'dragover', [jpgFile()]).defaultPrevented).toBe(true);
        expect(fireDrag(toolbar, 'drop', [jpgFile()]).defaultPrevented).toBe(true);
        await vi.waitFor(() => expect(photoCount(wrapper)).toBe(1));
        expect(upload).toHaveBeenCalledTimes(1);

        fireDrag(wrapper.find('.ProseMirror').element, 'drop', [jpgFile()]);
        await vi.waitFor(() => expect(photoCount(wrapper)).toBe(2));
        expect(upload).toHaveBeenCalledTimes(2);   // 루트가 defaultPrevented를 안 보면 3
    });

    test('REQ-15 · dropPendingImages 뒤엔 큐에 있던 사진도, 올리던 사진도 넣지 않는다 — 큐에 있던 장은 올리지도 않는다 · 그 뒤 넣은 사진은 들어간다(대조군)', async () => {
        const h = heldUploads();
        const wrapper = await mountEditor('', { uploadImage: h.upload });
        await pickFiles(wrapper, [jpgFile('1.jpg'), jpgFile('2.jpg'), jpgFile('3.jpg')]);
        await vi.waitFor(() => expect(h.calls).toHaveLength(1));
        (wrapper.vm as AnyEditor).dropPendingImages();
        h.calls[0].resolve('/api/study/notes/images/1');
        await vi.waitFor(() => expect(statusOf(wrapper)).toBe('사진을 넣으려던 필기가 바뀌어 넣지 않았어요 — 다시 넣어 주세요.'));
        expect(photoCount(wrapper)).toBe(0);
        expect(h.calls).toHaveLength(1);

        await pickFiles(wrapper, [jpgFile('4.jpg')]);
        await vi.waitFor(() => expect(h.calls).toHaveLength(2));
        h.calls[1].resolve('/api/study/notes/images/4');
        await vi.waitFor(() => expect(photoCount(wrapper)).toBe(1));
        expect(markdown(wrapper)).toBe(IMG(4));
    });

    test('REQ-15 · 사진은 선택의 끝에 들어간다 — 고른 사진·글을 갈아치우지 않고 제목 칸 포커스를 뺏지 않는다', async () => {
        const replies = ['/api/study/notes/images/2', '/api/study/notes/images/3'];
        const wrapper = await mountEditor(`가나다 ${IMG(1)}`, { uploadImage: async () => replies.shift()! });
        const title = document.createElement('input');
        document.body.appendChild(title);
        title.focus();

        editorOf(wrapper).commands.setTextSelection({ from: 1, to: 3 });   // 「가나」를 고른 채
        await pickFiles(wrapper, [jpgFile()]);
        await vi.waitFor(() => expect(photoCount(wrapper)).toBe(2));
        expect(markdown(wrapper)).toBe(`가나${IMG(2)}다 ${IMG(1)}`);

        const e = editorOf(wrapper);
        let photoPos = -1;
        e.state.doc.descendants((n: AnyEditor, pos: number) => { if (n.attrs?.src === '/api/study/notes/images/1') photoPos = pos; });
        e.commands.setNodeSelection(photoPos);   // 사진을 고른 채
        await pickFiles(wrapper, [jpgFile()]);
        await vi.waitFor(() => expect(photoCount(wrapper)).toBe(3));
        expect(markdown(wrapper)).toBe(`가나${IMG(2)}다 ${IMG(1)}${IMG(3)}`);
        expect(document.activeElement).toBe(title);
    });

    test('REQ-15 · 올리는 중엔 「사진 올리는 중… N장」, 실패하면 던진 문구를 보인다', async () => {
        const h = heldUploads();
        const wrapper = await mountEditor('', { uploadImage: h.upload });
        await pickFiles(wrapper, [jpgFile(), jpgFile()]);
        await vi.waitFor(() => expect(statusOf(wrapper)).toBe('사진 올리는 중… 2장'));
        await vi.waitFor(() => expect(h.calls).toHaveLength(1));
        h.calls[0].reject(new Error('사진은 3MB 이하로 올려 주세요'));
        await vi.waitFor(() => expect(statusOf(wrapper)).toBe('사진 올리는 중… 1장'));
        await vi.waitFor(() => expect(h.calls).toHaveLength(2));
        h.calls[1].reject(new Error('사진을 너무 자주 올렸어요 — 잠시 뒤 다시 올려 주세요'));
        await vi.waitFor(() => expect(statusOf(wrapper)).toBe('사진을 너무 자주 올렸어요 — 잠시 뒤 다시 올려 주세요'));
        expect(photoCount(wrapper)).toBe(0);
    });

    test('REQ-15 · 잠긴 편집기는 버튼이 꺼지고, 올리는 사이 잠기면 넣지 않고 「편집이 잠겨」를 말한다', async () => {
        const locked = await mountEditor('', { uploadImage: async () => '/api/study/notes/images/1', disabled: true });
        expect(locked.find('[data-testid="editor-btn-image"]').attributes('disabled')).toBeDefined();

        const h = heldUploads();
        const wrapper = await mountEditor('', { uploadImage: h.upload });
        expect(wrapper.find('[data-testid="editor-btn-image"]').attributes('disabled')).toBeUndefined();
        await pickFiles(wrapper, [jpgFile()]);
        await vi.waitFor(() => expect(h.calls).toHaveLength(1));
        await wrapper.setProps({ disabled: true });
        h.calls[0].resolve('/api/study/notes/images/5');
        await vi.waitFor(() => expect(statusOf(wrapper)).toBe('편집이 잠겨 사진을 넣지 않았어요 — 새로고침한 뒤 다시 넣어 주세요.'));
        expect(photoCount(wrapper)).toBe(0);
    });

    test('REQ-15 · 한글 조합 중엔 조합이 끝난 뒤 넣는다', async () => {
        const h = heldUploads();
        const wrapper = await mountEditor('가', { uploadImage: h.upload });
        let composing = true;
        Object.defineProperty(editorOf(wrapper).view, 'composing', { get: () => composing, configurable: true });
        await pickFiles(wrapper, [jpgFile()]);
        await vi.waitFor(() => expect(h.calls).toHaveLength(1));
        h.calls[0].resolve('/api/study/notes/images/6');
        await new Promise((r) => setTimeout(r, 160));
        expect(photoCount(wrapper)).toBe(0);
        composing = false;
        await vi.waitFor(() => expect(photoCount(wrapper)).toBe(1));
    });

    test('REQ-15 · 붙여넣은 HTML에 남의 사진이 있으면 「다른 곳의 사진은 옮겨 오지 않아요」를 말하고 글은 붙인다', async () => {
        const upload = vi.fn(async () => '/api/study/notes/images/1');
        const wrapper = await mountEditor('', { uploadImage: upload });
        const html = '<p>기사 글<img src="https://news.example.com/a.jpg"></p>';
        const fake = { clipboardData: { files: [], getData: (t: string) => (t === 'text/html' ? html : t === 'text/plain' ? '기사 글' : '') }, preventDefault() {} };
        editorOf(wrapper).view.pasteHTML(html, fake);
        await wrapper.vm.$nextTick();
        expect(statusOf(wrapper)).toBe('다른 곳의 사진은 옮겨 오지 않아요 — 사진은 파일로 저장한 뒤 넣어 주세요.');
        expect(markdown(wrapper)).toBe('기사 글');
        expect(upload).not.toHaveBeenCalled();
    });

    test('REQ-15 · 본문이 8000자에 거의 차면 올리지 않고 말한다', async () => {
        const upload = vi.fn(async () => '/api/study/notes/images/1');
        const wrapper = await mountEditor('ㄱ'.repeat(7980), { uploadImage: upload });
        await pickFiles(wrapper, [jpgFile()]);
        await vi.waitFor(() => expect(statusOf(wrapper)).toBe('본문이 8000자에 거의 차서 사진을 더 넣을 수 없어요 — 새 필기에 넣어 주세요.'));
        expect(upload).not.toHaveBeenCalled();
    });
});

// 필기 사진(설계 2026-10-19 T15) — 1568px 원본이 카드 밖으로 나가지 않고, 에디토리얼 테 규칙(REQ-09)을 어기지 않는다.
// ⚠️ 형식 검사라 「넘침이 실제로 안 난다」는 실 브라우저(원장 U-9)가 본다.
describe('필기 사진 CSS', () => {
    const HERE = dirname(fileURLToPath(import.meta.url));
    const css = readFileSync(join(HERE, '..', '..', '..', '..', 'src', 'main', 'resources', 'static', 'css', 'app.css'), 'utf8');

    test('REQ-19 · app.css가 편집기 사진을 폭 100%·높이 480px 안에 두고 테를 두르지 않는다', () => {
        const rules = [...css.matchAll(/\.study-editor \.ProseMirror img[^{]*\{([^}]*)\}/g)].map((m) => m[1]);
        expect(rules.length, '.study-editor .ProseMirror img 규칙').toBeGreaterThan(0);
        const base = rules[0];
        expect(base).toMatch(/max-width:\s*100%/);
        expect(base).toMatch(/max-height:\s*480px/);
        // 모서리(border-radius)는 테가 아니다 — var(--radius-sm)은 에디토리얼 REQ-07이 허용한다.
        for (const r of rules) expect(r).not.toMatch(/(^|[;\s])border(?!-radius)(-[a-z-]+)?\s*:/);
        expect('a { border: 1px solid var(--border); }').toMatch(/(^|[;\s])border(?!-radius)(-[a-z-]+)?\s*:/);   // 양성 대조 — 정규식이 테를 잡는다
    });
});
