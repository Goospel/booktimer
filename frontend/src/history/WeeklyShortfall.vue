<script setup lang="ts">
import type { DayDebt } from './HistoryApp.vue';

defineProps<{ weeklyShortfall: DayDebt[] }>();

function formatShortDate(dateStr: string): string {
    const [, m, d] = dateStr.split('-').map(Number);
    return `${m}월 ${d}일`;
}

function formatDebt(debtSeconds: number): string {
    return `${Math.floor(debtSeconds / 60)}분 부족`;
}
</script>

<template>
    <div v-if="weeklyShortfall.length > 0">
        <p class="muted">최근 7일 중 목표를 못 채운 날이에요. 그날 읽고 기록을 깜빡했다면 채워 넣어요.</p>
        <ul class="missed-list">
            <li v-for="d in weeklyShortfall" :key="d.date">
                <span class="missed-date">{{ formatShortDate(d.date) }}</span>
                <span class="missed-debt">{{ formatDebt(d.debtSeconds) }}</span>
                <a class="btn-ghost btn-fill" :href="'/sessions/manual?date=' + d.date">채우기</a>
            </li>
        </ul>
    </div>
    <p class="status-line" v-else>이번 주는 빠뜨린 날이 없어요. 잘하고 있어요! 🎉</p>
</template>
