<script setup lang="ts">
import { computed, ref, watch } from 'vue';

import { applyPlan, generatePlan, type PlanDraft, type StudyBookRow } from './api';
import { planWeeks, validatePlanForm, type PlanItem } from './pure';

/**
 * AI 일정 생성 — 폼 → 미리보기 → 「달력에 적용」.
 *
 * <p><b>미리보기가 이 화면의 요점</b>이다. 생성은 아무것도 저장하지 않고, 적용은 오늘 이후를 통째로
 * 갈아치운다 — 그 사이에 사람이 읽고 무를 기회를 두지 않으면, 마음에 안 드는 초안 하나가 남은 일정을
 * 조용히 지운다. 그래서 적용 버튼 옆에 「몇 개가 바뀌는가」가 늘 붙어 있다.
 *
 * <p>이 컴포넌트는 <b>승인됐고 키도 있을 때만</b> 그려진다(부모가 `aiEnabled`로 판단) — 서버의 403은
 * 2중 방어이지 UI 규칙이 아니다.
 */
const props = defineProps<{
    /** 서버(유저 tz) 기준 오늘 — 시험일 하한. 비어 있으면 아직 안 불러온 것이다. */
    today: string;
    /** 그날 일정 — 과목 프리필의 출처(대개 같은 과목을 이어서 짠다). */
    items: PlanItem[];
    /** 공부 서재 — 일정을 책에 걸 때 고르는 목록. */
    books: StudyBookRow[];
    /** 오늘 남은 일정 생성 몫. 0이면 버튼을 잠근다. */
    remainingPlan: number;
}>();

const emit = defineEmits<{ (e: 'applied'): void }>();

const subject = ref(props.items.length > 0 ? props.items[0].subject : '');
const scope = ref('');
const examDate = ref('');
const dailyMinutes = ref(120);
const daysPerWeek = ref(5);
const bookId = ref<number | null>(props.items.find((i) => i.bookId !== null)?.bookId ?? null);

const draft = ref<PlanDraft | null>(null);
const busy = ref(false);
const error = ref('');
const notice = ref('');

// 책을 고르면 과목 칸을 그 제목으로 채운다(DayPanel의 일정 추가와 같은 규칙 — 직접 고쳐도 된다).
watch(bookId, (id) => {
    const book = props.books.find((b) => b.id === id);
    if (book) subject.value = book.title;
});

const capSpent = computed(() => props.remainingPlan === 0);
const weeks = computed(() => (draft.value ? planWeeks(draft.value.days) : []));

function formInput() {
    return {
        subject: subject.value,
        scope: scope.value,
        examDate: examDate.value,
        dailyMinutes: Number(dailyMinutes.value),
        daysPerWeek: Number(daysPerWeek.value),
    };
}

/**
 * 초안을 받아 온다 — 받은 것은 화면에만 있다.
 *
 * 검증을 여기서 한 번 더 하는 것은 헛왕복을 막기 위해서다(서버가 같은 규칙으로 다시 잰다).
 */
async function onGenerate(): Promise<void> {
    if (busy.value || capSpent.value) return;
    const invalid = validatePlanForm(formInput(), props.today);
    if (invalid) {
        error.value = invalid;
        return;
    }
    busy.value = true;
    error.value = '';
    notice.value = '';
    try {
        draft.value = await generatePlan(formInput());
    } catch (e) {
        draft.value = null;
        error.value = e instanceof Error && e.message ? e.message : 'AI 일정을 만들지 못했어요.';
    } finally {
        busy.value = false;
    }
}

/**
 * 미리보기를 달력에 적는다.
 *
 * 적용 뒤에 <b>서버가 준 실제 숫자</b>를 말하는 것이 요점이다 — 미리보기의 `replaceCount`는 생성
 * 시점의 값이라, 그 사이에 일정을 더했으면 실제로 지워진 수가 더 크다.
 */
async function onApply(): Promise<void> {
    if (busy.value || !draft.value) return;
    busy.value = true;
    error.value = '';
    try {
        const result = await applyPlan({
            bookId: bookId.value,
            subject: subject.value.trim(),
            days: draft.value.days,
        });
        draft.value = null;
        notice.value = `일정 ${result.applied}개를 달력에 적었어요 (옛 일정 ${result.removed}개 교체).`;
        emit('applied');
    } catch (e) {
        error.value = e instanceof Error && e.message ? e.message : '일정을 적용하지 못했어요.';
    } finally {
        busy.value = false;
    }
}
</script>

