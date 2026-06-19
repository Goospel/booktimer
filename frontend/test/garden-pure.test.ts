// pure.ts를 직접 ESM import — 정규식 추출(1차)에서 진짜 import로 승격.
// pure.ts 함수 하나 누락/타입 불일치 시 import 실패로 즉시 RED.
import { describe, test, expect } from 'vitest';
import {
    clampNorm, pixelToNorm, normToPixel, isOutsideWorld,
    plantWorldSize, initialZoomFor, clampRotation, clampZoom,
    ZOOM_MIN,
    containZoomFor,
    GRID_COLS, GRID_ROWS, cellOf, cellCenter, snapToCell,
    normToIso, isoToNorm, normToIsoPixel, isoPixelToNorm,
    resolveDrop, nearestFreeCell,
    WanderState, wanderStep,
    WalkPose, walkPose,
    WALK_BOB_PX, WALK_TILT_DEG, WALK_SQUASH, WALK_STEP_MS,
    IDLE_BREATH_MS, IDLE_BREATH, FLIP_DEADZONE,
} from '../src/garden/pure';

const W = 1000, H = 640;
const COLS = GRID_COLS, ROWS = GRID_ROWS; // 20, 16

describe('garden pure.ts', () => {

    describe('clampNorm', () => {
        test('음수 → 0', () => expect(clampNorm(-0.5)).toBeCloseTo(0));
        test('1 초과 → 1', () => expect(clampNorm(1.5)).toBeCloseTo(1));
        test('범위 내 통과', () => expect(clampNorm(0.42)).toBeCloseTo(0.42));
        test('경계 0', () => expect(clampNorm(0)).toBeCloseTo(0));
        test('경계 1', () => expect(clampNorm(1)).toBeCloseTo(1));
    });

    describe('pixelToNorm', () => {
        test('중앙 x', () => expect(pixelToNorm(500, 320, W, H).x).toBeCloseTo(0.5));
        test('중앙 y', () => expect(pixelToNorm(500, 320, W, H).y).toBeCloseTo(0.5));
        test('음수 x 클램프', () => expect(pixelToNorm(-100, 999, W, H).x).toBeCloseTo(0));
        test('초과 y 클램프', () => expect(pixelToNorm(-100, 999, W, H).y).toBeCloseTo(1));
        test('원점 x', () => expect(pixelToNorm(0, 0, W, H).x).toBeCloseTo(0));
        test('원점 y', () => expect(pixelToNorm(0, 0, W, H).y).toBeCloseTo(0));
    });

    describe('normToPixel', () => {
        test('x', () => expect(normToPixel(0.25, 0.75, W, H).px).toBeCloseTo(250));
        test('y', () => expect(normToPixel(0.25, 0.75, W, H).py).toBeCloseTo(480));
        test('왕복 x 보존', () => {
            const p = normToPixel(0.25, 0.75, W, H);
            expect(pixelToNorm(p.px, p.py, W, H).x).toBeCloseTo(0.25);
        });
        test('왕복 y 보존', () => {
            const p = normToPixel(0.25, 0.75, W, H);
            expect(pixelToNorm(p.px, p.py, W, H).y).toBeCloseTo(0.75);
        });
    });

    describe('isOutsideWorld', () => {
        test('중앙 = 안', () => expect(isOutsideWorld(500, 320, W, H)).toBe(false));
        test('경계 모서리 = 안', () => expect(isOutsideWorld(0, 0, W, H)).toBe(false));
        test('반대 경계 = 안', () => expect(isOutsideWorld(W, H, W, H)).toBe(false));
        test('왼쪽 마진 안(−50)', () => expect(isOutsideWorld(-50, 320, W, H)).toBe(false));
        test('왼쪽 마진 너머(−200)', () => expect(isOutsideWorld(-200, 320, W, H)).toBe(true));
        test('오른쪽 마진 너머(1200)', () => expect(isOutsideWorld(1200, 320, W, H)).toBe(true));
        test('위 마진 너머(−100)', () => expect(isOutsideWorld(500, -100, W, H)).toBe(true));
        test('아래 마진 너머(800)', () => expect(isOutsideWorld(500, 800, W, H)).toBe(true));
    });

    describe('plantWorldSize', () => {
        test('ratio 1.0 = 50px', () => expect(plantWorldSize(1000, 20, 1.0)).toBeCloseTo(50));
        test('ratio 0.9 = 45px', () => expect(plantWorldSize(1000, 20, 0.9)).toBeCloseTo(45));
        test('기본 ratio', () => expect(plantWorldSize(1000, 20)).toBeCloseTo(50));
        test('다른 worldW/cols → 50px', () => expect(plantWorldSize(800, 16, 1.0)).toBeCloseTo(50));
    });

    describe('initialZoomFor', () => {
        test('데스크톱(720) → 1.0', () => expect(initialZoomFor(36, 50, 720, 1000)).toBeCloseTo(1.0));
        test('중간폭(360) → 2.0', () => expect(initialZoomFor(36, 50, 360, 1000)).toBeCloseTo(2.0));
        test('아주 좁음(200) → max 2.5 클램프', () => expect(initialZoomFor(36, 50, 200, 1000)).toBeCloseTo(2.5));
        test('canvasCss=0 → ZOOM_MIN 폴백(NaN 없음)', () => expect(initialZoomFor(36, 50, 0, 1000)).toBeCloseTo(ZOOM_MIN));
        test('plantPx=0 → ZOOM_MIN 폴백(Infinity 없음)', () => expect(initialZoomFor(36, 0, 720, 1000)).toBeCloseTo(ZOOM_MIN));
    });

    describe('clampRotation', () => {
        test('0', () => expect(clampRotation(0)).toBeCloseTo(0));
        test('360 → 0', () => expect(clampRotation(360)).toBeCloseTo(0));
        test('음수 wrap', () => expect(clampRotation(-10)).toBeCloseTo(350));
        test('초과 wrap', () => expect(clampRotation(370)).toBeCloseTo(10));
        test('범위 내', () => expect(clampRotation(180)).toBeCloseTo(180));
    });

    describe('clampZoom', () => {
        test('하한 미만(0.1→ZOOM_MIN)', () => expect(clampZoom(0.1)).toBeCloseTo(ZOOM_MIN));
        test('상한 초과(5→2.5)', () => expect(clampZoom(5)).toBeCloseTo(2.5));
        test('범위 내(1.8)', () => expect(clampZoom(1.8)).toBeCloseTo(1.8));
        test('경계 하(ZOOM_MIN)', () => expect(clampZoom(ZOOM_MIN)).toBeCloseTo(ZOOM_MIN));
        test('경계 상(2.5→2.5)', () => expect(clampZoom(2.5)).toBeCloseTo(2.5));
        test('커스텀 min(0.5 min=0.5)', () => expect(clampZoom(0.5, 0.5, 3)).toBeCloseTo(0.5));
        test('커스텀 max(4 max=3)', () => expect(clampZoom(4, 0.5, 3)).toBeCloseTo(3));
    });

    describe('containZoomFor', () => {
        test('portrait 390x844, world 1000x800 → width-limited 0.39', () =>
            expect(containZoomFor(390, 844, 1000, 800)).toBeCloseTo(0.39));
        test('landscape 1024x768, world 1000x800 → height-limited 0.96', () =>
            expect(containZoomFor(1024, 768, 1000, 800)).toBeCloseTo(0.96));
        test('정사각 800x800, world 800x800 → 1.0', () =>
            expect(containZoomFor(800, 800, 800, 800)).toBeCloseTo(1.0));
        test('viewW=0 → ZOOM_MIN 폴백', () =>
            expect(containZoomFor(0, 800, 1000, 800)).toBeCloseTo(ZOOM_MIN));
        test('viewH=0 → ZOOM_MIN 폴백', () =>
            expect(containZoomFor(390, 0, 1000, 800)).toBeCloseTo(ZOOM_MIN));
        test('landscape 1440x900, world 1000x800 → height-limited 1.125', () =>
            expect(containZoomFor(1440, 900, 1000, 800)).toBeCloseTo(1.125));
    });

    describe('GRID 상수', () => {
        test('GRID_COLS = 20', () => expect(GRID_COLS).toBe(20));
        test('GRID_ROWS = 16', () => expect(GRID_ROWS).toBe(16));
    });

    describe('cellOf', () => {
        test('중앙 → (10,8)', () => {
            const c = cellOf(0.5, 0.5, COLS, ROWS);
            expect(c.col).toBe(10); expect(c.row).toBe(8);
        });
        test('원점 → (0,0)', () => {
            const c = cellOf(0, 0, COLS, ROWS);
            expect(c.col).toBe(0); expect(c.row).toBe(0);
        });
        test('경계 1.0 → 클램프 (19,15)', () => {
            const c = cellOf(1, 1, COLS, ROWS);
            expect(c.col).toBe(19); expect(c.row).toBe(15);
        });
        test('음수 → (0,0) 클램프', () => {
            const c = cellOf(-1, -1, COLS, ROWS);
            expect(c.col).toBe(0); expect(c.row).toBe(0);
        });
        test('범위 초과 → max 클램프', () => {
            const c = cellOf(2, 2, COLS, ROWS);
            expect(c.col).toBe(19); expect(c.row).toBe(15);
        });
    });

    describe('cellCenter', () => {
        test('(0,0) x = 0.025', () => expect(cellCenter(0, 0, COLS, ROWS).x).toBeCloseTo(0.025));
        test('(0,0) y = 0.03125', () => expect(cellCenter(0, 0, COLS, ROWS).y).toBeCloseTo(0.03125));
        test('(10,8) x ≈ 0.525', () => expect(cellCenter(10, 8, COLS, ROWS).x).toBeCloseTo(0.525));
        test('(10,8) y ≈ 0.53125', () => expect(cellCenter(10, 8, COLS, ROWS).y).toBeCloseTo(0.53125));
        for (let col = 0; col < 20; col++) {
            for (let row = 0; row < 16; row++) {
                test(`왕복 (${col},${row})`, () => {
                    const { x, y } = cellCenter(col, row, COLS, ROWS);
                    const back = cellOf(x, y, COLS, ROWS);
                    expect(back.col).toBe(col);
                    expect(back.row).toBe(row);
                });
            }
        }
    });

    describe('snapToCell', () => {
        test('멱등 — 두 번 스냅해도 같음', () => {
            const s1 = snapToCell(0.37, 0.62, COLS, ROWS);
            const s2 = snapToCell(s1.x, s1.y, COLS, ROWS);
            expect(s2.x).toBeCloseTo(s1.x); expect(s2.y).toBeCloseTo(s1.y);
        });
        test('원점 → (0,0) 중심으로', () => {
            const s = snapToCell(0, 0, COLS, ROWS);
            const c = cellCenter(0, 0, COLS, ROWS);
            expect(s.x).toBeCloseTo(c.x); expect(s.y).toBeCloseTo(c.y);
        });
        test('1.0 → (19,15) 중심으로', () => {
            const s = snapToCell(1, 1, COLS, ROWS);
            const c = cellCenter(19, 15, COLS, ROWS);
            expect(s.x).toBeCloseTo(c.x); expect(s.y).toBeCloseTo(c.y);
        });
    });

    describe('normToIso / isoToNorm', () => {
        const pairs: [number, number][] = [
            [0.5, 0.5], [0.25, 0.75], [0.8, 0.3], [0.1, 0.9], [0.6, 0.4],
        ];
        for (const [nx, ny] of pairs) {
            test(`왕복 (${nx},${ny})`, () => {
                const iso = normToIso(nx, ny);
                const back = isoToNorm(iso.sx, iso.sy);
                expect(back.x).toBeCloseTo(nx, 6);
                expect(back.y).toBeCloseTo(ny, 6);
            });
        }
        test('중앙(0.5,0.5) → sx=0.5,sy=0.5', () => {
            const { sx, sy } = normToIso(0.5, 0.5);
            expect(sx).toBeCloseTo(0.5); expect(sy).toBeCloseTo(0.5);
        });
        test('(1,0) 오른쪽 꼭짓점 → sx=1,sy=0.5', () => {
            const { sx, sy } = normToIso(1, 0);
            expect(sx).toBeCloseTo(1); expect(sy).toBeCloseTo(0.5);
        });
        test('(0,1) 왼쪽 꼭짓점 → sx=0,sy=0.5', () => {
            const { sx, sy } = normToIso(0, 1);
            expect(sx).toBeCloseTo(0); expect(sy).toBeCloseTo(0.5);
        });
    });

    describe('normToIsoPixel / isoPixelToNorm', () => {
        const cases: [number, number, number, number][] = [
            [0.5, 0.5, W, H],
            [0.3, 0.7, W, H],
            [0.2, 0.4, W, H],
        ];
        for (const [nx, ny, w, h] of cases) {
            test(`픽셀 왕복 (${nx},${ny})`, () => {
                const { px, py } = normToIsoPixel(nx, ny, w, h);
                const back = isoPixelToNorm(px, py, w, h);
                expect(back.x).toBeCloseTo(nx, 5);
                expect(back.y).toBeCloseTo(ny, 5);
            });
        }
        test('중앙(0.5,0.5) → 픽셀 중앙', () => {
            const { px, py } = normToIsoPixel(0.5, 0.5, W, H);
            expect(px).toBeCloseTo(W / 2, 3);
            expect(py).toBeCloseTo(H / 2, 3);
        });
    });

    describe('resolveDrop', () => {
        test('밖 → remove', () => expect(resolveDrop(true, false)).toBe('remove'));
        test('밖+점유 → remove(isOutside 우선)', () => expect(resolveDrop(true, true)).toBe('remove'));
        test('점유 → revert', () => expect(resolveDrop(false, true)).toBe('revert'));
        test('정상 → place', () => expect(resolveDrop(false, false)).toBe('place'));
    });

    describe('nearestFreeCell', () => {
        test('빈 집합 → 선호 셀 그대로', () => {
            const result = nearestFreeCell(5, 5, new Set(), COLS, ROWS);
            expect(result).toEqual({ col: 5, row: 5 });
        });
        test('선호 셀 점유 → 이웃 셀 반환', () => {
            const occ = new Set(['5,5']);
            const result = nearestFreeCell(5, 5, occ, COLS, ROWS);
            expect(result).not.toBeNull();
            expect(result!.col !== 5 || result!.row !== 5).toBe(true);
        });
        test('전체 점유(작은 2x2 그리드) → null', () => {
            const occ = new Set(['0,0', '0,1', '1,0', '1,1']);
            const result = nearestFreeCell(0, 0, occ, 2, 2);
            expect(result).toBeNull();
        });
        test('경계 클램프 — 선호 셀이 범위 밖이어도 동작', () => {
            const result = nearestFreeCell(-5, -5, new Set(), COLS, ROWS);
            expect(result).not.toBeNull();
            expect(result!.col).toBeGreaterThanOrEqual(0);
            expect(result!.row).toBeGreaterThanOrEqual(0);
        });
    });

    // C2 — 배회 AI 순수 상태머신 (계획 §3.3 경계 7종)
    describe('wanderStep', () => {
        const SPEED = 0.001; // 1ms당 0.001 정규화 단위
        const idleExpired: WanderState = { phase: 'idle', x: 0.5, y: 0.5, tx: 0, ty: 0, timer: 0 };
        const constRand = (v: number) => () => v;

        test('① idle timer 만료 → walk 전이 + 목표 설정', () => {
            const s = wanderStep({ ...idleExpired, timer: 0 }, 1, SPEED, constRand(0.3));
            expect(s.phase).toBe('walk');
            expect(s.tx).toBeCloseTo(0.3);
            expect(s.ty).toBeCloseTo(0.3);
        });

        test('① idle timer 미만료 → idle 유지 + timer 감소', () => {
            const s = wanderStep({ ...idleExpired, timer: 500 }, 100, SPEED, constRand(0.3));
            expect(s.phase).toBe('idle');
            expect(s.timer).toBeCloseTo(400);
        });

        test('② walk 미도달 → 목표 방향으로 전진(거리 감소)', () => {
            const start: WanderState = { phase: 'walk', x: 0.1, y: 0.1, tx: 0.9, ty: 0.9, timer: 0 };
            const s = wanderStep(start, 100, SPEED, constRand(0));
            const d0 = Math.hypot(0.9 - 0.1, 0.9 - 0.1);
            const d1 = Math.hypot(0.9 - s.x, 0.9 - s.y);
            expect(d1).toBeLessThan(d0);
            expect(s.phase).toBe('walk');
        });

        test('③ walk 도달(거리<ε) → idle 전이 + dwell timer 설정', () => {
            const start: WanderState = { phase: 'walk', x: 0.5, y: 0.5, tx: 0.5001, ty: 0.5001, timer: 0 };
            const s = wanderStep(start, 100, SPEED, constRand(0.5));
            expect(s.phase).toBe('idle');
            expect(s.timer).toBeGreaterThan(0);
        });

        test('④ overshoot — 큰 dt 한 스텝에 목표 통과 → 목표에 clamp', () => {
            const start: WanderState = { phase: 'walk', x: 0.5, y: 0.5, tx: 0.51, ty: 0.5, timer: 0 };
            const s = wanderStep(start, 1000, 1, constRand(0.5));
            // 목표(0.51)를 넘지 않아야 하고 idle로 전이
            expect(s.x).toBeCloseTo(0.51);
            expect(s.phase).toBe('idle');
        });

        test('⑤ dt=0 → 상태 불변', () => {
            const start: WanderState = { phase: 'walk', x: 0.3, y: 0.4, tx: 0.7, ty: 0.8, timer: 0 };
            const s = wanderStep(start, 0, SPEED, constRand(0));
            expect(s.x).toBeCloseTo(0.3);
            expect(s.y).toBeCloseTo(0.4);
            expect(s.phase).toBe('walk');
        });

        test('⑥ rand 결정성 — 같은 시퀀스 → 같은 목표 좌표', () => {
            const seq = [0.2, 0.7];
            const makeRand = () => { let i = 0; return () => seq[i++] ?? 0.5; };
            const s1 = wanderStep({ ...idleExpired, timer: 0 }, 1, SPEED, makeRand());
            const s2 = wanderStep({ ...idleExpired, timer: 0 }, 1, SPEED, makeRand());
            expect(s1.tx).toBeCloseTo(s2.tx);
            expect(s1.ty).toBeCloseTo(s2.ty);
        });

        test('⑦ 독립성 — 두 WanderState가 서로 간섭 없음(순수함수)', () => {
            const s1: WanderState = { phase: 'walk', x: 0.1, y: 0.1, tx: 0.9, ty: 0.9, timer: 0 };
            const s2: WanderState = { phase: 'idle', x: 0.5, y: 0.5, tx: 0, ty: 0, timer: 1000 };
            const r1a = wanderStep(s1, 100, SPEED, constRand(0));
            wanderStep(s2, 100, SPEED, constRand(0));
            const r1b = wanderStep(s1, 100, SPEED, constRand(0));
            expect(r1b.x).toBeCloseTo(r1a.x);
            expect(r1b.y).toBeCloseTo(r1a.y);
        });
    });

    // D 폴리시 — 걷기 pose 순수함수 (계획 §4.1 경계 7종). 통짜 transform(bob·tilt·squash·flip).
    describe('walkPose', () => {
        const STEP = WALK_STEP_MS;

        // ① idle 정지: 어느 시각이든 bob·tilt 0 (phase 분기 누락 잡음)
        test('① idle → bobY=0, tilt=0 (여러 시각)', () => {
            for (const c of [0, 80, 160, 240, 500, 1234]) {
                const p: WalkPose = walkPose('idle', c, 0, false);
                expect(p.bobY).toBeCloseTo(0);
                expect(p.tilt).toBeCloseTo(0);
            }
        });

        // ② walk bob 범위·부호: 항상 [-A,0], 0 초과 절대 없음(발 안 뚫음)
        test('② walk bobY ∈ [-WALK_BOB_PX, 0] (위로만 — 발 안 뚫음)', () => {
            for (let c = 0; c <= STEP * 3; c += 7) {
                const { bobY } = walkPose('walk', c, 0.01, false);
                expect(bobY).toBeLessThanOrEqual(1e-9);
                expect(bobY).toBeGreaterThanOrEqual(-WALK_BOB_PX - 1e-9);
            }
        });

        // ③ 주기성 + 극값(착지 0·정점 -A 둘 다 한 주기 안에)
        test('③ bob 주기성 — clock과 clock+STEP 동치', () => {
            for (const c of [13, 97, 211]) {
                expect(walkPose('walk', c, 0.01, false).bobY)
                    .toBeCloseTo(walkPose('walk', c + STEP, 0.01, false).bobY, 6);
            }
        });
        test('③ 극값 — 착지(bobY≈0)·정점(bobY≈-A) 모두 존재', () => {
            expect(walkPose('walk', 0, 0.01, false).bobY).toBeCloseTo(0);              // sinθ=0
            expect(walkPose('walk', STEP / 4, 0.01, false).bobY).toBeCloseTo(-WALK_BOB_PX); // |sinθ|=1
        });

        // ④ 방향 flip + 데드존
        test('④ dx<-ε → flipX=true (왼쪽 향함)', () => {
            expect(walkPose('walk', 0, -0.01, false).flipX).toBe(true);
        });
        test('④ dx>+ε → flipX=false (오른쪽 향함)', () => {
            expect(walkPose('walk', 0, 0.01, true).flipX).toBe(false);
        });
        test('④ |dx|≤데드존 → prevFlipX 유지(미세 흔들림 깜빡 방지)', () => {
            expect(walkPose('walk', 0, FLIP_DEADZONE * 0.5, true).flipX).toBe(true);
            expect(walkPose('walk', 0, -FLIP_DEADZONE * 0.5, false).flipX).toBe(false);
        });

        // ⑤ squash 위상 정합 + 부피보존
        test('⑤ 정점 squash — scaleY>1 && scaleX<1 (공중서 늘임)', () => {
            const p = walkPose('walk', STEP / 4, 0.01, false);
            expect(p.scaleY).toBeGreaterThan(1);
            expect(p.scaleX).toBeLessThan(1);
        });
        test('⑤ 착지 squash ≈ 1 (원형)', () => {
            const p = walkPose('walk', 0, 0.01, false);
            expect(p.scaleX).toBeCloseTo(1);
            expect(p.scaleY).toBeCloseTo(1);
        });
        test('⑤ 부피보존 — scaleX·scaleY ≈ 1', () => {
            for (const c of [0, STEP / 8, STEP / 4, STEP / 3]) {
                const p = walkPose('walk', c, 0.01, false);
                expect(p.scaleX * p.scaleY).toBeCloseTo(1, 2);
            }
        });

        // ⑥ 위상 연속성 — 작은 Δclock에 bob·tilt 점프 없음(끊김/번쩍임 잡음)
        test('⑥ 연속성 — 작은 Δclock → 작은 ΔbobY·Δtilt', () => {
            for (const c of [0, 50, 130, 290]) {
                const a = walkPose('walk', c, 0.01, false);
                const b = walkPose('walk', c + 1, 0.01, false);
                expect(Math.abs(b.bobY - a.bobY)).toBeLessThan(0.2);
                expect(Math.abs(b.tilt - a.tilt)).toBeLessThan(0.2);
            }
        });

        // ⑦ idle facing 유지 + breathing 경계
        test('⑦ idle facing 유지 — 멈춰도 보던 방향', () => {
            expect(walkPose('idle', 123, 0, true).flipX).toBe(true);
            expect(walkPose('idle', 123, 0, false).flipX).toBe(false);
        });
        test('⑦ idle breathing — scaleY ∈ 1±IDLE_BREATH', () => {
            for (let c = 0; c <= IDLE_BREATH_MS; c += 137) {
                const p = walkPose('idle', c, 0, false);
                expect(p.scaleY).toBeGreaterThanOrEqual(1 - IDLE_BREATH - 1e-9);
                expect(p.scaleY).toBeLessThanOrEqual(1 + IDLE_BREATH + 1e-9);
            }
        });
    });
});
