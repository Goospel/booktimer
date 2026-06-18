import Phaser from 'phaser';
import {
    GRID_COLS, GRID_ROWS, ISO_FLATTEN,
    clampRotation, clampZoom, initialZoomFor, plantWorldSize,
    cellOf, cellCenter, snapToCell,
    normToIsoPixel, isoPixelToNorm,
    isOutsideWorld, resolveDrop, nearestFreeCell,
} from './pure';

export interface GardenItemMeta {
    kind?: string;
    axis?: string | null;
    code?: string;
    emoji?: string;
    name?: string;
    spriteId?: string;
    x?: number;
    y?: number;
    z?: number;
    rotation?: number;
    scale?: number;
}

export interface SelectionInfo {
    rotation: number;
}

export interface GardenSceneConfig {
    owned: GardenItemMeta[];
    decorations: GardenItemMeta[];
    placed: GardenItemMeta[];
    worldW: number;
    worldH: number;
    onChange?: (keys: string[]) => void;
    onSelect?: (info: SelectionInfo | null) => void;
    onMessage?: (msg: string) => void;
}

const TARGET_PLANT_CSS = 36;

function svgTextureUrl(symbolId: string): string | null {
    const sym = document.getElementById(symbolId);
    if (!sym) return null;
    const vb = sym.getAttribute('viewBox') || '0 0 32 32';
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${vb}" width="96" height="96">${sym.innerHTML}</svg>`;
    return URL.createObjectURL(new Blob([svg], { type: 'image/svg+xml' }));
}

type GObj = Phaser.GameObjects.Image | Phaser.GameObjects.Text;

export class GardenScene extends Phaser.Scene {
    cfg: GardenSceneConfig;
    objs: GObj[];
    ready: boolean;
    plantPx!: number;
    bg?: Phaser.GameObjects.Graphics;
    shadowLayer?: Phaser.GameObjects.Graphics;
    selected?: GObj | null;
    _selBox?: Phaser.GameObjects.Graphics;
    _dragged = false;
    _panning = false;
    _panMoved = false;
    _pinchDist = 0;

    constructor(cfg: GardenSceneConfig) {
        super('garden');
        this.cfg = cfg;
        this.objs = [];
        this.ready = false;
    }

    preload() {
        const seen = new Set<string>();
        const loadTex = (spriteId?: string) => {
            if (spriteId && !seen.has(spriteId)) {
                seen.add(spriteId);
                const url = svgTextureUrl('sprite-' + spriteId);
                if (url) this.load.image('tex-' + spriteId, url);
            }
        };
        for (const o of this.cfg.owned) loadTex(o.spriteId);
        for (const d of (this.cfg.decorations || [])) loadTex(d.spriteId);
    }

