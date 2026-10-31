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
  /** app/src 기준 상대 경로 접두 — 있으면 이 행은 여기만 본다(심은 픽스처도 첫 접두 안에 둔다) */
  only?: string[];
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
  // RNHostView matchContents 양 축이면 너비를 RN 글에서 받아 줄바꿈 없이 시트 밖으로 잘린다 — false 아니면 { vertical: true }만(T-274).
  // 앵커는 <RNHostView 여는 태그 안([^<]) — Host matchContents(SwiftUI 내용 맞춤)는 다른 방향이라 걸지 않는다.
  {
    id: 'T-274 시트 너비',
    pattern: /<RNHostView\b[^<]*?\bmatchContents\b(?!\s*=\s*\{\s*(?:false|\{\s*vertical\s*:\s*true\s*,?\s*\})\s*\})/,
    skip: [],
    planted: '<RNHostView matchContents>',
    strip: true,
  },
  // 달성 순간 알림 글은 lib(goalMetNotice · goalBody)만 만든다 — 화면이 조건을 스스로 달면 목표 0의 헛 알림이 돌아온다(REQ-178).
  // goalBody도 lib 밖에선 금지 — 화면이 글을 빌려 와 조건을 스스로 달아도 같은 헛 알림이다(리뷰 M11b).
  { id: 'REQ-178', pattern: /오늘 목표를 채웠어요|\bgoalBody\b/, skip: ['lib'], planted: "setNotice({ message: `오늘 목표를 채웠어요 · ${progress.streak}일째` })", strip: true },
  // 목표 줄 단서는 「바꾸기」 하나다 — 「목표 없음」을 고른 사람에게 목표를 권하지 않는다(REQ-177). 미니앱(동결)의 「정하기」는 앱 소스 밖이다.
  { id: 'REQ-177', pattern: /정하기/, skip: [], planted: "const goalCue = dash.todayGoalSeconds > 0 ? '바꾸기' : '정하기';", strip: true },
  // 글은 여백 쪽지로 합쳤다(D9) — 앱은 /api/app/drafts를 부르지 않는다. 옛 기기 칸을 옮기는 draftMigrate만 예외(J5 — 옛 JS가 사라지면 함께 지운다).
  // 짝 테스트(draftMigrate.test)는 기본 deps가 그 경로를 부르는지 단언하려고 경로를 적는다 — 앱 호출이 아니다(재리뷰 C).
  { id: 'REQ-224', pattern: /\/api\/app\/drafts\b/, skip: [join('lib', 'draftMigrate.ts'), join('lib', 'draftMigrate.test.ts')], planted: "request<DraftView>(`/api/app/drafts/${id}`)", strip: true },
  // 여백은 나만 본다(D1) — 「공개 책이에요」 확인 시트는 걷혔다.
  { id: 'REQ-223', pattern: /공개 책이에요/, skip: [], planted: '<Txt v="H2">공개 책이에요</Txt>', strip: true },
  // 「글」은 이제 블로그 글이다(D1 · D2 · D9) — 쪽지 화면 · 시트 · 그 문구를 내는 lib에 「이 글」 · 「내 글」 · 「쓴 글」이 없다(F1 §4.5 — 「쪽지」 · 「생각」).
  {
    id: 'REQ-221 낱말',
    pattern: /이 글|내 글|쓴 글/,
    skip: [],
    only: [join('app', 'margin'), join('components', 'QuestionSheet.tsx'), join('components', 'QuestionBand.tsx'), join('lib', 'questions.ts'), join('lib', 'aiStep.ts'), join('lib', 'draftSync.ts')],
    planted: "const LAST_ROUND = '이 글에서 받을 수 있는 질문을 모두 받았어요';",
    strip: true,
  },
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
    .filter((f) => !row.only || row.only.some((s) => f.rel.startsWith(s)))
    .filter((f) => row.pattern.test(row.strip ? stripComments(f.text) : f.text))
    .map((f) => f.rel);
}

const plantedRel = (row: Row) => (row.only ? join(row.only[0], 'planted.tsx') : join('app', 'planted.tsx'));

test('금지 표 · 검사할 소스 파일을 실제로 읽는다(빈 집합이면 늘 초록)', () => {
  expect(ALL.filter((f) => !f.rel.startsWith(SELF)).length).toBeGreaterThanOrEqual(3);
});

