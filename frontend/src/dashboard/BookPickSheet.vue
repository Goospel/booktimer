<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { getCsrfToken } from '../shared/follow'
import { initialOf, coverColor, byline, statusBadge } from '../books/pure'
import type { BookOption } from './types'

// 통합 책 시트(발견 1, §6.5) — 하나의 컴포넌트가 두 모드를 겸한다:
//  · mode='start' : 측정 전 "무슨 책을 읽을지" 고르기. 고르면 pick(id), 아래 CTA는 '책 없이 측정하기'(bookless).
//  · mode='tag'   : 측정 종료 후 "무슨 책이었나요?" 태깅. 고르면 pick(id), 아래 CTA는 '건너뛰기'(skip).
// 표지·저자·상태·검색담기는 책장 API(/api/books·/api/books/search)를 그대로 재사용 → 백엔드 변경 0.
// 시트가 열릴 때 /api/books로 표지 목록을 로드한다. 실패하면 부모가 넘긴 목록(제목만)으로 폴백.

interface ShelfBook {
    id: number; title: string; author: string | null; coverUrl: string | null
    isbn13: string | null; status: string; statusLabel: string
}
interface SearchRow {
    title: string; author: string | null; isbn13: string | null; coverUrl: string | null
    publisher: string | null; purchaseLink: string | null; category: string | null
    pubDate: string | null; owned: boolean
}

const props = defineProps<{
    mode: 'start' | 'tag'
    // 로드 실패 시 폴백(제목만) — 정상 경로에선 /api/books 응답을 쓴다.
    readingBooks: BookOption[]
    finishedBooks: BookOption[]
    wantToReadBooks: BookOption[]
    pending?: boolean
}>()

const emit = defineEmits<{
    pick: [bookId: number]
    bookless: []
    skip: []
    close: []
    added: [book: { id: number; title: string; status: string }]
}>()

const title = computed(() => props.mode === 'tag' ? '무슨 책을 읽으셨나요?' : '측정할 책을 고르세요')
const hint = computed(() => props.mode === 'tag'
    ? '방금 측정한 독서에 책을 연결해요. 나중에 정해도 괜찮아요.'
    : '무슨 책을 읽을지 고르거나, 책 없이 바로 시작할 수 있어요.')

const STATUS_TABS = [
    { key: 'ALL', label: '전체' },
    { key: 'READING', label: '읽는 중' },
    { key: 'FINISHED', label: '완독' },
    { key: 'WANT_TO_READ', label: '읽고 싶음' },
]
const filter = ref('ALL')

// 책장 로드(표지·저자·상태·검색가능 여부) — 없으면 폴백.
const shelf = ref<ShelfBook[]>([])
const searchEnabled = ref(false)
const loadFailed = ref(false)

const fallback = computed<ShelfBook[]>(() => [
    ...props.readingBooks.map(b => ({ id: b.id, title: b.title, author: null, coverUrl: null, isbn13: null, status: 'READING', statusLabel: '읽는 중' })),
    ...props.finishedBooks.map(b => ({ id: b.id, title: b.title, author: null, coverUrl: null, isbn13: null, status: 'FINISHED', statusLabel: '완독' })),
    ...props.wantToReadBooks.map(b => ({ id: b.id, title: b.title, author: null, coverUrl: null, isbn13: null, status: 'WANT_TO_READ', statusLabel: '읽고 싶음' })),
])
const allBooks = computed(() => loadFailed.value ? fallback.value : shelf.value)
const books = computed(() => filter.value === 'ALL' ? allBooks.value : allBooks.value.filter(b => b.status === filter.value))

async function loadShelf() {
    try {
        const res = await fetch('/api/books', { credentials: 'same-origin' })
        if (!res.ok) throw new Error('load failed')
        const data = await res.json()
        shelf.value = (data.books ?? []).map((b: ShelfBook) => ({
            id: b.id, title: b.title, author: b.author, coverUrl: b.coverUrl,
            isbn13: b.isbn13, status: b.status, statusLabel: b.statusLabel,
        }))
        searchEnabled.value = data.searchEnabled ?? false
    } catch {
        loadFailed.value = true
    }
}

// 검색·담기(searchEnabled일 때만) — 책장 검색을 그대로 재사용해 시트 안에서 새 책을 담고 바로 고른다.
const q = ref('')
const rows = ref<SearchRow[]>([])
const searched = ref(false)
async function runSearch() {
    if (!q.value.trim()) return
    try {
        const params = new URLSearchParams({ q: q.value, type: 'TITLE', page: '1' })
        const res = await fetch(`/api/books/search?${params}`, { credentials: 'same-origin' })
        const data = await res.json()
        rows.value = data.results ?? []
        searched.value = true
    } catch {
        rows.value = []; searched.value = true
    }
}
async function addRow(row: SearchRow) {
    const res = await fetch('/api/books', {
        method: 'POST', credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': getCsrfToken() },
        body: JSON.stringify({
            title: row.title, author: row.author, isbn13: row.isbn13, coverUrl: row.coverUrl,
            publisher: row.publisher, purchaseLink: row.purchaseLink, category: row.category,
            pubDate: row.pubDate, status: 'WANT_TO_READ',
        }),
    })
    if (!res.ok) return
    const saved = await res.json() as ShelfBook
    if (!shelf.value.find(b => b.id === saved.id)) shelf.value = [saved, ...shelf.value]
    row.owned = true // 담은 직후 그 검색행을 '이미 있음'으로 전환(응답 owned 재조회 없이 로컬 갱신)
    emit('added', { id: saved.id, title: saved.title, status: saved.status })
}

