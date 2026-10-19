<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { EditorContent, useEditor } from '@tiptap/vue-3';

import { recallExtensions, type SlashSuggestion } from './extensions';
import { foreignImageCount } from './noteImage';
import {
    OWN_IMAGE_PATH, PHOTO_MARK_MAX, bodyBudget, cleanMarkdown, droppedImages, filterSlashItems, pastedImages, slashPosition,
    type SlashItem,
} from './pure';

/**
 * 백지복습 본문 편집기 — 마크다운으로 받아 마크다운으로 돌려준다.
 *
 * <p>서식을 <b>배우지 않고</b> 쓰게 하는 것이 이 컴포넌트의 목적이다. 그래서 같은 명령에 입구를
 * 셋 둔다 — 툴바(눈으로 찾는 사람) · `/` 메뉴(손이 키보드에 있는 사람) · Tab(목록을 이미 쓰는 사람).
 *
 * <p>저장되는 값은 마크다운 문자열이라 서버·DB·AI 프롬프트가 그대로다. 되돌리려면 이 컴포넌트를
 * textarea로 되돌리면 되고, 그때 옛 글은 문법이 보일 뿐 깨지지 않는다.
 */
const props = withDefaults(defineProps<{
    modelValue: string;
    placeholder: string;
    disabled?: boolean;
    /** 스크린리더에게 이 상자가 무엇인지 — 필기 패널이 「필기 본문」으로 갈아 끼운다. */
    ariaLabel?: string;
    /**
     * 사진 올리기 — <b>이걸 받은 편집기만 사진을 안다</b>(필기만 넘긴다, 백지노트는 안 넘긴다 — 설계 D3).
     * 파일을 받아 서버에 올리고 본문에 넣을 주소를 돌려준다. 던진 Error의 message가 화면 문구다.
     */
    uploadImage?: (file: File) => Promise<string>;
}>(), { ariaLabel: '백지복습 본문', uploadImage: undefined });

const emit = defineEmits<{ (e: 'update:modelValue', value: string): void }>();

/** 사진 노드를 켤지 — 마운트 때 한 번(스키마는 도중에 안 바뀐다 — 나중에 prop이 바뀌어도 노드를 더하거나 빼지 않는다). */
const images = !!props.uploadImage;
const uploading = ref(0);
const imageMessage = ref('');
const imageInput = ref<HTMLInputElement | null>(null);
let imageQueue: Promise<void> = Promise.resolve();   // 한 장씩 — 서버 힙·시간당 상한·삽입 순서
/**
 * 문서 세대 — 부르는 쪽이 장·책을 갈아 끼울 때 dropPendingImages()로 올린다. 이 편집기는 장 사이에 재사용되고(key 없음)
 * 같은 문자열로 바뀌는 전환('' → '')은 modelValue watch가 못 본다 — 그래서 watch가 아니라 부르는 쪽이 알린다(설계 §4-8).
 */
let docGen = 0;
const STALE = '사진을 넣으려던 필기가 바뀌어 넣지 않았어요 — 다시 넣어 주세요.';

/** 한글 조합 중에 넣으면 조합 중인 음절이 깨진다 — 끝날 때까지 기다린다(상한 2초, 그 뒤엔 넣는다). */
async function untilComposed(): Promise<void> {
    for (let i = 0; i < 40 && editor.value?.view.composing; i += 1) await new Promise((r) => setTimeout(r, 50));
}

/** 툴바의 활성·잠금 상태를 다시 그리는 방아쇠 — `useEditor`는 트랜잭션에 반응하지 않는다. */
const tick = ref(0);

interface SlashState {
    items: SlashItem[];
    index: number;
    left: number;
    top: number;
    pick: (item: SlashItem) => void;
}

const slash = ref<SlashState | null>(null);

