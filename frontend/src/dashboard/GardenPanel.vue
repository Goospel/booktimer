<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import type { CatalogDto } from './types'
import { visibleAuthors, wheelScrollLeft, nextAutoScroll } from './timerProgress'

const props = defineProps<{ garden: CatalogDto }>()

// affection/level/title은 대시보드에서 0 고정이라 참조 금지 — name·emoji·spriteId만.
// 무대 하나로 통합: 입주한 작가 전체를 가로 스크롤로 보여준다(slice·+N 폐지).
// 이름 텍스트는 폐지(길어서 잘림) — 이름은 hover title·aria-label로만 보존.
const residents = computed(() => visibleAuthors(props.garden.ownedCharacters))

const stageEl = ref<HTMLElement | null>(null)

// 데스크톱: 세로 마우스 휠을 가로 스크롤로. 경계를 넘는 방향이면 페이지 세로 스크롤에 양보.
function onWheel(e: WheelEvent) {
    const el = stageEl.value
    if (!el) return
    const next = wheelScrollLeft(e.deltaY, el.scrollLeft, el.clientWidth, el.scrollWidth)
    if (next === null) return
    e.preventDefault()
    el.scrollLeft = next
}

// 데스크톱: 마우스로 잡아끌기(grab). 터치는 네이티브 가로 스크롤을 그대로 둔다(충돌 방지).
let dragging = false
let dragStartX = 0
let dragStartScroll = 0
function onPointerDown(e: PointerEvent) {
    if (e.pointerType === 'touch') return
    const el = stageEl.value
    if (!el || el.scrollWidth <= el.clientWidth) return
    dragging = true
    dragStartX = e.clientX
    dragStartScroll = el.scrollLeft
    try { el.setPointerCapture(e.pointerId) } catch { /* 캡처 미지원 환경 — 무시(드래그는 계속) */ }
    el.classList.add('is-grabbing')
}
function onPointerMove(e: PointerEvent) {
    if (!dragging) return
    const el = stageEl.value
    if (!el) return
    el.scrollLeft = dragStartScroll - (e.clientX - dragStartX)
}
function onPointerUp() {
    if (!dragging) return
    dragging = false
    stageEl.value?.classList.remove('is-grabbing')
}

// ── 자동 스크롤(한 칸씩, 끝에서 왕복) ─────────────────────────────────────────
// 일정 간격마다 한 작가(=한 칸)씩 옆으로 부드럽게 이동. 끝·시작에 닿으면 방향 반전.
// 사용자 조작(hover·드래그·터치) 중에는 일시정지해 충돌을 피한다.
// prefers-reduced-motion이면 아예 켜지 않는다(접근성).
const AUTO_INTERVAL_MS = 2500
let autoDir = 1
let hovering = false
let touching = false
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
    if (!el || hovering || dragging || touching) return
    if (residents.value.length <= 1) return
    const step = measureStep(el)
    const r = nextAutoScroll(el.scrollLeft, step, el.clientWidth, el.scrollWidth, autoDir)
    autoDir = r.dir
    el.scrollTo({ left: r.left, behavior: 'smooth' })
}

function onMouseEnter() { hovering = true }
function onMouseLeave() { hovering = false }
function onTouchStart() { touching = true }
function onTouchEnd() { touching = false }

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
            <span class="dash-pill">내 정원</span>
            <a class="dash-garden-link" href="/village">정원 가기 →</a>
        </div>

        <span class="dash-garden-authors-label">입주한 작가</span>

        <div class="dash-garden-stage-wrap">
            <div class="dash-garden-ground" aria-hidden="true"></div>
            <div ref="stageEl" class="dash-garden-stage" :class="{ 'is-empty': residents.length === 0 }"
                 @wheel="onWheel" @pointerdown="onPointerDown" @pointermove="onPointerMove"
                 @pointerup="onPointerUp" @pointercancel="onPointerUp"
                 @mouseenter="onMouseEnter" @mouseleave="onMouseLeave"
                 @touchstart="onTouchStart" @touchend="onTouchEnd" @touchcancel="onTouchEnd">
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
            · 건물 {{ garden.ownedBuildingCount }}/{{ garden.totalBuildingCount }}
        </p>
    </section>
</template>
