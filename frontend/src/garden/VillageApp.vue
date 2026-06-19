<template>
  <div class="village-shell">
    <div v-if="loadError" class="village-loading">⚠️ 마을을 불러오지 못했어요.</div>
    <div v-else-if="!data" class="village-loading">🌱 불러오는 중…</div>
    <template v-else>
      <!-- 게임 스테이지 — 풀스크린 채움 (카메라 회전이라 DOM 안 돎) -->
      <GardenGame ref="gameRef" :data="data" />

      <!-- UI 래퍼 — portrait에서 게임 카메라(+π/2)와 같은 시각 방향으로 회전 -->
      <div class="village-ui-wrap">
        <!-- HUD — 게임 위 절대배치 버튼들 -->
        <div class="village-hud">
          <div class="village-hud-top">
            <a href="/" class="village-hud-brand">
              <span>📚</span> BookTimer
            </a>
            <span class="village-hud-greeting">{{ nickname }}님의 마을</span>
          </div>
          <div class="village-hud-actions">
            <button type="button" class="village-hud-btn" @click="gameRef?.startEdit()">✏️ 꾸미기</button>
            <button type="button" class="village-hud-btn" @click="dexOpen = true">📖 도감</button>
            <a href="/" class="village-hud-btn">← 대시보드</a>
          </div>
        </div>

        <!-- 도감 전체 오버레이 (transform 조상 안 — portrait시 래퍼 기준 inset:0) -->
        <div v-if="dexOpen" class="village-dex-overlay" @click.self="dexOpen = false">
          <div class="village-dex-panel">
            <div class="village-dex-head">
              <span>📖 도감</span>
              <button type="button" class="village-dex-close" @click="dexOpen = false">✕</button>
            </div>
            <GardenDex :catalog="data.catalog" />
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue';
import GardenGame from './GardenGame.vue';
import GardenDex from './GardenDex.vue';

const data = ref<any>(null);
const loadError = ref(false);
const dexOpen = ref(false);
const gameRef = ref<any>(null);
const nickname = ref('');

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && dexOpen.value) dexOpen.value = false;
}

onMounted(async () => {
  nickname.value =
    (document.getElementById('village-app') as HTMLElement | null)?.dataset.nickname ?? '';
  document.addEventListener('keydown', onKeydown);

  try {
    const res = await fetch('/api/garden');
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    data.value = await res.json();
  } catch (e) {
    console.error('[VillageApp] API fetch failed:', e);
    loadError.value = true;
  }
});

onUnmounted(() => document.removeEventListener('keydown', onKeydown));
</script>