/**
 * 팝업을 캐럿 옆에 둔다 — 좌표는 <b>뷰포트 기준</b>이라 CSS도 `position: fixed`다.
 *
 * <p>편집기 상자 기준으로 잡던 첫 구현은 두 군데서 틀렸다: 좌표를 잰 상자(`.study-editor`)와
 * 실제로 좌표가 먹는 상자(`.study-editor-body`)가 달라 툴바 높이만큼 아래로 밀렸고, 세로 보정이
 * 없어 캐럿이 화면 아래쪽이면 팝업이 화면 밖으로 나갔다(로컬 실측 +82px).
 */
function place(clientRect: (() => DOMRect | null) | null | undefined, itemCount: number): { left: number; top: number } {
    const rect = clientRect?.();
    if (!rect) return { left: 0, top: 0 };
    return slashPosition(rect, { width: window.innerWidth, height: window.innerHeight }, itemCount);
}

const slashSuggestion: SlashSuggestion = {
    char: '/',
    allowSpaces: false,
    startOfLine: false,
    items: ({ query }) => filterSlashItems(query),
    command: ({ range, props: item }) => applySlash(item.id, range),
    render: () => {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const open = (p: any): void => {
            const items = p.items as SlashItem[];
            // 맞는 게 없으면 빈 상자를 띄우지 않는다 — 그냥 `/`를 글자로 친 것일 수도 있다.
            if (items.length === 0) {
                slash.value = null;
                return;
            }
            slash.value = {
                items,
                index: 0,
                ...place(p.clientRect, items.length),
                // 팝업을 먼저 닫는다 — 마우스는 mousedown·click 둘 다 오는데, 두 번 고르면 두 번째가
                // 이미 지워진 자리(range)에 명령을 건다.
                pick: (item) => {
                    if (!slash.value) return;
                    slash.value = null;
                    p.command(item);
                },
            };
        };
        return {
            onStart: open,
            onUpdate: open,
            onKeyDown: ({ event }: { event: KeyboardEvent }) => onSlashKey(event),
            onExit: () => { slash.value = null; },
        };
    },
};

/**
 * 저장될 값 — 엔티티를 되돌리고 빈 문단을 턴다(규칙은 `cleanMarkdown`).
 *
 * <p>직렬화가 내놓는 날것에는 사용자가 친 적 없는 것이 둘 섞인다: 글자 `>`·`&`가 `&gt;`·`&amp;`로
 * 이스케이프되고, `trailingNode`가 붙인 빈 문단이 `&nbsp;` 한 줄로 나온다(그 문단은 화면엔 필요하다 —
 * 목록 뒤를 눌러 이어 쓸 자리다). 저장값이 곧 DB·AI 프롬프트·8000자 예산이라 나가는 길에서 턴다.
 *
 * <p>밖으로 나가는 길과 비교하는 길이 <b>같은 함수</b>를 쓰는 것이 요점이다 — 한쪽만 다듬으면
 * 아래 watch가 매번 문서를 갈아치워 커서가 튄다.
 */
function markdownOf(e: NonNullable<typeof editor.value>): string {
    return cleanMarkdown(e.getMarkdown());
}

