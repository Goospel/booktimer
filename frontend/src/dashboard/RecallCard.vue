<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import RecallPanel from '../study/RecallPanel.vue'
import { fetchAgenda, type StudyBookRow } from '../study/api'
import { cellMarks, dayTitle, type PlanItem, type RecallMark } from '../study/pure'

/**
 * 홈 백지복습 카드 — 공부 모드에서 잔디가 있던 자리(2026-09-07).
 *
 * <p>패널은 /study의 것을 <b>그대로</b> 싣는다(새로 그리는 UI 0). 이 컴포넌트가 하는 일은 그 패널에
 * 오늘치를 먹이는 것뿐이다: 오늘 날짜 · 오늘 일정 · 남은 몫 · 어제 문제 여부. 공부 서재는 부모가 이미
 * `/api/dashboard`로 받아 두었으므로 다시 부르지 않는다 — 그래서 이 카드가 늘리는 왕복은 <b>1건</b>이고,
 * 이는 방금 걷어낸 공부 잔디가 쓰던 것과 같은 수다.
 *
 * <p>달을 기기 시계로 고르는 것은 /study와 같다(거기서도 `new Date()`가 달을 정한다). 미래 잠금·프리필의
 * 기준이 되는 <b>오늘</b>만 서버 값을 쓴다.
 */
const props = defineProps<{ books: StudyBookRow[] }>()

const today = ref('')
const items = ref<PlanItem[]>([])
const recalls = ref<RecallMark[]>([])
const aiEnabled = ref(false)
const remainingAnalyze = ref(0)
const remainingTranscribe = ref(0)
const failed = ref(false)

function currentMonth(): string {
    const now = new Date()
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

async function load(): Promise<void> {
    failed.value = false
    try {
        const agenda = await fetchAgenda(currentMonth())
        today.value = agenda.today
        items.value = agenda.items
        recalls.value = agenda.recalls
        aiEnabled.value = agenda.aiEnabled
        remainingAnalyze.value = agenda.remaining.analyze
        remainingTranscribe.value = agenda.remaining.transcribe
    } catch {
        failed.value = true
    }
}
onMounted(load)

// 오늘 것만 넘긴다 — 안 거르면 어제 일정의 과목·범위가 오늘 칸에 프리필된다(화면은 멀쩡하다).
const itemsToday = computed(() => items.value.filter(i => i.date === today.value))
const hasYesterdayQuestions = computed(() => cellMarks(today.value, recalls.value).questions)

// 저장·분석 뒤엔 남은 몫과 어제 문제 표식이 달라진다 — 달력이 하는 것과 같은 재조회다.
function onSaved(): void {
    void load()
}
</script>

<template>
    <section class="dash-card dash-recall-card is-study">
        <div class="dash-card-head">
            <div class="dash-card-head-left">
                <span class="dash-pill">백지복습</span>
                <span v-if="today" class="dash-card-sub">{{ dayTitle(today) }}</span>
            </div>
            <!-- 잔디 카드가 들고 있던 기록 진입점 -->
            <a class="dash-card-link" href="/study/history">공부 기록 →</a>
        </div>

        <p v-if="failed" class="dash-card-note">오늘 복습을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.</p>
        <p v-else-if="!today" class="dash-card-note">불러오는 중…</p>
        <!-- .study-day — /study의 입력 스타일(테두리·포커스·placeholder)이 걸리는 스코프다.
             카드 자체에 붙이면 .dash-card의 gap을 덮으므로 안쪽 한 겹으로 둔다. -->
        <div v-else class="study-day">
            <RecallPanel
                :date="today"
                :today="today"
                :items="itemsToday"
                :books="books"
                :ai-enabled="aiEnabled"
                :remaining-analyze="remainingAnalyze"
                :remaining-transcribe="remainingTranscribe"
                :has-yesterday-questions="hasYesterdayQuestions"
                @saved="onSaved"
            />
        </div>
    </section>
</template>
