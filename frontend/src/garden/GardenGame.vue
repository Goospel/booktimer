<template>
  <div class="village-game-root">
    <!-- 보기 Phaser 캔버스 — phaserReady 후 표시 -->
    <div id="garden-phaser-view" class="garden-phaser-fill" v-show="phaserReady && !editing"></div>

    <!-- Phaser 초기화 중 -->
    <div class="village-loading" v-if="!phaserReady && !editing">🏢 마을 불러오는 중…</div>

    <!-- 빈 마을 안내 -->
    <p v-if="!editing && phaserReady && emptyVillage" class="village-empty-msg">
      아직 빈 마을이에요. 「✏️ 꾸미기」로 모은 건물을 놓아보세요. 🏢
    </p>

    <!-- 메시지 (보기 모드) -->
    <p v-if="message && !editing" class="village-overlay-msg">{{ message }}</p>

    <!-- 편집 모드 -->
    <template v-if="editing">
      <!-- 편집 Phaser 캔버스 — 풀스크린 -->
      <div id="garden-phaser" class="garden-phaser-fill"></div>

      <!-- 편집 패널 래퍼 -->
      <div class="village-ui-wrap">
      <!-- 편집 패널 — 하단 오버레이 -->
      <div class="village-edit-panel">
        <p v-if="message" class="my-garden-msg">{{ message }}</p>
        <div class="village-edit-topbar">
          <p class="my-garden-hint">팔레트에서 누르면 마을에 놓여요. 끌어 옮기고, 밖으로 끌어내면 거둬요.
            <b>탭하면</b> 회전을 바꿀 수 있어요.</p>
          <div class="my-garden-edit-actions">
            <button type="button" class="garden-edit-btn primary" @click="save" :disabled="saving">
              {{ saving ? '저장 중…' : '저장' }}
            </button>
            <button type="button" class="garden-edit-btn" @click="cancel" :disabled="saving">취소</button>
          </div>
        </div>
        <div class="garden-toolbar" v-show="selected">
          <span class="garden-tool-label">선택한 건물</span>
          <button type="button" class="garden-tool-btn" @click="rotateSel(-15)" title="왼쪽으로 15°">⟲</button>
          <button type="button" class="garden-tool-btn" @click="rotateSel(15)" title="오른쪽으로 15°">⟳</button>
          <button type="button" class="garden-tool-btn danger" @click="removeSel" title="거두기">🗑</button>
        </div>
        <p class="garden-palette-label">🏢 건물</p>
        <div class="garden-palette">
          <p class="my-garden-hint" v-if="buildingOwned.length === 0">보유한 건물이 없어요 — 출판사 책을 모아 읽어보세요.</p>
          <button v-for="o in buildingOwned" :key="(o.axis || '') + '/' + o.code"
                  type="button" class="palette-plant"
                  :class="{ placed: isPlaced(o) }" :disabled="isPlaced(o)"
                  @click="addFromPalette(o)" :title="o.name">
            <template v-if="o.spriteId">
              <svg class="plant-svg" aria-hidden="true"><use :href="'#sprite-' + o.spriteId"></use></svg>
            </template>
            <template v-else>
              <span>{{ o.emoji }}</span>
            </template>
          </button>
        </div>
      </div>
      </div><!-- .village-ui-wrap -->
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted, onUnmounted, markRaw } from 'vue';
import Phaser from 'phaser';
import { GardenScene, GardenItemMeta } from './scene';

interface FeedResult {
    foodBalance: number;
    characterCode: string;
    affection: number;
}

interface GameData {
    world?: { width?: number; height?: number };
    placed?: GardenItemMeta[];
    owned?: GardenItemMeta[];
    characters?: GardenItemMeta[];
    foodBalance?: number;
}

const props = defineProps<{ data: GameData }>();
const emit = defineEmits<{ fed: [result: FeedResult] }>();

