<template>
  <div v-if="loadError" style="text-align:center;padding:2rem">⚠️ 마을을 불러오지 못했어요.</div>
  <div v-else-if="!data" style="text-align:center;padding:2rem">🌱 불러오는 중…</div>
  <template v-else>
    <section class="card my-garden">
      <GardenGame :data="data" />
    </section>
    <section class="card garden-page">
      <GardenDex :catalog="data.catalog" />
    </section>
  </template>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import GardenGame from './GardenGame.vue';
import GardenDex from './GardenDex.vue';

const data = ref<any>(null);
const loadError = ref(false);

onMounted(async () => {
  try {
    const res = await fetch('/api/garden');
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    data.value = await res.json();
  } catch (e) {
    console.error('[VillageApp] API fetch failed:', e);
    loadError.value = true;
  }
});
</script>
