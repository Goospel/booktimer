<script setup lang="ts">
// 공부 기록(/study/history) — /study와 같은 셸·같은 번들이고 main.ts가 경로로 고른다(설계 §2.3-ⓑ2).
// 잔디·월별 목록은 /history의 조각을 그대로 import 한다 — 잔디 방향 규약(weeks[0] = 최신 주 = 왼쪽)을
// 두 번 밟지 않기 위한 재사용이다. 「빠뜨린 날」은 공부에 없다(부채 개념 자체가 없어 안 그리면 끝).
// 날짜를 펼치면 측정 한 건씩 보이고, 그 줄에서 공부 책을 붙이거나 바꾼다(R2 — 독서 /history와 같은 모양).
import { ref, onMounted } from 'vue';
import ContributionGraph from '../history/ContributionGraph.vue';
import MonthlyRecords from '../history/MonthlyRecords.vue';
import StudyBookSheet from '../dashboard/StudyBookSheet.vue';
import type { GraphDto, SessionRow } from '../history/HistoryApp.vue';
import { fetchStudyShelf, type StudyBookRow } from './api';
import { getCsrfToken } from '../shared/follow';

/** `GET /api/study/history` = StudyHistoryService.StudyHistory 그대로 — 최신 월·최신 일 먼저.
 *  sessions = 그날 측정 한 건씩(시작순, manual 늘 false — 공부엔 직접 기록이 없다). */
interface StudyHistoryResponse {
    graph: GraphDto;
    months: { month: string; totalSeconds: number; days: { date: string; totalSeconds: number; sessions?: SessionRow[] }[] }[];
}

const data = ref<StudyHistoryResponse | null>(null);
const error = ref(false);

async function load(): Promise<void> {
    try {
        const res = await fetch('/api/study/history', { credentials: 'same-origin' });
        if (!res.ok) throw new Error(res.statusText);
        data.value = await res.json();
    } catch {
        error.value = true;
    }
}

// 공부 시트는 fetch를 안 하는 계약이다(목록은 props) — 그래서 공부 서재는 이 화면이 마운트 때 한 번 받아 둔다.
// 로딩 상태는 두지 않는다: 시트는 사용자가 줄을 눌러야 뜨고, 그 전에 못 받았으면 시트 안에서 그렇게 말한다.
const shelf = ref<StudyBookRow[]>([]);
const shelfFailed = ref(false);
async function loadShelf(): Promise<void> {
    try {
        shelf.value = (await fetchStudyShelf()).books ?? [];
    } catch {
        shelfFailed.value = true;
    }
}

onMounted(() => Promise.all([load(), loadShelf()]));

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
        const res = await fetch(`/api/study/sessions/${row.id}/book`, {
            method: 'POST',
            credentials: 'same-origin',
            headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': getCsrfToken() },
            body: JSON.stringify({ bookId }), // 떼기는 명시적 null — 키 없는 {}는 서버가 400
        });
        if (!res.ok) { if (!stale()) assignError.value = '책을 붙이지 못했어요'; return; }
        if (!stale()) assigning.value = null;
        await load();
    } catch {
        if (!stale()) assignError.value = '네트워크 오류가 발생했습니다';
    } finally {
        assignPending.value = false;
    }
}
</script>

<template>
    <!-- #study-app은 #dashboard-app 류 gap 목록에 없다 → .page-stack 래퍼 패턴(app.css:866-880) -->
    <div class="page-stack">
        <div v-if="error" class="status-line">데이터를 불러오지 못했습니다.</div>

        <template v-else-if="data">
            <header class="history-greeting">
                <h1>공부 기록</h1>
                <p>타이머가 잰 시간만 담아요. 지킴·못 지킴은 일정에서 봐요.</p>
            </header>

            <section class="card is-study">
                <ContributionGraph :graph="data.graph" mode="study" />
            </section>

            <section class="card hist-pane is-study">
                <h2>일자별 공부 시간</h2>
                <MonthlyRecords :months="data.months" @assign="openAssign"
                                empty-text="아직 공부 기록이 없어요. 홈에서 공부 모드로 측정을 시작해 보세요." />
            </section>
        </template>

        <div v-else class="status-line">불러오는 중…</div>

        <!-- 사용자가 줄의 버튼을 눌렀을 때만 뜬다(진입 직후 자동 노출 없음 — T-183). -->
        <StudyBookSheet v-if="assigning" mode="assign" :books="shelf"
                        :current-book-id="assigning.bookId" :pending="assignPending" :load-failed="shelfFailed"
                        :error="assignError ?? (shelfFailed ? '책 목록을 불러오지 못했어요' : null)"
                        @pick="(b) => assignBook(b.id)" @none="assignBook(null)" @close="assigning = null" />
    </div>
</template>
