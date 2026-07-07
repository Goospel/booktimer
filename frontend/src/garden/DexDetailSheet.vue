<template>
  <div class="dex-detail-overlay" @click.self="emit('close')">
    <div class="dex-detail-panel" role="dialog" aria-modal="true" ref="panel" tabindex="-1"
         @keydown.esc.stop="emit('close')">
      <div class="dex-detail-head">
        <span>{{ character.owned ? '서재 식구' : '아직 만나지 않은 작가' }}</span>
        <button type="button" class="dex-detail-close" @click="emit('close')" aria-label="닫기">✕</button>
      </div>

      <div class="dex-detail-portrait">
        <svg v-if="character.owned && character.spriteId" class="dex-detail-svg" aria-hidden="true">
          <use :href="'#sprite-' + character.spriteId"></use>
        </svg>
        <span v-else-if="character.owned" class="dex-detail-emoji" aria-hidden="true">{{ character.emoji }}</span>
        <span v-else class="dex-detail-emoji locked" aria-hidden="true">🔒</span>
      </div>

      <p class="dex-detail-name">{{ character.owned ? character.name : character.matchName }}</p>

      <!-- 보유: 레벨·칭호 + 정(affection) 진행바 -->
      <template v-if="character.owned">
        <p class="dex-detail-rank">Lv{{ character.level }} · {{ character.title || '아직 낯설어요' }}</p>
        <div class="garden-meter" role="progressbar"
             :aria-valuenow="Math.round(prog.progress * 100)" aria-valuemin="0" aria-valuemax="100">
          <div class="garden-meter-fill" :style="{ width: (prog.progress * 100) + '%' }"></div>
        </div>
        <p class="dex-detail-caption">
          <template v-if="prog.isMax">❤️ {{ character.affection }} · 베프 — 최고 단계예요</template>
          <template v-else>❤️ {{ character.affection }} / {{ prog.nextThreshold }} · 서재에서 밥을 주면 정이 쌓여요</template>
        </p>
      </template>

      <!-- 미보유: 해금 힌트 -->
      <template v-else>
        <p class="dex-detail-hint">『{{ character.matchName }}』 작가의 책을 완독하면 서재로 찾아와요 🌱</p>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { affectionProgress } from './pure';

interface Character {
  code: string; emoji: string; name: string; spriteId: string | null;
  owned: boolean; matchName: string; affection: number; level: number; title: string;
}
const props = defineProps<{ character: Character }>();
const emit = defineEmits<{ close: [] }>();

const prog = computed(() => affectionProgress(props.character.affection ?? 0));
const panel = ref<HTMLElement | null>(null);

// ESC는 패널에서 듣고 전파를 막는다(@keydown.esc.stop) — 상위 도감 오버레이의 document ESC 리스너까지
// 함께 닫히지 않게. 마운트 시 패널에 포커스를 줘 키보드로 바로 ESC가 먹히게 한다.
onMounted(() => panel.value?.focus());
</script>
