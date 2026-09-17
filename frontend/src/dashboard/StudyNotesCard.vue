<script setup lang="ts">
import { ref } from 'vue'
import NotesPanel from '../study/NotesPanel.vue'
import type { StudyBookRow } from '../study/api'

/**
 * 홈 필기 카드 — 공부 모드 홈의 카드는 필기 하나다(2026-09-15, 설계 2026-09-15-study-focus-lamp 결정 3).
 *
 * <p>백지노트는 오른쪽 바의 전용 화면(/study/recall)으로 떠났다. 필기할 책은 부모가 이미 아는
 * 「지금 공부하는 책」(측정 중이면 그 책, 아니면 칩 기본 책)이라 이 카드는 왕복을 늘리지 않는다 —
 * 옛 카드가 기본 책을 고르려고 부르던 오늘 일정(agenda) 1~2회가 사라졌다.
 *
 * <p>focus-body — 측정 중엔 타이머 카드와 한 장으로 합쳐지고 이 카드의 머리는 숨는다(app.css 「공부 집중」 절).
 */
defineProps<{
    books: StudyBookRow[]
    defaultBookId: number | null
    /** `/?note=<id>`로 들어왔을 때 열 장 — 부모가 주소에서 읽어 넘긴다(설계 2026-09-17 D2). */
    initialNoteId?: number | null
}>()

const panel = ref<{ focusEnd: () => void } | null>(null)
/** 캐럿 중계 — 부모(DashboardApp)가 공부 측정 시작 전환 뒤 부른다. */
defineExpose({ focusEnd: () => panel.value?.focusEnd() })
</script>

<template>
    <section class="dash-card dash-recall-card is-study focus-body">
        <div class="dash-card-head">
            <div class="dash-card-head-left">
                <span class="dash-pill">필기</span>
            </div>
        </div>
        <!-- .study-day — /study의 입력 스타일이 걸리는 스코프(카드 자체에 붙이면 .dash-card의 gap을 덮는다). -->
        <div class="study-day">
            <NotesPanel ref="panel" :books="books" :default-book-id="defaultBookId" :initial-note-id="initialNoteId" />
        </div>
    </section>
</template>
