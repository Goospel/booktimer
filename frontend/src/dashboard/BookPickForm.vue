<script setup lang="ts">
import { computed } from 'vue'
import type { BookOption } from './types'
import { initialOf, coverColor, hasCover } from '../books/pure'
import { allBooksOf, defaultBookOf } from './defaultBook'

// 측정 시작 진입(발견 1, §6.5) — 드롭다운을 걷어내고 기본 책을 표지 칩으로 보여준다.
//  · '측정 시작' = 기본 책(최근 읽은 책=이어 읽기)으로 1탭 시작.
//  · '바꾸기'    = 책 고르기 시트를 연다(openSheet). 시트에서 고르면 그 책으로 시작.
//  · '책 없이'   = start(null). 시작을 절대 가로막지 않는다.
// 책이 0권이어도 '책 없이 측정 시작' + '책 고르기'(검색·담기 시트)로 막지 않는다.
const props = withDefaults(defineProps<{
    readingBooks: BookOption[]
    finishedBooks: BookOption[]
    wantToReadBooks: BookOption[]
    recentBookId: number | null
    /** 시트에서 방금 고른 책 — 있으면 기본 규칙을 이긴다(고르기는 시작이 아니다). */
    pickedBook?: BookOption | null
    pending?: boolean
}>(), { pickedBook: null })

const emit = defineEmits<{
    start: [bookId: number | null]
    openSheet: []
}>()

// 기본 책 = 최근 읽은 책(이어 읽기) → 없으면 첫 책. 칩에 표시하고 '측정 시작'이 이 책으로 시작한다.
// 계산은 defaultBook.ts 한 곳 — 홈의 여백 카드가 같은 함수를 봐야 두 자리가 같은 책을 가리킨다.
const allBooks = computed(() => allBooksOf(props.readingBooks, props.finishedBooks, props.wantToReadBooks))
const defaultBook = computed<BookOption | null>(() =>
    props.pickedBook ?? defaultBookOf(allBooks.value, props.recentBookId))

// 칩 표지색 — BookOption엔 isbn이 없어 제목을 seed로 결정적 매핑(무표지 플레이스홀더).
function coverStyle(b: BookOption) {
    const c = coverColor(b.title)
    return { background: c.bg, color: c.fg }
}
function startDefault() { if (!props.pending && defaultBook.value) emit('start', defaultBook.value.id) }
function startBookless() { if (!props.pending) emit('start', null) }
</script>

<template>
    <!-- 고른 책이 있으면 목록이 비어도 칩을 세운다(시트에서 담아 고른 책) — 그래서 hasBooks가 아니라 이 값을 본다. -->
    <template v-if="defaultBook">
        <span class="dash-idle-label">이 책으로 측정할까요?</span>
        <div class="dash-book-chip">
            <!-- 표지가 있으면 실물, 없으면 제목 첫 글자 색 박스. 폴백을 남기는 이유는 표지 없는 책이
                 실제로 있어서다(직접 추가·알라딘 이미지 없음). referrerpolicy는 책장·책방과 같은 관례. -->
            <img v-if="hasCover(defaultBook.coverUrl ?? null)" class="dash-book-chip-cover"
                 :src="defaultBook.coverUrl!" alt="" loading="lazy" referrerpolicy="no-referrer">
            <span v-else class="dash-book-chip-cover" :style="coverStyle(defaultBook)" aria-hidden="true">{{ initialOf(defaultBook.title) }}</span>
            <span class="dash-book-chip-title" :title="defaultBook.title">{{ defaultBook.title }}</span>
            <button type="button" class="dash-book-chip-change" :disabled="pending" @click="emit('openSheet')">바꾸기</button>
        </div>
        <button type="button" class="dash-btn-fill" :disabled="pending" @click="startDefault">{{ pending ? '시작하는 중…' : '측정 시작' }}</button>
        <button type="button" class="dash-btn-link dash-bookless" :disabled="pending" @click="startBookless">책 없이 시작</button>
    </template>
    <!-- 책 0권이어도 시작을 막지 않는다: 책 없이 바로 시작 + 책 고르기(검색·담기 시트) -->
    <div v-else class="dash-empty">
        <p>무슨 책을 읽어볼까요? 책 없이 바로 시작해도 돼요.</p>
        <button type="button" class="dash-btn-fill" :disabled="pending" @click="startBookless">
            {{ pending ? '시작하는 중…' : '책 없이 측정 시작' }}
        </button>
        <button type="button" class="dash-btn-link dash-bookless" :disabled="pending" @click="emit('openSheet')">책 고르기</button>
    </div>
</template>
