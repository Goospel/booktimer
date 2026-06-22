<script setup lang="ts">
import type { CatalogDto } from './types'
defineProps<{ garden: CatalogDto }>()
</script>

<template>
    <details class="card garden-summary-panel">
        <summary>
            🌱 내 마을 식물 도감
            <span class="garden-count">{{ garden.ownedCount }} / {{ garden.totalCount }}</span>
        </summary>
        <p v-if="garden.daysToNextUnlock !== null" class="garden-next">
            다음 잠금 해제까지 <strong>{{ garden.daysToNextUnlock }}일</strong>
            <span v-if="garden.nextPlantName"> — {{ garden.nextPlantName }}</span>
        </p>
        <div class="garden-owned-list">
            <span v-for="p in garden.plants.filter(p => p.owned)" :key="p.code"
                  :title="p.name" class="garden-emoji">{{ p.emoji }}</span>
        </div>
        <a class="quick-tile garden-link-tile" href="/village">
            <span class="qt-icon">🌱</span><span class="qt-label">마을</span>
        </a>
    </details>
</template>
