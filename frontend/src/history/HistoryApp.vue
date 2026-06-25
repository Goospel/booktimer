<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue';
import ContributionGraph from './ContributionGraph.vue';
import MonthlyRecords from './MonthlyRecords.vue';
import WeeklyShortfall from './WeeklyShortfall.vue';
import { chooseLayout, type RecordsLayout } from './layout';

export interface ContributionDay {
    date: string | null;
    totalSeconds: number;
    level: number;
    manual: boolean;
}

export interface MonthLabel {
    weekIndex: number;
    label: string;
}

export interface GraphDto {
    weeks: ContributionDay[][];
    monthLabels: MonthLabel[];
    totalSeconds: number;
    activeDays: number;
    currentStreak: number;
    growthEmoji: string;
    growthLabel: string;
}

export interface DailyReadingRecord {
    date: string;
    totalSeconds: number;
    bookTitles: string[];
    manuallyFilled: boolean;
}

export interface MonthlyReadingSection {
    month: string;
    totalSeconds: number;
    days: DailyReadingRecord[];
}

export interface DayDebt {
    date: string;
    debtSeconds: number;
}

export interface HistoryApiResponse {
    nickname: string;
    months: MonthlyReadingSection[];
    graph: GraphDto;
    weeklyShortfall: DayDebt[];
}

const data = ref<HistoryApiResponse | null>(null);
const error = ref(false);
const activeTab = ref<'records' | 'missed'>('records');

// 반응형 레이아웃: 좁으면 pill 탭(stacked), 넓으면 2단(split). 판단은 순수함수 chooseLayout에 위임.
// split일 때 body에 history-wide를 달아 컨테이너를 넓힌다(미디어쿼리 대신 JS로 토글 — innerWidth와
// CSS @media의 스크롤바 폭 불일치로 경계에서 어긋나는 것 방지, 단일 출처=chooseLayout(innerWidth)).
const layout = ref<RecordsLayout>(chooseLayout(typeof window !== 'undefined' ? window.innerWidth : 0));

function applyLayout(): void {
    layout.value = chooseLayout(window.innerWidth);
    document.body.classList.toggle('history-wide', layout.value === 'split');
}

onMounted(async () => {
    applyLayout();
    window.addEventListener('resize', applyLayout);
    try {
        const res = await fetch('/api/history', { credentials: 'same-origin' });
        if (!res.ok) throw new Error('fetch failed');
        data.value = await res.json();
    } catch {
        error.value = true;
    }
});

onUnmounted(() => {
    window.removeEventListener('resize', applyLayout);
    document.body.classList.remove('history-wide');
});
</script>

<template>
    <div v-if="error" class="status-line">데이터를 불러오지 못했습니다.</div>

    <template v-else-if="data">
        <header class="history-greeting">
            <h1>{{ data.nickname }}님의 독서 기록</h1>
            <p>꾸준함이 정원처럼 천천히 쌓이고 있어요.</p>
        </header>

        <!-- 독서 잔디 카드 -->
        <section class="card">
            <ContributionGraph :graph="data.graph" />
        </section>

        <!-- 독서 기록: 넓으면 2단 split, 좁으면 pill 탭 stacked (JS chooseLayout 분기) -->
        <div v-if="layout === 'split'" class="hist-split">
            <section class="card hist-pane">
                <h2>일자별 독서 시간</h2>
                <MonthlyRecords :months="data.months" />
            </section>
            <section class="card hist-pane">
                <h2>이번 주 빠뜨린 날</h2>
                <WeeklyShortfall :weeklyShortfall="data.weeklyShortfall" />
            </section>
        </div>

        <section v-else class="card hist-records">
            <div class="hist-tabs" role="tablist" aria-label="독서 기록 보기">
                <button type="button" class="hist-tab" :class="{ active: activeTab === 'records' }"
                        role="tab" :aria-selected="activeTab === 'records'"
                        @click="activeTab = 'records'">일자별 독서 시간</button>
                <button type="button" class="hist-tab" :class="{ active: activeTab === 'missed' }"
                        role="tab" :aria-selected="activeTab === 'missed'"
                        @click="activeTab = 'missed'">이번 주 빠뜨린 날</button>
            </div>
            <div v-show="activeTab === 'records'" class="hist-panel">
                <MonthlyRecords :months="data.months" />
            </div>
            <div v-show="activeTab === 'missed'" class="hist-panel">
                <WeeklyShortfall :weeklyShortfall="data.weeklyShortfall" />
            </div>
        </section>

        <p class="link-row">
            <a href="/">← 대시보드</a>
            <a href="/books"><svg class="link-ico" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="4" y="4" width="3.4" height="16" rx="1" /><rect x="9.3" y="4" width="3.4" height="16" rx="1" /><path d="M15 5.4l3.3-.7 2.4 15.4-3.3.7z" /></svg>내 책장</a>
        </p>
    </template>

    <div v-else class="status-line">불러오는 중…</div>
</template>
