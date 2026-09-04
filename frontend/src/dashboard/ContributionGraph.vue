<script setup lang="ts">
import { computed } from 'vue'
import type { GraphDto, ContributionDay } from './types'
import { cellTone } from './timerProgress'

const props = withDefaults(defineProps<{ graph: GraphDto; mode?: 'reading' | 'study' }>(), { mode: 'reading' })

// 문구 묶음 — 독서 값은 옛 리터럴 그대로(마크업 경계도 안 옮겨 렌더 불변). 공부 범례가 「목표 미달/달성」이
// 아니라 「적게…많이」인 이유: 서버(StudyHistoryService)가 목표가 아니라 고정 절대 눈금 4h로 농도를 매긴다.
const L = computed(() => props.mode === 'study'
    ? { pill: '공부 기록', href: '/study/history', streak: '일 연속 공부', days: '일 공부', low: '적게', high: '많이', manual: false }
    : { pill: '독서 기록', href: '/history', streak: '일 연속 독서', days: '일 독서', low: '목표 미달', high: '목표 달성', manual: true })

// 세이지 톤(s1~s5)은 dashboard 스코프 .dash-grass-cell에만 적용 — /history의 .grass-cell.level-*과 격리.
function cellClasses(cell: ContributionDay): string[] {
    return ['dash-grass-cell', cellTone(cell), ...(cell.manual ? ['manual'] : [])]
}

function cellTitle(cell: ContributionDay): string | undefined {
    if (cell.date === null) return undefined
    const m = Math.floor(cell.totalSeconds / 60)
    return `${cell.date} · ${m}분${cell.manual ? ' · 직접 채움' : ''}`
}

const totalHM = computed(() =>
    `${Math.floor(props.graph.totalSeconds / 3600)}시간 ${Math.floor((props.graph.totalSeconds % 3600) / 60)}분`
)
</script>

<template>
    <section class="dash-card dash-grass-card" :class="{ 'is-study': mode === 'study' }">
        <div class="dash-grass-head">
            <div class="dash-grass-head-left">
                <span class="dash-pill">{{ L.pill }}</span>
                <span v-if="graph.currentStreak > 0" class="dash-streak-chip">
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                        <path d="M5 19c0-8 6-13 14-13 0 8-5 14-13 14-1 0-1-.6-1-1z" />
                    </svg>
                    <strong>{{ graph.currentStreak }}</strong>{{ L.streak }}
                </span>
            </div>
            <a class="dash-grass-link" :href="L.href">전체 기록 →</a>
        </div>

        <div class="dash-grass-scroll">
            <div class="dash-grass-grid">
                <template v-for="(week, wi) in graph.weeks" :key="wi">
                    <div v-for="(cell, di) in week" :key="`${wi}-${di}`"
                         :class="cellClasses(cell)" :title="cellTitle(cell)"></div>
                </template>
            </div>
        </div>

        <div class="dash-grass-foot">
            <span class="dash-grass-summary">
                지난 1년 <strong>{{ graph.activeDays }}</strong>{{ L.days }} · 총 <strong>{{ totalHM }}</strong>
            </span>
            <div class="dash-grass-legend" aria-hidden="true">
                <span>{{ L.low }}</span>
                <i class="dash-grass-cell s1"></i>
                <i class="dash-grass-cell s2"></i>
                <i class="dash-grass-cell s3"></i>
                <i class="dash-grass-cell s4"></i>
                <i class="dash-grass-cell s5"></i>
                <span>{{ L.high }}</span>
                <i v-if="L.manual" class="dash-grass-cell s3 manual dash-legend-gap"></i>
                <span v-if="L.manual">직접 채움</span>
            </div>
        </div>
    </section>
</template>