// 같은 스캐너에 심은 파일을 넣는다 — 스캐너가 망가져 늘 0을 내면 여기서 깨진다(범위 보충 §6.1).
test.each(ROWS)('$id · 심은 픽스처는 같은 스캐너에서 잡힌다(양성 대조)', (row) => {
  expect(hits(row, [{ rel: plantedRel(row), text: row.planted }])).toHaveLength(1);
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
  // 갈래마다 양성 대조 — 「구독」 · 「유료」를 패턴에서 빼면 여기서 걸린다
  expect(hits(row!, [{ rel: join('app', 'planted.tsx'), text: '<Txt v="B2">구독하고 질문 더 받기</Txt>' }])).toHaveLength(1);
  expect(hits(row!, [{ rel: join('app', 'planted.tsx'), text: '<Txt v="B2">유료 질문</Txt>' }])).toHaveLength(1);
});

test('T-274 · RNHostView matchContents는 양 축을 쓰지 않는다(글이 들어가면 { vertical: true })', () => {
  const row = ROWS.find((r) => r.id === 'T-274 시트 너비');
  expect(row).toBeDefined();
  expect(hits(row!)).toEqual([]);
  const at = (text: string) => hits(row!, [{ rel: join('app', 'planted.tsx'), text }]);
  // 양 축(맨 이름 · true · 두 축 객체 · 줄바꿈된 JSX)은 잡힌다
  expect(at('<RNHostView matchContents>')).toHaveLength(1);
  expect(at('<RNHostView matchContents={true}>')).toHaveLength(1);
  expect(at('<RNHostView matchContents={{ vertical: true, horizontal: true }}>')).toHaveLength(1);
  expect(at('<RNHostView\n  matchContents\n>')).toHaveLength(1);
  expect(at('<RNHostView style={s} onLayout={() => go()} matchContents>')).toHaveLength(1);
  // Host matchContents(SwiftUI 내용으로 RN 상자를 맞추는 정상 꼴 — @expo/ui MenuView)는 이 행의 대상이 아니다
  expect(at('<Host matchContents style={style}>')).toEqual([]);
  expect(at('<RNHostView matchContents={false}>\n  <Host matchContents>')).toEqual([]);
  // 끔 · 세로만 · 주석은 걸리지 않는다
  expect(at('<RNHostView matchContents={false}>')).toEqual([]);
  expect(at('<RNHostView matchContents={{ vertical: true }}>')).toEqual([]);
  expect(at('// matchContents 양 축 금지')).toEqual([]);
});

test('REQ-178 · 달성 순간 알림 글은 lib만 만든다 — 화면이 스스로 조건을 달지 않는다(심은 픽스처는 잡힌다)', () => {
  const row = ROWS.find((r) => r.id === 'REQ-178');
  expect(row).toBeDefined();
  expect(hits(row!)).toEqual([]);
  // goalBody 갈래 양성 대조 — 화면이 lib 글을 빌려 와 조건을 스스로 다는 꼴(M11b)도 잡힌다
  expect(hits(row!, [{ rel: join('app', 'planted.tsx'), text: 'if (was === false && p.filled) setNotice({ message: goalBody(p.streak) })' }])).toHaveLength(1);
  // lib 안 · 주석은 걸리지 않는다
  expect(hits(row!, [{ rel: join('lib', 'reminders.ts'), text: 'goalBody(progress.streak)' }])).toEqual([]);
  expect(hits(row!, [{ rel: join('app', 'planted.tsx'), text: '// goalBody는 lib만 부른다' }])).toEqual([]);
});

