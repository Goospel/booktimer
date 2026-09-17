<script setup lang="ts">
// 공부 서재(/study/books) — 미니앱 StudyLibrary의 웹판. 분류 축은 「회독 수」 하나다: 상태 탭·공개여부·
// 인기도·책방 링크가 없다. 그래서 독서 서재(BooksApp.vue 408줄)를 재사용하지 않고 books/pure.ts의 순수
// 헬퍼와 CSS 클래스만 빌린다(미니앱이 StudyLibrary.tsx를 따로 둔 것과 같은 판단).
//
// 구매 링크는 일부러 없다 — 공부 책엔 /books/{id}/buy 같은 클릭 추적 라우트가 없어서, 직링크로 열면
// 수익 경로가 무추적으로 열린다. 추적 라우트가 생기는 날 붙인다.
//
// linkUrl(강의 링크)은 반대다 — 제휴가 아니라 사용자가 직접 적은 바로가기라 직링크로 연다(추적할 수익이
// 없다). 그래서 purchaseLink 자리에 섞지 않는다: 섞으면 미니앱이 「알라딘에서 구매」 + 수수료 고지를 그려
// 인강 링크가 제휴 구매로 둔갑한다.
import { computed, onMounted, ref } from 'vue';

import { byline, coverColor, initialOf } from '../books/pure';
import {
    addStudyBook,
    deleteStudyBook,
    fetchStudyShelf,
    searchBooks,
    setStudyLink,
    setStudyReadCount,
    type SearchRow,
    type StudyBookRow,
} from './api';
import { readCountLabel, sameTitleExists, studyOwned } from './pure';

const books = ref<StudyBookRow[] | null>(null);   // null = 아직 못 받음(로딩 또는 실패)
const searchEnabled = ref(false);
const error = ref('');
const busy = ref(false);

const q = ref('');
const rows = ref<SearchRow[]>([]);
const searched = ref(false);
const manualTitle = ref('');
const manualAuthor = ref('');
const manualLink = ref('');

/** 내 공부 서재 isbn 집합 — 검색 행의 owned(독서 책장 기준)를 대신할 판정 재료. */
const myIsbns = computed(() => new Set(
    (books.value ?? []).map((b) => b.isbn13).filter((i): i is string => i !== null),
));

function msg(e: unknown, fallback: string): string {
    return e instanceof Error && e.message ? e.message : fallback;
}

async function load() {
    error.value = '';
    try {
        const shelf = await fetchStudyShelf();
        books.value = shelf.books;
        searchEnabled.value = shelf.searchEnabled;
    } catch (e) {
        error.value = msg(e, '공부 서재를 불러오지 못했어요.');
    }
}

/**
 * 뮤테이션 뒤엔 목록을 다시 받는다(미니앱과 같다) — 담기·삭제는 목록 자체가 흔들리고, 회독만 바뀌어도
 * 한 왕복이 화면과 서버를 맞춘다. busy 동안 버튼을 전부 잠가 「옛 값으로 같은 절대값을 다시 보내는」 창을 없앤다.
 */
async function run(action: Promise<unknown>) {
    busy.value = true;
    error.value = '';
    try {
        await action;
        await load();
    } catch (e) {
        error.value = msg(e, '처리하지 못했어요.');
    } finally {
        busy.value = false;
    }
}

async function runSearch() {
    if (!q.value.trim()) return;
    try {
        rows.value = await searchBooks(q.value.trim());
    } catch (e) {
        rows.value = [];
        error.value = msg(e, '검색하지 못했어요.');
    }
    searched.value = true;
}

function addRow(r: SearchRow) {
    return run(addStudyBook({
        title: r.title, author: r.author, isbn13: r.isbn13,
        coverUrl: r.coverUrl, publisher: r.publisher, purchaseLink: r.purchaseLink,
        linkUrl: null,   // 검색 결과엔 강의 링크가 없다 — 「직접 추가」만 채운다.
    }));
}

