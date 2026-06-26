<script setup lang="ts">
import { ICONS } from './icons'

// 라인 SVG 아이콘 래퍼 — name으로 ICONS 사전을 조회해 inner 마크업을 주입.
// stroke=currentColor라 부모 color를 상속(메타 14~16 / 버튼 16~18 / 네비 26).
const props = defineProps<{ name: string; size?: number }>()
const dim = () => props.size ?? 16
</script>

<template>
    <svg class="shop-ico" :width="dim()" :height="dim()" viewBox="0 0 24 24"
         fill="none" stroke="currentColor" stroke-width="1.6"
         stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <g v-html="ICONS[name]"></g>
    </svg>
</template>
