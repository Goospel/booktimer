<script setup lang="ts">
// 필기 화면(/study/notes) — 공부 바 「필기」가 여는 곳. 책별 목록만 보이고, 행을 누르면 홈 편집기가 그 장을 연다(/?note=).
// 편집기는 홈 하나다 — 두 곳에서 같은 장을 고치면 409 잠금으로 사용자가 자기 글을 잃는 경로가 생긴다(설계 D1).
import { onMounted, ref, watch } from 'vue';

import { fetchNotes, fetchStudyShelf, type NoteRow, type StudyBookRow } from './api';
import { noteDateLabel, noteLabel } from './notes';
import { notesBookParam } from './pure';

const books = ref<StudyBookRow[] | null>(null);   // null = 아직 못 받음(로딩 또는 실패)
const bookId = ref<number | null>(null);
const notes = ref<NoteRow[]>([]);
const loading = ref(true);
const error = ref('');

function msg(e: unknown, fallback: string): string {
    return e instanceof Error && e.message ? e.message : fallback;
}

/**
 * 서재를 받는다 — 실패를 「서재 0권」과 가르는 것이 요점이다(`fetchStudyBooks`는 실패에 빈 배열을 줘서
 * 서버 500에도 「비어 있어요」가 떴다). 그래서 던지는 `fetchStudyShelf`를 쓴다.
 */
async function init() {
    loading.value = true;
    error.value = '';
    try {
        books.value = (await fetchStudyShelf()).books;
    } catch (e) {
        error.value = msg(e, '공부 서재를 불러오지 못했어요.');
        loading.value = false;
        return;
    }
    bookId.value = notesBookParam(location.search, books.value);
    if (bookId.value === null) loading.value = false;   // 서재 0권 — watch가 안 도니 여기서 로딩을 끝낸다
}

async function load() {
    const want = bookId.value;
    if (want === null) {
        loading.value = false;
        return;
    }
    loading.value = true;
    error.value = '';
    try {
        const rows = await fetchNotes(want);
        // 기다리는 사이 책이 바뀌었으면 버린다 — 늦게 온 앞 책 응답이 지금 책 목록을 덮으면 안 된다.
        if (want !== bookId.value) return;
        notes.value = rows;
    } catch (e) {
        if (want !== bookId.value) return;
        notes.value = [];
        error.value = msg(e, '필기 목록을 불러오지 못했어요.');
    } finally {
        if (want === bookId.value) loading.value = false;
    }
}

/** 「다시 시도」 — 서재부터 못 받았으면 서재를, 아니면 목록을 다시 받는다. */
function retry() {
    return books.value === null ? init() : load();
}

onMounted(init);

// 첫 확정(null → id)도 이 watch가 부른다 — 목록 요청은 책 하나에 1회다.
watch(bookId, load);
</script>

<template>
    <div class="page-stack">
        <header class="history-greeting">
            <h1>필기</h1>
            <p>책마다 쓴 필기를 모아 봐요. 누르면 홈에서 이어 쓸 수 있어요.</p>
        </header>

        <!-- 「다시 시도」는 공부 서재·내 책장과 같은 에러 박스(.shelf-load-error) 안에 둔다 — .link-btn 스타일이
             그 안에서만 걸려서, 밖에 두면 전역 button 규칙(전폭·연필테)을 받는다. -->
        <div v-if="error" class="shelf-load-error" role="alert" data-testid="notes-page-error">
            {{ error }}
            <button type="button" class="link-btn" @click="retry">다시 시도</button>
        </div>

        <section class="card is-study">
            <p v-if="books !== null && books.length === 0" class="status-line muted">
                공부 서재가 비어 있어요. <a href="/study/books">공부 서재 열기</a>
            </p>
            <template v-else-if="books !== null">
                <div class="study-day">
                    <div class="study-select-wrap">
                        <select v-model="bookId" class="study-recall-book" aria-label="필기를 볼 책" data-testid="notes-page-book">
                            <option v-for="book in books" :key="book.id" :value="book.id">{{ book.title }}</option>
                        </select>
                        <svg class="study-select-chevron" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M6 9l6 6 6-6"/></svg>
                    </div>
                </div>

                <ul v-if="notes.length" class="study-notes-list">
                    <li v-for="note in notes" :key="note.id">
                        <a class="study-notes-item" :href="`/?note=${note.id}`" data-testid="notes-page-item">
                            <span class="study-notes-label">{{ noteLabel(note.title, note.preview) }}</span>
                            <span class="study-notes-meta">{{ noteDateLabel(note.updatedAt) }} · {{ note.chars }}자</span>
                        </a>
                    </li>
                </ul>
                <p v-else-if="!loading && !error" class="status-line muted">이 책의 필기가 아직 없어요.</p>
            </template>
        </section>
    </div>
</template>