/**
 * 직접 추가 — 인강이 들어오는 문이다(제목 + 저자·강사 + 강의 링크, 전부 ISBN·표지 없이).
 *
 * <p>같은 제목이면 한 번 묻는다: 서버는 「isbn 없는 책 여러 권」을 허용하므로 몇 달 뒤 잊고 다시 담으면
 * 누적 시간이 두 행으로 갈리고 <b>합칠 기능이 없다</b>.
 */
function addManual() {
    const title = manualTitle.value.trim();
    if (!title) return;
    if (sameTitleExists(books.value ?? [], title)
        && !confirm('같은 제목이 이미 서재에 있어요. 그래도 추가할까요?')) return;
    return run(addStudyBook({
        title, author: manualAuthor.value.trim() || null,
        isbn13: null, coverUrl: null, publisher: null, purchaseLink: null,
        linkUrl: manualLink.value.trim() || null,
    }).then(() => {
        manualTitle.value = '';
        manualAuthor.value = '';
        manualLink.value = '';
    }));
}

/**
 * 강의 링크를 갈아끼우거나 지운다 — 네이티브 {@code prompt}다(인라인 편집기는 YAGNI).
 * 취소(null)와 빈 문자열(해제)은 <b>다르다</b> — 취소가 해제로 둔갑하면 실수로 링크를 잃는다.
 */
function editLink(b: StudyBookRow) {
    const next = prompt('강의 링크 (비우면 해제)', b.linkUrl ?? '');
    if (next === null) return;
    run(setStudyLink(b.id, next.trim() || null));
}

function setCount(b: StudyBookRow, next: number) {
    return run(setStudyReadCount(b.id, next));
}

function remove(b: StudyBookRow) {
    if (!confirm(`「${b.title}」을(를) 공부 서재에서 뺄까요? 이 책의 회독 수도 함께 사라져요.`)) return;
    run(deleteStudyBook(b.id));
}

/** 무표지 책의 표지 플레이스홀더 색 — seed는 isbn13 우선, 없으면 제목(독서 서재와 같은 결정적 매핑). */
function coverStyle(b: { isbn13: string | null; title: string }): Record<string, string> {
    const c = coverColor(b.isbn13 || b.title);
    return { background: c.bg, color: c.fg };
}

function formatTime(secs: number): string {
    return `${Math.floor(secs / 3600)}시간 ${Math.floor((secs % 3600) / 60)}분`;
}

onMounted(load);
</script>

