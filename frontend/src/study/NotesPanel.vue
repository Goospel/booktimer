<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';

import {
    ApiError, createNote, deleteNote, fetchNote, fetchNotes, updateNote,
    type Note, type NoteRow, type StudyBookRow,
} from './api';
import RecallEditor from './editor/RecallEditor.vue';
import { bodyBudget } from './editor/pure';
import { nextSaveState, noteDateLabel, noteLabel, savedAtLabel, type SaveState } from './notes';

/**
 * 공부 필기 — 책을 보며 그때그때 적는 글. 백지복습(안 보고 쓰기)과 짝이고, 나중에 그 글을 채점하는
 * <b>기준</b>이 된다.
 *
 * <p>백지복습과 규칙이 셋 다르다:
 * <ul>
 *   <li><b>책이 필수</b>다 — 책이 정리 축이자 채점의 연결고리라 책 없는 필기는 어느 목록에도 안 뜬다.
 *   <li><b>날짜가 없다</b> — 한 책에 몇 장이든 쓴다. 달력 하루 패널은 진입점일 뿐이다.
 *   <li><b>저장 버튼이 없다</b> — 쓰는 대로 저장된다(디바운스 1.5초). 그래서 아래 상태기계가 화면의 절반이다.
 * </ul>
 */
const props = defineProps<{
    books: StudyBookRow[];
    /** 그날 일정이 가리키는 책 — 기본 선택. 없으면 서재 첫 책. */
    defaultBookId: number | null;
}>();

/** 마지막 타이핑에서 저장까지의 틈. 잃을 수 있는 최대치가 이만큼이다. */
const DEBOUNCE_MS = 1500;

const bookId = ref<number | null>(null);
const notes = ref<NoteRow[]>([]);
const listError = ref('');

interface Draft { id: number | null; bookId: number | null; title: string; body: string; revision: number }

/**
 * 지금 편집기에 있는 장. `id === null`이면 아직 서버에 없는 초안이다.
 *
 * <p><b>초안이 자기 책을 들고 다닌다</b> — 저장할 때 `bookId.value`를 읽으면 그건 「전송 시점에 고른
 * 책」이라, 디바운스 중에 책을 바꾸면 앞 책에 쓰던 초안이 <b>새 책에</b> 생긴다(필기 이동은 비목표라
 * 되돌릴 수단도 없다).
 */
const draft = ref<Draft>(blank());
const state = ref<SaveState>({ kind: 'idle' });

let timer: ReturnType<typeof setTimeout> | undefined;
/** 지금 날아가 있는 저장. 왕복은 한 번에 하나이고, 끝난 뒤 dirty면 한 번 더 돈다(큐 길이 1). */
let inflightP: Promise<void> | null = null;
/**
 * 편집 대상의 세대. 다른 장·다른 책으로 갈아탈 때마다 오른다.
 *
 * <p>저장은 <b>보낼 때의 세대를 들고 갔다가 응답에서 대조</b>한다. 이게 없으면 왕복 중에 갈아탔을 때
 * 돌아온 응답이 <b>새 장에 옛 장의 id·revision</b>을 붙여, 이어 쓴 글이 앞 장을 덮어쓴다.
 */
let seq = 0;

function blank(): Draft {
    return { id: null, bookId: bookId.value, title: '', body: '', revision: 0 };
}

/** 편집 대상을 갈아 끼운다 — 날아가 있던 왕복의 응답이 여기 닿지 않게 세대를 올린다. */
function retarget(next: Draft): void {
    seq += 1;
    draft.value = next;
    state.value = { kind: 'idle' };
}

const hasBooks = computed(() => props.books.length > 0);
/** 409 뒤엔 잠근다 — 더 쓰게 두면 새로고침할 때 그만큼을 버리게 된다. */
const locked = computed(() => state.value.kind === 'conflict');

onMounted(() => {
    if (!hasBooks.value) return;
    bookId.value = props.defaultBookId ?? props.books[0].id;
    draft.value = blank(); // 초안이 첫 책을 들고 시작한다(ref 초기화 시점엔 아직 책이 없었다)
    void loadList();
});

// 탭이 닫히는 중의 마지막 보험. 1차 방어는 디바운스와 즉시 플러시이고, 이건 잃는 1.5초를 줄인다.
const onPageHide = (): void => { void flush(true); };
onMounted(() => window.addEventListener('pagehide', onPageHide));

onBeforeUnmount(() => {
    window.removeEventListener('pagehide', onPageHide);
    // 탭을 [백지노트]로 옮기면 이 컴포넌트가 통째로 사라진다 — 쓰던 것을 여기서 마저 보낸다.
    void flush();
});

/** 책을 바꾸면 쓰던 것을 먼저 보내고 목록을 갈아 끼운다(다른 책의 필기와 섞이지 않게). */
watch(bookId, async (id, before) => {
    if (id === null || before === null) return;
    await flush();
    retarget(blank());
    await loadList();
});

async function loadList(): Promise<void> {
    if (bookId.value === null) return;
    listError.value = '';
    try {
        notes.value = await fetchNotes(bookId.value);
    } catch {
        notes.value = [];
        listError.value = '필기 목록을 불러오지 못했어요.';
    }
}

