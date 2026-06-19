<template>
  <div class="my-garden-head">
    <h2 class="garden-axis-title">🌱 내 마을</h2>
    <div class="my-garden-actions">
      <button v-if="!editing" type="button" class="garden-edit-btn" @click="startEdit">✏️ 꾸미기</button>
      <span v-else class="my-garden-edit-actions">
        <button type="button" class="garden-edit-btn primary" @click="save" :disabled="saving">
          {{ saving ? '저장 중…' : '저장' }}
        </button>
        <button type="button" class="garden-edit-btn" @click="cancel" :disabled="saving">취소</button>
      </span>
    </div>
  </div>

  <p v-if="message" class="my-garden-msg">{{ message }}</p>

  <!-- 보기 Phaser 캔버스 — phaserReady 후 표시 -->
  <div class="garden-stage" v-show="phaserReady && !editing">
    <div id="garden-phaser-view" class="garden-phaser"
         :style="`aspect-ratio:${worldW} / ${worldH}`"></div>
  </div>

  <!-- Phaser 초기화 중 -->
  <div class="garden-stage" v-if="!phaserReady && !editing">
    <div class="garden-view" :style="`aspect-ratio:${worldW} / ${worldH}`">
      <p style="text-align:center;padding:2rem">🌱 마을 불러오는 중…</p>
    </div>
  </div>
  <p class="garden-next" v-if="!editing && phaserReady && emptyVillage">
    아직 빈 마을이에요. 「✏️ 꾸미기」로 모은 식물과 소품(길·연못·울타리…)을 놓아보세요. 🌱
  </p>

  <!-- 편집 모드 -->
  <template v-if="editing">
    <div>
      <p class="my-garden-hint">팔레트에서 누르면 마을에 놓여요. 끌어 옮기고, 밖으로 끌어내면 거둬요.
        <b>탭하면</b> 회전을 바꿀 수 있어요. <b>소품(길·연못·울타리…)</b>은 여러 개 놓을 수 있어요.</p>
      <div class="garden-stage garden-stage-edit">
        <div id="garden-phaser" class="garden-phaser"
             :style="`aspect-ratio:${worldW} / ${worldH}`"></div>
      </div>
      <div class="garden-toolbar" v-show="selected">
        <span class="garden-tool-label">선택한 식물</span>
        <button type="button" class="garden-tool-btn" @click="rotateSel(-15)" title="왼쪽으로 15°">⟲</button>
        <button type="button" class="garden-tool-btn" @click="rotateSel(15)" title="오른쪽으로 15°">⟳</button>
        <button type="button" class="garden-tool-btn danger" @click="removeSel" title="거두기">🗑</button>
      </div>
      <p class="garden-palette-label">🌱 식물</p>
      <div class="garden-palette">
        <p class="my-garden-hint" v-if="owned.length === 0">보유한 식물이 없어요 — 책을 읽어 모아보세요.</p>
        <button v-for="o in owned" :key="(o.axis || '') + '/' + o.code"
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
      <p class="garden-palette-label">🪴 소품</p>
      <div class="garden-palette">
        <button v-for="d in decorations" :key="d.code"
                type="button" class="palette-plant"
                @click="addDecorFromPalette(d)" :title="d.name">
          <template v-if="d.spriteId">
            <svg class="plant-svg" aria-hidden="true"><use :href="'#sprite-' + d.spriteId"></use></svg>
          </template>
          <template v-else>
            <span>{{ d.emoji }}</span>
          </template>
        </button>
      </div>
    </div>
  </template>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted, onUnmounted, markRaw } from 'vue';
import Phaser from 'phaser';
import { GardenScene, GardenItemMeta } from './scene';

interface GameData {
    world?: { width?: number; height?: number };
    placed?: GardenItemMeta[];
    owned?: GardenItemMeta[];
    decorationCatalog?: GardenItemMeta[];
    characters?: GardenItemMeta[];
}

const props = defineProps<{ data: GameData }>();

// ⚠️ N-082: Phaser Game/Scene은 절대 ref/reactive에 넣지 않는다.
// Vue Proxy가 Phaser 내부 순환참조를 깨뜨려 붕괴한다 — setup 클로저 변수로 반응성 밖에 둔다.
let scene: GardenScene | null = null;
let game: Phaser.Game | null = null;

// 셸에서 받은 데이터 — 반응성 불필요 (props에서 직접 초기화)
let placed: GardenItemMeta[] = props.data.placed ?? [];
let chars: GardenItemMeta[] = props.data.characters ?? [];

// UI 반응 상태 (Phaser 객체 포함 금지)
const editing = ref(false);
const saving = ref(false);
const message = ref('');
const phaserReady = ref(false);
const emptyVillage = ref(placed.length === 0);
const owned = ref<GardenItemMeta[]>(props.data.owned ?? []);
const decorations = ref<GardenItemMeta[]>(props.data.decorationCatalog ?? []);
const placedKeys = ref<Set<string>>(new Set());
const selected = ref<{ rotation: number } | null>(null);
const worldW = ref(props.data.world?.width ?? 1000);
const worldH = ref(props.data.world?.height ?? 800);

// placed 초기화 후에 key set을 빌드한다
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

async function mountView() {
    phaserReady.value = true;
    await nextTick();
    destroyPhaser();
    // markRaw: scene 인스턴스도 반응성 밖으로 — GardenScene 생성자 내부에서
    // Phaser가 자신을 복잡하게 구성해 Proxy 타겟이 되면 안 된다.
    scene = markRaw(new GardenScene({
        owned: owned.value,
        decorations: decorations.value,
        placed,
        characters: chars,
        worldW: worldW.value,
        worldH: worldH.value,
        readonly: true,
    }));
    game = markRaw(new Phaser.Game({
        type: Phaser.AUTO,
        parent: 'garden-phaser-view',
        width: worldW.value,
        height: worldH.value,
        transparent: true,
        scale: { mode: Phaser.Scale.FIT, autoCenter: Phaser.Scale.CENTER_HORIZONTALLY },
        scene,
    }));
}

function mountPhaser() {
    destroyPhaser();
    scene = markRaw(new GardenScene({
        owned: owned.value,
        decorations: decorations.value,
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
        width: worldW.value,
        height: worldH.value,
        transparent: true,
        scale: { mode: Phaser.Scale.FIT, autoCenter: Phaser.Scale.CENTER_HORIZONTALLY },
        scene,
    }));
}

async function startEdit() {
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

function addDecorFromPalette(d: GardenItemMeta) {
    if (!scene) return;
    scene.addDecoration(d);
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
</script>
