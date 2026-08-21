<template>
  <!-- 전체 진척(작가 축) — 시각 진행바 -->
  <div class="garden-dex-progress">
    <p class="garden-page-progress">작가 캐릭터 {{ catalog.ownedAuthorCharacterCount }}/{{ catalog.totalAuthorCharacterCount }}</p>
    <div class="garden-meter" role="progressbar"
         :aria-valuenow="Math.round(progressPct)" aria-valuemin="0" aria-valuemax="100">
      <div class="garden-meter-fill" :style="{ width: progressPct + '%' }"></div>
    </div>
  </div>

  <!-- 상태 필터칩 (건물 은퇴로 축 탭 없이 작가 단일 축) -->
  <div class="garden-controls">
    <div class="garden-tabs" role="tablist">
      <button v-for="f in FILTERS" :key="f.key" type="button" class="garden-tab"
        :class="{ active: filter === f.key }" @click="filter = f.key">{{ f.label }}</button>
    </div>
  </div>

  <!-- 작가 캐릭터 -->
  <section class="garden-axis">
    <div class="garden-dex-head">
      <h2 class="garden-axis-title">작가 캐릭터</h2>
      <span class="garden-progress">{{ catalog.ownedAuthorCharacterCount }}/{{ catalog.totalAuthorCharacterCount }}명</span>
    </div>
    <div class="garden-grid">
      <DexCell v-for="a in visibleCharacters" :key="a.code"
        :isOwned="a.owned"
        :name="a.name"
        :emoji="a.emoji"
        :spriteId="a.spriteId"
        :lockedLabel="a.matchName"
        :cellTitle="a.owned ? a.name : a.matchName + ' 완독 시'"
        :affection="a.affection"
        :level="a.level"
        :affectionTitle="a.title"
        @select="selected = a" />
    </div>
    <p class="garden-next" v-if="visibleCharacters.length === 0">해당하는 캐릭터가 없어요.</p>
    <p class="garden-next" v-else-if="catalog.ownedAuthorCharacterCount < catalog.totalAuthorCharacterCount">
      작가의 책을 완독해 캐릭터를 모아보세요
    </p>
    <p class="garden-next" v-else-if="catalog.totalAuthorCharacterCount > 0">모든 작가 캐릭터를 모았어요!</p>
  </section>

  <!-- 캐릭터 상세 바텀시트 -->
  <DexDetailSheet v-if="selected" :character="selected" @close="selected = null" />
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import DexCell from './DexCell.vue';
import DexDetailSheet from './DexDetailSheet.vue';

interface AuthorCharacterDto {
  code: string; emoji: string; name: string; spriteId: string | null;
  owned: boolean; matchName: string; affection: number; level: number; title: string;
}
interface CatalogDto {
  authorCharacters: AuthorCharacterDto[];
  ownedAuthorCharacterCount: number; totalAuthorCharacterCount: number;
}

const props = defineProps<{ catalog: CatalogDto }>();
const catalog = computed(() => props.catalog);

type Filter = 'ALL' | 'OWNED' | 'LOCKED';
const FILTERS: { key: Filter; label: string }[] = [
  { key: 'ALL', label: '전체' },
  { key: 'OWNED', label: '보유' },
  { key: 'LOCKED', label: '미보유' },
];
const filter = ref<Filter>('ALL');

const visibleCharacters = computed(() => {
  const list = catalog.value.authorCharacters;
  if (filter.value === 'OWNED') return list.filter(a => a.owned);
  if (filter.value === 'LOCKED') return list.filter(a => !a.owned);
  return list;
});

const progressPct = computed(() => {
  const t = catalog.value.totalAuthorCharacterCount;
  return t > 0 ? (catalog.value.ownedAuthorCharacterCount / t) * 100 : 0;
});

const selected = ref<AuthorCharacterDto | null>(null);
</script>
