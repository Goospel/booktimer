<script setup lang="ts">
import { computed } from 'vue'
import type { CatalogDto } from './types'
import { visibleAuthors, avatarInitial } from './timerProgress'

const props = defineProps<{ garden: CatalogDto }>()

// affection/level/title은 대시보드에서 0 고정이라 참조 금지 — name·emoji만.
const authors = computed(() => visibleAuthors(props.garden.ownedCharacters))
const stageEmojis = computed(() => authors.value.slice(0, 5))
const shownAuthors = computed(() => authors.value.slice(0, 3))
const moreCount = computed(() => Math.max(0, authors.value.length - 3))
</script>

<template>
    <section class="dash-card dash-garden">
        <div class="dash-garden-head">
            <span class="dash-pill">내 정원</span>
            <a class="dash-garden-link" href="/village">정원 가기 →</a>
        </div>

        <div class="dash-garden-stage">
            <span v-for="(a, i) in stageEmojis" :key="i" class="dash-garden-emoji">{{ a.emoji }}</span>
            <span v-if="stageEmojis.length === 0" class="dash-garden-emoji">🏘️</span>
            <div class="dash-garden-ground" aria-hidden="true"></div>
        </div>

        <p class="dash-garden-summary">
            작가 {{ garden.ownedAuthorCharacterCount }}/{{ garden.totalAuthorCharacterCount }}
            · 건물 {{ garden.ownedBuildingCount }}/{{ garden.totalBuildingCount }}
        </p>

        <div class="dash-divider"></div>

        <div class="dash-garden-authors">
            <span class="dash-garden-authors-label">방문한 작가</span>
            <div class="dash-author-row">
                <div v-for="(a, i) in shownAuthors" :key="i" class="dash-author">
                    <div class="dash-author-avatar">{{ avatarInitial(a.name) }}</div>
                    <span class="dash-author-name">{{ a.name }}</span>
                </div>
                <div v-if="moreCount > 0" class="dash-author">
                    <div class="dash-author-avatar dash-author-more">+{{ moreCount }}</div>
                    <span class="dash-author-name">더보기</span>
                </div>
                <span v-if="shownAuthors.length === 0" class="dash-garden-empty">아직 만난 작가가 없어요</span>
            </div>
        </div>
    </section>
</template>
