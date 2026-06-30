<template>
  <div class="village-shell">
    <div v-if="loadError" class="village-loading">⚠️ 서재를 불러오지 못했어요.</div>
    <div v-else-if="!data" class="village-loading">🌱 불러오는 중…</div>
    <template v-else>
      <!-- 돌봄 뷰(다마고치) — 모든 화면 단일 모드. CoC 가로 무대(GardenGame)는 은퇴. -->
      <PortraitVillage :data="data" :nickname="nickname"
        @fed="onFed" @open-dex="dexOpen = true" />

      <!-- 도감 전체 오버레이 -->
      <div v-if="dexOpen" class="village-dex-overlay" @click.self="dexOpen = false">
        <div class="village-dex-panel">
          <div class="village-dex-head">
            <span>📖 도감</span>
            <button type="button" class="village-dex-close" @click="dexOpen = false">✕</button>
          </div>
          <GardenDex :catalog="data.catalog" />
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue';
import GardenDex from './GardenDex.vue';
import PortraitVillage from './PortraitVillage.vue';

const data = ref<any>(null);
const loadError = ref(false);
const dexOpen = ref(false);
const nickname = ref('');

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && dexOpen.value) dexOpen.value = false;
}

onMounted(async () => {
  const appEl = document.getElementById('village-app') as HTMLElement | null;
  nickname.value = appEl?.dataset.nickname ?? '';
  document.addEventListener('keydown', onKeydown);

  try {
    const res = await fetch('/api/garden');
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    data.value = await res.json();
  } catch (e) {
    console.error('[VillageApp] API fetch 실패:', e);
    loadError.value = true;
  }
});

function onFed(result: { foodBalance: number; characterCode: string; affection: number; level: number; title: string; leveledUp: boolean }) {
  if (!data.value) return;
  data.value.foodBalance = result.foodBalance;
  // 도감 authorCharacters 정·레벨 + hero 소스(ownedCharacters) 갱신 → 미터 반응성 재계산.
  if (data.value.catalog?.authorCharacters) {
    for (const a of data.value.catalog.authorCharacters) {
      if (a.code === result.characterCode) {
        a.affection = result.affection;
        a.level = result.level;
        a.title = result.title;
        break;
      }
    }
  }
  if (data.value.catalog?.ownedCharacters) {
    for (const o of data.value.catalog.ownedCharacters) {
      if (o.code === result.characterCode) {
        o.affection = result.affection;
        o.level = result.level;
        o.title = result.title;
        break;
      }
    }
  }
}

onUnmounted(() => {
  document.removeEventListener('keydown', onKeydown);
});
</script>
