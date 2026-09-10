<script setup lang="ts">
import { computed, ref, watch } from 'vue';

import NotesPanel from './NotesPanel.vue';
import PlanForm from './PlanForm.vue';
import RecallPanel from './RecallPanel.vue';
import type { AddItemInput, StudyBookRow } from './api';
import { aiStatusLine, dayTitle, type AiAccess, type PlanItem } from './pure';

const props = defineProps<{
    date: string;
    /** 서버(유저 tz) 기준 오늘. 비어 있으면 아직 안 불러온 것이다. */
    today: string;
    items: PlanItem[];
    books: StudyBookRow[];
    aiAccess: AiAccess;
    aiAccessAt: string | null;
    aiEnabled: boolean;
    /** 신청 요청이 날아가 있는 동안 — 버튼을 잠가 두 번 신청(409)이 나지 않게 한다. */
    aiBusy: boolean;
    /** 오늘 남은 분석 몫. */
    remainingAnalyze: number;
    /** 오늘 남은 사진 전사 몫. */
    remainingTranscribe: number;
    /** 오늘 남은 일정 생성 몫. */
    remainingPlan: number;
    /** 전날 복습에 문제가 붙어 있나 — 달력이 이미 아는 사실이라 그대로 내려 준다(불필요한 왕복 제거). */
    hasYesterdayQuestions: boolean;
}>();

const emit = defineEmits<{
    (e: 'add', input: AddItemInput): void;
    (e: 'remove', id: number): void;
    (e: 'request-ai'): void;
    (e: 'recall-saved'): void;
    (e: 'plan-applied'): void;
}>();

const subject = ref('');
const task = ref('');
const bookId = ref<number | null>(null);
/** AI 일정 폼을 펼쳤나 — 접어 두는 것이 기본이다(달력이 먼저 보여야 한다). */
const planOpen = ref(false);

/**
 * [필기] / [백지노트] 중 무엇을 보고 있나. 기본은 백지노트다(이 화면의 원래 주인공).
 *
 * <p><b>탭이 `RecallPanel` 안이 아니라 여기 있다</b> — `RecallPanel`은 홈 대시보드
 * (`dashboard/RecallCard.vue`)도 import하므로, 안에 두면 홈에 필기 탭이 샌다(홈은 백지노트만 보인다).
 *
 * <p>날짜를 옮겨도 이 값은 유지된다(`DayPanel`이 마운트를 유지한다) — 필기는 날짜와 무관하고,
 * 필기를 보다가 하루를 옮겼다고 백지노트로 튕기는 것은 놀랍다.
 */
const page = ref<'notes' | 'recall'>('recall');

/** 그날 일정이 가리키는 책 — 필기 패널의 기본 선택(대개 지금 공부하는 책이다). */
const dayBookId = computed(() => props.items.find((i) => i.bookId !== null)?.bookId ?? null);

const title = computed(() => dayTitle(props.date));

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

const aiStatus = computed(() => aiStatusLine(props.aiAccess, props.aiEnabled, props.aiAccessAt));

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

        <!-- AI 상태 줄 — 승인제라 대부분의 사용자에겐 여기가 AI의 유일한 접점이다.
             승인됐고 키도 있는 조합에선 문구가 비고(그 자리는 아래 백지복습의 분석 버튼 몫) 줄 자체가 사라진다. -->
        <p v-if="aiStatus.text || aiStatus.button" class="study-day-ai status-line">
            <span v-if="aiStatus.text">{{ aiStatus.text }}</span>
            <button
                v-if="aiStatus.button"
                type="button"
                class="btn btn-ghost btn-small"
                :disabled="aiBusy"
                @click="emit('request-ai')"
            >{{ aiStatus.button }}</button>
        </p>

        <!-- 두 페이지는 v-if로 갈린다 — 감춰만 두면 Tiptap 편집기가 둘 마운트된다. -->
        <div class="study-recall-tabs" role="tablist">
            <button
                type="button"
                id="study-day-tab-notes"
                class="btn btn-ghost btn-small"
                :class="{ 'is-active': page === 'notes' }"
                role="tab"
                :aria-selected="page === 'notes'"
                aria-controls="study-day-page"
                data-testid="day-tab-notes"
                @click="page = 'notes'"
            >필기</button>
            <button
                type="button"
                id="study-day-tab-recall"
                class="btn btn-ghost btn-small"
                :class="{ 'is-active': page === 'recall' }"
                role="tab"
                :aria-selected="page === 'recall'"
                aria-controls="study-day-page"
                data-testid="day-tab-recall"
                @click="page = 'recall'"
            >백지노트</button>
        </div>

        <!-- 패널은 하나다 — 두 탭이 같은 자리를 갈아 끼우므로 aria-labelledby가 지금 탭을 가리킨다.
             role=tab만 붙이고 여기를 비우면 스크린리더엔 「탭인데 여는 곳이 없는」 상태로 읽힌다. -->
        <div
            id="study-day-page"
            role="tabpanel"
            :aria-labelledby="page === 'notes' ? 'study-day-tab-notes' : 'study-day-tab-recall'"
        >
            <NotesPanel v-if="page === 'notes'" :books="books" :default-book-id="dayBookId" />

            <RecallPanel
                v-else
                :date="date"
                :today="today"
                :items="items"
                :books="books"
                :ai-enabled="aiEnabled"
                :remaining-analyze="remainingAnalyze"
                :remaining-transcribe="remainingTranscribe"
                :has-yesterday-questions="hasYesterdayQuestions"
                @saved="emit('recall-saved')"
            />
        </div>

        <form class="study-day-form" @submit.prevent="submit">
            <p class="study-day-label">일정 추가</p>
            <div class="study-select-wrap">
                <select v-model="bookId" class="study-day-book" aria-label="공부 책">
                    <option :value="null">책 없이 (직접 입력)</option>
                    <option v-for="book in books" :key="book.id" :value="book.id">{{ book.title }}</option>
                </select>
                <svg class="study-select-chevron" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M6 9l6 6 6-6"/></svg>
            </div>
            <input v-model="subject" type="text" maxlength="300" placeholder="주제 (예: 미적분 · 정보처리기사 실기)" aria-label="주제">
            <input v-model="task" type="text" maxlength="500" placeholder="할 일 한 줄 (예: 3장 함수 p.45-70)" aria-label="할 일">
            <button type="submit" class="btn btn-primary btn-small" :disabled="!canSubmit">추가</button>
        </form>

        <!-- AI 일정은 접어 둔다 — 시험을 앞둔 날에만 쓰는 기능이라 매번 펼쳐 두면 화면만 길어진다. -->
        <template v-if="aiEnabled">
            <button
                type="button"
                class="btn btn-ghost btn-small study-day-planbtn"
                data-testid="plan-toggle"
                @click="planOpen = !planOpen"
            >{{ planOpen ? 'AI 일정 닫기' : 'AI로 일정 만들기' }}</button>

            <PlanForm
                v-if="planOpen"
                :today="today"
                :items="items"
                :books="books"
                :remaining-plan="remainingPlan"
                @applied="emit('plan-applied')"
            />
        </template>
    </section>
</template>