// A1 화면 몫이 draft 화면 · 홈 reading.drafts() · client.ts의 drafts 함수를 지웠다 — 옛 글 화면으로 가는 길도 0.
test('REQ-224 · 앱이 /api/app/drafts를 부르지 않는다 — draftMigrate만 예외(심은 픽스처는 잡힌다)', () => {
  const row = ROWS.find((r) => r.id === 'REQ-224');
  expect(row).toBeDefined();
  expect(hits(row!)).toEqual([]);
  // 옛 글 화면 라우트 파일 · 그리로 가는 주소 0(심은 주소는 같은 식에 잡힌다)
  const toDraft = /['"`]\/draft\//;
  expect(ALL.filter((f) => !f.rel.startsWith(SELF) && (f.rel.startsWith(join('app', 'draft')) || toDraft.test(stripComments(f.text)))).map((f) => f.rel)).toEqual([]);
  expect(toDraft.test('router.push(`/draft/${d.id}`)')).toBe(true);
  const at = (rel: string, text: string) => hits(row!, [{ rel, text }]);
  expect(at(join('app', 'planted.tsx'), "request('/api/app/drafts')")).toHaveLength(1); // 목록 꼴도 잡힌다
  expect(at(join('lib', 'draftMigrate.ts'), row!.planted)).toEqual([]); // 예외 파일
  expect(at(join('lib', 'draftMigrateX.ts'), row!.planted)).toHaveLength(1); // 예외는 그 파일 하나뿐
  expect(at(join('app', 'planted.tsx'), '// 옛 /api/app/drafts는 410이다')).toEqual([]); // 주석의 「왜」는 남겨도 된다
});

test('REQ-223 · 앱 소스에 「공개 책이에요」 확인이 없다 — 여백은 나만 봐요(심은 픽스처는 잡힌다)', () => {
  const row = ROWS.find((r) => r.id === 'REQ-223');
  expect(row).toBeDefined();
  expect(hits(row!)).toEqual([]);
});

test('REQ-221 · 쪽지 화면 문구에 「이 글」 · 「내 글」 0 — 쪽지 화면 · 시트 · questions · aiStep · draftSync만(심은 픽스처는 잡힌다)', () => {
  const row = ROWS.find((r) => r.id === 'REQ-221 낱말');
  expect(row).toBeDefined();
  expect(hits(row!)).toEqual([]);
  // 범위 안 파일이 실제로 읽힌다 — 이름이 바뀌어 판정 범위에서 조용히 빠지지 않게
  for (const p of row!.only!) expect([p, ALL.some((f) => f.rel.startsWith(p))]).toEqual([p, true]);
  const at = (rel: string, text: string) => hits(row!, [{ rel, text }]);
  expect(at(join('components', 'QuestionSheet.tsx'), '<Txt v="H2">{`이 글에서 받은 질문 ${rounds}/${ROUNDS_MAX}`}</Txt>')).toHaveLength(1);
  expect(at(join('lib', 'questions.ts'), "return '내 글에서 받은 꼬리 질문';")).toHaveLength(1);
  expect(at(join('lib', 'aiStep.ts'), "const NET = '인터넷에 연결되지 않았어요 — 쓴 글은 이 기기에 남아 있어요';")).toHaveLength(1); // 「쓴 글」 갈래 양성 대조(A1 리뷰 5)
  expect(at(join('lib', 'consentCopy.ts'), "'지금 쓰는 글과 이 글에서 앞서 받은 질문'")).toEqual([]); // 동의 1판은 범위 밖(E2 전 그대로 — J35)
  expect(at(join('lib', 'questions.ts'), '// 꼬리 질문은 내 글을 읽는다')).toEqual([]); // 주석은 걸리지 않는다
});

// 책 화면 단추는 「여백에 글쓰기」 하나다(D9 · 사용자 확인 질문 1) — 옛 [이 책으로 글쓰기] · 「이 책의 글」 단은 없다.
const bookButtons = (src: string) => {
  const s = stripComments(src);
  return {
    buttons: s.match(/<Button\b/g)?.length ?? 0,
    presses: s.match(/\bonPress=/g)?.length ?? 0, // Pressable · Row로 만든 둘째 동작도 센다(A1 리뷰 17)
    write: /label="여백에 글쓰기"/.test(s),
    old: /이 책으로\s*\S*쓰기|이 책의 글/.test(s),
  };
};
test('REQ-220 · 책 화면 단추 하나 — 「여백에 글쓰기」 · 「이 책의 글」 없음(옛 화면 꼴을 심으면 잡힌다)', () => {
  const src = ALL.find((f) => f.rel === join('app', 'book', '[id].tsx'));
  expect(src).toBeDefined();
  expect(bookButtons(src!.text)).toEqual({ buttons: 1, presses: 3, write: true, old: false });
  expect(bookButtons('<Button kind="ink" label="여백 남기기" />\n<Button kind="outline" label="이 책으로 글쓰기" />')).toMatchObject({ buttons: 2, write: false, old: true });
  // A1 리뷰 17 — 누르는 것은 onPress= 수로 센다(상태 세 칸 map 1 · 단추 1 · 쪽지 줄 1 = 3). Button이 아닌 둘째 동작도 잡는다
  const real = src!.text;
  const at = '<Button kind="ink" label="여백에 글쓰기"';
  expect(real.includes(at)).toBe(true);
  const plant = (jsx: string) => bookButtons(real.replace(at, `${jsx}\n      ${at}`));
  expect(plant('<Pressable onPress={() => go()}><Txt>이 책으로 쓰기</Txt></Pressable>')).toMatchObject({ presses: 4, old: true }); // G220d
  expect(plant('<Row title="이 책으로 쓰기" onPress={() => go()} />')).toMatchObject({ presses: 4, old: true }); // 하우스 Row 변형
  expect(plant('<Button kind="outline" label="쓰기" onPress={() => go()} />')).toMatchObject({ buttons: 2, presses: 4 }); // G220a 꼴
});

test('REQ-177 · 목표 줄 단서는 「바꾸기」 하나다 — 앱 소스에 「정하기」가 없다(심은 픽스처는 잡힌다)', () => {
  const row = ROWS.find((r) => r.id === 'REQ-177');
  expect(row).toBeDefined();
  expect(hits(row!)).toEqual([]);
});

// ── 로그인 경로 가드(PR-P ③) — ROWS 밖: 「금지 문자열」 꼴이 아니라 허용 목록이다 ───────────────
// REQ-174 — 로그인 경로 파일의 console 인자는 문자열 리터럴과 취소 code만. 경로는 join으로 — 윈도에서 rel이 `\`다(SELF와 같은 이유).
// 토큰을 변수로 쥐는 새 파일은 여기에 더한다.
const TOKEN_PATHS = [join('lib', 'auth'), join('api', 'client.ts'), join('lib', 'loginGate.ts'), join('lib', 'deleteVia.ts'), join('lib', 'logout.ts'), join('lib', 'loginResume.ts'),
  join('lib', 'notify.ts'), join('app', '_layout.tsx'), join('app', '(auth)', 'login.tsx'), join('app', 'account-delete.tsx')];
const SAFE_ARG = /^\s*(?:'[^']*'|"[^"]*"|code|r\.type)\s*$/;
function consoleLeaks(all = ALL): string[] {
  const out: string[] = [];
  for (const f of all.filter((x) => TOKEN_PATHS.some((p) => x.rel.startsWith(p))))
    // [^)]는 줄바꿈도 넘는다 — 여러 줄 호출도 본다. 인자 안에 괄호 · 쉼표 든 리터럴이 있으면 허용 꼴이 깨져 걸린다(닫힌 쪽으로 실패)
    for (const m of stripComments(f.text).matchAll(/console\.\w+\(([^)]*)\)/g))
      if (!m[1].split(',').every((a) => SAFE_ARG.test(a))) out.push(`${f.rel}: ${m[0]}`);
  return out;
}

