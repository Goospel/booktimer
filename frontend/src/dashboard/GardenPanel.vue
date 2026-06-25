<script setup lang="ts">
import { computed } from 'vue'
import type { CatalogDto } from './types'
import { visibleAuthors } from './timerProgress'

const props = defineProps<{ garden: CatalogDto }>()

// affection/level/title은 대시보드에서 0 고정이라 참조 금지 — name·emoji·spriteId만.
// 무대 하나로 통합: 입주한 작가 전체를 가로 스크롤로 보여준다(slice·+N 폐지).
const residents = computed(() => visibleAuthors(props.garden.ownedCharacters))
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
            <div class="dash-garden-stage" :class="{ 'is-empty': residents.length === 0 }">
                <div v-for="(a, i) in residents" :key="a.code ?? i" class="dash-garden-resident" :title="a.name">
                    <svg v-if="a.spriteId" class="dash-garden-sprite" aria-hidden="true">
                        <use :href="'#sprite-' + a.spriteId"></use>
                    </svg>
                    <span v-else class="dash-garden-resident-emoji" aria-hidden="true">{{ a.emoji }}</span>
                    <span class="dash-garden-resident-name">{{ a.name }}</span>
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