const editor = useEditor({
    content: props.modelValue,
    contentType: 'markdown',
    extensions: recallExtensions(slashSuggestion, { images }),
    editable: !props.disabled,
    // textarea 시절의 aria-label을 잇는다 — 스크린리더에게 이 상자가 무엇인지.
    // 캐럿을 화면 아래 가운데 「독서|공부」 알약(띠 54.8px · 힌트 91.6px) 위로 올린다 — 기본값은 창 아래 5px라
    // 긴 글 끝에서 지금 쓰는 줄이 알약 밑에 깔렸다(2026-10-17 실측). ProseMirror는 CSS scroll-padding을 읽지 않으니
    // 같은 값(app.css `body.has-rails` padding-bottom 100)을 여기 다시 적는다 — RecallEditor.test.ts가 둘을 대조한다.
    // ponytail: safe-area는 지금 0이다 — 템플릿 viewport에 viewport-fit=cover가 없어 iOS가 env()를 0으로 준다.
    // cover를 켜는 날엔 이 100을 마운트 때 getComputedStyle(html).scrollPaddingBottom(env()를 px로 풀어 준다)에서
    // 읽게 바꾼다 — 홈 바 34px가 붙으면 힌트 띠 91.6+34=125.6이라 고정 100으로는 모자란다.
    editorProps: {
        attributes: { 'aria-label': props.ariaLabel, 'aria-expanded': 'false' },
        scrollThreshold: { top: 0, right: 0, bottom: 100, left: 0 },
        scrollMargin: { top: 5, right: 5, bottom: 100, left: 5 },
        handlePaste: (_view, event) => {
            if (!images) return false;
            const files = pastedImages(event.clipboardData);
            if (files.length === 0) {
                // 남의 사진이 섞인 HTML — 노드 규칙이 그 img를 버린다. 조용히 빠지지 않게 말하고, 글은 평소처럼 붙인다.
                if (foreignImageCount(event.clipboardData?.getData('text/html') ?? '') > 0) {
                    imageMessage.value = '다른 곳의 사진은 옮겨 오지 않아요 — 사진은 파일로 저장한 뒤 넣어 주세요.';
                }
                return false;
            }
            addImages(files);   // Chrome 「이미지 복사」(파일 + 외부 img HTML)는 이 갈래 — 파일로 올라간다(핫링크 아님)
            return true;
        },
        // 편집기 안에서 옮기기(moved)·글 조각 끌기는 기본 동작. 본문 위에 놓인 파일이면 언제나 받는다 — true면 ProseMirror가 preventDefault해
        // 브라우저가 그 파일로 페이지를 옮기지 않는다(필기 편집기 한정). 본문 밖(툴바·칩·상태줄)은 onRootDrop이 받는다.
        handleDrop: (view, event, _slice, moved) => {
            if (!images || moved || !event.dataTransfer?.files.length) return false;
            const files = droppedImages(event.dataTransfer);
            if (files.length === 0) {
                imageMessage.value = '사진 파일만 넣을 수 있어요.';
                return true;
            }
            const at = view.posAtCoords({ left: event.clientX, top: event.clientY });
            if (at) editor.value?.commands.setTextSelection(at.pos);
            addImages(files);
            return true;
        },
    },
    onUpdate: ({ editor: e }) => emit('update:modelValue', markdownOf(e)),
    onTransaction: () => { tick.value += 1; },
});

/**
 * 밖에서 들어온 글을 싣는다 — 날짜 이동 · 사진 전사 · 저장분 복원이 이 길이다.
 *
 * <p>같은 문자열이면 `setContent`를 <b>부르지 않는 것</b>이 요점이다. 내가 방금 알린 값이 v-model로
 * 되돌아오는데 그때마다 문서를 갈아치우면 글자를 칠 때마다 커서가 맨 앞으로 튄다.
 */
watch(() => props.modelValue, (value) => {
    const e = editor.value;
    if (!e || markdownOf(e) === value) return;
    e.commands.setContent(value, { contentType: 'markdown', emitUpdate: false });
});

watch(() => props.disabled, (off) => editor.value?.setEditable(!off));

// 팝업이 떠 있다는 사실을 글 상자 자신이 말한다 — 스크린리더는 아래 <ul>을 못 보고 있을 수 있다.
watch(() => !!slash.value, (open) => editor.value?.view.dom.setAttribute('aria-expanded', String(open)));

const slashList = ref<HTMLUListElement | null>(null);

/** 화살표로 고른 항목이 상자(`max-height: 220px`) 밖으로 나가면 안 보인다. jsdom엔 이 함수가 없다. */
function revealSelected(index: number): void {
    (slashList.value?.children[index] as HTMLElement | undefined)?.scrollIntoView?.({ block: 'nearest' });
}

