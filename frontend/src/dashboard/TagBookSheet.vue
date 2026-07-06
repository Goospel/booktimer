<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import type { BookOption } from './types'

// 종료 후 "무슨 책이었나요?" 태깅 시트(발견 1) — 책 없이 측정한 세션에 나중에 책을 붙인다.
// 읽는중+완독+읽고싶음 셋 중 고르면 tag(bookId), 건너뛰면 skip(세션은 책 없이 정당 유지).
const props = defineProps<{
    readingBooks: BookOption[]
    finishedBooks: BookOption[]
    wantToReadBooks: BookOption[]
    pending?: boolean
}>()

const emit = defineEmits<{
    tag: [bookId: number]
    skip: []
}>()

const groups = computed(() => [
    { label: '읽는 중', books: props.readingBooks },
    { label: '완독', books: props.finishedBooks },
    { label: '읽고 싶음', books: props.wantToReadBooks },
].filter(g => g.books.length > 0))

const hasAny = computed(() => groups.value.length > 0)

// 포커스를 시트 안으로 — 여는 컨텍스트(모달 밖)에 포커스가 남으면 Esc가 이벤트를 못 받는다(ReportModal와 동일).
const overlayEl = ref<HTMLElement | null>(null)
onMounted(() => overlayEl.value?.focus())
</script>

<template>
    <div ref="overlayEl" class="tag-sheet-overlay" @click.self="emit('skip')" @keydown.esc="emit('skip')"
         tabindex="-1">
        <div class="tag-sheet-panel" role="dialog" aria-modal="true" aria-labelledby="tag-sheet-title">
            <div class="tag-sheet-head">
                <p id="tag-sheet-title" class="tag-sheet-title">무슨 책을 읽으셨나요?</p>
                <button type="button" class="tag-sheet-close" aria-label="닫기" @click="emit('skip')">✕</button>
            </div>

            <div class="tag-sheet-body">
                <template v-if="hasAny">
                    <p class="tag-sheet-hint">방금 측정한 독서에 책을 연결해요. 나중에 정해도 괜찮아요.</p>
                    <div v-for="g in groups" :key="g.label" class="tag-sheet-group">
                        <span class="tag-sheet-group-label">{{ g.label }}</span>
                        <button v-for="b in g.books" :key="b.id" type="button" class="tag-sheet-book"
                                :disabled="pending" @click="emit('tag', b.id)">{{ b.title }}</button>
                    </div>
                </template>
                <p v-else class="tag-sheet-hint">아직 책장에 책이 없어요. 책을 추가하면 다음부터 연결할 수 있어요.</p>
            </div>

            <button type="button" class="tag-sheet-skip" :disabled="pending" @click="emit('skip')">
                책 없이 기록 · 건너뛰기
            </button>
        </div>
    </div>
</template>
