<script setup lang="ts">
import { ref } from 'vue'
import type { BookOption } from './types'

const props = defineProps<{
    readingBooks: BookOption[]
    finishedBooks: BookOption[]
    recentBookId: number | null
}>()

const emit = defineEmits<{
    start: [bookId: number]
}>()

const selectedId = ref<number | null>(props.recentBookId ?? props.readingBooks[0]?.id ?? props.finishedBooks[0]?.id ?? null)

function submit() {
    if (selectedId.value !== null) emit('start', selectedId.value)
}
</script>

<template>
    <template v-if="readingBooks.length > 0 || finishedBooks.length > 0">
        <span class="dash-idle-label">무슨 책을 읽으시겠어요?</span>
        <div class="dash-select-wrap">
            <select v-model="selectedId" class="dash-select" required aria-label="읽을 책">
                <optgroup v-if="readingBooks.length > 0" label="읽는 중">
                    <option v-for="b in readingBooks" :key="b.id" :value="b.id">{{ b.title }}</option>
                </optgroup>
                <optgroup v-if="finishedBooks.length > 0" label="완독">
                    <option v-for="b in finishedBooks" :key="b.id" :value="b.id">{{ b.title }}</option>
                </optgroup>
            </select>
            <span class="dash-select-caret" aria-hidden="true">▼</span>
        </div>
        <button type="button" class="dash-btn-fill" @click="submit">측정 시작</button>
    </template>
    <div v-else class="dash-empty">
        <p>아직 책장이 비어 있어요. <strong>첫 책</strong>을 추가하면 타이머를 켜고 오늘의 독서를 시작할 수 있어요. 📖</p>
        <a class="dash-btn-fill dash-btn-link" href="/books">첫 책 추가하기</a>
    </div>
</template>