<template>
    <!-- #study-app은 #dashboard-app 류 gap 목록에 없다 → .page-stack 래퍼 패턴(app.css) -->
    <div class="page-stack">
        <header class="history-greeting">
            <h1>공부 서재</h1>
            <p>회독 수로 세는 책장이에요. 공부 측정에 책을 걸면 누적 시간이 쌓여요.</p>
        </header>

        <div v-if="error" class="alert alert-error">
            {{ error }}
            <button v-if="books === null" type="button" class="link-btn" @click="load">다시 시도</button>
        </div>

        <section class="card is-study">
            <h2>책 담기</h2>
            <form v-if="searchEnabled" class="book-search-form" @submit.prevent="runSearch">
                <div class="search-row">
                    <input type="text" v-model="q" placeholder="책 제목으로 검색" required>
                    <button type="submit" class="btn-primary">검색</button>
                </div>
            </form>
            <p v-else class="status-line muted">
                도서 검색이 아직 설정되지 않았어요. 아래에서 직접 등록하세요.
            </p>

            <ul v-if="rows.length" class="book-list search-scroll">
                <li v-for="r in rows" :key="(r.isbn13 ?? '') + r.title" class="book-row">
                    <img v-if="r.coverUrl" class="book-cover" :src="r.coverUrl" alt="" loading="lazy" referrerpolicy="no-referrer">
                    <span v-else class="book-cover book-cover-ph" :style="coverStyle(r)" aria-hidden="true">{{ initialOf(r.title) }}</span>
                    <div class="book-meta">
                        <span class="book-title">{{ r.title }}</span>
                        <span v-if="byline(r.author, r.publisher)" class="book-byline">{{ byline(r.author, r.publisher) }}</span>
                    </div>
                    <div class="book-actions">
                        <!-- 응답의 owned는 독서 책장 기준 — 내 공부 서재 isbn 집합으로 다시 센다. -->
                        <span v-if="studyOwned(myIsbns, r.isbn13)" class="shelf-owned-badge">서재에 있어요</span>
                        <button v-else type="button" class="btn-primary" :disabled="busy" @click="addRow(r)">담기</button>
                    </div>
                </li>
            </ul>
            <p v-if="searched && rows.length === 0" class="status-line muted">검색 결과가 없어요.</p>

            <details class="manual-add" :open="!searchEnabled">
                <summary>찾는 책이 없거나 인강인가요? <span class="manual-add-cta">직접 추가</span></summary>
                <form class="book-manual-form" @submit.prevent="addManual">
                    <input type="text" v-model="manualTitle" placeholder="제목" required maxlength="300">
                    <input type="text" v-model="manualAuthor" placeholder="저자·강사 (선택)">
                    <!-- type=url은 오타를 걸러 줄 뿐이다 — javascript: 는 통과시키므로 서버 검사가 유일한 벨트다. -->
                    <input type="url" v-model="manualLink" placeholder="강의 링크 (선택, https://…)" maxlength="1000">
                    <button type="submit" class="btn-primary" :disabled="busy">추가</button>
                </form>
            </details>
        </section>

        <section class="card is-study">
            <h2>내 공부 책 <span v-if="books" class="muted">{{ books.length }}권</span></h2>
            <p v-if="books === null && !error" class="status-line">불러오는 중…</p>
            <p v-else-if="books && books.length === 0" class="status-line">아직 공부 책이 없어요. 위에서 담아 보세요.</p>
            <ul v-else-if="books" class="book-list shelf-list">
                <li v-for="b in books" :key="b.id" class="book-row">
                    <img v-if="b.coverUrl" class="book-cover" :src="b.coverUrl" alt="" loading="lazy" referrerpolicy="no-referrer">
                    <span v-else class="book-cover book-cover-ph" :style="coverStyle(b)" aria-hidden="true">{{ initialOf(b.title) }}</span>
                    <div class="book-meta">
                        <span class="book-title">{{ b.title }}</span>
                        <span v-if="b.author" class="book-author">{{ b.author }}</span>
                        <span class="study-read-chip">{{ readCountLabel(b.readCount) }}</span>
                        <!-- 0초는 「아직 그 책으로 안 쟀다」는 부재 — 칩을 안 그린다(0독과 반대). -->
                        <span v-if="b.totalSeconds > 0" class="book-time mono">{{ formatTime(b.totalSeconds) }} 공부</span>
                    </div>
                    <div class="book-actions study-count-actions">
                        <!-- 제휴가 아니라 사용자 바로가기라 직링크로 연다. rel은 새 탭에 opener를 안 넘기려는 것. -->
                        <a v-if="b.linkUrl" class="btn-ghost" :href="b.linkUrl"
                           target="_blank" rel="noopener noreferrer">강의 열기</a>
                        <button type="button" class="btn-ghost" :disabled="busy" @click="editLink(b)">링크</button>
                        <!-- 「−」는 U+2212 — ASCII 하이픈은 좁은 폭에서 점처럼 보인다. -->
                        <button type="button" class="btn-ghost study-count-minus" :disabled="busy || b.readCount === 0"
                                aria-label="회독 하나 빼기" @click="setCount(b, b.readCount - 1)">−</button>
                        <button type="button" class="btn-ghost study-count-plus" :disabled="busy"
                                aria-label="회독 하나 더하기" @click="setCount(b, b.readCount + 1)">회독 +1</button>
                        <button type="button" class="btn-danger" :disabled="busy" @click="remove(b)">삭제</button>
                    </div>
                </li>
            </ul>
        </section>
    </div>
</template>
