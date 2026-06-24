// 자유 위치(Phase 1) 순수 좌표 코어 — DOM·Phaser 의존 0.

export const ZOOM_MIN = 0.25, ZOOM_MAX = 2.5;
export const PLANT_CELL_RATIO = 1.0;
export const GRID_COLS = 20, GRID_ROWS = 16;
export const ISO_FLATTEN = 0.5;

export function clampNorm(v: number): number {
    return Math.min(1, Math.max(0, v));
}

export function pixelToNorm(px: number, py: number, worldW: number, worldH: number): { x: number; y: number } {
    return { x: clampNorm(px / worldW), y: clampNorm(py / worldH) };
}

export function normToPixel(x: number, y: number, worldW: number, worldH: number): { px: number; py: number } {
    return { px: x * worldW, py: y * worldH };
}

// 월드 밖 판정 = 거두기. margin(월드 비율)만큼 더 벗어나야 '밖' — 가장자리 오발 방지.
export function isOutsideWorld(px: number, py: number, worldW: number, worldH: number, margin = 0.12): boolean {
    const mx = worldW * margin, my = worldH * margin;
    return px < -mx || px > worldW + mx || py < -my || py > worldH + my;
}

export function plantWorldSize(worldW: number, cols: number, ratio = PLANT_CELL_RATIO): number {
    return (worldW / cols) * ratio;
}

// apparent = plantPx × zoom × (canvasCss / worldW) = targetCss — canvasCss ≤ 0이면 min 폴백(NaN/Infinity 방지).
export function initialZoomFor(targetCss: number, plantPx: number, canvasCss: number, worldW: number, min = ZOOM_MIN, max = ZOOM_MAX): number {
    if (canvasCss <= 0 || plantPx <= 0) return min;
    return clampZoom(targetCss * worldW / (plantPx * canvasCss), min, max);
}

export function clampRotation(deg: number): number {
    const r = deg % 360; return r < 0 ? r + 360 : r;
}

export function clampZoom(z: number, min = ZOOM_MIN, max = ZOOM_MAX): number {
    return Math.min(max, Math.max(min, z));
}

// viewport(vW×vH)에 world(worldW×worldH) 전체가 딱 들어오는 최소 줌 — min(vW/worldW, vH/worldH).
// 무효값(≤0) 시 ZOOM_MIN 폴백.
export function containZoomFor(viewW: number, viewH: number, worldW: number, worldH: number): number {
    if (viewW <= 0 || viewH <= 0 || worldW <= 0 || worldH <= 0) return ZOOM_MIN;
    return Math.min(viewW / worldW, viewH / worldH);
}

// 마을 보기 줌 한계 정책: 하한 = 월드 전체가 뷰에 들어오는 줌(containZoom), 상한 = ZOOM_MAX.
// containZoom이 [ZOOM_MIN, ZOOM_MAX]를 벗어나면 clamp(초소형 월드/0 뷰포트 방어).
export function viewZoomBounds(viewW: number, viewH: number, worldW: number, worldH: number): { min: number; max: number; initial: number } {
    const contain = clampZoom(containZoomFor(viewW, viewH, worldW, worldH), ZOOM_MIN, ZOOM_MAX);
    return { min: contain, max: ZOOM_MAX, initial: contain };
}

// 보이는 영역의 중심을 (worldW/2, worldH/2)에 맞추는 스크롤(월드 좌표). displayDim = viewDim / zoom.
export function cameraCenterScroll(worldW: number, worldH: number, viewW: number, viewH: number, zoom: number): { scrollX: number; scrollY: number } {
    return { scrollX: worldW / 2 - viewW / (2 * zoom), scrollY: worldH / 2 - viewH / (2 * zoom) };
}

export function cellOf(x: number, y: number, cols: number, rows: number): { col: number; row: number } {
    const col = Math.min(cols - 1, Math.max(0, Math.floor(x * cols)));
    const row = Math.min(rows - 1, Math.max(0, Math.floor(y * rows)));
    return { col, row };
}

export function cellCenter(col: number, row: number, cols: number, rows: number): { x: number; y: number } {
    return { x: (col + 0.5) / cols, y: (row + 0.5) / rows };
}

export function snapToCell(x: number, y: number, cols: number, rows: number): { x: number; y: number } {
    const c = cellOf(x, y, cols, rows);
    return cellCenter(c.col, c.row, cols, rows);
}

// 아이소메트릭 투영 — 정규화 격자좌표 (x,y)∈[0,1]² → 정규화 화면좌표 (sx,sy)∈[0,1]².
// ISO_FLATTEN 0.5 = 2:1 클래식 아이소.
export function normToIso(x: number, y: number, f = ISO_FLATTEN): { sx: number; sy: number } {
    return { sx: 0.5 + (x - y) * 0.5, sy: 0.5 + ((x + y) / 2 - 0.5) * f };
}

