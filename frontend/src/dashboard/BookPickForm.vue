<script setup lang="ts">
import { ref } from 'vue'
import type { BookOption } from './types'

const props = defineProps<{
    readingBooks: BookOption[]
    finishedBooks: BookOption[]
    recentBookId: number | null
    pending?: boolean
}>()

const emit = defineEmits<{
    start: [bookId: number | null]
}>()

// 기본 선택 = 최근 읽은 책(이어 읽기) → 없으면 첫 책 → 없으면 null(책 없이). 발견 1: 시작을 막지 않는다 —
// 책을 안 골라도(=null "책 없이 측정") 시작할 수 있고, 무슨 책이었는지는 종료 후 태깅으로 정해도 된다.
const selectedId = ref<number | null>(props.recentBookId ?? props.readingBooks[0]?.id ?? props.finishedBooks[0]?.id ?? null)

function submit() {
    if (props.pending) return
    emit('start', selectedId.value) // null이면 책 없이 시작
}
</script>

<template>
    <!-- 책이 있으면: 드롭다운(맨 위 "책 없이 측정" 포함) + 시작 버튼(항상 활성) -->
    <template v-if="readingBooks.length > 0 || finishedBooks.length > 0">
        <span class="dash-idle-label">무슨 책을 읽으시겠어요?</span>
        <div class="dash-select-wrap">
            <select v-model="selectedId" class="dash-select" aria-label="읽을 책" :disabled="pending">
                <option :value="null">책 없이 측정</option>
                <optgroup v-if="readingBooks.length > 0" label="읽는 중">
                    <option v-for="b in readingBooks" :key="b.id" :value="b.id">{{ b.title }}</option>
                </optgroup>
                <optgroup v-if="finishedBooks.length > 0" label="완독">
                    <option v-for="b in finishedBooks" :key="b.id" :value="b.id">{{ b.title }}</option>
                </optgroup>
            </select>
            <span class="dash-select-caret" aria-hidden="true">▼</span>
        </div>
        <button type="button" class="dash-btn-fill" @click="submit" :disabled="pending">{{ pending ? '시작하는 중…' : '측정 시작' }}</button>
    </template>
    <!-- 책이 0권이어도 시작을 막지 않는다(발견 1): 책 없이 바로 시작 + 첫 책 추가 안내 -->
    <div v-else class="dash-empty">
        <p>책을 고르지 않아도 <strong>바로 측정</strong>할 수 있어요. 무슨 책이었는지는 끝나고 정해도 돼요. 📖</p>
        <button type="button" class="dash-btn-fill" @click="submit" :disabled="pending">
            {{ pending ? '시작하는 중…' : '책 없이 측정 시작' }}
        </button>
        <a class="dash-btn-link dash-empty-add" href="/books">또는 책 추가하기</a>
    </div>
</template>
