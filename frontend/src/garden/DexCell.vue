<template>
  <button type="button" class="plant-cell"
       :class="isOwned ? 'owned' : 'plant-locked'"
       :title="cellTitle"
       @click="emit('select')">
    <!-- 보유/발견: sprite 있으면 SVG, 없으면 이름 첫 글자(서버 `emoji` 폴백을 글자로 바꿨다) -->
    <span v-if="isOwned" class="plant-emoji">
      <svg v-if="spriteId" class="plant-svg" aria-hidden="true">
        <use :href="'#sprite-' + spriteId"></use>
      </svg>
      <span v-else>{{ (name || '?').charAt(0) }}</span>
    </span>
    <!-- 미보유: 아직 못 만난 작가는 '?', 조건을 아는 작가는 '잠김'(기본 이모지를 쓰지 않는다) -->
    <span v-else class="plant-emoji" :class="mystery ? 'mystery' : 'locked'" aria-hidden="true">
      {{ mystery ? '?' : '잠김' }}
    </span>

    <!-- 이름/잠금 라벨 -->
    <span v-if="isOwned" class="plant-name">{{ name }}</span>
    <span v-else class="plant-name locked">{{ mystery ? '???' : lockedLabel }}</span>

    <!-- 정(affection) 배지 — 보유 작가 + affection > 0 일 때만 -->
    <span v-if="isOwned && affection" class="plant-affection" :class="level ? `affection-lv${level}` : ''">
      Lv{{ level }} {{ affectionTitle }} · 정 {{ affection }}
    </span>

    <!-- NEW 배지 -->
    <span v-if="isNew" class="plant-new">NEW</span>
  </button>
</template>

<script setup lang="ts">
defineProps<{
  isOwned: boolean;
  name: string;
  emoji: string;
  spriteId: string | null;
  lockedLabel: string;
  cellTitle: string;
  isNew?: boolean;
  mystery?: boolean;
  affection?: number;
  level?: number;
  affectionTitle?: string;
}>();
const emit = defineEmits<{ select: [] }>();
</script>
