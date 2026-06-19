<template>
  <p class="garden-beta">🚧 베타 · 마을은 개발 중이에요. 곧 더 다양한 식물과 상세 이야기로 채워집니다.</p>

  <!-- 전체 진척 한 줄(6축 합산) -->
  <p class="garden-page-progress">
    ⏳ 시간 {{ catalog.ownedCount }}/{{ catalog.totalCount }}
    · 🌸 장르 {{ catalog.ownedGenreCount }}/{{ catalog.totalGenreCount }}
    · 🖋️ 작가다양성 {{ catalog.ownedAuthorCount }}/{{ catalog.totalAuthorCount }}
    · 🏢 건물 {{ catalog.ownedBuildingCount }}/{{ catalog.totalBuildingCount }}
    · 🧑 작가캐릭터 {{ catalog.ownedAuthorCharacterCount }}/{{ catalog.totalAuthorCharacterCount }}
    · 🔍 숨은 레시피 {{ catalog.recipePlants.length - catalog.mysterySlotCount }}/{{ catalog.recipePlants.length }} 발견
  </p>

  <!-- 컨트롤 바: 축 탭 + 보유/미보유 필터 -->
  <div class="garden-controls">
    <div class="garden-tabs" role="tablist" aria-label="도감 축 선택">
      <button type="button" class="garden-tab" :class="{ active: tab === 'all' }" @click="tab = 'all'">전체</button>
      <button type="button" class="garden-tab" :class="{ active: tab === 'time' }" @click="tab = 'time'">⏳ 시간</button>
      <button type="button" class="garden-tab" :class="{ active: tab === 'genre' }" @click="tab = 'genre'">🌸 장르</button>
      <button type="button" class="garden-tab" :class="{ active: tab === 'diversity' }" @click="tab = 'diversity'">🖋️ 작가</button>
      <button type="button" class="garden-tab" :class="{ active: tab === 'building' }" @click="tab = 'building'">🏢 건물</button>
      <button type="button" class="garden-tab" :class="{ active: tab === 'author' }" @click="tab = 'author'">🧑 작가캐릭터</button>
      <button type="button" class="garden-tab" :class="{ active: tab === 'recipe' }" @click="tab = 'recipe'">🔍 숨은 레시피</button>
    </div>
    <label class="garden-filter">
      <input type="checkbox" v-model="ownedOnly">
      <span>보유한 것만</span>
    </label>
  </div>

  <!-- 시간 도감 -->
  <section class="garden-axis" v-show="tab === 'all' || tab === 'time'">
    <div class="garden-dex-head">
      <h2 class="garden-axis-title">⏳ 시간 도감</h2>
      <span class="garden-progress">{{ catalog.ownedCount }}/{{ catalog.totalCount }}종</span>
    </div>
    <div class="garden-grid">
      <DexCell v-for="p in catalog.plants" :key="p.code"
        :isOwned="p.owned"
        :name="p.name"
        :emoji="p.emoji"
        :spriteId="p.spriteId"
        :lockedLabel="'목표 ' + p.unlockThresholdDays + '일'"
        :ownedOnly="ownedOnly"
        :cellTitle="p.owned ? p.name + (p.isNew ? ' · 최근 획득' : '') : '목표 ' + p.unlockThresholdDays + '일 달성 시'"
        :isNew="p.isNew" />
    </div>
    <p class="garden-next" v-if="catalog.nextPlantName">
      다음 식물({{ catalog.nextPlantName }})까지 {{ catalog.daysToNextUnlock }}일
    </p>
    <p class="garden-next" v-else>모든 시간 식물을 모았어요! 🎉</p>
  </section>

  <!-- 장르 수집 -->
  <section class="garden-axis" v-show="tab === 'all' || tab === 'genre'">
    <div class="garden-dex-head">
      <h2 class="garden-axis-title">🌸 장르 수집</h2>
      <span class="garden-progress">{{ catalog.ownedGenreCount }}/{{ catalog.totalGenreCount }}종</span>
    </div>
    <div class="garden-grid">
      <DexCell v-for="g in catalog.genrePlants" :key="g.code"
        :isOwned="g.owned"
        :name="g.genreLabel ?? g.name"
        :emoji="g.emoji"
        :spriteId="g.spriteId"
        :lockedLabel="g.genreLabel ? g.genreLabel : g.name"
        :ownedOnly="ownedOnly"
        :cellTitle="g.owned ? g.name : (g.genreLabel ? g.genreLabel + ' 장르 완독 시' : '시드에 없는 장르 완독 시')" />
    </div>
    <p class="garden-next">📚 장르를 완독해 다양한 식물을 모아보세요</p>
  </section>

  <!-- 작가 다양성 -->
  <section class="garden-axis" v-show="tab === 'all' || tab === 'diversity'">
    <div class="garden-dex-head">
      <h2 class="garden-axis-title">🖋️ 작가 다양성</h2>
      <span class="garden-progress">{{ catalog.ownedAuthorCount }}/{{ catalog.totalAuthorCount }}종</span>
    </div>
    <div class="garden-grid">
      <DexCell v-for="d in catalog.diversityPlants" :key="d.code"
        :isOwned="d.owned"
        :name="d.name"
        :emoji="d.emoji"
        :spriteId="d.spriteId"
        :lockedLabel="'작가 ' + d.thresholdCount + '명'"
        :ownedOnly="ownedOnly"
        :cellTitle="d.owned ? d.name : '서로 다른 작가 ' + d.thresholdCount + '명 완독 시'" />
    </div>
    <p class="garden-next">✍️ 다양한 작가를 완독해 독서의 폭을 넓혀보세요</p>
  </section>

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

  <!-- 숨은 레시피 -->
  <section class="garden-axis" v-show="tab === 'all' || tab === 'recipe'">
    <div class="garden-dex-head">
      <h2 class="garden-axis-title">🔍 숨은 레시피</h2>
      <span class="garden-progress">
        {{ catalog.recipePlants.length - catalog.mysterySlotCount }}/{{ catalog.recipePlants.length }}종 발견
      </span>
    </div>
    <div class="garden-grid" v-if="catalog.recipePlants.length > 0">
      <DexCell v-for="r in catalog.recipePlants" :key="r.code"
        :isOwned="r.discovered"
        :name="r.name"
        :emoji="r.emoji"
        :spriteId="r.spriteId"
        lockedLabel="???"
        :ownedOnly="ownedOnly"
        :cellTitle="r.discovered ? r.name + (r.story ? ' · ' + r.story : '') : '아직 발견하지 못한 조합'"
        :mystery="!r.discovered" />
    </div>
    <p class="garden-next" v-if="catalog.mysterySlotCount > 0">
      🔍 아직 발견하지 못한 레시피가 {{ catalog.mysterySlotCount }}개 있어요 — 책을 읽으며 조합을 찾아보세요
    </p>
    <p class="garden-next" v-else-if="catalog.recipePlants.length > 0">✨ 숨은 레시피를 모두 발견했어요!</p>
  </section>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import DexCell from './DexCell.vue';

