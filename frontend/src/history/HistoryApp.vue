<script setup lang="ts">
import { ref, onMounted } from 'vue';
import ContributionGraph from './ContributionGraph.vue';
import MonthlyRecords from './MonthlyRecords.vue';
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
    /** 1초 연속 — 공부 기록 화면 칩이 읽는다. 독서 기록 화면은 읽지 않는다(REQ-118). */
    currentStreak: number;
    /** 목표를 채운 날의 연속(REQ-117 · 118) — 독서 기록만 싣는다. 공부 잔디 DTO엔 없는 키라 optional. */
    goalStreak?: number;
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

// 서버는 「빠뜨린 날」(weeklyShortfall)도 그대로 보내지만 웹은 읽지 않는다(책생글 PR-R · REQ-119 — 미니앱 · 옛 클라이언트 몫).
export interface HistoryApiResponse {
    nickname: string;
    months: MonthlyReadingSection[];
    graph: GraphDto;
}

const data = ref<HistoryApiResponse | null>(null);
const error = ref(false);

async function load(): Promise<void> {
    try {
        const res = await fetch('/api/history', { credentials: 'same-origin' });
        if (!res.ok) throw new Error('fetch failed');
        data.value = await res.json();
    } catch {
        error.value = true;
    }
}

onMounted(load);

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
            <ContributionGraph :graph="data.graph" :streak="data.graph.goalStreak ?? 0" />
        </section>

        <!-- 독서 기록 — 한 칸. 「빠뜨린 날」 목록은 걷었다(책생글 PR-R · REQ-119 — 웹 연속은 목표 기준이다).
             hist-records는 목록 시간 글자색이 걸린 클래스다(app.css 「일자별 시간 색」). -->
        <section class="card hist-records">
            <h2>일자별 독서 시간</h2>
            <MonthlyRecords :months="data.months" @assign="openAssign" />
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