// R-03 — 로그인 SDK 꼴 import 집합이 정확히 넷(허용 목록). 상대 경로는 보지 않는다.
const LOGIN_SDK = /auth|log-?in|sign-?in|oauth|kakao|naver|facebook|firebase|google/i;
const IMPORT = /(?:\bfrom\s+|\bimport\s*\(\s*|\brequire\(\s*|^\s*import\s+)['"]([^'"]+)['"]/gm;
function loginSdkImports(all = ALL): string[] {
  const found = new Set<string>();
  for (const f of all.filter((x) => !x.rel.startsWith(SELF)))
    for (const m of f.text.matchAll(IMPORT)) if (!m[1].startsWith('.') && LOGIN_SDK.test(m[1])) found.add(m[1]);
  return [...found].sort();
}

test('REQ-174 · 로그인 경로 파일의 console 호출은 고정 문자열과 취소 code만 싣는다(토큰 이름 · 객체째 · 여러 줄 · 서버 토큰을 심으면 잡힌다)', () => {
  expect(consoleLeaks()).toEqual([]);
  // 목록의 경로가 모두 있다 — 이름이 바뀌어 판정 범위에서 조용히 빠지지 않게
  for (const p of TOKEN_PATHS) expect([p, ALL.some((f) => f.rel.startsWith(p))]).toEqual([p, true]);
  const at = (text: string) => consoleLeaks([{ rel: join('lib', 'auth', 'planted.ts'), text }]);
  // 양성 대조 다섯 — 토큰 이름 · 맨 변수 · 여러 줄 · Bearer 템플릿 · 중첩 호출
  for (const planted of ["console.log('PROBE', cred.identityToken)", 'console.log(t)', 'console.log(\n  res,\n)', 'console.warn(`Bearer ${saved}`)', 'console.log(JSON.stringify(token))']) {
    expect([planted, at(planted)]).toEqual([planted, [expect.any(String)]]);
  }
  // 음성 대조 넷 — 허용 꼴과 주석
  for (const safe of ["console.log('login closed', 'kakao', code)", "console.log('login closed', 'google', r.type)", "console.warn('kakao unlink failed', code)", '// console.log(t)']) {
    expect([safe, at(safe)]).toEqual([safe, []]);
  }
  // 목록 밖 파일은 판정 범위가 아니다(요구 칸에 적은 범위)
  expect(consoleLeaks([{ rel: join('lib', 'format.ts'), text: 'console.log(t)' }])).toEqual([]);
});

test('R-03 · 로그인 SDK import는 Apple · expo-auth-session · 카카오 둘뿐이다(심은 구글 SDK import는 잡힌다)', () => {
  expect(loginSdkImports()).toEqual(['@react-native-kakao/core', '@react-native-kakao/user', 'expo-apple-authentication', 'expo-auth-session']);
  const planted = [
    { rel: join('lib', 'auth', 'planted.ts'), text: "import { GoogleSignin } from '@react-native-google-signin/google-signin';" },
    { rel: join('lib', 'auth', 'planted2.ts'), text: "import * as Google from 'expo-auth-session/providers/google';" },
    { rel: join('lib', 'auth', 'planted3.ts'), text: "import { x } from './google';" }, // 상대 경로는 보지 않는다
    { rel: join('lib', 'auth', 'planted4.ts'), text: "import OneTap from 'react-native-google-one-tap';" }, // 「google」 갈래만 잡는 이름
  ];
  expect(loginSdkImports(planted)).toEqual(['@react-native-google-signin/google-signin', 'expo-auth-session/providers/google', 'react-native-google-one-tap']);
});

// ── 화면 배선 — lib 판정을 화면이 실제로 쓰는가(A1 리뷰). 렌더러는 jest-expo가 끌어오는 선언 안 된 전이 의존이라 기대지 않고 소스로 본다 ─────────
const srcOf = (rel: string) => {
  const f = ALL.find((x) => x.rel === rel);
  if (!f) throw new Error(`없는 파일: ${rel}`);
  return stripComments(f.text);
};
const count = (s: string, needle: string) => s.split(needle).length - 1;
/** 함수 본문(여는 { 부터 짝 맞는 }까지) — 없으면 ''. */
const bodyOf = (s: string, head: string) => {
  const at = s.indexOf(head);
  if (at < 0) return '';
  const open = s.indexOf('{', s.indexOf(')', at));
  let depth = 0;
  for (let i = open; i < s.length; i++) {
    if (s[i] === '{') depth++;
    else if (s[i] === '}' && --depth === 0) return s.slice(open, i + 1);
  }
  return '';
};
const NOTE = join('app', 'margin', '[bookId].tsx');
const BOOK = join('app', 'book', '[id].tsx');
const LIBRARY = join('app', '(tabs)', 'library.tsx');

/** 기존 쪽지 갈래에서 sync.current 대입이 GET · open · applyText 뒤에 있다(A1 리뷰 1 — 불러오기 전 정리는 일찍 빠진다). */
const syncAfterLoad = (s: string) => {
  const branch = bodyOf(s, 'if (route.noteId !== null)');
  const assign = branch.indexOf('sync.current = s');
  return assign > branch.indexOf('margins.get(id)') && assign > branch.indexOf('applyText(') && assign > 0;
};
test('A1 리뷰 1 · 12 · 쪽지 화면 정리는 leaveAction대로 · 떠날 때 저장을 pendingLeave에 · 기존 쪽지는 불러온 뒤에 sync를 잡는다(옛 꼴을 심으면 잡힌다)', () => {
  const s = srcOf(NOTE);
  const cleanup = bodyOf(s.slice(s.indexOf("AppState.addEventListener('change'")), 'return () =>'); // 떠날 때 정리(AppState effect)
  // 판정(leaveAction) → 효과(runLeave) → pendingLeave가 한 식이다(재리뷰 A — 갈래별 효과는 marginSave.test의 runLeave가 본다)
  const LEAVE = /pendingLeave\.set\(\s*runLeave\(\s*leaveAction\(/;
  expect(LEAVE.test(cleanup)).toBe(true);
  expect(LEAVE.test('const act = leaveAction(p);\n if (act === "flush") void s.flush(id);\n pendingLeave.set(Promise.resolve());')).toBe(false);
  // 3차 3 — runLeave에 넘기는 효과 묶음(이른 noteId 읽기 · no-op del · flush→drop을 심으면 잡힌다)
  const DEPS = '{ dropNewSlot, createFirst, del: margins.delete, drop: s.drop, flush: (n) => s.flush(n), noteId: () => noteId.current }';
  expect(count(cleanup, DEPS)).toBe(1);
  // 3차 5 — 판정에 「만드는 중」을 넘긴다(adopt 창에 떠나도 새 쪽지 갈래). 빼면 잡힌다
  expect(count(cleanup, 'creating: creating.current !== null')).toBe(1);
  expect(count(cleanup.replace('creating: creating.current !== null', 'creating: false'), 'creating: creating.current !== null')).toBe(0);
  for (const [from, to] of [
    ['noteId: () => noteId.current', 'noteId: () => route.noteId'],
    ['del: margins.delete', 'del: async () => undefined'],
    ['flush: (n) => s.flush(n)', 'flush: s.drop'],
    ['drop: s.drop', 'drop: async () => undefined'],
  ]) {
    const mutated = cleanup.replace(from, to);
    expect([from, mutated !== cleanup, count(mutated, DEPS)]).toEqual([from, true, 0]);
  }
  expect(syncAfterLoad(s)).toBe(true);
  // 양성 대조 — 리뷰 때 꼴(GET 전에 대입)은 잡힌다
  const old = 'const load = async () => { sync.current = s;\n if (route.noteId !== null) { const v = await margins.get(id); applyText({ quote: slot.title, text: slot.body }); } }';
  expect(syncAfterLoad(old)).toBe(false);
});

test('A1 리뷰 12 · 책 화면 · 서재는 목록을 읽기 전에 떠날 때 저장을 기다린다(빼면 잡힌다)', () => {
  const waitsBefore = (s: string, list: string) => /pendingLeave\.wait\(\)\.then\(\(\) => margins\.(?:ofBook|bookless)\(/.test(s) && s.includes(list);
  expect(waitsBefore(srcOf(BOOK), 'margins.ofBook(id)')).toBe(true);
  expect(waitsBefore(srcOf(LIBRARY), 'margins.bookless()')).toBe(true);
  expect(waitsBefore('Promise.all([library.materials(id), margins.ofBook(id)])', 'margins.ofBook(id)')).toBe(false);
});

/** createFirst가 adopt를 기다리기 전에 localId를 비우고 noteId를 세운다(A1 리뷰 6 — 그사이 편집이 새 쪽지 칸을 되살려 고아로 남기지 않게). */
const adoptAfterSwitch = (s: string) => {
  const body = bodyOf(s, 'function createFirst(');
  const adopt = body.indexOf('await adoptNewNote(');
  return adopt > 0 && body.indexOf('localId.current = null') > 0 && body.indexOf('localId.current = null') < adopt && body.indexOf('noteId.current = v.id') > 0 && body.indexOf('noteId.current = v.id') < adopt;
};
test('A1 리뷰 6 · createFirst는 adopt 전에 localId를 비우고 noteId를 세운다(옛 순서를 심으면 잡힌다)', () => {
  expect(adoptAfterSwitch(srcOf(NOTE))).toBe(true);
  const old = 'function createFirst() { await newSlotChain.current;\n if (localId.current !== null) await adoptNewNote(AsyncStorage, u, localId.current, v.id);\n localId.current = null;\n noteId.current = v.id; }';
  expect(adoptAfterSwitch(old)).toBe(false);
});

// J5 · J100 — 앱 시작 동기화는 로그인 게이트 안에서 startSlotSync 한 호출이다(호출을 빼거나 게이트를 늘 return으로 만들면 잡힌다 — A1 리뷰 14).
const START = /useEffect\(\(\) => \{\s*if \(!signedIn\) return;\s*void session\.userId\(\)\.then\(\s*\(userId\) => \{\s*if \(userId === null\) return;\s*return startSlotSync\(AsyncStorage, userId\);\s*\},\s*\(\) => undefined,?\s*\);\s*\}, \[signedIn\]\);/;
test('A1 리뷰 14 · _layout이 signedIn 게이트 안에서 startSlotSync를 부른다(빼면 잡힌다)', () => {
  const s = srcOf(join('app', '_layout.tsx'));
  expect(START.test(s)).toBe(true);
  expect(START.test(s.replace('return startSlotSync(AsyncStorage, userId);', ''))).toBe(false);
  expect(START.test(s.replace('if (!signedIn) return;', 'if (!signedIn || true) return;'))).toBe(false);
});

// A1 리뷰 2 · 8 — 쪽지 지우기(확인 창 · 서버 지우기 · 기기 칸 지우기)는 한 곳(NoteRow.confirmRemoveNote)을 책 화면 · 서재가 같이 쓴다.
test('A1 리뷰 2 · 서재 「책 없는 여백」 줄도 지운다 — 확인 창은 한 곳 · 책 화면과 서재가 같이 부른다(복붙 · 빠짐을 심으면 잡힌다)', () => {
  const dialogs = (all: { rel: string; text: string }[]) => all.filter((f) => !f.rel.startsWith(SELF) && stripComments(f.text).includes("'이 여백을 지울까요?'")).map((f) => f.rel);
  expect(dialogs(ALL)).toEqual([join('components', 'NoteRow.tsx')]);
  expect(dialogs([...ALL, { rel: BOOK, text: "Alert.alert('이 여백을 지울까요?', '지우면 되돌릴 수 없어요')" }])).toHaveLength(2);
  const wired = (s: string) => count(s, 'onDelete={() => confirmRemoveNote(');
  expect(wired(srcOf(BOOK))).toBe(1);
  expect(wired(srcOf(LIBRARY))).toBe(1);
  expect(wired('<NoteRow key={m.id} m={m} source onPress={() => router.push(noteHref(null, m.id))} />')).toBe(0);
});

// A1 리뷰 4 — 서재에서 연 쪽지의 「여백을 남겼어요」 표시는 서재가 소비만 한다(알림 화면은 홈 · 책 — F1 §4.4-7). 안 하면 나중에 엉뚱한 화면에 뜬다.
test('A1 리뷰 4 · 서재 포커스가 savedNotice를 소비한다 · 알림은 띄우지 않는다(빼면 잡힌다)', () => {
  const focus = (s: string) => bodyOf(s, 'useFocusEffect(');
  const lib = focus(srcOf(LIBRARY));
  expect(lib).toContain('savedNotice.take()');
  expect(lib).not.toContain('여백을 남겼어요');
  expect(focus('useFocusEffect(useCallback(() => { void load(); }, [load]))')).not.toContain('savedNotice.take()');
});

// REQ-221 — 질문 고르기(pick)는 생각 칸을 건드리지 않는다: 칸 패치는 lib pickPatch(키 focus뿐 — questions.test)만, 본문엔 생각 칸을 바꾸는 호출 0(A1 리뷰 3).
// 화면 edit(…) 호출은 잡고, 칸 패치(sync.current?.edit(id, pickPatch(q)))는 앞의 「.」로 뺀다(재리뷰 D)
const PICK_WRITES = /applyText\(|setThoughtText\(|setQuoteText\(|setInitial\(|(?:textRef|quoteRef)\.current\s*(?:\+=|=(?!=))|(?<![.\w])edit\(|\b(?:body|title)\s*:|[{,]\s*(?:body|title)\s*[,}]|setNativeProps\(/;
test('REQ-221 · 쪽지 화면 pick 본문에 생각 칸을 바꾸는 호출이 없다 · 칸 패치는 pickPatch(질문 글자를 생각에 넣는 꼴을 심으면 잡힌다)', () => {
  const s = srcOf(NOTE);
  const pick = bodyOf(s, 'function pick(');
  expect(pick).toContain('pickPatch(q)');
  expect(PICK_WRITES.test(pick)).toBe(false);
  // 양성 대조(G221d · G221c) — 실제 pick 본문에 심어 같은 검사를 돌린다
  const planted = (line: string) => bodyOf(s.replace('function pick(q: Question) {', `function pick(q: Question) {\n    ${line}`), 'function pick(');
  expect(PICK_WRITES.test(planted('applyText({ quote: quoteRef.current, text: textRef.current + q.text });'))).toBe(true);
  expect(PICK_WRITES.test(planted('void sync.current?.edit(id, { ...pickPatch(q), body: textRef.current + q.text });'))).toBe(true);
  expect(PICK_WRITES.test(planted('textRef.current = textRef.current + q.text;'))).toBe(true);
  // 재리뷰 D — 넓힌 꼴들
  for (const line of [
    'textRef.current += q.text;',
    'quoteRef.current = q.text;',
    'edit({ text: textRef.current + q.text });',
    'void sync.current?.edit(id, { ...pickPatch(q), title: q.text });',
    'setQuoteText(q.text);',
    'textInput.current?.setNativeProps({ text: q.text });',
    'void sync.current?.edit(id, { ...pickPatch(q), body });', // 3차 6 — 단축 속성
    'void sync.current?.edit(id, { title, ...pickPatch(q) });',
  ]) expect([line, PICK_WRITES.test(planted(line))]).toEqual([line, true]);
  expect(planted('x();')).toContain('x();'); // 심기가 실제로 들어갔다
});

// A1 리뷰 13 — runAi는 singleFlight 잠금 안에서 돈다(새 쪽지 첫 POST 중 두 번 눌러도 AI 요청 하나).
test('A1 리뷰 13 · runAi는 한 번에 하나(aiOnce) 안에서 돈다(빼면 잡힌다)', () => {
  const locked = (s: string) => /function runAi\([^)]*\)\s*\{\s*return aiOnce\(async \(\) => \{/.test(s) && /const aiOnce = useRef\(singleFlight\(\)\)\.current;/.test(s);
  expect(locked(srcOf(NOTE))).toBe(true);
  expect(locked('async function runAi(step: Step, f: string | null, consented = false) {\n    if (waiting) return;')).toBe(false);
});

// A1 리뷰 11 — 자동저장 응답 비교는 Java strip 집합(keptBody)으로. JS trim() 비교로 되돌리면 잡힌다.
test('A1 리뷰 11 · 쪽지 화면 put 어댑터는 keptBody로 비교한다(trim 비교를 심으면 잡힌다)', () => {
  const uses = (s: string) => s.includes('body: keptBody(b.body, v.text)') && !/v\.text === b\.body\.trim\(\)/.test(s);
  expect(uses(srcOf(NOTE))).toBe(true);
  expect(uses("return { ...toDoc(v), body: v.text === b.body.trim() ? b.body : v.text };")).toBe(false);
});

// 재리뷰 B — createFirst가 noteId를 세운 뒤 adopt를 기다리는 사이에 save()가 불리면 id 칸이 아직 없어 거짓 「저장하지 못했어요」가 난다.
// 만드는 중이면 그 약속(createFirst는 진행 중이면 같은 약속)을 기다린다.
test('재리뷰 B · save는 만드는 중이면 flush 대신 createFirst를 기다린다(옛 꼴을 심으면 잡힌다)', () => {
  // 3차 1 — 만든 뒤 「만드는 동안 더 쓴 것」을 flush까지 해야 AI가 그 PUT 뒤의 판으로 나간다(v1 REQ-36 「비우기 → 그 revision으로 /ai」).
  // POST 중(id === null) · adopt 창(creating) 둘 다 같은 갈래다. not-dirty면 flush는 PUT 없이 칸의 판(= takeTheirs가 넣은 서버 판)을 돌려준다.
  const waits = (s: string) =>
    /const r =\s*id === null \|\| creating\.current\s*\?\s*await createFirst\(\)\.then\(\(c\) => \(c\.kind === 'saved' && noteId\.current !== null \? s\.flush\(noteId\.current, opts\) : c\)\)\s*:\s*await s\.flush\(id, opts\);/.test(
      bodyOf(s, 'async function save('),
    );
  expect(waits(srcOf(NOTE))).toBe(true);
  expect(waits('async function save(opts) {\n    const r = id === null ? await createFirst() : await s.flush(id, opts);\n  }')).toBe(false); // B 전
  expect(waits('async function save(opts) {\n    const r = id === null || creating.current ? await createFirst() : await s.flush(id, opts);\n  }')).toBe(false); // 67026ad6(만든 뒤 flush 없음)
});

// 재리뷰 G2 — 공용 지우기가 기기 칸을 지우려면 userId를 넘겨야 한다(null이면 칸이 남는다).
test('재리뷰 G2 · confirmRemoveNote는 deleteNote에 userId를 넘긴다(null을 심으면 잡힌다)', () => {
  const passes = (s: string) => count(bodyOf(s, 'export function confirmRemoveNote('), 'deleteNote(AsyncStorage, userId, id)') === 1;
  const s = srcOf(join('components', 'NoteRow.tsx'));
  expect(passes(s)).toBe(true);
  const mutated = s.replace('deleteNote(AsyncStorage, userId, id)', 'deleteNote(AsyncStorage, null, id)');
  expect(mutated).not.toBe(s);
  expect(passes(mutated)).toBe(false);
});

// A1 리뷰 15 — 화면이 lib 판정을 쓰는가(돌연변이 M2b · M3d · M7b · M9b · M10b가 살던 자리). 각 줄에 리뷰 때 돌연변이를 실제 소스에 심어 양성 대조.
const WIRES: { id: string; rel: string; needle: string; n: number; mutant: string }[] = [
  { id: 'M2b 옮긴 문장 칸 = hasQuoteField', rel: NOTE, needle: 'hasQuoteField(v)', n: 1, mutant: 'v.bookId !== null' },
  { id: 'M3d C2 띠 = quoteOnlyBand', rel: NOTE, needle: 'quoteOnlyBand(aiInput) ?', n: 1, mutant: 'true ?' },
  { id: 'M7b 내보내기 책 줄 = bookSourceLine(재료 · 새 쪽지 둘)', rel: NOTE, needle: 'bookSourceLine(m.book)', n: 2, mutant: 'null' },
  { id: 'M9b AI 배지 = noteRow().ai', rel: join('components', 'NoteRow.tsx'), needle: 'r.ai ?', n: 1, mutant: 'true ?' },
  { id: 'M10b 책 없는 여백 = bookless 결과', rel: LIBRARY, needle: '.then(setBookless', n: 1, mutant: '.then(() => setBookless([])' },
];
test.each(WIRES)('A1 리뷰 15 · $id — 화면이 lib 판정을 쓴다(돌연변이를 심으면 잡힌다)', ({ rel, needle, n, mutant }) => {
  const s = srcOf(rel);
  expect(count(s, needle)).toBe(n);
  const mutated = s.replace(needle, mutant); // 첫 자리 하나만 — 둘인 줄은 하나만 바뀌어도 잡혀야 한다
  expect(mutated).not.toBe(s); // 심기가 실제로 들어갔다
  expect(count(mutated, needle)).not.toBe(n);
});
