import Phaser from 'phaser';
import {
    GRID_COLS, GRID_ROWS, ISO_FLATTEN,
    ZOOM_MIN, ZOOM_MAX,
    clampRotation, clampZoom, plantWorldSize,
    viewZoomBounds, cameraCenterScroll,
    cellOf, cellCenter, snapToCell,
    normToIsoPixel, isoPixelToNorm,
    isOutsideWorld, resolveDrop, nearestFreeCell,
    WanderState, wanderStep,
    walkPose, WALK_STEP_MS,
    AMBIENT_DECOR,
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

export interface FeedResult {
    foodBalance: number;
    characterCode: string;
    affection: number;
}

export interface GardenSceneConfig {
    owned: GardenItemMeta[];
    decorations: GardenItemMeta[];
    placed: GardenItemMeta[];
    characters?: GardenItemMeta[];
    worldW: number;
    worldH: number;
    readonly?: boolean;
    onChange?: (keys: string[]) => void;
    onSelect?: (info: SelectionInfo | null) => void;
    onMessage?: (msg: string) => void;
    onFeed?: (characterCode: string) => Promise<FeedResult | null>;
}

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
    _minZoom = ZOOM_MIN;

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
        for (const c of (this.cfg.characters || [])) loadTex(c.spriteId);
        for (const d of AMBIENT_DECOR) loadTex(d.spriteId);
    }

    create() {
        this.plantPx = plantWorldSize(this.cfg.worldW, GRID_COLS);
        this.drawBackground();
        this.drawAmbientDecor();
        if (!this.cfg.readonly) this.drawGrid();
        this.shadowLayer = this.add.graphics();
        this.shadowLayer.setDepth(0.6);
        for (const p of this.cfg.placed) {
            if (p.axis !== 'BUILDING') continue; // 고아 식물·소품 행 무시 (PR-1 필터)
            const { px, py } = normToIsoPixel(p.x ?? 0, p.y ?? 0, this.cfg.worldW, this.cfg.worldH);
            this.spawnObject(p, px, py);
        }

        for (const c of (this.cfg.characters || [])) {
            this.spawnCharacter(c);
        }

        const cam = this.cameras.main;
        // RESIZE 모드: gameSize를 초기 측정 소스로 통일 — getBoundingClientRect 혼용 제거.
        // 모바일 100dvh 체인은 create() 시점 캔버스 height가 0일 수 있어 fitCamera 가드를 스킵함(T-069).
        const { width: initW, height: initH } = this.scale.gameSize;
        this.fitCamera(initW, initH);
        // dvh 확정은 첫 프레임 이후 — 한 프레임 뒤 재보정으로 모바일 초기 중앙 정렬 보장.
        this.time.delayedCall(0, () => {
            const { width: w, height: h } = this.scale.gameSize;
            this.fitCamera(w, h);
        });

        // RESIZE 이벤트 — 뷰포트가 바뀔 때(화면 회전·창 크기 변경) 줌·중심 재계산.
        this.scale.on('resize', (gameSize: Phaser.Structs.Size) => {
            const { width: w, height: h } = gameSize;
            if (w <= 0) return;
            this.fitCamera(w, h);
        });

        // 공통 입력 — 보기·편집 모두: 팬·핀치·줌
        this.input.addPointer(1);
        this._pinchDist = 0;

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
                    cam.setZoom(clampZoom(cam.zoom * (dist / this._pinchDist), this._minZoom, ZOOM_MAX));
                    const after = cam.getWorldPoint(mx, my);
                    cam.scrollX += before.x - after.x;
                    cam.scrollY += before.y - after.y;
                }
                this._pinchDist = dist;
                return;
            }
            this._pinchDist = 0;
            if (!this._panning || !pointer.isDown) return;
            // getWorldPoint 경유 — 카메라 줌에 맞게 스크롤 델타 보정.
            const before = cam.getWorldPoint(pointer.prevPosition.x, pointer.prevPosition.y);
            const after = cam.getWorldPoint(pointer.x, pointer.y);
            cam.scrollX -= after.x - before.x;
            cam.scrollY -= after.y - before.y;
            this._panMoved = true;
        });
        this.input.on('pointerup', () => {
            this._pinchDist = 0;
            if (this._panning && !this._panMoved && this.selected) this.deselectPlant();
            this._panning = false;
        });
        this.input.on('wheel', (pointer: Phaser.Input.Pointer, _objs: unknown, _dx: number, dy: number) => {
            const before = cam.getWorldPoint(pointer.x, pointer.y);
            cam.setZoom(clampZoom(cam.zoom * (dy > 0 ? 0.9 : 1.1), this._minZoom, ZOOM_MAX));
            const after = cam.getWorldPoint(pointer.x, pointer.y);
            cam.scrollX += before.x - after.x;
            cam.scrollY += before.y - after.y;
        });

        // 보기 전용 입력 — 배회 작가 탭 먹이기
        if (this.cfg.readonly && this.cfg.onFeed) {
            this.input.on('gameobjectup', (_p: Phaser.Input.Pointer, obj: GObj) => {
                if (this._panMoved) return; // 드래그 중 오발 방지
                if (obj.getData('kind') !== 'character') return;
                const code = obj.getData('code') as string;
                if (!code) return;
                this.cfg.onFeed!(code).then(result => {
                    if (result) this.playFeedReaction(obj);
                });
            });
        }

        // 편집 전용 입력 — 드래그·선택
        if (!this.cfg.readonly) {
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
        }

        this.ready = true;
    }

    // 매 프레임 — 캐릭터 배회 AI. 비-character 오브젝트는 건드리지 않는다.
    update(_time: number, delta: number) {
        let moved = false;
        for (const o of this.objs) {
            if (o.getData('kind') !== 'character') continue;
            const s: WanderState = o.getData('wander');
            if (!s) continue;
            const next = wanderStep(s, delta, 0.0004, Math.random);
            o.setData('wander', next);

            const { px, py } = normToIsoPixel(next.x, next.y, this.cfg.worldW, this.cfg.worldH);
            const clock = (o.getData('animClock') as number) + delta;
            o.setData('animClock', clock);

            // 진행 방향 = 이번 스텝의 화면 dx(직전 footX 대비). 멈추면 ≈0 → 데드존서 방향 유지.
            const dx = px - (o.getData('footX') as number);
            const pose = walkPose(next.phase, clock, dx, o.getData('flipX') as boolean);

            o.x = px;
            o.y = py + pose.bobY;          // bob은 시각에만(논리 발밑 y와 분리)
            o.setData('footX', px);
            o.setData('footY', py);         // depth·접지용 논리 y
            o.setAngle(pose.tilt);
            o.setData('flipX', pose.flipX);
            if (o instanceof Phaser.GameObjects.Image) {
                const base = o.getData('baseScale') as { x: number; y: number };
                o.setScale(base.x * pose.scaleX, base.y * pose.scaleY); // base 곱(크기 보존)
                o.setFlipX(pose.flipX);
            }
            moved = true;
        }
        if (moved) this.restack();
    }

    spawnCharacter(meta: GardenItemMeta) {
        const sx = 0.2 + Math.random() * 0.6;
        const sy = 0.2 + Math.random() * 0.6;
        const { px, py } = normToIsoPixel(sx, sy, this.cfg.worldW, this.cfg.worldH);
        let obj: GObj;
        if (meta.spriteId && this.textures.exists('tex-' + meta.spriteId)) {
            obj = this.add.image(px, py, 'tex-' + meta.spriteId)
                    .setOrigin(0.5, 1)
                    .setDisplaySize(this.plantPx, this.plantPx);
            // setDisplaySize가 설정한 scale을 base로 저장 — squash가 매 프레임 base×poseScale로 곱해져 크기 보존.
            obj.setData('baseScale', { x: obj.scaleX, y: obj.scaleY });
        } else {
            obj = this.add.text(px, py, meta.emoji || '🧑', { fontSize: Math.round(this.plantPx * 0.8) + 'px' })
                    .setOrigin(0.5, 1);
        }
        obj.setData('kind', 'character');
        obj.setData('code', meta.code);
        obj.setData('emoji', meta.emoji);
        obj.setData('name', meta.name);
        const initState: WanderState = { phase: 'idle', x: sx, y: sy, tx: sx, ty: sy, timer: Math.random() * 2000 };
        obj.setData('wander', initState);
        // 걷기 애니 시드(두 분기 공통 — Text 폴백도 update에서 안전). animClock 위상 오프셋 = 걸음 군무 방지.
        obj.setData('animClock', Math.random() * WALK_STEP_MS);
        obj.setData('footX', px);
        obj.setData('footY', py);
        obj.setData('flipX', false);
        // 보기 모드에서만 탭 가능(편집 모드는 꾸미기 방해 금지)
        if (this.cfg.readonly) obj.setInteractive({ useHandCursor: true });
        this.objs.push(obj);
        this.restack();
        return obj;
    }

    drawBackground() {
        const W = this.cfg.worldW, H = this.cfg.worldH;
        const css = (n: string) => getComputedStyle(document.documentElement).getPropertyValue(n).trim();
        const hex = (s: string) => { try { return Phaser.Display.Color.HexStringToColor(s).color; } catch (_) { return 0xffffff; } };
        const g = this.add.graphics();
        this.bg = g;
        g.setDepth(0);
        g.fillStyle(hex(css('--garden-water')) || 0x6FA8C7);
        g.fillRect(0, 0, W, H);
        const diamondVerts = [{x:0,y:0},{x:1,y:0},{x:1,y:1},{x:0,y:1}];
        const corners = diamondVerts.map(v => {
            const p = normToIsoPixel(v.x, v.y, W, H); return { x: p.px, y: p.py };
        });
        g.fillStyle(hex(css('--garden-grass')) || 0xCADDB2);
        g.fillPoints(corners, true);
    }

    drawAmbientDecor() {
        const W = this.cfg.worldW, H = this.cfg.worldH;
        for (const d of AMBIENT_DECOR) {
            const px = d.sx * W, py = d.sy * H;
            const size = this.plantPx * d.sizeFactor;
            if (!this.textures.exists('tex-' + d.spriteId)) continue;
            const obj = this.add.image(px, py, 'tex-' + d.spriteId)
                    .setOrigin(0.5, d.footAnchored ? 1 : 0.5)
                    .setDisplaySize(size, size);
            // 객체(depth≥1) 아래 전용 밴드 — grid(0.5)·shadow(0.6)보다도 아래.
            obj.setDepth(0.1 + 0.3 * (py / H));
            // this.objs에 넣지 않는다(격자·저장·배회와 격리).
        }
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
            // 건물은 넓은 아이소 박스라 식물용 작은 발밑 타원(plantPx*0.55)이 바닥보다 좁아
            // "그림자 위에 뜬" 인상을 준다 → 건물은 타일에 flush로 앉히고 캐스트 그림자를 생략한다
            // (CoC식: 건물은 제 footprint로 접지). 식물·소품은 기존 발밑 그림자 유지.
            if (o.getData('axis') === 'BUILDING') continue;
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
        if (!this.cfg.readonly) obj.setInteractive({ draggable: true, useHandCursor: true });
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
        if (!free) { if (this.cfg.onMessage) this.cfg.onMessage('마을이 가득 찼어요 🌿'); return false; }
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
        if (!free) { if (this.cfg.onMessage) this.cfg.onMessage('마을이 가득 찼어요 🌿'); return false; }
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
            // BUILDING 축만 내보냄 — 고아 식물 행이 재저장되지 않게 (PR-1 필터)
            if (o.getData('kind') !== 'decor' && o.getData('axis') === 'BUILDING') {
                plants.push({ axis: o.getData('axis'), ...t });
            }
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

    // 초기 줌 = 월드 전체 보기(containZoom), 중앙 = displayDim 기반 setScroll.
    // centerOn은 zoom 미보정이라 와이드 화면에서 좌상단 쏠림 발생 → setScroll로 대체.
    fitCamera(w: number, h: number) {
        const cam = this.cameras.main;
        if (w > 0 && h > 0) {
            const { min, initial } = viewZoomBounds(w, h, this.cfg.worldW, this.cfg.worldH);
            this._minZoom = min;
            cam.setZoom(initial);
            const s = cameraCenterScroll(this.cfg.worldW, this.cfg.worldH, w, h, cam.zoom);
            // 뷰포트가 월드보다 넓을 때 centering scrollX/Y가 음수 → setBounds(0,0,W,H)가
            // 클램핑해 왼쪽/위 고정 버그 발생(T-069 진짜 원인).
            // offset만큼 bounds를 확장해 음수 스크롤을 허용 — containZoom에서 월드 중앙 고정.
            const offX = Math.max(0, -s.scrollX);
            const offY = Math.max(0, -s.scrollY);
            cam.setBounds(-offX, -offY, this.cfg.worldW + 2 * offX, this.cfg.worldH + 2 * offY);
            cam.setScroll(s.scrollX, s.scrollY);
        }
    }

    // z-order: y 오름차순 정렬(낮은 y=뒤, 높은 y=앞) — CoC 아이소 자동 깊이. T-055.
    // 정렬키는 논리 발밑 footY(캐릭터는 bob으로 o.y≠발밑 → bob이 depth를 흔들지 않게).
    // 식물·건물은 footY 미설정 → o.y 폴백(기존과 동일).
    restack() {
        if (this.bg) this.bg.setDepth(0);
        const fy = (o: GObj) => (o.getData('footY') as number | undefined) ?? o.y;
        this.objs.slice().sort((a, b) => fy(a) - fy(b)).forEach((o, i) => o.setDepth(i + 1));
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

    // ★ 반응 애니: 캐릭터와 독립된 하트 오브젝트로 — update()가 캐릭터 y/scale을 매 프레임 덮어써
    //   캐릭터에 직접 tween을 걸면 즉시 무효화된다. 하트는 objs에 넣지 않고 별도 tween으로 처리.
    playFeedReaction(obj: GObj) {
        const heart = this.add.text(obj.x, obj.y - this.plantPx, '❤️', {
            fontSize: Math.round(this.plantPx * 0.6) + 'px',
        });
        heart.setOrigin(0.5, 1);
        heart.setDepth(1e7);
        this.tweens.add({
            targets: heart,
            y: heart.y - this.plantPx * 0.8,
            alpha: 0,
            duration: 900,
            ease: 'Cubic.easeOut',
            onComplete: () => heart.destroy(),
        });
    }
}
