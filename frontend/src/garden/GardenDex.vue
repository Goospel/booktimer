<template>
  <p class="garden-beta">🚧 베타 · 마을은 개발 중이에요. 더 다양한 작가·건물·이야기로 채워집니다.</p>

  <!-- 전체 진척 한 줄(작가·건물 2축) -->
  <p class="garden-page-progress">
    🧑 작가캐릭터 {{ catalog.ownedAuthorCharacterCount }}/{{ catalog.totalAuthorCharacterCount }}
    · 🏢 건물 {{ catalog.ownedBuildingCount }}/{{ catalog.totalBuildingCount }}
  </p>

  <!-- 컨트롤 바: 축 탭 + 보유/미보유 필터 -->
  <div class="garden-controls">
    <div class="garden-tabs" role="tablist" aria-label="도감 축 선택">
      <button type="button" class="garden-tab" :class="{ active: tab === 'all' }" @click="tab = 'all'">전체</button>
      <button type="button" class="garden-tab" :class="{ active: tab === 'author' }" @click="tab = 'author'">🧑 작가캐릭터</button>
      <button type="button" class="garden-tab" :class="{ active: tab === 'building' }" @click="tab = 'building'">🏢 건물</button>
    </div>
    <label class="garden-filter">
      <input type="checkbox" v-model="ownedOnly">
      <span>보유한 것만</span>
    </label>
  </div>

  <!-- 작가 캐릭터 -->
  <section class="garden-axis" v-show="tab === 'all' || tab === 'author'">
    <div class="garden-dex-head">
      <h2 class="garden-axis-title">🧑 작가 캐릭터</h2>
      <span class="garden-progress">{{ catalog.ownedAuthorCharacterCount }}/{{ catalog.totalAuthorCharacterCount }}명</span>
    </div>
    <div class="garden-grid">
      <DexCell v-for="a in catalog.authorCharacters" :key="a.code"
        :isOwned="a.owned"
        :name="a.name"
        :emoji="a.emoji"
        :spriteId="a.spriteId"
        :lockedLabel="a.matchName"
        :ownedOnly="ownedOnly"
        :cellTitle="a.owned ? a.name : a.matchName + ' 완독 시'" />
    </div>
    <p class="garden-next" v-if="catalog.ownedAuthorCharacterCount < catalog.totalAuthorCharacterCount">
      🧑 작가의 책을 완독해 캐릭터를 모아보세요
    </p>
    <p class="garden-next" v-else-if="catalog.totalAuthorCharacterCount > 0">🎉 모든 작가 캐릭터를 모았어요!</p>
  </section>

  <!-- 출판사 건물 -->
  <section class="garden-axis" v-show="tab === 'all' || tab === 'building'">
    <div class="garden-dex-head">
      <h2 class="garden-axis-title">🏢 출판사 건물</h2>
      <span class="garden-progress">{{ catalog.ownedBuildingCount }}/{{ catalog.totalBuildingCount }}채</span>
    </div>
    <div class="garden-grid">
      <DexCell v-for="b in catalog.buildings" :key="b.code"
        :isOwned="b.owned"
        :name="b.name"
        :emoji="b.emoji"
        :spriteId="b.spriteId"
        :lockedLabel="b.matchName + ' ' + b.thresholdCount + '권'"
        :ownedOnly="ownedOnly"
        :cellTitle="b.owned ? b.name : b.matchName + ' ' + b.thresholdCount + '권 완독 시'" />
    </div>
    <p class="garden-next" v-if="catalog.ownedBuildingCount < catalog.totalBuildingCount">
      🏢 특정 출판사의 책을 모아 읽으면 건물이 생겨요
    </p>
    <p class="garden-next" v-else-if="catalog.totalBuildingCount > 0">🎉 모든 출판사 건물을 지었어요!</p>
  </section>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import DexCell from './DexCell.vue';

interface AuthorCharacterDto {
  code: string; emoji: string; name: string; spriteId: string | null;
  owned: boolean; matchName: string;
}
interface BuildingDto {
  code: string; emoji: string; name: string; spriteId: string | null;
  owned: boolean; matchName: string; thresholdCount: number;
}
interface CatalogDto {
  authorCharacters: AuthorCharacterDto[];
  ownedAuthorCharacterCount: number; totalAuthorCharacterCount: number;
  buildings: BuildingDto[];
  ownedBuildingCount: number; totalBuildingCount: number;
}

const props = defineProps<{ catalog: CatalogDto }>();
const catalog = computed(() => props.catalog);

const tab = ref<'all' | 'author' | 'building'>('all');
const ownedOnly = ref(false);
</script>
