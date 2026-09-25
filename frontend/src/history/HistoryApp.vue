<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue';
import ContributionGraph from './ContributionGraph.vue';
import MonthlyRecords from './MonthlyRecords.vue';
import WeeklyShortfall from './WeeklyShortfall.vue';
import { chooseLayout, type RecordsLayout } from './layout';
import BookPickSheet from '../dashboard/BookPickSheet.vue';
import { getCsrfToken } from '../shared/follow';

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

/**
 * `DailyReadingRecord.SessionRow` — 그날 측정 한 건. 기록 화면이 한 줄로 그리고, `id`가 [책 붙이기]/[바꾸기]의
 * 좌표다(`POST /api/sessions/{id}/book`, 공부는 `/api/study/sessions/{id}/book`). 공부 기록도 같은 꼴(manual 늘 false).
 */
export interface SessionRow {
    id: number;
    /** 유저 타임존 "HH:mm". 수동 기록은 null — 그 시각은 서버 앵커라 실측이 아니다. */
    start: string | null;
    end: string | null;
    seconds: number;
    bookId: number | null;
    bookTitle: string | null;
    /** 직접 적은 기록 — 책이 필수라 「책 없이 두기」를 열지 않는다(서버 409). */
    manual: boolean;
}

export interface DailyReadingRecord {
    date: string;
    totalSeconds: number;
    /** 그날 읽은 책(제목별 합산, 오래 읽은 순). `totalSeconds`는 이 합보다 클 수 있다(책 미지정 세션). */
    books: BookRead[];
    manuallyFilled: boolean;
    /** 펼침용 측정 목록(실측 시작순 뒤 직접 기록). 옛 서버 응답엔 없어 optional — 없으면 펼치지 않는다. */
    sessions?: SessionRow[];
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

async function load(): Promise<void> {
    try {
        const res = await fetch('/api/history', { credentials: 'same-origin' });
        if (!res.ok) throw new Error('fetch failed');
        data.value = await res.json();
    } catch {
        error.value = true;
    }
}

onMounted(async () => {
    applyLayout();
    window.addEventListener('resize', applyLayout);
    await load();
});

// ── 측정 한 건의 책 붙이기·바꾸기·떼기(R2) ─────────────────────────────────────────
// 시트는 독서 BookPickSheet(assign)를 그대로 쓴다 — 목록은 시트가 /api/books로 받고, 폴백 세 목록은 빈 배열이다
// (이 화면엔 대시보드 목록이 없다. 그래서 로드 실패면 시트가 「책 목록을 불러오지 못했어요」라고 말한다).
const assigning = ref<SessionRow | null>(null);
const assignPending = ref(false);
const assignError = ref<string | null>(null);

function openAssign(row: SessionRow): void {
    assignError.value = null;
    assigning.value = row;
}

async function assignBook(bookId: number | null): Promise<void> {
    const row = assigning.value;
    if (!row || assignPending.value) return;
    // 기다리는 사이 시트를 닫고 다른 줄을 열었으면, 이 응답은 새 시트의 것이 아니다 — 오류도 닫힘도 새 시트에 붙이지 않는다.
    const stale = () => assigning.value !== row;
    assignPending.value = true;
    assignError.value = null;
    try {
        const res = await fetch(`/api/sessions/${row.id}/book`, {
            method: 'POST',
            credentials: 'same-origin',
            headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': getCsrfToken() },
            // 떼기는 명시적인 null이다 — 서버는 키 없는 {}를 400으로 거절한다.
            body: JSON.stringify({ bookId }),
        });
        if (!res.ok) { if (!stale()) assignError.value = '책을 붙이지 못했어요'; return; }
        if (!stale()) assigning.value = null;
        await load(); // 자정을 걸친 실측은 서버가 양쪽 조각을 함께 고친다 — 두 날짜 줄을 재조회로 함께 맞춘다.
    } catch {
        if (!stale()) assignError.value = '네트워크 오류가 발생했습니다';
    } finally {
        assignPending.value = false;
    }
}

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
                <MonthlyRecords :months="data.months" @assign="openAssign" />
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
                <MonthlyRecords :months="data.months" @assign="openAssign" />
            </div>
            <div v-show="activeTab === 'missed'" class="hist-panel">
                <WeeklyShortfall :weeklyShortfall="data.weeklyShortfall" />
            </div>
        </section>
    </template>

    <div v-else class="status-line">불러오는 중…</div>

    <!-- 사용자가 줄의 버튼을 눌렀을 때만 뜬다(진입 직후 자동 노출 없음 — T-183). 수동 기록은 책 필수라 「책 없이 두기」가 없다. -->
    <BookPickSheet v-if="assigning" mode="assign"
                   :reading-books="[]" :finished-books="[]" :want-to-read-books="[]"
                   :current-book-id="assigning.bookId" :none-allowed="!assigning.manual"
                   :pending="assignPending" :error="assignError"
                   @pick="(b) => assignBook(b.id)" @bookless="assignBook(null)" @close="assigning = null" />
</template>
