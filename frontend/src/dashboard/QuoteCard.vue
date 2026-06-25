<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import type { QuoteDto } from './types'
import { nextQuoteIndex } from './timerProgress'

const props = defineProps<{ quotes: QuoteDto[] }>()

// 명언 슬롯머신 로테이션: 일정 간격마다 한 줄씩 아래→위로 굴려 바꾼다(작가 캐러셀과 달리 세로 롤).
// hover 중에는 멈춰 읽을 시간을 준다. prefers-reduced-motion이면 슬라이드만 CSS로 꺼지고
// 로테이션(교체)은 유지된다(명언 다양성 보존). 빈/단일 목록 가드.
const QUOTE_INTERVAL_MS = 6000
const idx = ref(0)
const current = computed<QuoteDto | null>(() => props.quotes[idx.value] ?? props.quotes[0] ?? null)

let hovering = false
let timer: ReturnType<typeof setInterval> | null = null

function rotate() {
    if (hovering || props.quotes.length <= 1) return
    idx.value = nextQuoteIndex(idx.value, props.quotes.length)
}
function onEnter() { hovering = true }
function onLeave() { hovering = false }

onMounted(() => {
    timer = setInterval(rotate, QUOTE_INTERVAL_MS)
})
onUnmounted(() => {
    if (timer !== null) clearInterval(timer)
    timer = null
})
</script>

<template>
    <section class="dash-card dash-quote-card" @mouseenter="onEnter" @mouseleave="onLeave">
        <div class="dash-quote-reel">
            <!-- 숨은 sizer: 모든 명언을 grid 한 칸에 겹쳐 쌓아 reel 높이를 '가장 긴 명언'에 맞춘다
                 (길이 다른 명언에도 클립·높이 점프 없음, 화면폭 줄바꿈에 반응형). -->
            <div class="dash-quote-sizer" aria-hidden="true">
                <div v-for="(q, i) in quotes" :key="'s' + i" class="dash-quote-item">
                    <p class="dash-quote-text">{{ q.text }}</p>
                    <span class="dash-quote-author">— {{ q.author }}</span>
                </div>
            </div>
            <!-- 실제 보이는 한 줄. idx가 바뀌면 Transition이 아래→위 슬롯 롤을 재생. -->
            <Transition name="dash-quote-slot">
                <div v-if="current" :key="idx" class="dash-quote-item dash-quote-live" aria-live="polite">
                    <p class="dash-quote-text">{{ current.text }}</p>
                    <span class="dash-quote-author">— {{ current.author }}</span>
                </div>
            </Transition>
        </div>
    </section>
</template>
