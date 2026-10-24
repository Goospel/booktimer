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
  /** 주석을 걷은 소스에서 찾는다(낱말 금지 — 주석의 「왜」는 남겨도 된다) */
  strip?: boolean;
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
  // 공개 범위 · 공유는 웹이 맡는다 — 앱은 그 문을 부르지 않는다(v1 REQ-40). 웹 /books 주소는 걸리지 않는다.
  { id: 'REQ-40', pattern: /\/(?:visibility|share)\b/, skip: [], planted: "request(`/api/stories/${id}/share`)" },
  // 키보드 위 막대는 KeyboardAvoidingView 안 보통 View다 — InputAccessoryView는 다시 마운트한 입력칸에 안 붙었다(U-05a 실측).
  // 낱말이 아니라 JSX 사용을 본다 — 「왜 안 쓰는가」를 적은 주석은 남겨도 된다(PR-H 사양 §8-27).
  { id: 'REQ-136', pattern: /<InputAccessoryView\b/, skip: [], planted: '<InputAccessoryView nativeID="bar">' },
  // 공부엔 목표 · 회당 시간 · 회독이 없다(사용자 결정) — 앱은 그 문을 부르지도 그 필드를 읽지도 않는다.
  {
    id: 'REQ-98',
    pattern: /\/(?:session-goal|read-count)\b|\b(?:sessionGoalSeconds|readCount)\b/,
    skip: [],
    planted: 'request(`/api/study/books/${id}/session-goal`)',
  },
  // v1에 결제는 없다(v1 REQ-40 둘째 이름 · R-10) — 화면 · 문자열에 결제 · 구독 · 프리미엄 문구가 없다. 주석의 「구독」(이벤트 구독)은 걸리지 않는다.
  { id: 'REQ-40 결제', pattern: /결제|구독|프리미엄|유료/, skip: [], planted: '<Txt v="B2">프리미엄으로 질문 더 받기</Txt>', strip: true },
];

// 블록 주석 · 줄 주석을 걷는다. URL의 //(앞이 :)는 주석이 아니다.
const stripComments = (s: string) => s.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:])\/\/.*$/gm, '$1');

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
    .filter((f) => row.pattern.test(row.strip ? stripComments(f.text) : f.text))
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

test('REQ-136 · 앱 소스에 InputAccessoryView가 없다(심은 픽스처는 잡힌다)', () => {
  const row = ROWS.find((r) => r.id === 'REQ-136');
  expect(row).toBeDefined();
  expect(hits(row!)).toEqual([]); // 여백 쓰기의 「InputAccessoryView가 아니라」 주석은 JSX 사용이 아니라 걸리지 않는다
  expect(hits(row!, [{ rel: join('app', 'planted.tsx'), text: 'const why = "InputAccessoryView가 아니라 보통 View"' }])).toEqual([]);
});

test('REQ-40 · 앱 소스에 /visibility·/share 호출이 없다(심은 픽스처는 잡힌다)', () => {
  expect(hits(ROWS[3])).toEqual([]);
  expect(hits(ROWS[3], [{ rel: join('app', 'planted.tsx'), text: "request(`/api/books/${id}/visibility`, { body })" }])).toHaveLength(1);
  // 웹 공개 설정 화면(/books)을 여는 주소는 공개 문 호출이 아니다
  expect(hits(ROWS[3], [{ rel: join('app', 'planted.tsx'), text: "Linking.openURL('https://booktimer.app/books')" }])).toEqual([]);
});

test('REQ-98 · 앱 소스에 회당 시간·회독 호출이 없다(심은 픽스처는 잡힌다)', () => {
  const row = ROWS.find((r) => r.id === 'REQ-98');
  expect(row).toBeDefined();
  expect(hits(row!)).toEqual([]);
  expect(hits(row!, [{ rel: join('app', 'planted.tsx'), text: 'request(`/api/study/books/${id}/session-goal`)' }])).toHaveLength(1);
  expect(hits(row!, [{ rel: join('app', 'planted.tsx'), text: 'const n = b.readCount' }])).toHaveLength(1);
});

test('REQ-40 · 앱 소스에 결제·구독·프리미엄 문구가 없다', () => {
  const row = ROWS.find((r) => r.id === 'REQ-40 결제');
  expect(row).toBeDefined();
  expect(hits(row!)).toEqual([]);
  expect(hits(row!, [{ rel: join('app', 'planted.tsx'), text: row!.planted }])).toHaveLength(1);
  // 주석 대조 — 주석의 「구독」(이벤트 구독)은 걸리지 않고, URL의 //는 주석이 아니라 걷지 않는다
  expect(hits(row!, [{ rel: join('app', 'planted.tsx'), text: '// 화면을 떠날 때도 구독을 지운다' }])).toEqual([]);
  expect(hits(row!, [{ rel: join('app', 'planted.tsx'), text: "Linking.openURL('https://booktimer.app/terms') // 결제 없음" }])).toEqual([]);
  expect(hits(row!, [{ rel: join('app', 'planted.tsx'), text: "Linking.openURL('https://booktimer.app/결제') // 주석" }])).toHaveLength(1);
});