interface PlantDto {
  code: string; emoji: string; name: string; spriteId: string | null;
  owned: boolean; unlockedOn: string | null; isNew: boolean;
  unlockThresholdDays: number;
}
interface GenrePlantDto {
  code: string; emoji: string; name: string; spriteId: string | null;
  owned: boolean; genreLabel: string | null;
}
interface DiversityPlantDto {
  code: string; emoji: string; name: string; spriteId: string | null;
  owned: boolean; thresholdCount: number;
}
interface RecipePlantDto {
  code: string; emoji: string; name: string; spriteId: string | null;
  discovered: boolean; story: string | null;
}
interface AuthorCharacterDto {
  code: string; emoji: string; name: string; spriteId: string | null;
  owned: boolean; matchName: string;
}
interface BuildingDto {
  code: string; emoji: string; name: string; spriteId: string | null;
  owned: boolean; matchName: string; thresholdCount: number;
}
interface CatalogDto {
  plants: PlantDto[];
  ownedCount: number; totalCount: number; achievedDays: number;
  daysToNextUnlock: number | null; nextPlantName: string | null;
  genrePlants: GenrePlantDto[];
  ownedGenreCount: number; totalGenreCount: number;
  diversityPlants: DiversityPlantDto[];
  ownedAuthorCount: number; totalAuthorCount: number;
  recipePlants: RecipePlantDto[];
  mysterySlotCount: number; justDiscovered: string[];
  authorCharacters: AuthorCharacterDto[];
  ownedAuthorCharacterCount: number; totalAuthorCharacterCount: number;
  ownedCharacters: never[];
  buildings: BuildingDto[];
  ownedBuildingCount: number; totalBuildingCount: number;
}

const props = defineProps<{ catalog: CatalogDto }>();
const catalog = computed(() => props.catalog);

const tab = ref<'all' | 'time' | 'genre' | 'diversity' | 'building' | 'author' | 'recipe'>('all');
const ownedOnly = ref(false);
</script>
