<template>
  <div class="plant-cell"
       :class="isOwned ? 'owned' : 'plant-locked'"
       v-show="!ownedOnly || isOwned"
       :title="cellTitle">
    <!-- 보유/발견: sprite 있으면 SVG, 없으면 이모지 -->
    <span v-if="isOwned" class="plant-emoji">
      <svg v-if="spriteId" class="plant-svg" aria-hidden="true">
        <use :href="'#sprite-' + spriteId"></use>
      </svg>
      <span v-else>{{ emoji }}</span>
    </span>
    <!-- 미보유: 레시피 미발견은 ❔, 나머지는 🔒 -->
    <span v-else class="plant-emoji" :class="mystery ? 'mystery' : 'locked'" aria-hidden="true">
      {{ mystery ? '❔' : '🔒' }}
    </span>

    <!-- 이름/잠금 라벨 -->
    <span v-if="isOwned" class="plant-name">{{ name }}</span>
    <span v-else class="plant-name locked">{{ mystery ? '???' : lockedLabel }}</span>

    <!-- 정(affection) 배지 — 보유 작가 + affection > 0 일 때만 -->
    <span v-if="isOwned && affection" class="plant-affection" :class="level ? `affection-lv${level}` : ''">
      Lv{{ level }} {{ affectionTitle }} ❤️{{ affection }}
    </span>

    <!-- NEW 배지 -->
    <span v-if="isNew" class="plant-new">NEW</span>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  isOwned: boolean;
  name: string;
  emoji: string;
  spriteId: string | null;
  lockedLabel: string;
  ownedOnly: boolean;
  cellTitle: string;
  isNew?: boolean;
  mystery?: boolean;
  affection?: number;
  level?: number;
  affectionTitle?: string;
}>();
</script>
