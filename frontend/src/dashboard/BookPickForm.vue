<script setup lang="ts">
import type { BookOption } from './types'

const props = defineProps<{
    readingBooks: BookOption[]
    finishedBooks: BookOption[]
    recentBookId: number | null
}>()

const emit = defineEmits<{
    start: [bookId: number]
}>()

import { ref } from 'vue'
const selectedId = ref<number | null>(props.recentBookId ?? props.readingBooks[0]?.id ?? props.finishedBooks[0]?.id ?? null)

function submit() {
    if (selectedId.value !== null) emit('start', selectedId.value)
}
</script>

<template>
    <div v-if="readingBooks.length > 0 || finishedBooks.length > 0" class="timer-controls">
        <label class="book-pick">읽을 책
            <select v-model="selectedId" required>
                <optgroup v-if="readingBooks.length > 0" label="읽는 중">
                    <option v-for="b in readingBooks" :key="b.id" :value="b.id">{{ b.title }}</option>
                </optgroup>
                <optgroup v-if="finishedBooks.length > 0" label="완독">
                    <option v-for="b in finishedBooks" :key="b.id" :value="b.id">{{ b.title }}</option>
                </optgroup>
            </select>
        </label>
        <button type="button" class="btn-primary" @click="submit">측정 시작</button>
    </div>
    <div v-else class="timer-controls">
        <div class="timer-empty">
            <p>아직 책장이 비어 있어요. <strong>첫 책</strong>을 추가하면 타이머를 켜고 오늘의 독서를 시작할 수 있어요. 📖</p>
            <a class="btn btn-primary" href="/books">첫 책 추가하기</a>
        </div>
    </div>
</template>