export function isoToNorm(sx: number, sy: number, f = ISO_FLATTEN): { x: number; y: number } {
    const a = 2 * sx - 1, b = 1 + 2 * (sy - 0.5) / f;
    return { x: clampNorm((a + b) / 2), y: clampNorm((b - a) / 2) };
}

export function normToIsoPixel(x: number, y: number, worldW: number, worldH: number, f = ISO_FLATTEN): { px: number; py: number } {
    const p = normToIso(x, y, f); return { px: p.sx * worldW, py: p.sy * worldH };
}

export function isoPixelToNorm(px: number, py: number, worldW: number, worldH: number, f = ISO_FLATTEN): { x: number; y: number } {
    return isoToNorm(px / worldW, py / worldH, f);
}

// 드롭 결정 — remove(밖 거두기) / revert(점유 막힘) / place(정상 배치). isOutside 우선.
export function resolveDrop(isOutside: boolean, occupiedByOther: boolean): 'remove' | 'revert' | 'place' {
    if (isOutside) return 'remove';
    if (occupiedByOther) return 'revert';
    return 'place';
}

// 가장 가까운 빈 셀 — 선호 (prefCol, prefRow)에서 BFS. 없으면 null.
// 이웃 방향 object 배열 — 이중괄호 표기는 Thymeleaf inline 구문과 충돌하므로 object 형태(T-053 선례, 여기선 불필요하나 유지).
export function nearestFreeCell(
    prefCol: number, prefRow: number, occupiedSet: Set<string>, cols: number, rows: number
): { col: number; row: number } | null {
    const sc = Math.min(cols - 1, Math.max(0, Math.round(prefCol)));
    const sr = Math.min(rows - 1, Math.max(0, Math.round(prefRow)));
    const key = (c: number, r: number) => `${c},${r}`;
    const visited = new Set([key(sc, sr)]);
    const queue: Array<{ col: number; row: number }> = [{ col: sc, row: sr }];
    const dirs = [{dc:-1,dr:0},{dc:1,dr:0},{dc:0,dr:-1},{dc:0,dr:1},{dc:-1,dr:-1},{dc:-1,dr:1},{dc:1,dr:-1},{dc:1,dr:1}];
    while (queue.length > 0) {
        const { col, row } = queue.shift()!;
        if (!occupiedSet.has(key(col, row))) return { col, row };
        for (const {dc, dr} of dirs) {
            const nc = col + dc, nr = row + dr;
            if (nc < 0 || nc >= cols || nr < 0 || nr >= rows) continue;
            const nk = key(nc, nr);
            if (!visited.has(nk)) { visited.add(nk); queue.push({ col: nc, row: nr }); }
        }
    }
    return null;
}

// ── 여백 앰비언트 장식 — 마름모 판정 + 결정적 배치표 ──

// 화면-norm 점이 아이소 마름모(놀이 가능 격자) 안인가 — 여백 장식이 섬을 침범하지 않게.
// 마름모: |sx-0.5|/0.5 + |sy-0.5|/(0.5·f) <= 1 (대각선 가로 1.0, 세로 f).
export function isInsideDiamond(sx: number, sy: number, f = ISO_FLATTEN): boolean {
    return Math.abs(sx - 0.5) / 0.5 + Math.abs(sy - 0.5) / (0.5 * f) <= 1;
}

export type AmbientKind = 'island' | 'rock' | 'tree' | 'lily';

export interface AmbientDecor {
    sx: number;          // 화면-norm x [0,1]
    sy: number;          // 화면-norm y [0,1]
    spriteId: string;    // <symbol id="sprite-{spriteId}">
    kind: AmbientKind;
    sizeFactor: number;  // plantPx 대비 배수
    footAnchored: boolean; // true=발밑 origin(0.5,1, 나무), false=중심(0.5,0.5, 물 위 바위·섬·수련)
}

