<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';

import NavLinks from '../shared/NavLinks.vue';
import DayPanel from './DayPanel.vue';
import {
    addPlanItem,
    deletePlanItem,
    fetchAgenda,
    fetchCalendar,
    fetchStudyBooks,
    saveCheck,
    type AddItemInput,
    type StudyBookRow,
} from './api';
import {
    calendarCells,
    cellLabel,
    cellMarks,
    cycleCheck,
    monthTitle,
    planSummary,
    studyNavLinks,
    type CalendarDay,
    type PlanItem,
    type RecallMark,
} from './pure';

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

const now = new Date();
const year = ref(now.getFullYear());
const month = ref(now.getMonth() + 1);

/** 서버(유저 tz) 기준 오늘 — 기기 시계가 아니다. 응답 전엔 빈 문자열이라 아무 칸도 탭할 수 없다. */
const today = ref('');
const items = ref<PlanItem[]>([]);
const recalls = ref<RecallMark[]>([]);
const days = ref<CalendarDay[]>([]);
const books = ref<StudyBookRow[]>([]);
const selected = ref('');
const busyDate = ref<string | null>(null);
const loading = ref(true);
const error = ref('');

const monthParam = computed(() => `${year.value}-${String(month.value).padStart(2, '0')}`);
const cells = computed(() => calendarCells(year.value, month.value));
const byDate = computed(() => new Map(days.value.map((d) => [d.date, d])));

function itemsOn(date: string): PlanItem[] {
    return items.value.filter((i) => i.date === date);
}

async function load(): Promise<void> {
    loading.value = true;
    error.value = '';
    try {
        const [agenda, calendar] = await Promise.all([fetchAgenda(monthParam.value), fetchCalendar(monthParam.value)]);
        today.value = agenda.today;
        items.value = agenda.items;
        recalls.value = agenda.recalls;
        days.value = calendar.days;
        if (!selected.value || !selected.value.startsWith(monthParam.value)) {
            selected.value = agenda.today.startsWith(monthParam.value) ? agenda.today : `${monthParam.value}-01`;
        }
    } catch {
        error.value = '일정을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.';
    } finally {
        loading.value = false;
    }
}

function shiftMonth(delta: number): void {
    const moved = new Date(year.value, month.value - 1 + delta, 1);
    year.value = moved.getFullYear();
    month.value = moved.getMonth() + 1;
    void load();
}

/**
 * 칸 탭 = 그날을 펼치고, 지난 날이면 판정을 한 칸 돌린다(무기록 → 지킴 → 못 지킴 → 무기록).
 *
 * 낙관 갱신을 하지 않는다 — 서버가 유저 tz로 미래를 다시 판정하므로(400), 응답을 받고 나서 칠한다.
 * 잠그는 것은 화면 전체가 아니라 그 칸 하나다(연타 방지).
 */
async function pick(date: string): Promise<void> {
    selected.value = date;
    if (!today.value || date > today.value || busyDate.value) return;
    const current = byDate.value.get(date)?.kept ?? null;
    const next = cycleCheck(current);
    busyDate.value = date;
    try {
        await saveCheck(date, next);
        applyCheck(date, next);
    } catch {
        error.value = '표시를 저장하지 못했어요.';
    } finally {
        busyDate.value = null;
    }
}

function applyCheck(date: string, kept: boolean | null): void {
    const existing = days.value.find((d) => d.date === date);
    if (existing) {
        existing.kept = kept;
        // 측정도 판정도 없는 날은 서버 응답에서 빠지는 희소 표현이라, 화면 쪽도 같은 모양으로 되돌린다.
        if (kept === null && existing.studiedSeconds === 0) {
            days.value = days.value.filter((d) => d.date !== date);
        }
        return;
    }
    if (kept !== null) days.value = [...days.value, { date, studiedSeconds: 0, kept }];
}

async function onAdd(input: AddItemInput): Promise<void> {
    error.value = '';
    try {
        items.value = [...items.value, await addPlanItem(input)];
    } catch (e) {
        error.value = e instanceof Error && e.message ? e.message : '일정을 추가하지 못했어요.';
    }
}

async function onDelete(id: number): Promise<void> {
    error.value = '';
    try {
        await deletePlanItem(id);
        items.value = items.value.filter((i) => i.id !== id);
    } catch {
        error.value = '일정을 지우지 못했어요.';
    }
}

onMounted(async () => {
    void fetchStudyBooks().then((rows) => (books.value = rows));
    await load();
});
</script>

<template>
    <section class="study">
        <div class="study-monthbar">
            <button type="button" class="btn btn-ghost btn-small" aria-label="이전 달" @click="shiftMonth(-1)">‹</button>
            <h2 class="study-month">{{ monthTitle(year, month) }}</h2>
            <button type="button" class="btn btn-ghost btn-small" aria-label="다음 달" @click="shiftMonth(1)">›</button>
        </div>

        <p v-if="error" class="status-line study-error">{{ error }}</p>
        <p v-if="loading" class="status-line muted">불러오는 중…</p>

        <div class="study-weekdays">
            <span v-for="w in WEEKDAYS" :key="w">{{ w }}</span>
        </div>

        <div class="study-grid">
            <template v-for="(date, index) in cells">
                <span v-if="date === null" :key="`pad-${index}`" aria-hidden="true"></span>
                <button
                    v-else
                    :key="date"
                    type="button"
                    class="study-cell"
                    :class="{
                        'is-selected': date === selected,
                        'is-today': date === today,
                        'is-locked': !today || date > today,
                    }"
                    :aria-label="cellLabel(date, byDate.get(date)?.kept ?? null, itemsOn(date).length)"
                    :data-cal-day="date"
                    :data-cal-state="(byDate.get(date)?.kept ?? null) === null ? 'none' : (byDate.get(date)!.kept ? 'kept' : 'missed')"
                    :disabled="busyDate === date"
                    @click="pick(date)"
                >
                    <span
                        class="study-mark"
                        :class="{
                            'is-kept': byDate.get(date)?.kept === true,
                            'is-missed': byDate.get(date)?.kept === false,
                        }"
                    >{{ Number(date.slice(8)) }}</span>
                    <span v-if="(byDate.get(date)?.studiedSeconds ?? 0) > 0" class="study-dot" aria-label="측정 있음"></span>
                    <span v-if="planSummary(itemsOn(date))" class="study-plan">{{ planSummary(itemsOn(date)) }}</span>
                    <span class="study-flags">
                        <span v-if="cellMarks(date, recalls).recall" class="study-flag">복습</span>
                        <span v-if="cellMarks(date, recalls).questions" class="study-flag">문제</span>
                    </span>
                </button>
            </template>
        </div>

        <p class="study-legend">
            <span><span class="study-legend-mark is-kept"></span>지킴</span>
            <span><span class="study-legend-mark is-missed"></span>못 지킴</span>
            <span><span class="study-dot"></span>측정 있음</span>
            <span>칸을 누르면 그날이 열리고, 지난 날은 지킴 표시가 한 칸 돌아가요</span>
        </p>

        <DayPanel
            v-if="selected"
            :date="selected"
            :today="today"
            :items="itemsOn(selected)"
            :books="books"
            @add="onAdd"
            @remove="onDelete"
        />

        <NavLinks :links="studyNavLinks()" />
    </section>
</template>