/**
 * 고쳤다 — 디바운스를 다시 건다.
 *
 * <p><b>`invalid`(400)에서 아직 안 고쳐졌으면 아무것도 하지 않는 것</b>이 요점이다. 400은 사용자가
 * 줄이기 전엔 영원히 400이라, 키 입력마다 다시 보내면 무한 왕복이고 진짜 이유(서버가 준 문구)는
 * 그 소음에 묻힌다. 글이 상한 아래로 내려오면 그때 자동저장이 되살아난다.
 */
function onEdit(): void {
    if (state.value.kind === 'invalid' && bodyBudget(draft.value.body).over) return;
    state.value = nextSaveState(state.value, { type: 'edit' });
    clearTimer();
    timer = setTimeout(() => { void save(); }, DEBOUNCE_MS);
}

function clearTimer(): void {
    if (timer !== undefined) clearTimeout(timer);
    timer = undefined;
}

/**
 * 디바운스를 기다리지 않고 지금 보낸다 — 필기·책·탭 전환, 페이지 이탈.
 *
 * <p><b>왕복 중이면 끝나기를 기다린다.</b> 그냥 반환하면 그 사이에 친 글자가 어디에도 도달하지 않는다 —
 * 부르는 쪽(`openNote`·`newNote`·책 전환)은 이 반환을 「다 보냈다」로 믿고 초안을 갈아 끼우기 때문이다.
 */
function flush(keepalive = false): Promise<void> {
    clearTimer();
    return inflightP ? inflightP.then(() => save(keepalive)) : save(keepalive);
}

async function save(keepalive = false): Promise<void> {
    if (inflightP) return; // 왕복은 하나씩 — 끝난 뒤 아래 큐가 한 번 더 돌린다
    const p = sendOnce(keepalive).then(async () => {
        inflightP = null;
        if (state.value.kind === 'dirty') await save();
    });
    inflightP = p;
    await p;
}

async function sendOnce(keepalive: boolean): Promise<void> {
    const next = nextSaveState(state.value, { type: 'flush' });
    if (next.kind !== 'saving') return;
    const mine = seq;
    const { id, bookId: book, title, body, revision } = draft.value;
    // 빈 초안은 서버에 행을 만들지 않는다 — 「새 필기」를 눌러만 두고 떠나면 아무 일도 없어야 한다.
    if (book === null || body.trim().length === 0) {
        state.value = { kind: 'idle' };
        return;
    }

    state.value = next;
    try {
        const saved: Note = id === null
            ? await createNote({ bookId: book, title, body }, keepalive)
            : await updateNote(id, { title, body, revision }, keepalive);
        // 갈아탄 뒤 도착한 응답은 통째로 버린다 — id·revision을 되쓰면 새 장이 옛 장의 자리에 저장되고,
        // 목록 행도 남의 책에 꽂힌다. 화면에 없는 장의 성패를 상태줄에 띄울 이유도 없다.
        if (mine !== seq) return;
        // 본문·제목은 되받지 않는다 — 왕복 중에 사용자가 더 쳤을 수 있다. 판 번호와 시각만 갱신한다.
        draft.value.id = saved.id;
        draft.value.revision = saved.revision;
        state.value = nextSaveState(state.value, { type: 'ok', at: saved.updatedAt });
        upsertRow(saved);
    } catch (e) {
        // 상태 없는 실패(네트워크 끊김)는 0 — 5xx와 같은 갈래(재시도 대상)로 떨어진다.
        const status = e instanceof ApiError ? e.status : 0;
        const message = e instanceof Error && e.message ? e.message : '필기를 저장하지 못했어요.';
        if (mine !== seq) return;
        state.value = nextSaveState(state.value, { type: 'fail', status, message });
    }
}

/**
 * 저장된 장을 목록에 반영한다 — 다시 조회하지 않는다.
 *
 * <p>자동저장은 1.5초마다 두드리는 문이라 저장마다 목록을 다시 부르면 왕복이 두 배가 된다. 최근순
 * 정렬은 방금 저장한 장이 맨 앞이라는 뜻이므로 여기서 그대로 만들 수 있다.
 */
function upsertRow(saved: Note): void {
    const row: NoteRow = {
        id: saved.id, title: saved.title, chars: saved.body.length, updatedAt: saved.updatedAt,
    };
    notes.value = [row, ...notes.value.filter((n) => n.id !== saved.id)];
}

async function openNote(id: number): Promise<void> {
    await flush();
    const mine = seq + 1;
    seq = mine; // 조회 중에 또 갈아타면, 늦게 온 이쪽이 화면을 덮지 않는다
    try {
        const found = await fetchNote(id);
        if (mine !== seq) return;
        retarget({
            id: found.id, bookId: found.bookId, title: found.title ?? '',
            body: found.body, revision: found.revision,
        });
    } catch {
        listError.value = '필기를 불러오지 못했어요.';
    }
}

async function newNote(): Promise<void> {
    await flush();
    retarget(blank());
}

