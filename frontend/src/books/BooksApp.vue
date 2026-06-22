<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getCsrfToken } from '../shared/follow'

const STATUSES = [
    { name: 'WANT_TO_READ', label: '읽고 싶음' },
    { name: 'READING', label: '읽는 중' },
    { name: 'FINISHED', label: '완독' },
]

interface Popularity { wantCount: number; readCount: number }
interface MyBookSummary {
    id: number; title: string; author: string | null; coverUrl: string | null
    isbn13: string | null; status: string; statusLabel: string
    visibility: string; visibilityLabel: string; isPublic: boolean
    seconds: number; purchaseLink: string | null
}
interface SearchRow {
    title: string; author: string | null; isbn13: string | null; coverUrl: string | null
    publisher: string | null; purchaseLink: string | null; category: string | null
    pubDate: string | null; owned: boolean; selectedStatus: string
}

const appEl = document.getElementById('books-app')
const myLoginId = ref<string>(appEl?.dataset.myLoginId ?? '')

const loading = ref(true)
const books = ref<MyBookSummary[]>([])
const popularity = ref<Record<string, Popularity>>({})
const searchEnabled = ref(false)
const coupangEnabled = ref(false)
const nickname = ref('')
const error = ref<string | null>(null)

const statusFilter = ref<string | null>(null)
const visFilter = ref<string | null>(null)

const q = ref('')
const searchType = ref('TITLE')
const searchResults = ref<SearchRow[]>([])
const searchPopularity = ref<Record<string, Popularity>>({})
const searched = ref(false)

const manualStatus = ref('WANT_TO_READ')
const manualTitle = ref('')
const manualAuthor = ref('')

const shelfBooks = computed(() => books.value.filter(b =>
    (!statusFilter.value || b.status === statusFilter.value) &&
    (!visFilter.value || b.visibility === visFilter.value)
))
const manualOpen = computed(() => !searchEnabled.value || (searched.value && searchResults.value.length === 0))

function popularityFor(isbn: string | null): Popularity | null {
    if (!isbn) return null
    const pop = popularity.value[isbn]
    return pop && (pop.wantCount > 0 || pop.readCount > 0) ? pop : null
}
function popularityForSearch(isbn: string | null): Popularity | null {
    if (!isbn) return null
    const pop = searchPopularity.value[isbn]
    return pop && (pop.wantCount > 0 || pop.readCount > 0) ? pop : null
}
function formatTime(secs: number): string {
    return `${Math.floor(secs / 3600)}시간 ${Math.floor((secs % 3600) / 60)}분`
}

async function load() {
    loading.value = true
    try {
        const res = await fetch('/api/books', { credentials: 'same-origin' })
        const data = await res.json()
        books.value = data.books ?? []
        popularity.value = data.popularity ?? {}
        searchEnabled.value = data.searchEnabled ?? false
        coupangEnabled.value = data.coupangEnabled ?? false
        nickname.value = data.nickname ?? ''
    } finally {
        loading.value = false
    }
}
onMounted(load)

function selectStatus(name: string | null) { statusFilter.value = name }
function selectVis(name: string | null) { visFilter.value = name }

async function runSearch() {
    if (!q.value.trim()) return
    const params = new URLSearchParams({ q: q.value, type: searchType.value, page: '1' })
    const res = await fetch(`/api/books/search?${params}`, { credentials: 'same-origin' })
    const data = await res.json()
    searchResults.value = (data.results ?? []).map((r: Omit<SearchRow, 'selectedStatus'>) =>
        ({ ...r, selectedStatus: 'WANT_TO_READ' })
    )
    searchPopularity.value = data.popularity ?? {}
    searched.value = true
}

