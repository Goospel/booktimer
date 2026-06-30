<script setup lang="ts">
import { avatarInitial } from '../dashboard/timerProgress'

// 프로필 아바타 내용물 — 도감에서 보유한 작가를 골랐으면 그 SVG 스프라이트(#sprite-{code})를,
// 아니면 로그인ID 첫 글자 이니셜을 보여준다. 부모(.dash-header-avatar 원형) 안의 "내용물"만 렌더한다.
// 스프라이트 심볼(<symbol id="sprite-{code}">)은 페이지에 garden-character-sprites fragment로 들어와 있어야 한다.
defineProps<{
    /** 선택한 도감 작가 캐릭터 코드. 있으면 스프라이트, null/빈 값이면 이니셜 폴백. */
    code?: string | null
    /** code가 없을 때 이니셜로 쓸 문자열(보통 loginId). */
    fallbackText: string
}>()
</script>

<template>
    <svg v-if="code" class="dash-header-avatar-sprite" aria-hidden="true">
        <use :href="'#sprite-' + code"></use>
    </svg>
    <template v-else>{{ avatarInitial(fallbackText) }}</template>
</template>