const budget = computed(() => bodyBudget(props.modelValue));
/**
 * 안내문구를 띄울지 — 「글자가 없다」가 아니라 <b>「아직 아무것도 만들지 않았다」</b>가 기준이다.
 *
 * <p>Tiptap의 `isEmpty`만으로는 안 된다. 그건 자식을 재귀로 훑어 글자·leaf가 없으면 참이라
 * 목록 버튼이 만든 빈 항목(`<ol><li><p></p></li></ol>`)도 empty로 센다 — 안내문구가 걷히지 않아
 * 「1.」 위에 겹쳐 그려졌다(태블릿 실측 2026-09-16). 빈 제목·인용·체크목록도 같은 뿌리였고,
 * `1. `를 손으로 쳐도 입력규칙이 같은 문서를 만드니 안내문구가 되돌아왔다.
 *
 * <p>그래서 「글자가 없다」에 <b>「블록이 문단뿐이다」</b>를 더한다. 문단 <i>하나</i>로 좁히지
 * 않는 것이 요점 — 목록을 만들었다 되돌리면 `trailingNode`가 붙인 빈 문단이 남아 둘이 되는데,
 * 그때 화면은 분명히 비어 있다.
 */
const empty = computed(() => {
    void tick.value;
    const e = editor.value;
    if (!e) return props.modelValue.length === 0;
    if (!e.isEmpty) return false;
    let onlyParagraphs = true;
    e.state.doc.forEach((node) => { if (node.type.name !== 'paragraph') onlyParagraphs = false; });
    return onlyParagraphs;
});

