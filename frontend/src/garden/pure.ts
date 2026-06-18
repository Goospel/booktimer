// 자유 위치(Phase 1) 순수 좌표 코어 — DOM·Phaser 의존 0.

export const ZOOM_MIN = 1, ZOOM_MAX = 2.5;
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