function coverStyle(b: { isbn13: string | null; title: string }) {
    const c = coverColor(b.isbn13 || b.title)
    return { background: c.bg, color: c.fg }
}
function statusStyle(status: string) {
    const s = statusBadge(status)
    return { background: s.bg, color: s.fg }
}

const overlayEl = ref<HTMLElement | null>(null)
onMounted(() => { overlayEl.value?.focus(); loadShelf() })
</script>

<template>
    <div ref="overlayEl" class="book-sheet-overlay" @click.self="emit('close')" @keydown.esc="emit('close')" tabindex="-1">
        <div class="book-sheet-panel" role="dialog" aria-modal="true" aria-labelledby="book-sheet-title">
            <div class="sheet-handle" aria-hidden="true"></div>
            <div class="book-sheet-head">
                <p id="book-sheet-title" class="book-sheet-title">{{ title }}</p>
                <button type="button" class="book-sheet-close" aria-label="닫기" @click="emit('close')">✕</button>
            </div>
            <p class="book-sheet-hint">{{ hint }}</p>

            <!-- 검색해서 담기 — 검색 가능할 때만. 카탈로그에서 찾아 시트 안에서 바로 책장에 담는다. -->
            <form v-if="searchEnabled" class="book-sheet-search" @submit.prevent="runSearch">
                <input type="text" v-model="q" placeholder="책 제목으로 검색" aria-label="책 검색">
                <button type="submit" class="book-sheet-search-btn" aria-label="검색">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                        <circle cx="11" cy="11" r="7"/><path d="m21 21-4.3-4.3"/>
                    </svg>
                </button>
            </form>
            <ul v-if="rows.length" class="book-sheet-list book-sheet-results">
                <li v-for="row in rows" :key="(row.isbn13 ?? '') + row.title" class="book-sheet-result">
                    <img v-if="row.coverUrl" class="book-sheet-cover" :src="row.coverUrl" alt="" loading="lazy" referrerpolicy="no-referrer">
                    <span v-else class="book-sheet-cover" :style="coverStyle(row)" aria-hidden="true">{{ initialOf(row.title) }}</span>
                    <div class="book-sheet-meta">
                        <span class="book-sheet-book-title">{{ row.title }}</span>
                        <span v-if="byline(row.author, row.publisher)" class="book-sheet-byline">{{ byline(row.author, row.publisher) }}</span>
                    </div>
                    <span v-if="row.owned" class="book-sheet-owned">책장에 있어요</span>
                    <button v-else type="button" class="book-sheet-add-btn" :disabled="pending" @click="addRow(row)">담기</button>
                </li>
            </ul>
            <p v-else-if="searched" class="book-sheet-hint">검색 결과가 없어요.</p>

            <!-- 상태 필터칩 -->
            <div class="book-sheet-filters" role="group" aria-label="상태 필터">
                <button v-for="t in STATUS_TABS" :key="t.key" type="button" class="book-sheet-chip"
                        :class="{ active: filter === t.key }" @click="filter = t.key">{{ t.label }}</button>
            </div>

            <!-- 내 책 목록 — 표지 + 제목/저자 + 상태 배지. 고르면 pick. -->
            <ul v-if="books.length" class="book-sheet-list">
                <li v-for="b in books" :key="b.id">
                    <button type="button" class="book-sheet-book" :disabled="pending" @click="emit('pick', b.id)">
                        <img v-if="b.coverUrl" class="book-sheet-cover" :src="b.coverUrl" alt="" loading="lazy" referrerpolicy="no-referrer">
                        <span v-else class="book-sheet-cover" :style="coverStyle(b)" aria-hidden="true">{{ initialOf(b.title) }}</span>
                        <span class="book-sheet-meta">
                            <span class="book-sheet-book-title">{{ b.title }}</span>
                            <span v-if="b.author" class="book-sheet-byline">{{ b.author }}</span>
                        </span>
                        <span class="book-sheet-badge" :style="statusStyle(b.status)">{{ b.statusLabel }}</span>
                    </button>
                </li>
            </ul>
            <p v-else class="book-sheet-empty">아직 책장에 책이 없어요. 위에서 검색해 담거나, 책 없이 시작해도 돼요.</p>

            <!-- 하단 CTA — start=책 없이 측정 / tag=건너뛰기. -->
            <button v-if="mode === 'start'" type="button" class="book-sheet-cta" :disabled="pending" @click="emit('bookless')">
                책 없이 측정하기
            </button>
            <button v-else type="button" class="book-sheet-cta" :disabled="pending" @click="emit('skip')">
                책 없이 기록 · 건너뛰기
            </button>
        </div>
    </div>
</template>