// 여백(void) 큐레이션 배치 — 결정적(상수). 4개 코너 삼각형 + 상/하 중앙 띠에 분산.
// 불변식(테스트): 모든 항목이 마름모 밖 + [INSET,1-INSET] 안(containZoom서 안 잘림).
export const AMBIENT_INSET = 0.04;
export const AMBIENT_DECOR: ReadonlyArray<AmbientDecor> = [
    // 상단 좌/우 코너 (sy<0.25 영역 + 좌우 코너)
    { sx: 0.12, sy: 0.10, spriteId: 'amb_island', kind: 'island', sizeFactor: 1.8, footAnchored: false },
    { sx: 0.50, sy: 0.07, spriteId: 'amb_rock',   kind: 'rock',   sizeFactor: 0.8, footAnchored: false },
    { sx: 0.86, sy: 0.12, spriteId: 'amb_tree',   kind: 'tree',   sizeFactor: 1.2, footAnchored: true  },
    // 좌/우 코너 (마름모 좌우 꼭짓점 위/아래 바깥)
    { sx: 0.08, sy: 0.22, spriteId: 'amb_rock',   kind: 'rock',   sizeFactor: 0.7, footAnchored: false },
    { sx: 0.93, sy: 0.30, spriteId: 'amb_lily',   kind: 'lily',   sizeFactor: 0.6, footAnchored: false },
    { sx: 0.07, sy: 0.74, spriteId: 'amb_lily',   kind: 'lily',   sizeFactor: 0.6, footAnchored: false },
    { sx: 0.92, sy: 0.72, spriteId: 'amb_island', kind: 'island', sizeFactor: 1.5, footAnchored: false },
    // 하단 좌/우/중앙 (sy>0.75)
    { sx: 0.16, sy: 0.90, spriteId: 'amb_tree',   kind: 'tree',   sizeFactor: 1.3, footAnchored: true  },
    { sx: 0.50, sy: 0.93, spriteId: 'amb_rock',   kind: 'rock',   sizeFactor: 0.9, footAnchored: false },
    { sx: 0.84, sy: 0.90, spriteId: 'amb_island', kind: 'island', sizeFactor: 1.6, footAnchored: false },
];

// ── C2 배회 AI — 순수 상태머신. DOM·Phaser 의존 0. rand 주입으로 결정성 보장. ──

const WANDER_ARRIVAL_EPS = 0.01;   // 도달 판정 정규화 거리
const WANDER_DWELL_MIN = 500;       // 최소 대기 ms
const WANDER_DWELL_RANGE = 1500;    // 추가 랜덤 대기 ms (0~1500)

// PR-A — idle 행동 타입 + 분포 가중치 (rand [0,1) 구간 매핑)
export type IdleAction = 'stand' | 'read' | 'stretch' | 'look';
export const IDLE_STAND_WEIGHT = 0.60;    // [0, 0.60) → stand
export const IDLE_READ_WEIGHT  = 0.75;   // [0.60, 0.75) → read
export const IDLE_STRETCH_WEIGHT = 0.90; // [0.75, 0.90) → stretch
                                          // [0.90, 1.0) → look

export interface WanderState {
    phase: 'idle' | 'walk';
    x: number; y: number;   // 현재 정규화 좌표 [0,1]
    tx: number; ty: number; // 목표 (walk 시 유효)
    timer: number;          // idle 잔여 ms
    idleAction?: IdleAction; // idle 행동 (walk→idle 진입 시 1회 결정, idle 동안 고정)
}

function stepToward(x: number, y: number, tx: number, ty: number, dist: number): { x: number; y: number } {
    const d = Math.hypot(tx - x, ty - y);
    if (d <= 0) return { x, y };
    const step = Math.min(dist, d);
    return { x: x + (tx - x) / d * step, y: y + (ty - y) / d * step };
}

export function wanderStep(s: WanderState, dtMs: number, speedPerMs: number, rand: () => number): WanderState {
    if (s.phase === 'idle') {
        const timer = s.timer - dtMs;
        if (timer > 0) return { ...s, timer };
        const tx = rand(), ty = rand();
        return { ...s, phase: 'walk', tx, ty, timer: 0 };
    }
    const pos = stepToward(s.x, s.y, s.tx, s.ty, dtMs * speedPerMs);
    const dist = Math.hypot(s.tx - pos.x, s.ty - pos.y);
    if (dist < WANDER_ARRIVAL_EPS) {
        const timer = WANDER_DWELL_MIN + rand() * WANDER_DWELL_RANGE;
        return { ...s, phase: 'idle', x: s.tx, y: s.ty, timer, idleAction: pickIdleAction(rand) };
    }
    return { ...s, x: pos.x, y: pos.y };
}

export function pickIdleAction(rand: () => number): IdleAction {
    const r = rand();
    if (r < IDLE_STAND_WEIGHT) return 'stand';
    if (r < IDLE_READ_WEIGHT) return 'read';
    if (r < IDLE_STRETCH_WEIGHT) return 'stretch';
    return 'look';
}

// ── D 폴리시 — 통짜 스프라이트 절차 걷기 pose. DOM·Phaser 의존 0, Date·random 없음(결정성). ──
// 단일 Image에 적용할 시각 변형: 상하 통통(bob)·좌우 흔들(tilt)·납작늘임(squash)·진행방향(flipX).

export interface WalkPose {
    bobY: number;        // 시각 상하 offset(px). 위로만: [-WALK_BOB_PX, 0] (발 안 뚫게)
    tilt: number;        // 좌우 기울기(deg). [-WALK_TILT_DEG, +WALK_TILT_DEG]
    scaleX: number;      // base scale에 곱할 배수(squash&stretch). ≈1
    scaleY: number;
    flipX: boolean;      // 진행 방향(왼쪽이면 true)
}