// ⚠️ N-082: Phaser Game/Scene은 절대 ref/reactive에 넣지 않는다.
// Vue Proxy가 Phaser 내부 순환참조를 깨뜨린다 — setup 클로저 변수로 반응성 밖에 둔다.
let scene: GardenScene | null = null;
let game: Phaser.Game | null = null;

let placed: GardenItemMeta[] = props.data.placed ?? [];
let chars: GardenItemMeta[] = props.data.characters ?? [];

const editing = ref(false);
const saving = ref(false);
const message = ref('');
const phaserReady = ref(false);
const emptyVillage = ref(placed.length === 0);
const owned = ref<GardenItemMeta[]>(props.data.owned ?? []);
// BUILDING 축만 팔레트에 표시 — 식물 4축 고아 항목 숨김 (PR-1)
const buildingOwned = computed(() => owned.value.filter(o => o.axis === 'BUILDING'));
const placedKeys = ref<Set<string>>(new Set());
const selected = ref<{ rotation: number } | null>(null);
const worldW = ref(props.data.world?.width ?? 1000);
const worldH = ref(props.data.world?.height ?? 800);

placedKeys.value = buildPlacedKeySet();

function buildPlacedKeySet(): Set<string> {
    return new Set(
        placed.filter(p => p.kind !== 'decor').map(p => `${p.axis}/${p.code}`)
    );
}

function isPlaced(o: GardenItemMeta): boolean {
    return placedKeys.value.has(`${o.axis}/${o.code}`);
}

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
        owned: owned.value,
        decorations: [],
        placed,
        characters: chars,
        worldW: worldW.value,
        worldH: worldH.value,
        readonly: true,
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

function mountPhaser() {
    destroyPhaser();
    scene = markRaw(new GardenScene({
        owned: owned.value,
        decorations: [],
        placed,
        characters: chars,
        worldW: worldW.value,
        worldH: worldH.value,
        onChange: (keys: string[]) => { placedKeys.value = new Set(keys); },
        onSelect: (info) => { selected.value = info; },
        onMessage: (msg: string) => { message.value = msg; },
    }));
    game = markRaw(new Phaser.Game({
        type: Phaser.AUTO,
        parent: 'garden-phaser',
        transparent: true,
        scale: { mode: Phaser.Scale.RESIZE },
        scene,
    }));
}

async function startEdit() {
    if (editing.value) return;
    destroyPhaser();
    phaserReady.value = false;
    editing.value = true;
    message.value = '';
    placedKeys.value = buildPlacedKeySet();
    await nextTick();
    mountPhaser();
}

function addFromPalette(o: GardenItemMeta) {
    if (!scene || isPlaced(o)) return;
    scene.addPlant(o);
}

function rotateSel(delta: number) { if (scene) scene.rotateSelected(delta); }
function removeSel() { if (scene) scene.removeSelected(); }

async function cancel() {
    destroyPhaser();
    editing.value = false;
    message.value = '';
    placedKeys.value = buildPlacedKeySet();
    selected.value = null;
    await mountView();
}

async function save() {
    if (!scene || saving.value) return;
    saving.value = true;
    message.value = '';
    const payload = scene.exportPlacements();
    try {
        const token = document.querySelector('meta[name=_csrf]') as HTMLMetaElement | null;
        const res = await fetch('/api/garden/layout', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-TOKEN': token?.content ?? '',
            },
            body: JSON.stringify(payload),
        });
        if (res.ok) {
            window.location.reload();
            return;
        }
        message.value = '저장에 실패했어요. 다시 시도해주세요.';
    } catch (_e) {
        message.value = '네트워크 오류로 저장하지 못했어요.';
    }
    saving.value = false;
}

onMounted(async () => {
    await mountView();
});

onUnmounted(() => {
    destroyPhaser();
});

// VillageApp HUD에서 편집 시작 진입점
defineExpose({ startEdit });
</script>
