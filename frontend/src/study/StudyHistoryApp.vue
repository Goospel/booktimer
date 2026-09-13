<script setup lang="ts">
// 공부 기록(/study/history) — /study와 같은 셸·같은 번들이고 main.ts가 경로로 고른다(설계 §2.3-ⓑ2).
// 잔디·월별 목록은 /history의 조각을 그대로 import 한다 — 잔디 방향 규약(weeks[0] = 최신 주 = 왼쪽)을
// 두 번 밟지 않기 위한 재사용이다. 「빠뜨린 날」은 공부에 없다(부채 개념 자체가 없어 안 그리면 끝).
import { ref, onMounted } from 'vue';
import ContributionGraph from '../history/ContributionGraph.vue';
import MonthlyRecords from '../history/MonthlyRecords.vue';
import NavLinks from '../shared/NavLinks.vue';
import type { GraphDto } from '../history/HistoryApp.vue';
import { studyNavLinks } from './pure';

/** `GET /api/study/history` = StudyHistoryService.StudyHistory 그대로 — 최신 월·최신 일 먼저, manual 항상 false. */
interface StudyHistoryResponse {
    graph: GraphDto;
    months: { month: string; totalSeconds: number; days: { date: string; totalSeconds: number }[] }[];
}

const data = ref<StudyHistoryResponse | null>(null);
const error = ref(false);

onMounted(async () => {
    try {
        const res = await fetch('/api/study/history', { credentials: 'same-origin' });
        if (!res.ok) throw new Error(res.statusText);
        data.value = await res.json();
    } catch {
        error.value = true;
    }
});
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
                <MonthlyRecords :months="data.months"
                                empty-text="아직 공부 기록이 없어요. 홈에서 공부 모드로 측정을 시작해 보세요." />
            </section>

            <NavLinks :links="studyNavLinks('history')" />
        </template>

        <div v-else class="status-line">불러오는 중…</div>
    </div>
</template>
