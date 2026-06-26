<script setup lang="ts">
// 전 페이지 하단 네비 — 대시보드 타일과 같은 룩의 가로 타일 버튼 묶음(단일 출처).
// 호출부는 links 배열만 넘긴다(조건부 링크는 호출부 computed에서 필터). 마크업·아이콘·
// 룩이 모두 여기로 모여 "한 곳 수정 = 전체 반영". 같은 .link-row CSS를 Thymeleaf fragment와
// 공유한다. (계획 §해법-3)
import NavIcon from './NavIcon.vue'

export interface NavLink {
    href: string
    icon: string
    label: string
}
defineProps<{ links: NavLink[] }>()
</script>

<template>
    <nav class="link-row" aria-label="페이지 이동">
        <a v-for="l in links" :key="l.href + l.label" :href="l.href">
            <NavIcon :name="l.icon" /><span>{{ l.label }}</span>
        </a>
    </nav>
</template>
