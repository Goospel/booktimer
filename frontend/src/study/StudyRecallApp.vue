<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';

import RecallPanel from './RecallPanel.vue';
import { fetchAgenda, fetchStudyBooks, type StudyBookRow } from './api';
import { cellMarks, dayTitle, recallDateParam, type PlanItem, type RecallMark } from './pure';

/**
 * 백지노트 화면(/study/recall) — 오른쪽 바 「백지노트」가 여는 곳(설계 2026-09-15-study-focus-lamp §2-3).
 *
 * <p>필기와 백지노트를 한 탭 묶음에서 떼면서(2026-09-15) 옛 홈 카드(dashboard/RecallCard.vue)의 로직이
 * 이리 왔다. 패널은 /study의 것을 그대로 싣고, 이 화면이 하는 일은 그 패널에 <b>그날치</b>를 먹이는 것뿐이다:
 * 날짜 · 그날 일정 · 남은 몫 · 어제 문제 여부 · 공부 서재.
 *
 * <p>날짜는 `?date=`(일정 화면 하루 패널의 링크)가 정하고, 없거나 틀리면 오늘이다. 오늘 모드에선 달을
 * 기기 시계로 고르는 것이 /study와 같고, 미래 잠금·프리필의 기준인 <b>오늘</b>만 서버 값을 쓴다.
 */
const param = recallDateParam(location.search);

const date = ref('');
const today = ref('');
const items = ref<PlanItem[]>([]);
const recalls = ref<RecallMark[]>([]);
const books = ref<StudyBookRow[]>([]);
const aiEnabled = ref(false);
const remainingAnalyze = ref(0);
const remainingTranscribe = ref(0);
const failed = ref(false);

function currentMonth(): string {
    const now = new Date();
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

async function load(): Promise<void> {
    failed.value = false;
    try {
        const asked = param ? param.slice(0, 7) : currentMonth();
        let agenda = await fetchAgenda(asked);
        // 오늘 모드에서만: 달은 기기 시계로 고르는데 today는 서버(유저 tz)다 — 시차로 둘이 다른 달로 갈리면
        // 요청한 달에 오늘이 없어 **일정이 빈 채로 화면은 멀쩡하게** 그려진다(프리필과 어제 문제만 조용히
        // 사라진다). 한 번만 바로잡는다 — 두 번째도 어긋나면 못 맞추는 서버이므로 매달리지 않는다.
        // `?date=`는 달을 날짜가 정했으므로 today와 달라도 되부르지 않는다.
        if (!param && agenda.today.slice(0, 7) !== asked) agenda = await fetchAgenda(agenda.today.slice(0, 7));
        today.value = agenda.today;
        date.value = param ?? agenda.today;
        items.value = agenda.items;
        recalls.value = agenda.recalls;
        aiEnabled.value = agenda.aiEnabled;
        remainingAnalyze.value = agenda.remaining.analyze;
        remainingTranscribe.value = agenda.remaining.transcribe;
    } catch {
        failed.value = true;
    }
}

onMounted(async () => {
    void fetchStudyBooks().then((rows) => (books.value = rows));
    await load();
});

// 그날 것만 넘긴다 — 안 거르면 다른 날 일정의 과목·범위가 이 날 칸에 프리필된다(화면은 멀쩡하다).
const itemsOn = computed(() => items.value.filter((i) => i.date === date.value));
const hasYesterdayQuestions = computed(() => cellMarks(date.value, recalls.value).questions);
</script>

<template>
    <section class="dash-card dash-recall-card is-study">
        <div class="dash-card-head">
            <div class="dash-card-head-left">
                <span class="dash-pill">백지노트</span>
                <span v-if="date" class="dash-card-sub">{{ dayTitle(date) }}</span>
            </div>
        </div>

        <p v-if="failed" class="dash-card-note">백지노트를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.</p>
        <p v-else-if="!date" class="dash-card-note">불러오는 중…</p>
        <!-- .study-day — /study의 입력 스타일(테두리·포커스·placeholder)이 걸리는 스코프다.
             카드 자체에 붙이면 .dash-card의 gap을 덮으므로 안쪽 한 겹으로 둔다. -->
        <div v-else class="study-day">
            <!-- 저장·분석 뒤엔 남은 몫과 어제 문제 표식이 달라진다 — 달력이 하는 것과 같은 재조회다. -->
            <RecallPanel
                :date="date"
                :today="today"
                :items="itemsOn"
                :books="books"
                :ai-enabled="aiEnabled"
                :remaining-analyze="remainingAnalyze"
                :remaining-transcribe="remainingTranscribe"
                :has-yesterday-questions="hasYesterdayQuestions"
                @saved="load"
            />
        </div>
    </section>
</template>
