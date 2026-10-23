import { readdirSync, readFileSync, statSync } from 'fs';
import { join, resolve } from 'path';

// 금지 표 — 앱 소스에 있으면 안 되는 것(범위 보충 §6.1). 행마다 금지 문자열을 심은 픽스처로 검사가 실제로 잡는지 본다.
// 뒤 PR이 행을 더한다(PR-F REQ-91 · PR-S1 REQ-98 · PR-S2 REQ-101).

const APP_SRC = resolve(__dirname, '..');

interface Row {
  id: string;
  pattern: RegExp;
  /** app/src 기준 상대 경로 접두 — 이 행이 보지 않는 곳 */
  skip: string[];
  planted: string;
}

const ROWS: Row[] = [
  // 측정 중 밤 화면 폐지 — 밤 토큰은 테마(와 위젯)만 쓴다.
  { id: 'REQ-107', pattern: /\bnight\./, skip: ['theme', 'widgets'], planted: 'backgroundColor: night.desk' },
  // 미니앱 이름 경로를 앱이 부르지 않는다 — 폐기 때 할 일 0(/api/app/* 별칭만).
  { id: 'REQ-109', pattern: /\/api\/(?:miniapp|toss)\//, skip: [], planted: "request('/api/toss/logout')" },
  // 부채는 영원히 뺀다 — 앱은 밀린 시간 필드를 읽지도 그리지도 않고 지우개를 부르지 않는다.
  {
    id: 'REQ-91',
    pattern: /\b(?:remainingSeconds|carriedDebtSeconds|carryover|debtWaiverAvailable)\b|debt-waiver/,
    skip: [],
    planted: 'const left = d.carriedDebtSeconds',
  },
];

const SELF = join('__guards__');

function files(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const p = join(dir, name);
    if (statSync(p).isDirectory()) return files(p);
    return /\.tsx?$/.test(name) ? [p] : [];
  });
}

const ALL = files(APP_SRC).map((p) => ({ rel: p.slice(APP_SRC.length + 1), text: readFileSync(p, 'utf8') }));

function hits(row: Row, all = ALL): string[] {
  return all.filter((f) => !f.rel.startsWith(SELF) && !row.skip.some((s) => f.rel.startsWith(s)))
    .filter((f) => row.pattern.test(f.text))
    .map((f) => f.rel);
}

test('금지 표 · 검사할 소스 파일을 실제로 읽는다(빈 집합이면 늘 초록)', () => {
  expect(ALL.filter((f) => !f.rel.startsWith(SELF)).length).toBeGreaterThanOrEqual(3);
});

// 같은 스캐너에 심은 파일을 넣는다 — 스캐너가 망가져 늘 0을 내면 여기서 깨진다(범위 보충 §6.1).
test.each(ROWS)('$id · 심은 픽스처는 같은 스캐너에서 잡힌다(양성 대조)', (row) => {
  expect(hits(row, [{ rel: join('app', 'planted.tsx'), text: row.planted }])).toHaveLength(1);
});

test('REQ-107 · 화면 코드에 밤 토큰 사용이 없다(위젯·테마 제외 — 심은 픽스처는 잡힌다)', () => {
  expect(hits(ROWS[0])).toEqual([]);
});

test('REQ-109 · 앱 소스에 /api/miniapp/·/api/toss/ 호출이 없다(심은 픽스처는 잡힌다)', () => {
  expect(hits(ROWS[1])).toEqual([]);
});

test('REQ-91 · 앱 소스에 부채 필드 읽기와 지우개 호출이 없다(심은 픽스처는 잡힌다)', () => {
  expect(hits(ROWS[2])).toEqual([]);
  expect(hits(ROWS[2], [{ rel: join('app', 'planted.tsx'), text: "request('/api/miniapp/debt-waiver')" }])).toHaveLength(1);
});