<template>
    <div class="study-plan-form">
        <p class="study-day-label">AI로 일정 만들기</p>
        <p class="status-line muted">
            공부할 범위를 적으면 시험일까지 날짜별로 나눠 드려요. 만든 뒤 확인하고 달력에 적용해요.
        </p>

        <div class="study-select-wrap">
            <select v-model="bookId" class="study-plan-book" aria-label="공부 책" data-testid="plan-book">
                <option :value="null">책 없이 (직접 입력)</option>
                <option v-for="book in books" :key="book.id" :value="book.id">{{ book.title }}</option>
            </select>
            <svg class="study-select-chevron" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M6 9l6 6 6-6"/></svg>
        </div>
        <input
            v-model="subject"
            type="text"
            maxlength="300"
            placeholder="과목 (예: 정보보안기사 필기)"
            aria-label="과목"
            data-testid="plan-subject"
        >
        <textarea
            v-model="scope"
            class="study-plan-scope"
            rows="4"
            maxlength="4000"
            placeholder="공부할 범위를 적어 주세요 (예: 1장 접근통제 p.10-60 / 2장 암호학 p.61-120)"
            aria-label="공부할 범위"
            data-testid="plan-scope"
        ></textarea>

        <div class="study-plan-row">
            <label>
                <span>시험일</span>
                <input v-model="examDate" type="date" :min="today" aria-label="시험일" data-testid="plan-exam-date">
            </label>
            <label>
                <span>하루 공부 시간(분)</span>
                <input v-model.number="dailyMinutes" type="number" min="10" max="600" step="10" data-testid="plan-daily-minutes">
            </label>
            <label>
                <span>주 공부일수</span>
                <input v-model.number="daysPerWeek" type="number" min="1" max="7" data-testid="plan-days-per-week">
            </label>
        </div>

        <p v-if="error" class="status-line study-error" data-testid="plan-error">{{ error }}</p>
        <p v-else-if="notice" class="status-line muted" data-testid="plan-notice">{{ notice }}</p>
        <p v-else-if="capSpent" class="status-line muted" data-testid="plan-cap-spent">
            오늘 몫을 다 썼어요 — 내일 다시 해 주세요.
        </p>

        <button
            type="button"
            class="btn btn-primary btn-small"
            :disabled="busy || capSpent"
            data-testid="plan-generate"
            @click="onGenerate"
        >{{ busy ? '만드는 중…' : `일정 만들기 (${remainingPlan}회 남음)` }}</button>

        <div v-if="draft" class="study-plan-preview" data-testid="plan-preview">
            <p class="study-plan-heading">미리보기 — {{ draft.days.length }}일</p>
            <div v-for="week in weeks" :key="week.label" class="study-plan-week">
                <p class="study-plan-week-label">{{ week.label }}</p>
                <ul class="study-plan-list">
                    <li v-for="day in week.days" :key="day.date">
                        <span class="study-plan-date">{{ day.date.slice(5) }}</span>
                        <span class="study-plan-task">{{ day.task }}</span>
                    </li>
                </ul>
            </div>

            <!-- 「지금 기준」이 정직한 말이다 — 이 숫자는 만들 때 센 값이라, 읽는 동안 일정을 더하면 어긋난다. -->
            <p class="status-line study-plan-replace" data-testid="plan-replace">
                달력에 적용하면 <strong>지금 기준</strong> 오늘 이후 일정 {{ draft.replaceCount }}개가 새 일정으로 바뀌어요.
                지난 일정은 그대로 남아요.
            </p>
            <p class="status-line muted">AI 판단이라 틀릴 수 있어요. 적용한 뒤 하나씩 지울 수 있어요.</p>

            <div class="study-plan-actions">
                <button
                    type="button"
                    class="btn btn-ghost btn-small"
                    :disabled="busy"
                    data-testid="plan-discard"
                    @click="draft = null"
                >버리기</button>
                <button
                    type="button"
                    class="btn btn-primary btn-small"
                    :disabled="busy"
                    data-testid="plan-apply"
                    @click="onApply"
                >달력에 적용</button>
            </div>
        </div>
    </div>
</template>
