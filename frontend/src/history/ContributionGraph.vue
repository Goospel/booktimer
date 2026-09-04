<script setup lang="ts">
import { ref, computed } from 'vue';
import type { GraphDto, ContributionDay } from './HistoryApp.vue';
import { cellTooltip } from './grassTooltip';

const props = withDefaults(defineProps<{ graph: GraphDto; mode?: 'reading' | 'study' }>(), { mode: 'reading' });

// 문구 묶음 — 독서 값은 옛 리터럴 그대로(마크업 경계도 안 옮겨 렌더 불변). 공부 범례가 「목표 미달/달성」이
// 아니라 「적게…많이」인 이유: 서버(StudyHistoryService)가 목표가 아니라 고정 절대 눈금 4h로 농도를 매긴다.
const L = computed(() => props.mode === 'study'
    ? { title: '공부 잔디', activity: '공부', low: '적게', high: '많이', manual: false }
    : { title: '독서 잔디', activity: '독서', low: '목표 미달', high: '목표 달성', manual: true });

function cellClass(cell: ContributionDay): string[] {
    if (cell.date === null) {
        return ['grass-cell', 'empty'];
    }
    return ['grass-cell', `level-${cell.level}`, ...(cell.manual ? ['manual'] : [])];
}

function totalTime(totalSeconds: number): string {
    const h = Math.floor(totalSeconds / 3600);
    const m = Math.floor((totalSeconds % 3600) / 60);
    return `${h}시간 ${m}분`;
}

// 커서를 따라다니는 다크 툴팁(시안). placeholder/future 셀은 cellTooltip이 null을 돌려줘 뜨지 않는다.
const tip = ref<{ x: number; y: number; text: string } | null>(null);

function onCellMove(cell: ContributionDay, e: MouseEvent): void {
    const text = cellTooltip(cell);
    if (text === null) { tip.value = null; return; }
    tip.value = { x: e.clientX, y: e.clientY, text };
}

function onCellLeave(): void {
    tip.value = null;
}
</script>

<template>
    <div class="grass-head">
        <h2>{{ L.title }}</h2>
        <!-- 연속 일수 뱃지 — 대시보드(.dash-streak-chip)와 통일: SVG 새싹 + "N일 연속 독서". streak 0이면 숨김. -->
        <span v-if="graph.currentStreak > 0" class="grass-streak">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                <path d="M5 19c0-8 6-13 14-13 0 8-5 14-13 14-1 0-1-.6-1-1z" />
            </svg>
            <strong>{{ graph.currentStreak }}</strong>일 연속 {{ L.activity }}
        </span>
    </div>

    <p class="grass-summary">
        지난 1년 동안 <strong>{{ graph.activeDays }}일</strong> {{ L.activity }} ·
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

            <!-- 53주 × 7요일 그리드 — app.css의 .grass-cell.level-N / .empty / .manual 재사용.
                 hover 시 다크 툴팁(커서 추적). placeholder는 cellTooltip이 null → 안 뜸. -->
            <div class="grass-grid">
                <template v-for="(week, wi) in graph.weeks" :key="wi">
                    <div v-for="(cell, di) in week"
                         :key="`${wi}-${di}`"
                         :class="cellClass(cell)"
                         @mousemove="onCellMove(cell, $event)"
                         @mouseleave="onCellLeave">
                    </div>
                </template>
            </div>

            <!-- 범례 -->
            <div class="grass-legend" aria-hidden="true">
                <span class="grass-legend-group">
                    <span>{{ L.low }}</span>
                    <span class="grass-legend-scale">
                        <i class="grass-cell level-0"></i>
                        <i class="grass-cell level-1"></i>
                        <i class="grass-cell level-2"></i>
                        <i class="grass-cell level-3"></i>
                        <i class="grass-cell level-4"></i>
                    </span>
                    <span>{{ L.high }}</span>
                </span>
                <!-- 「직접 채움」 스와치는 독서 전용 — 공부엔 수동 기록이 없다(서버가 manual을 항상 false로 준다) -->
                <span v-if="L.manual" class="grass-legend-group">
                    <i class="grass-cell level-2 manual"></i>
                    <span>직접 채움</span>
                </span>
            </div>
        </div>
    </div>

    <!-- 커서 추적 다크 툴팁 (position:fixed라 카드 overflow에 안 잘림) -->
    <div v-if="tip" class="grass-tip" :style="{ left: tip.x + 'px', top: (tip.y - 40) + 'px' }">
        {{ tip.text }}
    </div>
</template>
