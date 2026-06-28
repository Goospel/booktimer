<template>
  <div class="village-shell">
    <div v-if="loadError" class="village-loading">⚠️ 마을을 불러오지 못했어요.</div>
    <div v-else-if="!data" class="village-loading">🌱 불러오는 중…</div>
    <template v-else>
      <!-- 게임 스테이지 — 풀스크린 채움 -->
      <GardenGame ref="gameRef" :data="data" @fed="onFed" />

      <!-- HUD — .village-shell 기준 절대배치 -->
      <div class="village-hud">
        <div class="village-hud-top">
          <a href="/" class="village-hud-brand">
            <span>📚</span> BookTimer
          </a>
          <span class="village-hud-greeting">{{ nickname }}님의 마을</span>
          <span class="village-hud-food">🍙 {{ data.foodBalance ?? 0 }}</span>
        </div>
        <div class="village-hud-actions">
          <button type="button" class="village-hud-btn" @click="gameRef?.startEdit()">✏️ 꾸미기</button>
          <button type="button" class="village-hud-btn" @click="dexOpen = true">📖 도감</button>
          <a href="/" class="village-hud-btn">← 홈</a>
        </div>
      </div>

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
  // 배회 캐릭터 정(affection) 갱신
  if (data.value.characters) {
    for (const c of data.value.characters) {
      if (c.code === result.characterCode) { c.affection = result.affection; break; }
    }
  }
  // 도감 authorCharacters 정 + 레벨 갱신
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

onUnmounted(() => document.removeEventListener('keydown', onKeydown));
</script>