export const WALK_BOB_PX = 2.2;      // 통통 진폭(px)
export const WALK_TILT_DEG = 4;      // 좌우 흔들 각
export const WALK_SQUASH = 0.06;     // 납작/늘임 폭(±6%)
export const WALK_STEP_MS = 320;     // 한 걸음(=bob 한 사이클) 주기 ms
export const IDLE_BREATH_MS = 2600;  // 숨쉬기 주기 ms
export const IDLE_BREATH = 0.02;     // 숨쉬기 scaleY 폭(±2%)
export const FLIP_DEADZONE = 0.0006; // |dx| 이하면 flip 유지(미세 흔들림 깜빡임 방지)
export const LOOK_TOGGLE_MS = 1200;   // look 동작 flipX 토글 주기 ms
export const STRETCH_PERIOD_MS = 2800; // 기지개 scaleY 주기 ms
export const STRETCH_AMP = 0.12;      // 기지개 최대 scaleY 늘임

export function walkPose(
    phase: 'idle' | 'walk',
    clockMs: number,
    dx: number,
    prevFlipX: boolean,
): WalkPose {
    // flip은 phase 무관 — 데드존 안이면 직전 방향 유지(idle은 dx=0 → 항상 유지).
    const flipX = dx < -FLIP_DEADZONE ? true : dx > FLIP_DEADZONE ? false : prevFlipX;
    if (phase === 'walk') {
        const theta = 2 * Math.PI * clockMs / WALK_STEP_MS;
        const s = Math.abs(Math.sin(theta));          // 착지=0, 정점=1
        const stretch = WALK_SQUASH * s;              // 정점 늘임 / 착지 원형
        return {
            bobY: -WALK_BOB_PX * s,                   // 위로만(발 안 뚫음)
            tilt: WALK_TILT_DEG * Math.sin(theta),    // 좌우 무게이동
            scaleX: 1 - stretch,                      // 부피보존: 세로 늘리면 가로 줄임
            scaleY: 1 + stretch,
            flipX,
        };
    }
    // idle — 잔잔한 숨쉬기(없이 완전정지도 무방하나 "살아있음" 위해 약한 breathing).
    const breath = IDLE_BREATH * Math.sin(2 * Math.PI * clockMs / IDLE_BREATH_MS);
    return { bobY: 0, tilt: 0, scaleX: 1 - breath * 0.5, scaleY: 1 + breath, flipX };
}

// PR-A — idle 행동별 pose. stand=기존 breathing / read=조용한 breathing /
// stretch=느린 세로 늘임 / look=clock 기반 flipX 토글. Date·random 없음.
export function idlePose(action: IdleAction, clockMs: number, prevFlipX: boolean): WalkPose {
    if (action === 'stand') {
        const breath = IDLE_BREATH * Math.sin(2 * Math.PI * clockMs / IDLE_BREATH_MS);
        return { bobY: 0, tilt: 0, scaleX: 1 - breath * 0.5, scaleY: 1 + breath, flipX: prevFlipX };
    }
    if (action === 'read') {
        // 독서: IDLE_BREATH의 30% — 더 정적인 숨쉬기
        const breath = IDLE_BREATH * 0.3 * Math.sin(2 * Math.PI * clockMs / IDLE_BREATH_MS);
        return { bobY: 0, tilt: 0, scaleX: 1 - breath * 0.5, scaleY: 1 + breath, flipX: prevFlipX };
    }
    if (action === 'stretch') {
        // 기지개: [0,1] 범위 t, scaleY = 1+STRETCH_AMP*t (발 끝으로 천천히 늘어남)
        const t = 0.5 - 0.5 * Math.cos(2 * Math.PI * clockMs / STRETCH_PERIOD_MS);
        const stretch = STRETCH_AMP * t;
        return {
            bobY: -WALK_BOB_PX * t * 0.3,
            tilt: 0,
            scaleX: 1 - stretch * 0.5,
            scaleY: 1 + stretch,
            flipX: prevFlipX,
        };
    }
    // look: LOOK_TOGGLE_MS마다 flipX 토글 — prevFlipX 기준으로 좌우 두리번
    const toggleCount = Math.floor(clockMs / LOOK_TOGGLE_MS);
    const breath = IDLE_BREATH * 0.5 * Math.sin(2 * Math.PI * clockMs / IDLE_BREATH_MS);
    return {
        bobY: 0,
        tilt: 0,
        scaleX: 1 - breath * 0.5,
        scaleY: 1 + breath,
        flipX: toggleCount % 2 === 0 ? prevFlipX : !prevFlipX,
    };
}
