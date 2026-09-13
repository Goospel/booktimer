<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue';
import ContributionGraph from './ContributionGraph.vue';
import MonthlyRecords from './MonthlyRecords.vue';
import WeeklyShortfall from './WeeklyShortfall.vue';
import { chooseLayout, type RecordsLayout } from './layout';
import NavLinks from '../shared/NavLinks.vue';

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
    // 식물 성장 단계는 2026-08-29에 폐기했다 — 서버 응답에도 더 이상 없다.
}

/** `session.BookRead` — 그날 이 책만 읽은 시간. 표지·시간은 미니앱 기록 화면이 쓰고, 웹은 제목만 쓴다. */
export interface BookRead {
    title: string;
    coverUrl: string | null;
    seconds: number;
}

export interface DailyReadingRecord {
    date: string;
    totalSeconds: number;
    /** 그날 읽은 책(제목별 합산, 오래 읽은 순). `totalSeconds`는 이 합보다 클 수 있다(책 미지정 세션). */
    books: BookRead[];
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
            <p>꾸준함이 차곡차곡 쌓이고 있어요.</p>
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
                <h2>빠뜨린 날</h2>
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
                        @click="activeTab = 'missed'">빠뜨린 날</button>
            </div>
            <div v-show="activeTab === 'records'" class="hist-panel">
                <MonthlyRecords :months="data.months" />
            </div>
            <div v-show="activeTab === 'missed'" class="hist-panel">
                <WeeklyShortfall :weeklyShortfall="data.weeklyShortfall" />
            </div>
        </section>

        <NavLinks :links="[
            { href: '/', icon: 'home', label: '홈' },
            { href: '/books', icon: 'books', label: '내 책장' },
        ]" />
    </template>

    <div v-else class="status-line">불러오는 중…</div>
</template>
