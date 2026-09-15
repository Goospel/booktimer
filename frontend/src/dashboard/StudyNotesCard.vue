<script setup lang="ts">
import NotesPanel from '../study/NotesPanel.vue'
import type { StudyBookRow } from '../study/api'

/**
 * 홈 필기 카드 — 공부 모드 홈의 카드는 필기 하나다(2026-09-15, 설계 2026-09-15-study-focus-lamp 결정 3).
 *
 * <p>백지노트는 오른쪽 바의 전용 화면(/study/recall)으로 떠났다. 필기할 책은 부모가 이미 아는
 * 「지금 공부하는 책」(측정 중이면 그 책, 아니면 칩 기본 책)이라 이 카드는 왕복을 늘리지 않는다 —
 * 옛 카드가 기본 책을 고르려고 부르던 오늘 일정(agenda) 1~2회가 사라졌다.
 */
defineProps<{ books: StudyBookRow[]; defaultBookId: number | null }>()
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
            <NotesPanel :books="books" :default-book-id="defaultBookId" />
        </div>
    </section>
</template>