/** 409에서 서버 판을 다시 싣는다 — 내 미저장분은 버린다(덮어쓰기보다 정직하다). */
async function reload(): Promise<void> {
    const id = draft.value.id;
    state.value = { kind: 'idle' };
    if (id === null) return;
    await openNote(id);
}

async function remove(): Promise<void> {
    const id = draft.value.id;
    if (id === null || !window.confirm('이 필기를 지울까요? 되돌릴 수 없어요.')) return;
    clearTimer();
    // 지우기는 플러시하지 않는다(지울 글을 먼저 저장할 이유가 없다) — 대신 세대를 올려, 날아가 있던
    // 저장이 돌아와 **지운 장의 id**를 새 초안에 붙이는 것을 막는다.
    seq += 1;
    try {
        await deleteNote(id);
        notes.value = notes.value.filter((n) => n.id !== id);
        retarget(blank());
    } catch (e) {
        listError.value = e instanceof Error && e.message ? e.message : '필기를 지우지 못했어요.';
    }
}
</script>

<template>
    <div class="study-notes">
        <p class="study-day-label">필기</p>

        <p v-if="!hasBooks" class="status-line muted" data-testid="notes-no-books">
            공부 서재에 책을 먼저 담아 주세요 — 필기는 책에 붙어요.
            <a href="/study/books">공부 서재 열기</a>
        </p>

        <div v-else class="study-recall-split">
            <div class="study-recall-side">
                <div class="study-select-wrap">
                    <select v-model="bookId" class="study-recall-book" aria-label="필기할 책" data-testid="notes-book">
                        <option v-for="book in books" :key="book.id" :value="book.id">{{ book.title }}</option>
                    </select>
                    <svg class="study-select-chevron" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M6 9l6 6 6-6"/></svg>
                </div>

                <ul v-if="notes.length" class="study-notes-list">
                    <li v-for="note in notes" :key="note.id">
                        <button
                            type="button"
                            class="study-notes-item"
                            :class="{ 'is-active': note.id === draft.id }"
                            data-testid="notes-item"
                            @click="openNote(note.id)"
                        >
                            <!-- ⚠️ 목록 행에는 본문이 없다(서버 `NoteRow`가 안 싣는다 — 설계 §3.6의 값). 그래서
                                 제목 없는 장은 지금 열어 둔 장만 첫 줄에서 이름을 얻고, 나머지는 「제목 없음」이다.
                                 설계 §3.7이 `noteLabel(title, body)`를 목록 라벨로 두면서 §3.4의 목록 응답엔
                                 body를 안 넣은 어긋남이라 PR body에 드러냈다. -->
                            <span class="study-notes-label">{{ noteLabel(note.title, note.id === draft.id ? draft.body : '') }}</span>
                            <span class="study-notes-meta">{{ noteDateLabel(note.updatedAt) }} · {{ note.chars }}자</span>
                        </button>
                    </li>
                </ul>
                <p v-else class="status-line muted">이 책의 필기가 아직 없어요.</p>

                <button type="button" class="btn btn-ghost btn-small" data-testid="notes-new" @click="newNote">＋ 새 필기</button>
                <p v-if="listError" class="status-line study-error">{{ listError }}</p>
            </div>

            <div class="study-recall-main">
                <input
                    v-model="draft.title"
                    type="text"
                    maxlength="200"
                    placeholder="제목 (목록에 이름이 필요하면 적어 주세요)"
                    aria-label="필기 제목"
                    data-testid="notes-title"
                    :disabled="locked"
                    @input="onEdit"
                >

                <RecallEditor
                    v-model="draft.body"
                    aria-label="필기 본문"
                    :disabled="locked"
                    placeholder="책을 보며 그때그때 적어 보세요 — 쓰는 대로 저장돼요."
                    @update:model-value="onEdit"
                />

                <!-- 상태줄이 저장 버튼을 대신한다 — 「지금 저장됐나」를 사용자가 물을 곳이 여기뿐이다. -->
                <p class="status-line study-notes-status" :class="{ 'study-error': state.kind !== 'saving' && state.kind !== 'saved' }" data-testid="notes-status">
                    <template v-if="state.kind === 'saving'">저장 중…</template>
                    <template v-else-if="state.kind === 'saved'">저장됨 · {{ savedAtLabel(state.at) }}</template>
                    <template v-else-if="state.kind === 'conflict' || state.kind === 'invalid' || state.kind === 'error'">{{ state.message }}</template>
                </p>

                <div class="study-recall-actions">
                    <button
                        v-if="state.kind === 'conflict'"
                        type="button"
                        class="btn btn-primary btn-small"
                        data-testid="notes-reload"
                        @click="reload"
                    >새로고침</button>
                    <button
                        v-if="state.kind === 'error'"
                        type="button"
                        class="btn btn-ghost btn-small"
                        data-testid="notes-retry"
                        @click="flush()"
                    >다시 저장</button>
                    <button
                        v-if="draft.id !== null"
                        type="button"
                        class="btn btn-ghost btn-small"
                        data-testid="notes-delete"
                        @click="remove"
                    >지우기</button>
                </div>
            </div>
        </div>
    </div>
</template>
