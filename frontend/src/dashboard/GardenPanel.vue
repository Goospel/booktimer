<script setup lang="ts">
import type { CatalogDto } from './types'
defineProps<{ garden: CatalogDto }>()
</script>

<template>
    <details class="card garden-summary-panel">
        <summary>
            🏘️ 내 마을
            <span class="garden-count">
                작가 {{ garden.ownedAuthorCharacterCount }}/{{ garden.totalAuthorCharacterCount }}
                · 건물 {{ garden.ownedBuildingCount }}/{{ garden.totalBuildingCount }}
            </span>
        </summary>
        <a class="quick-tile garden-link-tile" href="/village">
            <span class="qt-icon">🏘️</span><span class="qt-label">마을</span>
        </a>
    </details>
</template>
