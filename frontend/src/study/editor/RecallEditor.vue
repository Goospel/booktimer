<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { EditorContent, useEditor } from '@tiptap/vue-3';

import { recallExtensions, type SlashSuggestion } from './extensions';
import { bodyBudget, cleanMarkdown, filterSlashItems, slashPosition, type SlashItem } from './pure';

/**
 * 백지복습 본문 편집기 — 마크다운으로 받아 마크다운으로 돌려준다.
 *
 * <p>서식을 <b>배우지 않고</b> 쓰게 하는 것이 이 컴포넌트의 목적이다. 그래서 같은 명령에 입구를
 * 셋 둔다 — 툴바(눈으로 찾는 사람) · `/` 메뉴(손이 키보드에 있는 사람) · Tab(목록을 이미 쓰는 사람).
 *
 * <p>저장되는 값은 마크다운 문자열이라 서버·DB·AI 프롬프트가 그대로다. 되돌리려면 이 컴포넌트를
 * textarea로 되돌리면 되고, 그때 옛 글은 문법이 보일 뿐 깨지지 않는다.
 */
const props = defineProps<{
    modelValue: string;
    placeholder: string;
    disabled?: boolean;
}>();

const emit = defineEmits<{ (e: 'update:modelValue', value: string): void }>();

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
    extensions: recallExtensions(slashSuggestion),
    editable: !props.disabled,
    // textarea 시절의 aria-label을 잇는다 — 스크린리더에게 이 상자가 무엇인지.
    editorProps: { attributes: { 'aria-label': '백지복습 본문', 'aria-expanded': 'false' } },
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
const empty = computed(() => {
    void tick.value;
    return editor.value ? editor.value.isEmpty : props.modelValue.length === 0;
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

defineExpose({ editor, markdown: () => (editor.value ? markdownOf(editor.value) : '') });
</script>

<template>
    <div class="study-editor" data-testid="recall-body">
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
        </div>

        <div class="study-editor-body">
            <EditorContent :editor="editor" class="study-editor-content" />
            <p v-if="empty" class="study-editor-placeholder" aria-hidden="true">{{ placeholder }}</p>

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
    </div>
</template>
