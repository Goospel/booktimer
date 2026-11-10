import { measureShortcut, repeatGate, shortcutHolders, TOGGLE_GAP_MS } from './measureShortcut';

// 타이머 오프라인 T2 — 기기 쪽 시작 · 정지는 몇 ms라 busy가 두 번째 탭을 막지 못한다(빠른 두 번 누르기가 측정을 바로 끝내지 않게)
test('REQ-372 · 시작 · 정지 뒤 700ms 안의 두 번째 누름은 버린다', () => {
  expect(TOGGLE_GAP_MS).toBe(700);
  const gate = repeatGate(TOGGLE_GAP_MS);
  expect(gate(10_000)).toBe(true); // 시작
  expect(gate(10_699)).toBe(false); // 같은 손가락의 두 번째 탭 — 정지가 아니다
  expect(gate(11_398)).toBe(false); // 버린 누름도 간격을 다시 잰다(누르고 있는 반복)
  expect(gate(12_098)).toBe(true); // 마지막 누름에서 700ms
});

// Option+P = 그 화면 측정 버튼의 처리기(REQ-105).

test('REQ-105 · 버튼이 없는 화면에서는 단축키가 꺼져 있다', () => {
  const button = () => undefined;
  expect(measureShortcut({ button: null, busy: false, covered: false }).on).toBe(false);
  expect(measureShortcut({ button, busy: false, covered: true }).on).toBe(false);
  // 양성 대조 — 버튼이 있고 아무것도 덮지 않으면 켜진다
  expect(measureShortcut({ button, busy: false, covered: false }).on).toBe(true);
});

test('REQ-105 · 단축키는 그 화면 버튼의 처리기를 부르고 요청 중이면 무시된다', () => {
  const button = () => undefined;
  expect(measureShortcut({ button, busy: false, covered: false }).target).toBe(button);
  const busy = measureShortcut({ button, busy: true, covered: false });
  expect(busy.on).toBe(true);
  expect(busy.target).toBeNull();
});

test('REQ-105 · 두 탭 화면이 포커스를 주고받는 순서와 무관하게 마지막 보유자가 놓을 때만 단축키를 끈다', () => {
  for (const order of [[0, 1], [1, 0]]) {
    const calls: boolean[] = [];
    const hold = shortcutHolders((on) => calls.push(on));
    const releases = [hold(), hold()];
    expect(calls).toEqual([true]);
    releases[order[0]]();
    expect(calls).toEqual([true]);
    releases[order[0]](); // 두 번 놓아도 한 번
    expect(calls).toEqual([true]);
    releases[order[1]]();
    expect(calls).toEqual([true, false]);
  }
});

test('REQ-105 · 키를 누르고 있어 이어 오는 이벤트는 처음 한 번만 받는다', () => {
  const fresh = repeatGate();
  const got: boolean[] = [];
  let t = 0;
  for (; t <= 3000; t += 33) got.push(fresh(t));
  expect(got[0]).toBe(true);
  expect(got.slice(1).every((ok) => !ok)).toBe(true);
  expect(fresh(t - 33 + 1200)).toBe(true);
  // 첫 반복 전 지연(수백 ms)보다 짧은 간격이면 「시작 → 곧바로 종료」가 되살아난다 — 간격을 1초 근처에 묶는다
  const slow = repeatGate();
  slow(0);
  expect(slow(999)).toBe(false);
});