    create() {
        const canvasCss = this.sys.game.canvas.getBoundingClientRect().width;
        this.plantPx = plantWorldSize(this.cfg.worldW, GRID_COLS);
        this.drawBackground();
        this.drawGrid();
        this.shadowLayer = this.add.graphics();
        this.shadowLayer.setDepth(0.6);
        for (const p of this.cfg.placed) {
            const { px, py } = normToIsoPixel(p.x ?? 0, p.y ?? 0, this.cfg.worldW, this.cfg.worldH);
            this.spawnObject(p, px, py);
        }

        const cam = this.cameras.main;
        cam.setBounds(0, 0, this.cfg.worldW, this.cfg.worldH);
        cam.setZoom(initialZoomFor(TARGET_PLANT_CSS, this.plantPx, canvasCss, this.cfg.worldW));
        cam.centerOn(this.cfg.worldW / 2, this.cfg.worldH / 2);
        this.input.addPointer(1);
        this._pinchDist = 0;

        this.input.on('gameobjectdown', () => { this._dragged = false; });
        this.input.on('dragstart', (_p: Phaser.Input.Pointer, obj: any) => {
            this._dragged = true;
            obj._homeX = obj.x; obj._homeY = obj.y;
            obj._rawX = obj.x; obj._rawY = obj.y;
        });
        this.input.on('drag', (_p: Phaser.Input.Pointer, obj: any, dragX: number, dragY: number) => {
            obj._rawX = dragX; obj._rawY = dragY;
            const norm = isoPixelToNorm(dragX, dragY, this.cfg.worldW, this.cfg.worldH);
            const snapped = snapToCell(norm.x, norm.y, GRID_COLS, GRID_ROWS);
            const pix = normToIsoPixel(snapped.x, snapped.y, this.cfg.worldW, this.cfg.worldH);
            obj.x = pix.px; obj.y = pix.py;
            const occupied = this.occupiedCells(obj as GObj);
            const { x: nx, y: ny } = isoPixelToNorm(obj.x, obj.y, this.cfg.worldW, this.cfg.worldH);
            const { col, row } = cellOf(nx, ny, GRID_COLS, GRID_ROWS);
            if (occupied.has(`${col},${row}`)) obj.setTint(0xff6b6b); else obj.clearTint();
            if (obj === this.selected) this.highlightSelected();
            this.restack();
        });
        this.input.on('dragend', (_p: Phaser.Input.Pointer, obj: any) => {
            obj.clearTint();
            const outside = isOutsideWorld(obj._rawX ?? obj.x, obj._rawY ?? obj.y, this.cfg.worldW, this.cfg.worldH);
            const { x: nx, y: ny } = isoPixelToNorm(obj.x, obj.y, this.cfg.worldW, this.cfg.worldH);
            const { col, row } = cellOf(nx, ny, GRID_COLS, GRID_ROWS);
            const occupiedByOther = !outside && this.occupiedCells(obj as GObj).has(`${col},${row}`);
            const decision = resolveDrop(outside, occupiedByOther);
            if (decision === 'remove') {
                if (obj === this.selected) this.deselectPlant();
                this.removePlant(obj as GObj);
            } else if (decision === 'revert') {
                obj.x = obj._homeX; obj.y = obj._homeY;
                if (obj === this.selected) this.highlightSelected();
                this.restack();
            } else {
                if (obj === this.selected) this.highlightSelected();
                this.restack();
            }
            this.emitChange();
        });
        this.input.on('gameobjectup', (_p: Phaser.Input.Pointer, obj: GObj) => {
            if (!this._dragged) this.selectPlant(obj);
        });

        this.input.on('pointerdown', (pointer: Phaser.Input.Pointer) => {
            this._panning = this.input.hitTestPointer(pointer).length === 0;
            this._panMoved = false;
        });
        this.input.on('pointermove', (pointer: Phaser.Input.Pointer) => {
            if (this.input.pointer1.isDown && this.input.pointer2.isDown) {
                this._panning = false;
                const p1 = this.input.pointer1, p2 = this.input.pointer2;
                const dist = Phaser.Math.Distance.Between(p1.x, p1.y, p2.x, p2.y);
                if (this._pinchDist > 0) {
                    const mx = (p1.x + p2.x) / 2, my = (p1.y + p2.y) / 2;
                    const before = cam.getWorldPoint(mx, my);
                    cam.setZoom(clampZoom(cam.zoom * (dist / this._pinchDist)));
                    const after = cam.getWorldPoint(mx, my);
                    cam.scrollX += before.x - after.x;
                    cam.scrollY += before.y - after.y;
                }
                this._pinchDist = dist;
                return;
            }
            this._pinchDist = 0;
            if (!this._panning || !pointer.isDown) return;
            cam.scrollX -= (pointer.x - pointer.prevPosition.x) / cam.zoom;
            cam.scrollY -= (pointer.y - pointer.prevPosition.y) / cam.zoom;
            this._panMoved = true;
        });
        this.input.on('pointerup', () => {
            this._pinchDist = 0;
            if (this._panning && !this._panMoved && this.selected) this.deselectPlant();
            this._panning = false;
        });

        this.input.on('wheel', (pointer: Phaser.Input.Pointer, _objs: unknown, _dx: number, dy: number) => {
            const before = cam.getWorldPoint(pointer.x, pointer.y);
            cam.setZoom(clampZoom(cam.zoom * (dy > 0 ? 0.9 : 1.1)));
            const after = cam.getWorldPoint(pointer.x, pointer.y);
            cam.scrollX += before.x - after.x;
            cam.scrollY += before.y - after.y;
        });

        this.ready = true;
    }