async function addFromResult(row: SearchRow) {
    const res = await fetch('/api/books', {
        method: 'POST', credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': getCsrfToken() },
        body: JSON.stringify({
            title: row.title, author: row.author, isbn13: row.isbn13,
            coverUrl: row.coverUrl, publisher: row.publisher, purchaseLink: row.purchaseLink,
            category: row.category, pubDate: row.pubDate, status: row.selectedStatus,
        }),
    })
    if (!res.ok) { error.value = '추가에 실패했습니다.'; return }
    const saved: MyBookSummary = await res.json()
    if (!books.value.find(b => b.id === saved.id)) books.value.unshift(saved)
    row.owned = true
}

async function addManual() {
    if (!manualTitle.value.trim()) return
    const res = await fetch('/api/books', {
        method: 'POST', credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': getCsrfToken() },
        body: JSON.stringify({ title: manualTitle.value, author: manualAuthor.value, status: manualStatus.value }),
    })
    if (!res.ok) { error.value = '추가에 실패했습니다.'; return }
    const saved: MyBookSummary = await res.json()
    books.value.unshift(saved)
    manualTitle.value = ''; manualAuthor.value = ''; manualStatus.value = 'WANT_TO_READ'
}

async function changeStatus(book: MyBookSummary, newStatus: string) {
    const prev = { status: book.status, statusLabel: book.statusLabel }
    book.status = newStatus
    book.statusLabel = STATUSES.find(s => s.name === newStatus)?.label ?? newStatus
    const res = await fetch(`/api/books/${book.id}/status`, {
        method: 'POST', credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': getCsrfToken() },
        body: JSON.stringify({ status: newStatus }),
    })
    if (!res.ok) {
        book.status = prev.status; book.statusLabel = prev.statusLabel
        error.value = '상태 변경에 실패했습니다.'; return
    }
    const updated: MyBookSummary = await res.json()
    book.status = updated.status; book.statusLabel = updated.statusLabel
}

async function toggleVisibility(book: MyBookSummary) {
    const prev = { isPublic: book.isPublic, visibility: book.visibility, visibilityLabel: book.visibilityLabel }
    const newVis = book.isPublic ? 'PRIVATE' : 'PUBLIC'
    book.isPublic = !book.isPublic
    book.visibility = newVis
    book.visibilityLabel = book.isPublic ? '공개' : '비공개'
    const res = await fetch(`/api/books/${book.id}/visibility`, {
        method: 'POST', credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': getCsrfToken() },
        body: JSON.stringify({ visibility: newVis }),
    })
    if (!res.ok) {
        book.isPublic = prev.isPublic; book.visibility = prev.visibility; book.visibilityLabel = prev.visibilityLabel
        error.value = '공개 설정 변경에 실패했습니다.'; return
    }
    const updated: MyBookSummary = await res.json()
    book.isPublic = updated.isPublic; book.visibility = updated.visibility; book.visibilityLabel = updated.visibilityLabel
}

async function removeBook(book: MyBookSummary) {
    if (!confirm('이 책을 삭제할까요?')) return
    const res = await fetch(`/api/books/${book.id}/delete`, {
        method: 'POST', credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': getCsrfToken() },
        body: JSON.stringify({}),
    })
    if (!res.ok) { error.value = '삭제에 실패했습니다.'; return }
    books.value = books.value.filter(b => b.id !== book.id)
}
</script>

