import Phaser from 'phaser';
import { GardenScene, GardenItemMeta } from './scene';

interface GardenData {
    placed: GardenItemMeta[];
    owned: GardenItemMeta[];
    decorations: GardenItemMeta[];
    worldWidth: number;
    worldHeight: number;
}

// Alpine myGarden 컴포넌트 팩토리.
// Phaser 인스턴스(scene·game)는 Alpine reactive Proxy 밖 클로저 변수에 둔다 —
// x-data 속성에 넣으면 reactive Proxy가 Phaser 내부 순환참조를 깨뜨린다(N-082).
export function myGarden() {
    const data: GardenData = (window as unknown as { __garden?: GardenData }).__garden ?? {
        placed: [], owned: [], decorations: [],
        worldWidth: 1000, worldHeight: 800,
    };
    const placedKeySet = () => new Set(
        (data.placed || []).filter(p => p.kind !== 'decor').map(p => `${p.axis}/${p.code}`)
    );
    let scene: GardenScene | null = null;
    let game: Phaser.Game | null = null;

    return {
        editing: false,
        saving: false,
        message: '',
        phaserReady: false,
        owned: data.owned || [] as GardenItemMeta[],
        decorations: data.decorations || [] as GardenItemMeta[],
        placedKeys: placedKeySet(),
        selected: null as { rotation: number } | null,

        isPlaced(o: GardenItemMeta) { return this.placedKeys.has(`${o.axis}/${o.code}`); },

        // Alpine 라이프사이클 — 컴포넌트 초기화 시 자동 호출. 보기 Phaser를 바로 마운트.
        init() {
            (this as unknown as { mountView: () => void }).mountView();
        },

        mountView() {
            // 컨테이너를 먼저 보이게 하고($nextTick 후 실제 마운트) SSR 폴백을 숨긴다.
            this.phaserReady = true;
            (this as unknown as { $nextTick: (fn: () => void) => void }).$nextTick(() => {
                scene = new GardenScene({
                    owned: data.owned || [],
                    decorations: data.decorations || [],
                    placed: data.placed || [],
                    worldW: data.worldWidth,
                    worldH: data.worldHeight,
                    readonly: true,
                });
                game = new Phaser.Game({
                    type: Phaser.AUTO,
                    parent: 'garden-phaser-view',
                    width: data.worldWidth,
                    height: data.worldHeight,
                    transparent: true,
                    scale: { mode: Phaser.Scale.FIT, autoCenter: Phaser.Scale.CENTER_HORIZONTALLY },
                    scene: scene,
                });
            });
        },

        startEdit() {
            this.destroyPhaser();
            this.phaserReady = false;
            this.editing = true;
            this.message = '';
            this.placedKeys = placedKeySet();
            // Alpine 매직 $nextTick — DOM 업데이트 후 Phaser 마운트
            (this as unknown as { $nextTick: (fn: () => void) => void }).$nextTick(
                () => (this as unknown as { mountPhaser: () => void }).mountPhaser()
            );
        },
        mountPhaser() {
            const self = this;
            scene = new GardenScene({
                owned: this.owned,
                decorations: this.decorations,
                placed: data.placed || [],
                worldW: data.worldWidth,
                worldH: data.worldHeight,
                onChange: (keys: string[]) => { self.placedKeys = new Set(keys); },
                onSelect: (info) => { self.selected = info; },
                onMessage: (msg: string) => { self.message = msg; },
            });
            game = new Phaser.Game({
                type: Phaser.AUTO,
                parent: 'garden-phaser',
                width: data.worldWidth,
                height: data.worldHeight,
                transparent: true,
                scale: { mode: Phaser.Scale.FIT, autoCenter: Phaser.Scale.CENTER_HORIZONTALLY },
                scene: scene,
            });
        },
        destroyPhaser() {
            if (game) { game.destroy(true); game = null; scene = null; }
        },
        addFromPalette(o: GardenItemMeta) {
            if (!scene || this.isPlaced(o)) return;
            scene.addPlant(o);
        },
        addDecorFromPalette(d: GardenItemMeta) {
            if (!scene) return;
            scene.addDecoration(d);
        },
        rotateSel(delta: number) { if (scene) scene.rotateSelected(delta); },
        removeSel() { if (scene) scene.removeSelected(); },
        cancel() {
            this.destroyPhaser();
            this.editing = false;
            this.message = '';
            this.placedKeys = placedKeySet();
            this.selected = null;
            (this as unknown as { mountView: () => void }).mountView();
        },
        async save() {
            if (!scene || this.saving) return;
            this.saving = true;
            this.message = '';
            const payload = scene.exportPlacements();
            try {
                const token = document.querySelector('meta[name=_csrf]') as HTMLMetaElement | null;
                const res = await fetch('/garden/layout', {
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
                this.message = '저장에 실패했어요. 다시 시도해주세요.';
            } catch (_e) {
                this.message = '네트워크 오류로 저장하지 못했어요.';
            }
            this.saving = false;
        },
    };
}
