<script setup lang="ts">
import { computed, ref, watch } from 'vue';

import type { AddItemInput, StudyBookRow } from './api';
import type { PlanItem } from './pure';

const props = defineProps<{
    date: string;
    /** 서버(유저 tz) 기준 오늘. 비어 있으면 아직 안 불러온 것이다. */
    today: string;
    items: PlanItem[];
    books: StudyBookRow[];
}>();

const emit = defineEmits<{
    (e: 'add', input: AddItemInput): void;
    (e: 'remove', id: number): void;
}>();

const subject = ref('');
const task = ref('');
const bookId = ref<number | null>(null);

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

const title = computed(() => {
    const [y, m, d] = props.date.split('-').map(Number);
    return `${m}월 ${d}일 (${WEEKDAYS[new Date(y, m - 1, d).getDay()]})`;
});

// 책을 고르면 과목 칸을 그 제목으로 채운다 — 대개 같은 값이라 두 번 쓰게 하지 않는다(직접 고쳐도 된다).
watch(bookId, (id) => {
    const book = props.books.find((b) => b.id === id);
    if (book) subject.value = book.title;
});

// 다른 날로 옮기면 쓰던 입력은 버린다 — 남아 있으면 엉뚱한 날에 붙는다.
watch(() => props.date, () => {
    subject.value = '';
    task.value = '';
    bookId.value = null;
});

const canSubmit = computed(() => subject.value.trim().length > 0 && task.value.trim().length > 0);

function submit(): void {
    if (!canSubmit.value) return;
    emit('add', {
        date: props.date,
        bookId: bookId.value,
        subject: subject.value.trim(),
        task: task.value.trim(),
    });
    task.value = '';
}
</script>

<template>
    <section class="card study-day">
        <h3 class="study-day-title">{{ title }}</h3>

        <div class="study-day-block">
            <p class="study-day-label">이 날의 일정</p>
            <ul v-if="items.length" class="study-day-list">
                <li v-for="item in items" :key="item.id">
                    <span class="study-day-subject">{{ item.subject }}</span>
                    <span class="study-day-task">{{ item.task }}</span>
                    <button type="button" class="btn btn-ghost btn-small" @click="emit('remove', item.id)">지우기</button>
                </li>
            </ul>
            <p v-else class="status-line muted">아직 일정이 없어요.</p>
        </div>

        <form class="study-day-form" @submit.prevent="submit">
            <p class="study-day-label">일정 추가</p>
            <select v-model="bookId" class="study-day-book" aria-label="공부 책">
                <option :value="null">책 없이 (직접 입력)</option>
                <option v-for="book in books" :key="book.id" :value="book.id">{{ book.title }}</option>
            </select>
            <input v-model="subject" type="text" maxlength="300" placeholder="과목 (예: 정보처리기사 실기)" aria-label="과목">
            <input v-model="task" type="text" maxlength="500" placeholder="할 일 한 줄 (예: 3장 함수 p.45-70)" aria-label="할 일">
            <button type="submit" class="btn btn-primary btn-small" :disabled="!canSubmit">추가</button>
        </form>
    </section>
</template>
