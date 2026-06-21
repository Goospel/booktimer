<script setup lang="ts">
import { ref, computed } from 'vue';
import type { MonthlyReadingSection } from './HistoryApp.vue';

const props = defineProps<{ months: MonthlyReadingSection[] }>();

const monthIndex = ref(0);

const prevDisabled = computed(() => monthIndex.value >= props.months.length - 1);
const nextDisabled = computed(() => monthIndex.value <= 0);

function prevMonth() {
    if (!prevDisabled.value) monthIndex.value++;
}
function nextMonth() {
    if (!nextDisabled.value) monthIndex.value--;
}

function formatMonthLabel(monthStr: string, totalSeconds: number): string {
    const [y, m] = monthStr.split('-').map(Number);
    const h = Math.floor(totalSeconds / 3600);
    const min = Math.floor((totalSeconds % 3600) / 60);
    return `${y}년 ${m}월 · ${h}시간 ${min}분`;
}

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

function formatRecordDate(dateStr: string): string {
    const [y, m, d] = dateStr.split('-').map(Number);
    const dow = new Date(y, m - 1, d).getDay();
    return `${dateStr} (${WEEKDAYS[dow]})`;
}

function formatTime(totalSeconds: number): string {
    const h = Math.floor(totalSeconds / 3600);
    const min = Math.floor((totalSeconds % 3600) / 60);
    return `${h}시간 ${min}분`;
}
</script>

<template>
    <p class="status-line" v-if="months.length === 0">
        아직 독서 기록이 없습니다. 측정을 시작해 보세요.
    </p>

    <div v-else class="month-browser">
        <div class="month-nav">
            <button type="button"
                    class="month-nav-btn month-nav-prev"
                    aria-label="이전 달"
                    :disabled="prevDisabled"
                    @click="prevMonth">◀</button>
            <span class="month-nav-label">
                {{ formatMonthLabel(months[monthIndex].month, months[monthIndex].totalSeconds) }}
            </span>
            <button type="button"
                    class="month-nav-btn month-nav-next"
                    aria-label="다음 달"
                    :disabled="nextDisabled"
                    @click="nextMonth">▶</button>
        </div>

        <ul class="record-list record-scroll">
            <li class="record-row" v-for="r in months[monthIndex].days" :key="r.date">
                <div class="record-main">
                    <span class="record-date">{{ formatRecordDate(r.date) }}</span>
                    <span class="record-books" v-if="r.bookTitles.length > 0">
                        {{ r.bookTitles.join(', ') }}
                    </span>
                </div>
                <span class="record-time mono">{{ formatTime(r.totalSeconds) }}</span>
            </li>
        </ul>
    </div>
</template>
