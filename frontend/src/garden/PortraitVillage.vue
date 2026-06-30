<template>
  <div class="portrait-village">
    <!-- 슬림 HUD -->
    <div class="pv-hud">
      <a href="/" class="pv-hud-home" aria-label="홈으로">🏠 홈</a>
      <span class="pv-hud-title">{{ nickname }}님의 마을</span>
      <span class="pv-hud-food">🍙 {{ foodBalance }}</span>
      <button type="button" class="pv-hud-dex" @click="emit('open-dex')" aria-label="도감 열기">📖 도감</button>
    </div>

    <!-- 식구가 없을 때 -->
    <div v-if="!hero" class="pv-empty">
      <div class="pv-empty-emoji" aria-hidden="true">🏡</div>
      <p>아직 마을 식구가 없어요.</p>
      <p class="pv-empty-sub">작가의 책을 완독하면 그 작가가 마을로 찾아와요.</p>
    </div>

    <template v-else>
      <!-- 오늘의 작가 히어로 카드 -->
      <div class="pv-hero" :class="{ 'pv-hero-levelup': levelUpFlash }">
        <p class="pv-hero-label">오늘의 작가</p>
        <div class="pv-hero-portrait">
          <svg v-if="hero.spriteId" class="pv-hero-svg" aria-hidden="true">
            <use :href="'#sprite-' + hero.spriteId"></use>
          </svg>
          <span v-else class="pv-hero-emoji" aria-hidden="true">{{ hero.emoji }}</span>
          <span v-if="levelUpFlash" class="pv-hero-sparkle" aria-hidden="true">✨</span>
        </div>
        <p class="pv-hero-name">{{ hero.name }}</p>
        <p class="pv-hero-rank">Lv{{ hero.level }} · {{ hero.title || '아직 낯설어요' }}</p>

        <!-- 정 미터 -->
        <div class="pv-meter" role="progressbar"
             :aria-valuenow="Math.round(prog.progress * 100)" aria-valuemin="0" aria-valuemax="100">
          <div class="pv-meter-fill" :style="{ width: (prog.progress * 100) + '%' }"></div>
        </div>
        <p class="pv-meter-caption">
          <template v-if="prog.isMax">❤️ {{ hero.affection }} · 베프 — 최고 단계예요</template>
          <template v-else>❤️ {{ hero.affection }} / {{ prog.nextThreshold }} · 다음 단계까지 {{ (prog.nextThreshold ?? 0) - hero.affection }}번</template>
        </p>

        <!-- 밥 주기 -->
        <button type="button" class="pv-feed-btn" :disabled="!canFeed || feeding" @click="feed">
          {{ feeding ? '주는 중…' : (canFeed ? '🍙 밥 주기' : '🍙 밥이 없어요') }}
        </button>
        <p class="pv-feed-hint" aria-live="polite">
          <template v-if="message">{{ message }}</template>
          <template v-else-if="!canFeed">오늘 독서 목표를 채우면 먹이를 얻어요 🍙</template>
          <template v-else>밥을 주면 정이 쌓여요</template>
        </p>
      </div>

      <!-- 식구 작가 카드 스택 -->
      <div v-if="ownedCharacters.length > 1" class="pv-family">
        <p class="pv-family-label">마을 식구 {{ ownedCharacters.length }}명</p>
        <div class="pv-family-scroll">
          <button v-for="c in ownedCharacters" :key="c.code" type="button"
                  class="pv-family-card" :class="{ active: c.code === heroCode }"
                  @click="selectAuthor(c.code)" :title="c.name">
            <svg v-if="c.spriteId" class="pv-family-svg" aria-hidden="true"><use :href="'#sprite-' + c.spriteId"></use></svg>
            <span v-else class="pv-family-emoji" aria-hidden="true">{{ c.emoji }}</span>
            <span class="pv-family-name">{{ c.name }}</span>
            <span class="pv-family-lv">Lv{{ c.level }}</span>
          </button>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { pickTodayAuthor, affectionProgress } from './pure';

interface OwnedCharacter {
    code: string; emoji: string; name: string; spriteId: string | null;
    affection: number; level: number; title: string;
}
interface VillageData {
    foodBalance?: number;
    catalog?: { ownedCharacters?: OwnedCharacter[] };
}
interface FeedResult {
    foodBalance: number; characterCode: string; affection: number;
    level: number; title: string; leveledUp: boolean;
}

const props = defineProps<{ data: VillageData; nickname: string }>();
const emit = defineEmits<{ fed: [result: FeedResult]; 'open-dex': [] }>();

const selectedCode = ref<string | null>(null);
const feeding = ref(false);
const message = ref('');
const levelUpFlash = ref(false);

const ownedCharacters = computed<OwnedCharacter[]>(() => props.data.catalog?.ownedCharacters ?? []);
const foodBalance = computed(() => props.data.foodBalance ?? 0);

// 오늘의 작가 = 사용자가 고른 코드 우선, 없으면 정 레벨 최저(키울 여지 큰 순) 자동 선정.
const hero = computed<OwnedCharacter | null>(() => {
    const list = ownedCharacters.value;
    if (selectedCode.value) {
        const found = list.find(c => c.code === selectedCode.value);
        if (found) return found;
    }
    return pickTodayAuthor(list);
});
const heroCode = computed(() => hero.value?.code ?? null);
const prog = computed(() => affectionProgress(hero.value?.affection ?? 0));
const canFeed = computed(() => foodBalance.value > 0 && !!hero.value);

function selectAuthor(code: string) {
    selectedCode.value = code;
    message.value = '';
}

async function feed() {
    const target = hero.value;
    if (!target || feeding.value || foodBalance.value <= 0) return;
    // 먹이는 작가에 히어로를 고정 — 먹인 직후 자동선정이 다른 작가로 튀어 미터 차오름을 못 보는 일 방지.
    selectedCode.value = target.code;
    feeding.value = true;
    message.value = '';
    try {
        const token = document.querySelector('meta[name=_csrf]') as HTMLMetaElement | null;
        const res = await fetch('/api/garden/feed', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': token?.content ?? '' },
            body: JSON.stringify({ characterCode: target.code }),
        });
        if (res.ok) {
            const result: FeedResult = await res.json();
            emit('fed', result);   // 부모(VillageApp)가 data 갱신 → hero/prog 반응성 재계산(미터 차오름)
            if (result.leveledUp) {
                levelUpFlash.value = true;
                message.value = `✨ ${result.title}! 정이 깊어졌어요`;
                setTimeout(() => { levelUpFlash.value = false; }, 1600);
            } else {
                message.value = '냠냠 — 정이 조금 쌓였어요';
            }
        } else {
            const errText = await res.text().catch(() => '');
            message.value = errText || '오늘 목표를 채우면 먹이를 얻어요 🍙';
        }
    } catch (_e) {
        message.value = '네트워크 오류가 발생했어요.';
    } finally {
        feeding.value = false;
    }
}
</script>
