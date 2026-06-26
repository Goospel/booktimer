<script setup lang="ts">
// 공통 라인 아이콘 — navIcons 사전의 inner SVG를 공통 stroke/fill 래퍼에 주입.
// stroke=currentColor라 부모(.link-row a 등) color를 그대로 따라간다. profile의 ShopIcon과
// 동일 구조이나 공통 사전(navIcons)을 쓴다. (계획 §해법-2)
import { NAV_ICONS } from './navIcons'
withDefaults(defineProps<{ name: string; size?: number }>(), { size: 18 })
</script>

<template>
    <svg class="link-ico" :width="size" :height="size" viewBox="0 0 24 24" fill="none"
         stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"
         aria-hidden="true"><g v-html="NAV_ICONS[name]"></g></svg>
</template>