    drawBackground() {
        const W = this.cfg.worldW, H = this.cfg.worldH;
        const css = (n: string) => getComputedStyle(document.documentElement).getPropertyValue(n).trim();
        const hex = (s: string) => { try { return Phaser.Display.Color.HexStringToColor(s).color; } catch (_) { return 0xffffff; } };
        const g = this.add.graphics();
        this.bg = g;
        g.setDepth(0);
        g.fillStyle(hex(css('--garden-sky')) || 0xDCEAF0);
        g.fillRect(0, 0, W, H);
        const diamondVerts = [{x:0,y:0},{x:1,y:0},{x:1,y:1},{x:0,y:1}];
        const corners = diamondVerts.map(v => {
            const p = normToIsoPixel(v.x, v.y, W, H); return { x: p.px, y: p.py };
        });
        g.fillStyle(hex(css('--garden-grass')) || 0xCADDB2);
        g.fillPoints(corners, true);
    }

    drawGrid() {
        const W = this.cfg.worldW, H = this.cfg.worldH;
        const g = this.add.graphics();
        g.setDepth(0.5);
        g.lineStyle(1, 0x2e7d32, 0.18);
        for (let col = 0; col <= GRID_COLS; col++) {
            const nx = col / GRID_COLS;
            const a = normToIsoPixel(nx, 0, W, H), b = normToIsoPixel(nx, 1, W, H);
            g.beginPath(); g.moveTo(a.px, a.py); g.lineTo(b.px, b.py); g.strokePath();
        }
        for (let row = 0; row <= GRID_ROWS; row++) {
            const ny = row / GRID_ROWS;
            const a = normToIsoPixel(0, ny, W, H), b = normToIsoPixel(1, ny, W, H);
            g.beginPath(); g.moveTo(a.px, a.py); g.lineTo(b.px, b.py); g.strokePath();
        }
    }

    drawShadows() {
        if (!this.shadowLayer) return;
        this.shadowLayer.clear();
        this.shadowLayer.fillStyle(0x2e2a22, 0.22);
        for (const o of this.objs) {
            if (o.getData('kind') !== 'plant') continue;
            const w = this.plantPx * 0.55;
            const h = w * ISO_FLATTEN;
            this.shadowLayer.fillEllipse(o.x, o.y, w, h);
        }
    }

    spawnObject(meta: GardenItemMeta, px: number, py: number): GObj {
        const kind = meta.kind || 'plant';
        const rotation = clampRotation(meta.rotation || 0);
        const isPlant = kind === 'plant';
        const oy = isPlant ? 1 : 0.5;
        let obj: GObj;
        if (meta.spriteId && this.textures.exists('tex-' + meta.spriteId)) {
            obj = this.add.image(px, py, 'tex-' + meta.spriteId)
                    .setOrigin(0.5, oy)
                    .setDisplaySize(this.plantPx, this.plantPx);
        } else {
            obj = this.add.text(px, py, meta.emoji || '🌱', { fontSize: Math.round(this.plantPx * 0.8) + 'px' })
                    .setOrigin(0.5, oy);
        }
        obj.setAngle(rotation);
        obj.setData('kind', kind);
        obj.setData('axis', meta.axis || null);
        obj.setData('code', meta.code);
        obj.setData('emoji', meta.emoji);
        obj.setData('name', meta.name);
        obj.setData('spriteId', meta.spriteId);
        obj.setData('rotation', rotation);
        obj.setData('scale', 1);
        obj.setInteractive({ draggable: true, useHandCursor: true });
        this.objs.push(obj);
        this.restack();
        return obj;
    }

    removePlant(obj: GObj) {
        const i = this.objs.indexOf(obj);
        if (i >= 0) this.objs.splice(i, 1);
        obj.destroy();
        this.restack();
    }

    addPlant(meta: GardenItemMeta): boolean {
        if (!this.ready) return false;
        if (this.objs.some(o => o.getData('kind') === 'plant'
                && o.getData('axis') === meta.axis && o.getData('code') === meta.code)) return false;
        const pref = cellOf(0.5, 0.55, GRID_COLS, GRID_ROWS);
        const free = nearestFreeCell(pref.col, pref.row, this.occupiedCells(), GRID_COLS, GRID_ROWS);
        if (!free) { if (this.cfg.onMessage) this.cfg.onMessage('정원이 가득 찼어요 🌿'); return false; }
        const center = cellCenter(free.col, free.row, GRID_COLS, GRID_ROWS);
        const { px, py } = normToIsoPixel(center.x, center.y, this.cfg.worldW, this.cfg.worldH);
        this.spawnObject({ ...meta, kind: 'plant' }, px, py);
        this.emitChange();
        return true;
    }

