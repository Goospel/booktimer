<script setup lang="ts">
import { ref, onMounted } from 'vue';
import ContributionGraph from './ContributionGraph.vue';
import MonthlyRecords from './MonthlyRecords.vue';
import WeeklyShortfall from './WeeklyShortfall.vue';

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

onMounted(async () => {
    try {
        const res = await fetch('/api/history', { credentials: 'same-origin' });
        if (!res.ok) throw new Error('fetch failed');
        data.value = await res.json();
    } catch {
        error.value = true;
    }
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

        <!-- 독서 기록 탭 카드 -->
        <section class="card record-card">
            <!-- 탭 헤더: CSS 라디오 + v-model로 activeTab 동기화 -->
            <input type="radio" v-model="activeTab" value="records"
                   name="histtab" id="tab-records" class="tab-radio">
            <label for="tab-records" class="record-tab">일자별 독서 시간</label>
            <input type="radio" v-model="activeTab" value="missed"
                   name="histtab" id="tab-missed" class="tab-radio">
            <label for="tab-missed" class="record-tab">이번 주 빠뜨린 날</label>

            <!-- 패널 ①: 일자별 독서 -->
            <div class="tab-panel panel-records">
                <MonthlyRecords :months="data.months" />
            </div>

            <!-- 패널 ②: 이번 주 빠뜨린 날 -->
            <div class="tab-panel panel-missed">
                <WeeklyShortfall :weeklyShortfall="data.weeklyShortfall" />
            </div>
        </section>

        <p class="link-row">
            <a href="/">← 대시보드</a>
            <a href="/books">📚 내 책장</a>
        </p>
    </template>

    <div v-else class="status-line">불러오는 중…</div>
</template>
