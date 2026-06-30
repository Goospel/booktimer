<template>
  <p class="garden-beta">🚧 베타 · 마을은 개발 중이에요. 더 다양한 작가·이야기로 채워집니다.</p>

  <!-- 전체 진척 한 줄(작가 축) -->
  <p class="garden-page-progress">
    🧑 작가캐릭터 {{ catalog.ownedAuthorCharacterCount }}/{{ catalog.totalAuthorCharacterCount }}
  </p>

  <!-- 컨트롤 바: 보유/미보유 필터 (건물 은퇴로 축 탭 제거 — 작가 단일 축) -->
  <div class="garden-controls">
    <label class="garden-filter">
      <input type="checkbox" v-model="ownedOnly">
      <span>보유한 것만</span>
    </label>
  </div>

  <!-- 작가 캐릭터 -->
  <section class="garden-axis">
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
        :cellTitle="a.owned ? a.name : a.matchName + ' 완독 시'"
        :affection="a.affection"
        :level="a.level"
        :affectionTitle="a.title" />
    </div>
    <p class="garden-next" v-if="catalog.ownedAuthorCharacterCount < catalog.totalAuthorCharacterCount">
      🧑 작가의 책을 완독해 캐릭터를 모아보세요
    </p>
    <p class="garden-next" v-else-if="catalog.totalAuthorCharacterCount > 0">🎉 모든 작가 캐릭터를 모았어요!</p>
  </section>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import DexCell from './DexCell.vue';

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

const ownedOnly = ref(false);
</script>
