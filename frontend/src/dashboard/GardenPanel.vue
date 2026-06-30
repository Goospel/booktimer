<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import type { CatalogDto } from './types'
import { visibleAuthors, nextAutoScroll, centeredIndex } from './timerProgress'

const props = defineProps<{ garden: CatalogDto }>()

// affection/level/title은 대시보드에서 0 고정이라 참조 금지 — name·emoji·spriteId만.
// 무대 하나로 통합: 입주한 작가 전체를 가운데 포커스 캐러셀로(좌우 중앙 패딩).
// 개별 이름 텍스트는 폐지 — 대신 "지금 중앙 작가"의 이름만 상단에 한 줄로 보여준다.
const residents = computed(() => visibleAuthors(props.garden.ownedCharacters))

const stageEl = ref<HTMLElement | null>(null)

// 가운데(중앙)에 온 작가 인덱스 + 그 이름. 스크롤(자동·수동)마다 onScroll이 갱신해
// 작가가 중앙을 지날 때 상단 이름도 함께 바뀐다.
const focusIdx = ref(0)
const focusName = computed(() => residents.value[focusIdx.value]?.name ?? '')
function onScroll() {
    const el = stageEl.value
    if (!el) return
    focusIdx.value = centeredIndex(el.scrollLeft, measureStep(el), residents.value.length)
}

// ── 자동 스크롤(한 칸씩, 끝에서 왕복) ─────────────────────────────────────────
// 일정 간격마다 한 작가(=한 칸)씩 옆으로 부드럽게 이동. 끝·시작에 닿으면 방향 반전.
// 사용자 스크롤(휠·드래그·터치)은 폐지 — 무대는 CSS overflow:hidden이라 사용자가 못 굴리고
// 오직 이 자동 스크롤만 scrollTo로 움직인다. hover 중에는 멈춰 읽을 시간을 준다.
// prefers-reduced-motion이면 아예 켜지 않는다(접근성).
const AUTO_INTERVAL_MS = 2500
let autoDir = 1
let hovering = false
let timer: ReturnType<typeof setInterval> | null = null

// 한 칸 너비 = 첫 작가 카드 폭 + gap. 측정 실패 시 0 → nextAutoScroll이 멈춤 처리.
function measureStep(el: HTMLElement): number {
    const first = el.querySelector('.dash-garden-resident') as HTMLElement | null
    if (!first) return 0
    const gap = parseFloat(getComputedStyle(el).columnGap || '0') || 0
    return first.offsetWidth + gap
}

function autoTick() {
    const el = stageEl.value
    if (!el || hovering) return
    if (residents.value.length <= 1) return
    const step = measureStep(el)
    const r = nextAutoScroll(el.scrollLeft, step, el.clientWidth, el.scrollWidth, autoDir)
    autoDir = r.dir
    el.scrollTo({ left: r.left, behavior: 'smooth' })
}

function onMouseEnter() { hovering = true }
function onMouseLeave() { hovering = false }

onMounted(() => {
    const reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
    if (reduced) return
    timer = setInterval(autoTick, AUTO_INTERVAL_MS)
})
onUnmounted(() => {
    if (timer !== null) clearInterval(timer)
    timer = null
})
</script>

<template>
    <section class="dash-card dash-garden">
        <div class="dash-garden-head">
            <span class="dash-pill">서재</span>
            <a class="dash-garden-link" href="/village">서재 가기 →</a>
        </div>

        <div class="dash-garden-stage-wrap">
            <div class="dash-garden-ground" aria-hidden="true"></div>
            <!-- 상단 중앙: 지금 가운데에 온 작가 이름(스크롤 따라 바뀜). 한 명만 표시라 잘릴 일 적음. -->
            <div v-if="residents.length > 0" class="dash-garden-focus-name" aria-live="polite">{{ focusName }}</div>
            <div ref="stageEl" class="dash-garden-stage" :class="{ 'is-empty': residents.length === 0 }"
                 @scroll="onScroll"
                 @mouseenter="onMouseEnter" @mouseleave="onMouseLeave">
                <div v-for="(a, i) in residents" :key="a.code ?? i" class="dash-garden-resident"
                     :title="a.name" :aria-label="a.name">
                    <svg v-if="a.spriteId" class="dash-garden-sprite" aria-hidden="true">
                        <use :href="'#sprite-' + a.spriteId"></use>
                    </svg>
                    <span v-else class="dash-garden-resident-emoji" aria-hidden="true">{{ a.emoji }}</span>
                </div>
                <span v-if="residents.length === 0" class="dash-garden-empty">아직 입주한 작가가 없어요</span>
            </div>
        </div>

        <p class="dash-garden-summary">
            작가 {{ garden.ownedAuthorCharacterCount }}/{{ garden.totalAuthorCharacterCount }}
        </p>
    </section>
</template>
