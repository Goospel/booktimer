import Phaser from 'phaser';
import {
    GRID_COLS,
    ZOOM_MIN, ZOOM_MAX,
    clampZoom, plantWorldSize,
    viewZoomBounds, cameraCenterScroll,
    normToIsoPixel,
    WanderState, wanderStep,
    walkPose, idlePose, WALK_STEP_MS,
    AMBIENT_DECOR,
    isNear, faceEachOther, INTERACT_DIST, INTERACT_COOLDOWN_MS,
} from './pure';

export interface GardenItemMeta {
    kind?: string;
    axis?: string | null;
    code?: string;
    emoji?: string;
    name?: string;
    spriteId?: string;
}

export interface FeedResult {
    foodBalance: number;
    characterCode: string;
    affection: number;
    level: number;
    title: string;
    leveledUp: boolean;
}

/**
 * 보기 전용 마을 무대 설정.
 *
 * <p>배치/편집 엔진 은퇴(PR-2) 후 좌표 저장·팔레트·드래그가 사라졌다 — 배회 캐릭터를 그리고
 * 탭으로 먹이를 주는 보기 전용 씬만 남는다.
 */
export interface GardenSceneConfig {
    characters?: GardenItemMeta[];
    worldW: number;
    worldH: number;
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
    _panning = false;
    _panMoved = false;
    _pinchDist = 0;
    _minZoom = ZOOM_MIN;
    _greetCooldowns: Map<string, number> = new Map(); // pairKey → lastGreetClock(ms)

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
        for (const c of (this.cfg.characters || [])) loadTex(c.spriteId);
        for (const d of AMBIENT_DECOR) loadTex(d.spriteId);
    }

    create() {
        this.plantPx = plantWorldSize(this.cfg.worldW, GRID_COLS);
        this.drawBackground();
        this.drawAmbientDecor();

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

        // 입력 — 팬·핀치·줌
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
            this._panning = false;
        });
        this.input.on('wheel', (pointer: Phaser.Input.Pointer, _objs: unknown, _dx: number, dy: number) => {
            const before = cam.getWorldPoint(pointer.x, pointer.y);
            cam.setZoom(clampZoom(cam.zoom * (dy > 0 ? 0.9 : 1.1), this._minZoom, ZOOM_MAX));
            const after = cam.getWorldPoint(pointer.x, pointer.y);
            cam.scrollX += before.x - after.x;
            cam.scrollY += before.y - after.y;
        });

        // 배회 작가 탭 → 먹이주기
        if (this.cfg.onFeed) {
            this.input.on('gameobjectup', (_p: Phaser.Input.Pointer, obj: GObj) => {
                if (this._panMoved) return; // 드래그(팬) 중 오발 방지
                if (obj.getData('kind') !== 'character') return;
                const code = obj.getData('code') as string;
                if (!code) return;
                this.cfg.onFeed!(code).then(result => {
                    if (result) this.playFeedReaction(obj, result);
                });
            });
        }

        this.ready = true;
    }

    // 매 프레임 — 캐릭터 배회 AI. 비-character 오브젝트는 건드리지 않는다.
    update(time: number, delta: number) {
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
            const flipX = o.getData('flipX') as boolean;
            const pose = next.phase === 'walk'
                ? walkPose('walk', clock, dx, flipX)
                : idlePose(next.idleAction ?? 'stand', clock, flipX);

            // idle 진입 순간(walk→idle)에 read/stretch 이모트 1회 (T-084: 독립 오브젝트)
            if (s.phase === 'walk' && next.phase === 'idle') {
                const action = next.idleAction ?? 'stand';
                if (action === 'read' || action === 'stretch') {
                    const emote = action === 'read' ? '📖' : '🙆';
                    const et = this.add.text(px, py - this.plantPx * 0.5, emote, {
                        fontSize: Math.round(this.plantPx * 0.5) + 'px',
                    });
                    et.setOrigin(0.5, 1);
                    et.setDepth(1e7);
                    this.tweens.add({
                        targets: et,
                        y: et.y - this.plantPx * 0.4,
                        alpha: 0,
                        duration: 1000,
                        ease: 'Cubic.easeOut',
                        onComplete: () => et.destroy(),
                    });
                }
            }

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

        // PR-B: 근접 상호작용 — 캐릭터 쌍 순회 (메인 루프 뒤: idlePose look 토글 덮어쓰기 위해)
        const chars = this.objs.filter(o => o.getData('kind') === 'character');
        for (let i = 0; i < chars.length - 1; i++) {
            for (let j = i + 1; j < chars.length; j++) {
                const a = chars[i], b = chars[j];
                const sa: WanderState = a.getData('wander');
                const sb: WanderState = b.getData('wander');
                if (!sa || !sb) continue;

                if (!isNear(sa.x, sa.y, sb.x, sb.y, INTERACT_DIST)) continue;

                // 마주보기 (idle 상태일 때만 — 걷는 중엔 진행방향 우선)
                if (sa.phase === 'idle' && sb.phase === 'idle') {
                    const { aFlipX, bFlipX } = faceEachOther(
                        sa.x, sb.x,
                        a.getData('flipX') as boolean,
                        b.getData('flipX') as boolean,
                    );
                    a.setData('flipX', aFlipX);
                    b.setData('flipX', bFlipX);
                    if (a instanceof Phaser.GameObjects.Image) a.setFlipX(aFlipX);
                    if (b instanceof Phaser.GameObjects.Image) b.setFlipX(bFlipX);
                }

                // 인사 이모트 (쿨다운 보호)
                const codeA = a.getData('code') as string ?? '';
                const codeB = b.getData('code') as string ?? '';
                const pairKey = [codeA, codeB].sort().join(':');
                const lastGreet = this._greetCooldowns.get(pairKey) ?? -Infinity;
                if (time - lastGreet < INTERACT_COOLDOWN_MS) continue;

                this._greetCooldowns.set(pairKey, time);
                const emote = Math.random() < 0.3 ? '❤️' : '👋';
                for (const ch of [a, b]) {
                    const cx = ch.x, cy = (ch.getData('footY') as number) - this.plantPx * 0.5;
                    const et = this.add.text(cx, cy, emote, {
                        fontSize: Math.round(this.plantPx * 0.5) + 'px',
                    });
                    et.setOrigin(0.5, 1).setDepth(1e7);
                    this.tweens.add({
                        targets: et,
                        y: et.y - this.plantPx * 0.4,
                        alpha: 0,
                        duration: 900,
                        ease: 'Cubic.easeOut',
                        onComplete: () => et.destroy(),
                    });
                }
            }
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
        obj.setInteractive({ useHandCursor: true }); // 탭 → 먹이주기
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
            // 객체(depth≥1) 아래 전용 밴드.
            obj.setDepth(0.1 + 0.3 * (py / H));
            // this.objs에 넣지 않는다(배회와 격리).
        }
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
    restack() {
        if (this.bg) this.bg.setDepth(0);
        const fy = (o: GObj) => (o.getData('footY') as number | undefined) ?? o.y;
        this.objs.slice().sort((a, b) => fy(a) - fy(b)).forEach((o, i) => o.setDepth(i + 1));
    }

    // ★ 반응 애니: 캐릭터와 독립된 오브젝트로 — update()가 캐릭터 y/scale을 매 프레임 덮어써
    //   캐릭터에 직접 tween을 걸면 즉시 무효화된다(T-084). 하트/별/텍스트 전부 objs에 안 넣고 자체 tween.
    playFeedReaction(obj: GObj, result: FeedResult) {
        // 하트 (항상)
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

        // 레벨업 연출 (leveledUp일 때만)
        if (result.leveledUp) {
            // 별 ✨
            const star = this.add.text(obj.x, obj.y - this.plantPx * 1.6, '✨', {
                fontSize: Math.round(this.plantPx * 0.7) + 'px',
            });
            star.setOrigin(0.5, 1);
            star.setDepth(1e7);
            this.tweens.add({
                targets: star,
                y: star.y - this.plantPx * 0.6,
                alpha: 0,
                duration: 1200,
                ease: 'Cubic.easeOut',
                onComplete: () => star.destroy(),
            });

            // 칭호 텍스트
            const label = this.add.text(obj.x, obj.y - this.plantPx * 0.6, result.title + '!', {
                fontSize: Math.round(this.plantPx * 0.38) + 'px',
                backgroundColor: '#ffffffdd',
                color: '#333333',
                padding: { x: 6, y: 3 },
            });
            label.setOrigin(0.5, 1);
            label.setDepth(1e7);
            this.tweens.add({
                targets: label,
                y: label.y - this.plantPx * 0.5,
                alpha: 0,
                duration: 1400,
                ease: 'Cubic.easeOut',
                onComplete: () => label.destroy(),
            });
        }
    }
}