    addDecoration(meta: GardenItemMeta): boolean {
        if (!this.ready) return false;
        const pref = cellOf(0.5, 0.55, GRID_COLS, GRID_ROWS);
        const free = nearestFreeCell(pref.col, pref.row, this.occupiedCells(), GRID_COLS, GRID_ROWS);
        if (!free) { if (this.cfg.onMessage) this.cfg.onMessage('정원이 가득 찼어요 🌿'); return false; }
        const center = cellCenter(free.col, free.row, GRID_COLS, GRID_ROWS);
        const { px, py } = normToIsoPixel(center.x, center.y, this.cfg.worldW, this.cfg.worldH);
        this.spawnObject({ ...meta, kind: 'decor' }, px, py);
        this.emitChange();
        return true;
    }

    exportPlacements(): { plants: Array<Record<string, unknown>>; decorations: Array<Record<string, unknown>> } {
        const plants: Array<Record<string, unknown>> = [];
        const decorations: Array<Record<string, unknown>> = [];
        this.objs.forEach((o, i) => {
            const { x, y } = isoPixelToNorm(o.x, o.y, this.cfg.worldW, this.cfg.worldH);
            const t = { code: o.getData('code'), x, y, z: i,
                        rotation: o.getData('rotation') || 0, scale: 1 };
            if (o.getData('kind') === 'decor') decorations.push(t);
            else plants.push({ axis: o.getData('axis'), ...t });
        });
        return { plants, decorations };
    }

    emitChange() {
        if (this.cfg.onChange) {
            this.cfg.onChange(
                this.objs.filter(o => o.getData('kind') === 'plant')
                         .map(o => `${o.getData('axis')}/${o.getData('code')}`)
            );
        }
    }

    selectPlant(obj: GObj) { this.selected = obj; this.notifySelection(); }

    deselectPlant() {
        this.selected = null;
        if (this._selBox) this._selBox.clear();
        if (this.cfg.onSelect) this.cfg.onSelect(null);
    }

    notifySelection() {
        this.highlightSelected();
        if (this.cfg.onSelect) this.cfg.onSelect(this.selectionInfo());
    }

    selectionInfo(): SelectionInfo | null {
        if (!this.selected) return null;
        return { rotation: Math.round(this.selected.getData('rotation') || 0) };
    }

    highlightSelected() {
        if (!this._selBox) this._selBox = this.add.graphics();
        this._selBox.clear();
        if (!this.selected) return;
        this._selBox.setDepth(1e6);
        const b = this.selected.getBounds();
        this._selBox.lineStyle(3, 0x6E8A6A, 0.9).strokeRect(b.x - 5, b.y - 5, b.width + 10, b.height + 10);
    }

    rotateSelected(delta: number) {
        if (!this.selected) return;
        const r = clampRotation((this.selected.getData('rotation') || 0) + delta);
        this.selected.setAngle(r); this.selected.setData('rotation', r);
        this.notifySelection(); this.emitChange();
    }

    occupiedCells(exclude: GObj | null = null): Set<string> {
        const set = new Set<string>();
        for (const o of this.objs) {
            if (o === exclude) continue;
            const { x, y } = isoPixelToNorm(o.x, o.y, this.cfg.worldW, this.cfg.worldH);
            const { col, row } = cellOf(x, y, GRID_COLS, GRID_ROWS);
            set.add(`${col},${row}`);
        }
        return set;
    }

    // z-order: y 오름차순 정렬(낮은 y=뒤, 높은 y=앞) — CoC 아이소 자동 깊이. T-055.
    restack() {
        if (this.bg) this.bg.setDepth(0);
        this.objs.slice().sort((a, b) => a.y - b.y).forEach((o, i) => o.setDepth(i + 1));
        if (this._selBox) this._selBox.setDepth(1e6);
        this.drawShadows();
    }

    removeSelected() {
        if (this.selected) {
            const o = this.selected;
            this.deselectPlant();
            this.removePlant(o);
            this.emitChange();
        }
    }
}
