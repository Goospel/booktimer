<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { initialOf, coverColor } from '../books/pure'
import type { StudyBookRow } from '../study/api'

// 공부 책 시트 — 하나의 컴포넌트가 세 자리를 겸한다(미니앱 BookSheet·ChangeBookSheet의 웹판):
//  · 'start'  : 측정 전 「무슨 책으로 공부할지」. 고르면 pick(id), CTA는 책 없이 시작(none).
//  · 'tag'    : 종료 후 「무슨 책이었나」. 고르면 pick(id), CTA는 건너뛰기(none).
//  · 'change' : 측정 중 교체. 지금 그 책엔 aria-current, CTA는 책 없이 공부하기(none).
// 독서 BookPickSheet와 마크업·CSS(.book-sheet-*)만 같고 **데이터 계약이 다르다** — 여기선 fetch도
// 검색도 없다. 목록은 /api/dashboard가 이미 실어 온 study.books가 props로 내려온다.
// 그래서 독서 시트에 world/mode prop을 더하지 않았다: 공용 조각의 기본값 사각이 독서에 닿을 수 없다.

const props = defineProps<{
    mode: 'start' | 'tag' | 'change'
    books: StudyBookRow[]
    /** 측정 중인 책 — 'change'에서 그 행에 aria-current를 붙인다. */
    currentBookId?: number | null
    pending?: boolean
}>()

const emit = defineEmits<{ pick: [bookId: number]; none: []; close: [] }>()

// [제목, 힌트, 하단 CTA] — 세 모드의 문구는 여기 한 곳에만 있다.
const T = {
    start: ['공부할 책을 고르세요', '고른 책으로 측정을 시작해요. 책 없이 시작해도 돼요.', '책 없이 측정하기'],
    tag: ['무슨 책을 공부하셨나요?', '방금 잰 시간을 책에 붙여요. 나중에 정해도 괜찮아요.', '책 없이 기록 · 건너뛰기'],
    change: ['다른 책으로 바꿀까요?', '지금까지 잰 시간이 통째로 새 책에 붙어요.', '책 없이 공부하기'],
} as const
const text = computed(() => T[props.mode])

function coverStyle(b: StudyBookRow) {
    const c = coverColor(b.isbn13 || b.title)
    return { background: c.bg, color: c.fg }
}

const overlayEl = ref<HTMLElement | null>(null)
onMounted(() => overlayEl.value?.focus())
</script>

<template>
    <div ref="overlayEl" class="book-sheet-overlay" @click.self="emit('close')" @keydown.esc="emit('close')" tabindex="-1">
        <!-- is-study — 토큰 스왑이 hover·회독 칩까지 파랑으로 끌고 온다(리터럴 0줄). -->
        <div class="book-sheet-panel is-study" role="dialog" aria-modal="true" aria-labelledby="study-sheet-title">
            <div class="sheet-handle" aria-hidden="true"></div>
            <div class="book-sheet-head">
                <p id="study-sheet-title" class="book-sheet-title">{{ text[0] }}</p>
                <button type="button" class="book-sheet-close" aria-label="닫기" @click="emit('close')">✕</button>
            </div>
            <p class="book-sheet-hint">{{ text[1] }}</p>

            <ul v-if="books.length" class="book-sheet-list">
                <li v-for="b in books" :key="b.id">
                    <button type="button" class="book-sheet-book" :disabled="pending"
                            :aria-current="b.id === currentBookId ? 'true' : undefined"
                            @click="emit('pick', b.id)">
                        <img v-if="b.coverUrl" class="book-sheet-cover" :src="b.coverUrl" alt="" loading="lazy" referrerpolicy="no-referrer">
                        <span v-else class="book-sheet-cover" :style="coverStyle(b)" aria-hidden="true">{{ initialOf(b.title) }}</span>
                        <span class="book-sheet-meta">
                            <span class="book-sheet-book-title">{{ b.title }}</span>
                            <span v-if="b.author" class="book-sheet-byline">{{ b.author }}</span>
                        </span>
                        <span class="book-sheet-badge study-read-chip">{{ b.readCount }}독</span>
                    </button>
                </li>
            </ul>
            <!-- 시트 안에서 담지 않는다(⏸) — 담는 자리는 공부 서재 한 곳이다. -->
            <p v-else class="book-sheet-empty">
                아직 공부 서재에 책이 없어요. <a href="/study/books">공부 서재에서 담기</a>
            </p>

            <button type="button" class="book-sheet-cta" :disabled="pending" @click="emit('none')">{{ text[2] }}</button>
        </div>
    </div>
</template>
