<script setup lang="ts">
// 필기 화면(/study/notes) — 공부 바 「필기」가 여는 곳. 책별 목록만 보이고, 행을 누르면 홈 편집기가 그 장을 연다(/?note=).
// 편집기는 홈 하나다 — 두 곳에서 같은 장을 고치면 409 잠금으로 사용자가 자기 글을 잃는 경로가 생긴다(설계 D1).
import { onMounted, ref, watch } from 'vue';

import { fetchNotes, fetchStudyBooks, type NoteRow, type StudyBookRow } from './api';
import { noteDateLabel, noteLabel } from './notes';
import { notesBookParam } from './pure';

const books = ref<StudyBookRow[]>([]);
const bookId = ref<number | null>(null);
const notes = ref<NoteRow[]>([]);
const loading = ref(true);
const error = ref('');

async function load() {
    if (bookId.value === null) {
        loading.value = false;
        return;
    }
    loading.value = true;
    error.value = '';
    try {
        notes.value = await fetchNotes(bookId.value);
    } catch (e) {
        notes.value = [];
        error.value = e instanceof Error && e.message ? e.message : '필기 목록을 불러오지 못했어요.';
    } finally {
        loading.value = false;
    }
}

onMounted(async () => {
    books.value = await fetchStudyBooks();
    bookId.value = notesBookParam(location.search, books.value);
    if (bookId.value === null) loading.value = false;   // 서재 0권 — watch가 안 도니 여기서 로딩을 끝낸다
});

// 첫 확정(null → id)도 이 watch가 부른다 — 목록 요청은 책 하나에 1회다.
watch(bookId, load);
</script>

<template>
    <div class="page-stack">
        <header class="history-greeting">
            <h1>필기</h1>
            <p>책마다 쓴 필기를 모아 봐요. 누르면 홈에서 이어 쓸 수 있어요.</p>
        </header>

        <section class="card is-study">
            <p v-if="!loading && books.length === 0" class="status-line muted">
                공부 서재가 비어 있어요. <a href="/study/books">공부 서재 열기</a>
            </p>
            <template v-else>
                <div class="study-day">
                    <div class="study-select-wrap">
                        <select v-model="bookId" class="study-recall-book" aria-label="필기를 볼 책" data-testid="notes-page-book">
                            <option v-for="book in books" :key="book.id" :value="book.id">{{ book.title }}</option>
                        </select>
                        <svg class="study-select-chevron" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M6 9l6 6 6-6"/></svg>
                    </div>
                </div>

                <p v-if="error" class="status-line study-error">
                    {{ error }}
                    <button type="button" class="link-btn" @click="load">다시 시도</button>
                </p>
                <ul v-else-if="notes.length" class="study-notes-list">
                    <li v-for="note in notes" :key="note.id">
                        <a class="study-notes-item" :href="`/?note=${note.id}`" data-testid="notes-page-item">
                            <span class="study-notes-label">{{ noteLabel(note.title, note.preview) }}</span>
                            <span class="study-notes-meta">{{ noteDateLabel(note.updatedAt) }} · {{ note.chars }}자</span>
                        </a>
                    </li>
                </ul>
                <p v-else-if="!loading" class="status-line muted">이 책의 필기가 아직 없어요.</p>
            </template>
        </section>
    </div>
</template>