function applySlash(id: string, range: { from: number; to: number }): void {
    const e = editor.value;
    if (!e) return;
    const chain = e.chain().focus().deleteRange(range);
    run(chain, id);
    chain.run();
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function run(chain: any, id: string): void {
    switch (id) {
        case 'h1': chain.toggleHeading({ level: 1 }); break;
        case 'h2': chain.toggleHeading({ level: 2 }); break;
        case 'bulletList': chain.toggleBulletList(); break;
        case 'orderedList': chain.toggleOrderedList(); break;
        case 'taskList': chain.toggleTaskList(); break;
        case 'blockquote': chain.toggleBlockquote(); break;
        case 'horizontalRule': chain.setHorizontalRule(); break;
        case 'bold': chain.toggleBold(); break;
        case 'underline': chain.toggleUnderline(); break;
        case 'highlight': chain.toggleHighlight(); break;
        case 'sink': chain.sinkListItem('listItem'); break;
        case 'lift': chain.liftListItem('listItem'); break;
        case 'undo': chain.undo(); break;
    }
}

function onSlashKey(event: KeyboardEvent): boolean {
    const state = slash.value;
    if (!state) return false;
    if (event.key === 'ArrowDown') {
        state.index = (state.index + 1) % state.items.length;
        revealSelected(state.index);
        return true;
    }
    if (event.key === 'ArrowUp') {
        state.index = (state.index - 1 + state.items.length) % state.items.length;
        revealSelected(state.index);
        return true;
    }
    // Tab도 Enter와 같은 자리다 — 안 먹으면 Tab 키맵이 「고르려던 손」으로 목록을 만들어 버린다.
    if (event.key === 'Enter' || event.key === 'Tab') {
        state.pick(state.items[state.index]);
        return true;
    }
    if (event.key === 'Escape') {
        slash.value = null;
        return true;
    }
    return false;
}

interface ToolButton {
    id: string;
    label: string;
    /** 24×24 뷰박스 기준 path. 이모지를 쓰지 않는다(제품 UI 규칙). */
    paths: string[];
    /** 글자로 그리는 편이 또렷한 것(H1·H2)만. */
    text?: string;
}

const TOOLBAR: readonly ToolButton[] = [
    { id: 'h1', label: '큰 제목', paths: [], text: 'H1' },
    { id: 'h2', label: '작은 제목', paths: [], text: 'H2' },
    { id: 'bulletList', label: '글머리 목록', paths: ['M9 6h11', 'M9 12h11', 'M9 18h11', 'M4.5 6h.01', 'M4.5 12h.01', 'M4.5 18h.01'] },
    { id: 'orderedList', label: '번호 목록', paths: ['M10 6h10', 'M10 12h10', 'M10 18h10', 'M4 4.5h1V9', 'M3.5 15h2l-2 3h2'] },
    { id: 'taskList', label: '체크 목록', paths: ['M3 5.5h6v6H3z', 'M4.5 8.5l1.5 1.5 2.5-3', 'M13 8.5h8', 'M13 16h8'] },
    { id: 'bold', label: '굵게', paths: ['M7 5h5.5a3.5 3.5 0 0 1 0 7H7z', 'M7 12h6.5a3.5 3.5 0 0 1 0 7H7z'] },
    { id: 'underline', label: '밑줄', paths: ['M7 4v6a5 5 0 0 0 10 0V4', 'M5 20h14'] },
    { id: 'highlight', label: '형광펜', paths: ['M9 13l-4 4v2h4l10-10-4-4z', 'M4 22h16'] },
    { id: 'blockquote', label: '인용', paths: ['M5 6v12', 'M10 9h9', 'M10 15h9'] },
    { id: 'horizontalRule', label: '구분선', paths: ['M4 12h16'] },
    { id: 'sink', label: '들여쓰기', paths: ['M4 6h16', 'M10 12h10', 'M4 18h16', 'M4 10l3 2-3 2'] },
    { id: 'lift', label: '내어쓰기', paths: ['M4 6h16', 'M10 12h10', 'M4 18h16', 'M7 10l-3 2 3 2'] },
    { id: 'undo', label: '되돌리기', paths: ['M4 10h9a5 5 0 0 1 0 10H8', 'M4 10l4-4', 'M4 10l4 4'] },
];

/** 지금 그 서식으로 쓰고 있나 — 툴바가 「무엇으로 쓰는 중인지」를 말해 준다. */
function isOn(id: string): boolean {
    void tick.value;
    const e = editor.value;
    if (!e) return false;
    switch (id) {
        case 'h1': return e.isActive('heading', { level: 1 });
        case 'h2': return e.isActive('heading', { level: 2 });
        case 'bulletList': return e.isActive('bulletList');
        case 'orderedList': return e.isActive('orderedList');
        case 'taskList': return e.isActive('taskList');
        case 'blockquote': return e.isActive('blockquote');
        case 'bold': return e.isActive('bold');
        case 'underline': return e.isActive('underline');
        case 'highlight': return e.isActive('highlight');
        default: return false;
    }
}

/** 들여쓰기·내어쓰기는 목록 안에서만, 되돌리기는 되돌릴 게 있을 때만. */
function isEnabled(id: string): boolean {
    void tick.value;
    const e = editor.value;
    if (!e || props.disabled) return false;
    if (id === 'sink' || id === 'lift') return e.isActive('listItem') || e.isActive('taskItem');
    if (id === 'undo') return e.can().undo();
    return true;
}

function onToolClick(id: string): void {
    const e = editor.value;
    if (!e) return;
    const chain = e.chain().focus();
    run(chain, id);
    chain.run();
}

function addImages(files: File[]): void {
    if (!props.uploadImage || props.disabled || files.length === 0) return;
    imageMessage.value = '';
    const gen = docGen;   // 넣으려던 장 — 큐에서 차례가 올 때의 세대가 아니라 <b>지금</b> 세대를 들고 간다(2번째 장부터 새 장에 들어가던 길)
    for (const file of files) {
        uploading.value += 1;
        imageQueue = imageQueue.then(() => insertOne(file, gen)).finally(() => { uploading.value -= 1; });
    }
}

async function insertOne(file: File, gen: number): Promise<void> {
    try {
        if (gen !== docGen) throw new Error(STALE);   // ① 차례를 기다리는 사이 장이 바뀌었다 — 올리지도 않는다
        const now = editor.value ? markdownOf(editor.value) : props.modelValue;
        if (bodyBudget(now).remaining < PHOTO_MARK_MAX) {
            throw new Error('본문이 8000자에 거의 차서 사진을 더 넣을 수 없어요 — 새 필기에 넣어 주세요.');
        }
        const src = await props.uploadImage!(file);
        if (!OWN_IMAGE_PATH.test(src)) throw new Error('사진을 넣지 못했어요.');   // 서버 주소만 — data:·외부 주소는 어떤 길로도 본문에 안 들어간다
        await untilComposed();
        if (gen !== docGen) throw new Error(STALE);   // ② 올리는·조합을 기다리는 사이 장이 바뀌었다 — 올라간 사진은 고아(정리가 치운다)
        const e = editor.value;
        if (!e || e.isDestroyed) return;   // 화면이 사라졌다 — 올라간 사진은 어디에도 안 쓰여 정리 배치가 치운다(파괴된 편집기에 부르면 예외 — 실측)
        // ⚠️ 명령은 setEditable(false)를 무시한다(실측) — 잠김은 여기서 직접 본다.
        if (!e.isEditable) throw new Error('편집이 잠겨 사진을 넣지 않았어요 — 새로고침한 뒤 다시 넣어 주세요.');
        // 선택의 <b>끝</b>에 넣는다 — 고른 글·사진을 갈아치우지 않는다. focus()를 부르지 않는다(제목 칸 캐럿을 안 뺏는다). 실측: 설계 §2-4 ⑥.
        e.commands.insertContentAt(e.state.selection.to, { type: 'noteImage', attrs: { src } });
    } catch (err) {
        imageMessage.value = err instanceof Error && err.message ? err.message : '사진을 넣지 못했어요.';
    }
}

function onPickImages(event: Event): void {
    const input = event.target as HTMLInputElement;
    addImages(Array.from(input.files ?? []).filter((f) => f.type.startsWith('image/')));
    input.value = '';   // 같은 파일을 다시 고를 수 있게(RecallPanel 선례)
}

/**
 * 상자 루트의 드래그 — 본문(.ProseMirror) 밖(툴바·「이어 쓰기」 칩·상태줄)에 떨어진 파일도 받는다. 안 받으면 브라우저가 그 파일을 열며
 * 앱을 떠난다(칩은 슬롯 형제라 ProseMirror의 dragover·drop이 못 본다 — app.css .study-editor-empty-slot). 본문 위 드롭은 handleDrop이
 * 이미 처리해 defaultPrevented라 여기서 건너뛴다(두 번 올리지 않는다 — jsdom 실측). 백지노트(images 아님)는 지금 그대로.
 */
function onRootDragOver(event: DragEvent): void {
    if (images && Array.from(event.dataTransfer?.types ?? []).includes('Files')) event.preventDefault();   // 놓을 수 있는 자리로 — 안 하면 drop이 오지 않는다
}
function onRootDrop(event: DragEvent): void {
    if (!images || event.defaultPrevented || !event.dataTransfer?.files.length) return;
    event.preventDefault();
    const files = droppedImages(event.dataTransfer);
    if (files.length === 0) {
        imageMessage.value = '사진 파일만 넣을 수 있어요.';
        return;
    }
    addImages(files);   // 본문 밖이라 놓은 자리가 없다 — 선택 끝에 들어간다
}

defineExpose({
    editor,
    markdown: () => (editor.value ? markdownOf(editor.value) : ''),
    /**
     * 캐럿을 글 끝에 — 홈에서 공부 측정 시작 전환이 끝난 뒤 부른다(바로 이어 쓰게).
     * 스크롤은 하지 않는다: 긴 필기면 막대(측정 종료·시간)가 시작 직후 화면 밖으로 밀린다. 치기 시작하면 브라우저가 캐럿으로 간다.
     */
    focusEnd: () => { editor.value?.commands.focus('end', { scrollIntoView: false }); },
    /**
     * 장·책을 갈아 끼울 때 부른다 — 큐에 있거나 올리는 중인 사진을 새 장에 넣지 않게(insertOne이 세대를 대조한다).
     * <b>문구는 건드리지 않는다</b> — 버려지는 장이 STALE을 남긴다. 여기서 지우면 장 열기처럼 연달아 두 번 불릴 때(선올림 → retarget)
     * 그 안내가 사라진다(설계 §4-8 실측). 남은 옛 문구는 다음 사진 넣기(addImages)가 지운다.
     */
    dropPendingImages: () => { docGen += 1; },
});
</script>

<template>
    <div class="study-editor" data-testid="recall-body" @dragover="onRootDragOver" @drop="onRootDrop">
        <div class="study-editor-toolbar" role="toolbar" aria-label="글 서식">
            <button
                v-for="btn in TOOLBAR"
                :key="btn.id"
                type="button"
                class="study-editor-btn"
                :class="{ 'is-on': isOn(btn.id) }"
                :title="btn.label"
                :aria-label="btn.label"
                :aria-pressed="String(isOn(btn.id))"
                :disabled="!isEnabled(btn.id)"
                :data-testid="`editor-btn-${btn.id}`"
                @click="onToolClick(btn.id)"
            >
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <path v-for="(d, i) in btn.paths" :key="i" :d="d" />
                    <text v-if="btn.text" x="12" y="17" text-anchor="middle" font-size="13" font-weight="700" fill="currentColor" stroke="none">{{ btn.text }}</text>
                </svg>
            </button>
            <button
                v-if="images"
                type="button"
                class="study-editor-btn"
                title="사진 넣기 — 붙여 넣기·끌어다 놓기도 돼요"
                aria-label="사진 넣기"
                :disabled="disabled"
                data-testid="editor-btn-image"
                @click="imageInput?.click()"
            >
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <path d="M4 5h16v14H4z" /><path d="M4 16l5-5 4 4 3-3 4 4" /><path d="M15.5 9h.01" />
                </svg>
            </button>
        </div>
        <input v-if="images" ref="imageInput" type="file" accept="image/jpeg,image/png,image/webp" multiple hidden data-testid="editor-image-input" @change="onPickImages">

        <div class="study-editor-body">
            <EditorContent :editor="editor" class="study-editor-content" />
            <p v-if="empty" class="study-editor-placeholder" aria-hidden="true">{{ placeholder }}</p>
            <!-- 빈자리 슬롯 — 홈 필기의 「이어 쓰기」 칩. 안내문구와 같은 조건(empty)이고 절대 배치라 레이아웃에 끼지 않는다
                 (칩이 들고 날 때 편집기 높이·아래 상태줄이 뛰지 않게 — 설계 2026-09-17 D9). 백지노트는 슬롯을 안 넘긴다. -->
            <div v-if="empty && $slots.empty" class="study-editor-empty-slot" data-testid="editor-empty-slot"><slot name="empty" /></div>

            <ul
                v-if="slash"
                ref="slashList"
                class="study-editor-slash"
                data-testid="slash-menu"
                role="listbox"
                aria-label="블록 고르기"
                :style="{ left: `${slash.left}px`, top: `${slash.top}px` }"
            >
                <li v-for="(item, i) in slash.items" :key="item.id" role="option" :aria-selected="i === slash.index">
                    <button
                        type="button"
                        class="study-editor-slash-item"
                        :class="{ 'is-on': i === slash.index }"
                        data-testid="slash-item"
                        @mousedown.prevent="slash.pick(item)"
                        @click="slash.pick(item)"
                    >{{ item.label }}</button>
                </li>
            </ul>
        </div>

        <p v-if="budget.over" class="status-line study-error" data-testid="editor-over">
            8000자를 {{ -budget.remaining }}자 넘었어요 — 줄여야 저장돼요.
        </p>
        <p v-else-if="budget.remaining < 1000" class="status-line muted" data-testid="editor-budget">
            {{ budget.remaining }}자 남았어요.
        </p>
        <!-- 늘 그려 두는 live region(내용만 바뀐다). 한 줄에 쓴다: 템플릿 줄바꿈이 공백 글자가 되면 :empty가 안 걸린다 -->
        <p v-if="images" class="status-line study-editor-image-status" :class="uploading ? 'muted' : 'study-error'" role="status" data-testid="editor-image-status">{{ uploading ? `사진 올리는 중… ${uploading}장` : imageMessage }}</p>
    </div>
</template>