<template>
  <div v-if="loading" class="status-line">불러오는 중…</div>
  <template v-else>
    <p class="greeting"><span>{{ nickname }}</span>님의 책장</p>
    <div v-if="error" class="alert alert-error">{{ error }}</div>

    <!-- 책 추가 카드 -->
    <section class="card">
      <h2>책 추가</h2>
      <form v-if="searchEnabled" @submit.prevent="runSearch" class="book-search-form">
        <div class="search-type" role="radiogroup" aria-label="검색 기준">
          <label><input type="radio" name="type" value="TITLE" v-model="searchType"><span>제목</span></label>
          <label><input type="radio" name="type" value="AUTHOR" v-model="searchType"><span>저자</span></label>
          <label><input type="radio" name="type" value="PUBLISHER" v-model="searchType"><span>출판사</span></label>
        </div>
        <div class="search-row">
          <input type="text" v-model="q" placeholder="검색어 입력" required>
          <button type="submit" class="btn-primary">검색</button>
        </div>
      </form>
      <p v-if="!searchEnabled" class="status-line muted">
        도서 검색이 아직 설정되지 않았습니다. 아래에서 직접 등록하세요.
      </p>

      <ul v-if="searchResults.length" class="book-list search-scroll">
        <li v-for="row in searchResults" :key="(row.isbn13 ?? '') + row.title" class="book-row">
          <img v-if="row.coverUrl" class="book-cover" :src="row.coverUrl" alt="" loading="lazy" referrerpolicy="no-referrer">
          <div class="book-meta">
            <span class="book-title">{{ row.title }}</span>
            <span v-if="row.author" class="book-author">{{ row.author }}</span>
            <span v-if="row.publisher" class="book-pub">{{ row.publisher }}</span>
            <template v-for="pop in [popularityForSearch(row.isbn13)]" :key="'sp'">
              <a v-if="pop" class="follow-popularity"
                 :href="`/books/readers?isbn=${row.isbn13}&title=${encodeURIComponent(row.title)}`"
                 title="누가 읽는지 보기">👥 팔로우 중
                <span v-if="pop.wantCount > 0">{{ pop.wantCount }}명 원함</span>
                <span v-if="pop.wantCount > 0 && pop.readCount > 0">·</span>
                <span v-if="pop.readCount > 0">{{ pop.readCount }}명 읽음</span>
              </a>
            </template>
            <span v-if="row.owned" class="shelf-owned-badge">📚 이미 책장에 있음</span>
          </div>
          <div v-if="!row.owned" class="book-add-form">
            <select v-model="row.selectedStatus">
              <option v-for="s in STATUSES" :key="s.name" :value="s.name">{{ s.label }}</option>
            </select>
            <button @click="addFromResult(row)" class="btn-primary">추가</button>
          </div>
        </li>
      </ul>
      <p v-if="searched && searchResults.length === 0" class="status-line muted">검색 결과가 없습니다.</p>

      <details class="manual-add" :open="manualOpen">
        <summary>찾는 책이 없나요? 직접 추가</summary>
        <form @submit.prevent="addManual" class="book-manual-form">
          <input type="text" v-model="manualTitle" placeholder="제목" required>
          <input type="text" v-model="manualAuthor" placeholder="저자 (선택)">
          <select v-model="manualStatus">
            <option v-for="s in STATUSES" :key="s.name" :value="s.name">{{ s.label }}</option>
          </select>
          <button type="submit" class="btn-primary">추가</button>
        </form>
      </details>
    </section>

    <!-- 내 책장 -->
    <section class="card">
      <h2>내 책장</h2>
      <p class="shelf-public-hint">
        🌍 <strong>책방 공개</strong>로 켠 책은 <a :href="`/u/${myLoginId}`">내 책방</a>에서 누구나 볼 수 있어요.
      </p>

      <nav class="shelf-filter">
        <a href="#" @click.prevent="selectStatus(null)" :class="{ active: statusFilter === null }">전체</a>
        <a v-for="s in STATUSES" :key="s.name" href="#"
           @click.prevent="selectStatus(s.name)" :class="{ active: statusFilter === s.name }">{{ s.label }}</a>
      </nav>
      <nav class="vis-filter-bar" aria-label="공개여부 필터">
        <span class="vis-filter">
          <a href="#" @click.prevent="selectVis(null)" :class="{ active: visFilter === null }">전체</a>
          <a href="#" @click.prevent="selectVis('PUBLIC')" :class="{ active: visFilter === 'PUBLIC' }">🌍 공개</a>
          <a href="#" @click.prevent="selectVis('PRIVATE')" :class="{ active: visFilter === 'PRIVATE' }">🔒 비공개</a>
        </span>
      </nav>

      <p v-if="shelfBooks.length === 0 && statusFilter === null && visFilter === null" class="status-line">
        아직 등록한 책이 없습니다. 위에서 검색해 추가해 보세요.
      </p>
      <p v-else-if="shelfBooks.length === 0" class="status-line">이 조건의 책이 없습니다.</p>

      <ul v-if="shelfBooks.length" class="book-list shelf-scroll">
        <li v-for="book in shelfBooks" :key="book.id" class="book-row">
          <img v-if="book.coverUrl" class="book-cover" :src="book.coverUrl" alt="" loading="lazy" referrerpolicy="no-referrer">
          <div class="book-meta">
            <a class="book-title" :href="`/books/${book.id}`">{{ book.title }}</a>
            <span v-if="book.author" class="book-author">{{ book.author }}</span>
            <span class="book-status-badge">{{ book.statusLabel }}</span>
            <span v-if="book.seconds > 0" class="book-time mono">⏱ {{ formatTime(book.seconds) }}</span>
            <template v-for="pop in [popularityFor(book.isbn13)]" :key="'bp'">
              <a v-if="pop" class="follow-popularity"
                 :href="`/books/readers?isbn=${book.isbn13}&title=${encodeURIComponent(book.title)}`"
                 title="누가 읽는지 보기">👥 팔로우 중
                <span v-if="pop.wantCount > 0">{{ pop.wantCount }}명 원함</span>
                <span v-if="pop.wantCount > 0 && pop.readCount > 0">·</span>
                <span v-if="pop.readCount > 0">{{ pop.readCount }}명 읽음</span>
              </a>
            </template>
          </div>
          <div class="book-actions">
            <select :value="book.status"
                    @change="changeStatus(book, ($event.target as HTMLSelectElement).value)">
              <option v-for="s in STATUSES" :key="s.name" :value="s.name">{{ s.label }}</option>
            </select>
            <button type="button" class="vis-toggle" :class="{ 'is-public': book.isPublic }"
                    :aria-pressed="String(book.isPublic)"
                    :title="book.isPublic ? '책방에 공개 중 — 누르면 비공개로 전환' : '비공개 — 누르면 책방에 공개'"
                    @click="toggleVisibility(book)">
              <span class="vis-text">
                <span class="vis-icon" aria-hidden="true">{{ book.isPublic ? '🌍' : '🔒' }}</span>
                <span>{{ book.isPublic ? '책방 공개' : '비공개' }}</span>
              </span>
              <span class="vis-track" aria-hidden="true"><span class="vis-knob"></span></span>
            </button>
            <!-- 구매 3분기 -->
            <details v-if="book.purchaseLink && coupangEnabled" class="buy-menu">
              <summary class="btn-ghost">구매</summary>
              <div class="buy-menu-items">
                <a :href="`/books/${book.id}/buy`" target="_blank" rel="noopener nofollow">알라딘</a>
                <a :href="`/books/${book.id}/buy/coupang`" target="_blank" rel="noopener nofollow">쿠팡</a>
              </div>
            </details>
            <a v-else-if="book.purchaseLink && !coupangEnabled"
               :href="`/books/${book.id}/buy`" target="_blank" rel="noopener nofollow" class="btn-ghost">구매</a>
            <a v-else-if="!book.purchaseLink && coupangEnabled"
               :href="`/books/${book.id}/buy/coupang`" target="_blank" rel="noopener nofollow" class="btn-ghost">쿠팡</a>
            <button type="button" class="btn-danger" @click="removeBook(book)">삭제</button>
          </div>
        </li>
      </ul>
    </section>

    <!-- affiliate-note: 알라딘 무조건, 쿠팡은 조건 -->
    <p class="affiliate-note">
      ※ "구매" 링크는 제휴(알라딘) 링크로, 구매 시 일부 수수료를 받을 수 있습니다.
    </p>
    <p v-if="coupangEnabled" class="affiliate-note">
      이 포스팅은 쿠팡 파트너스 활동의 일환으로, 이에 따른 일정액의 수수료를 제공받습니다.
    </p>

    <p class="link-row">
      <a href="/">← 대시보드</a>
      <a href="/history">📖 독서 기록</a>
    </p>
  </template>
</template>
