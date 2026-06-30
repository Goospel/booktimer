<template>
  <div class="village-game-root">
    <!-- 보기 Phaser 캔버스 — phaserReady 후 표시 -->
    <div id="garden-phaser-view" class="garden-phaser-fill" v-show="phaserReady"></div>

    <!-- Phaser 초기화 중 -->
    <div class="village-loading" v-if="!phaserReady">🏘️ 마을 불러오는 중…</div>

    <!-- 빈 마을 안내 — 보유 작가 캐릭터가 없을 때 -->
    <p v-if="phaserReady && emptyVillage" class="village-empty-msg">
      아직 마을 식구가 없어요 — 작가의 책을 완독하면 찾아와요. 🚶
    </p>

    <!-- 메시지 (먹이주기 안내 등) -->
    <p v-if="message" class="village-overlay-msg">{{ message }}</p>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted, onUnmounted, markRaw } from 'vue';
import Phaser from 'phaser';
import { GardenScene, GardenItemMeta } from './scene';

interface FeedResult {
    foodBalance: number;
    characterCode: string;
    affection: number;
    level: number;
    title: string;
    leveledUp: boolean;
}

interface GameData {
    world?: { width?: number; height?: number };
    characters?: GardenItemMeta[];
    foodBalance?: number;
}

const props = defineProps<{ data: GameData }>();
const emit = defineEmits<{ fed: [result: FeedResult] }>();

// ⚠️ N-082: Phaser Game/Scene은 절대 ref/reactive에 넣지 않는다.
// Vue Proxy가 Phaser 내부 순환참조를 깨뜨린다 — setup 클로저 변수로 반응성 밖에 둔다.
let scene: GardenScene | null = null;
let game: Phaser.Game | null = null;

const chars: GardenItemMeta[] = props.data.characters ?? [];

const message = ref('');
const phaserReady = ref(false);
const emptyVillage = ref(chars.length === 0);
const worldW = ref(props.data.world?.width ?? 1000);
const worldH = ref(props.data.world?.height ?? 800);

function destroyPhaser() {
    if (game) { game.destroy(true); game = null; scene = null; }
}

async function feedCharacter(characterCode: string): Promise<FeedResult | null> {
    message.value = '';
    try {
        const token = document.querySelector('meta[name=_csrf]') as HTMLMetaElement | null;
        const res = await fetch('/api/garden/feed', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-TOKEN': token?.content ?? '',
            },
            body: JSON.stringify({ characterCode }),
        });
        if (res.ok) {
            const result: FeedResult = await res.json();
            emit('fed', result);
            return result;
        }
        const errText = await res.text().catch(() => '');
        message.value = errText || '오늘 목표를 채우면 먹이를 얻어요 🍙';
        return null;
    } catch (_e) {
        message.value = '네트워크 오류가 발생했어요.';
        return null;
    }
}

async function mountView() {
    phaserReady.value = true;
    await nextTick();
    destroyPhaser();
    scene = markRaw(new GardenScene({
        characters: chars,
        worldW: worldW.value,
        worldH: worldH.value,
        onFeed: feedCharacter,
        onMessage: (msg: string) => { message.value = msg; },
    }));
    game = markRaw(new Phaser.Game({
        type: Phaser.AUTO,
        parent: 'garden-phaser-view',
        transparent: true,
        scale: { mode: Phaser.Scale.RESIZE },
        scene,
    }));
}

onMounted(async () => {
    await mountView();
});

onUnmounted(() => {
    destroyPhaser();
});
</script>
