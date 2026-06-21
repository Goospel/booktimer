<script setup lang="ts">
import type { GraphDto, ContributionDay } from './HistoryApp.vue';

const props = defineProps<{ graph: GraphDto }>();

function cellClass(cell: ContributionDay): string[] {
    if (cell.date === null) {
        return ['grass-cell', 'empty'];
    }
    return ['grass-cell', `level-${cell.level}`, ...(cell.manual ? ['manual'] : [])];
}

function cellTitle(cell: ContributionDay): string | undefined {
    if (cell.date === null) return undefined;
    const minutes = Math.floor(cell.totalSeconds / 60);
    return `${cell.date} · ${minutes}분${cell.manual ? ' · 직접 채움' : ''}`;
}

function totalTime(totalSeconds: number): string {
    const h = Math.floor(totalSeconds / 3600);
    const m = Math.floor((totalSeconds % 3600) / 60);
    return `${h}시간 ${m}분`;
}
</script>

<template>
    <div class="grass-head">
        <h2>독서 잔디</h2>
        <span class="grass-streak" :title="graph.growthLabel">
            <span class="grass-streak-icon">{{ graph.growthEmoji }}</span>
            <small v-if="graph.currentStreak > 0" class="grass-streak-days">
                {{ graph.currentStreak }}일 연속
            </small>
        </span>
    </div>

    <p class="grass-summary">
        지난 1년 동안 <strong>{{ graph.activeDays }}</strong>일 독서 ·
        총 <strong>{{ totalTime(graph.totalSeconds) }}</strong>
    </p>

    <div class="grass">
        <!-- 좌측 요일 라벨 (월/수/금) -->
        <div class="grass-weekdays" aria-hidden="true">
            <span></span><span>월</span><span></span><span>수</span><span></span><span>금</span><span></span>
        </div>

        <div class="grass-main">
            <!-- 상단 월 라벨 -->
            <div class="grass-months" aria-hidden="true">
                <span v-for="(m, i) in graph.monthLabels"
                      :key="i"
                      :style="{ gridColumnStart: m.weekIndex + 1 }">
                    {{ m.label }}
                </span>
            </div>

            <!-- 53주 × 7요일 그리드 — app.css의 .grass-cell.level-N / .empty / .manual 재사용 -->
            <div class="grass-grid">
                <template v-for="(week, wi) in graph.weeks" :key="wi">
                    <div v-for="(cell, di) in week"
                         :key="`${wi}-${di}`"
                         :class="cellClass(cell)"
                         :title="cellTitle(cell)">
                    </div>
                </template>
            </div>

            <!-- 범례 -->
            <div class="grass-legend" aria-hidden="true">
                <span>목표 미달</span>
                <i class="grass-cell level-0"></i>
                <i class="grass-cell level-1"></i>
                <i class="grass-cell level-2"></i>
                <i class="grass-cell level-3"></i>
                <i class="grass-cell level-4"></i>
                <span>목표 달성</span>
                <i class="grass-cell level-2 manual legend-gap"></i>
                <span>직접 채움</span>
            </div>
        </div>
    </div>
</template>
