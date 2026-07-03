<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import type { QuoteDto } from './types'
import { nextQuoteIndex, quoteFontScale } from './timerProgress'

const props = defineProps<{ quotes: QuoteDto[] }>()

// 헤더 한 줄 명언 — 스토리 도입으로 카드(QuoteCard)에서 브랜드 행 옆으로 약화(2026-07-02).
// 6초 슬롯 롤·hover 정지·reduced-motion 처리는 카드 시절 그대로 승계.
// SSR 브랜드 행(#brand-quote-slot)으로 Teleport — 브랜드는 SSR 즉시 렌더 유지, 명언만 데이터 도착 시 합류.
const QUOTE_INTERVAL_MS = 6000
const idx = ref(0)
const current = computed<QuoteDto | null>(() => props.quotes[idx.value] ?? props.quotes[0] ?? null)
// 블록은 CSS로 2줄 높이 고정 → 아래 요소는 안 밀린다. 2줄에도 넘칠 만큼 긴 명언만
// 글자수 기반으로 폰트를 줄여(--q-scale) 잘림 없이 2줄에 담는다(데스크톱은 CSS가 축소 무시).
const scale = computed(() => current.value ? quoteFontScale(current.value.text.length, current.value.author.length) : 1)

let hovering = false
let timer: ReturnType<typeof setInterval> | null = null

function rotate() {
    if (hovering || props.quotes.length <= 1) return
    idx.value = nextQuoteIndex(idx.value, props.quotes.length)
}
function onEnter() { hovering = true }
function onLeave() { hovering = false }

onMounted(() => { timer = setInterval(rotate, QUOTE_INTERVAL_MS) })
onUnmounted(() => {
    if (timer !== null) clearInterval(timer)
    timer = null
})
</script>

<template>
    <Teleport to="#brand-quote-slot">
        <div v-if="current" class="brand-quote" aria-live="polite"
             @mouseenter="onEnter" @mouseleave="onLeave">
            <Transition name="brand-quote-roll" mode="out-in">
                <span :key="idx" class="brand-quote-line" :style="{ '--q-scale': scale }">
                    <span class="brand-quote-text">{{ current.text }}</span>
                    <span class="brand-quote-author">— {{ current.author }}</span>
                </span>
            </Transition>
        </div>
    </Teleport>
</template>
