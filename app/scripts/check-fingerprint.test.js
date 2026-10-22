// 런타임 규칙(앞당김 보충 §6.5 · REQ-69) — decide는 순수 함수다. 지문 계산 모듈은 main에서만 싣는다.
const { decide } = require('./check-fingerprint');

const LOCK = { version: '0.1.0', iosHash: 'c9ede8adff870b7c6846f208957acd75c453ae86' };
const OTHER = 'aaaaaaaaaaaabbbbbbbbbbbbccccccccccccdddd';

test('REQ-69 · 지문과 version이 잠금과 같으면 통과한다', () => {
  const r = decide({ base: LOCK, lock: LOCK, current: LOCK });
  expect(r.ok).toBe(true);
  expect(r.lines).toContain('fingerprint ok c9ede8adff87');
});

test('REQ-69 · 지문이 다르면 실패하고 메시지에 「version을 올리고」가 있다', () => {
  const r = decide({ base: LOCK, lock: LOCK, current: { ...LOCK, iosHash: OTHER } });
  expect(r.ok).toBe(false);
  expect(r.lines.join('\n')).toContain('version을 올리고');
  expect(r.lines.join('\n')).toContain(OTHER); // 새 hash를 알려 준다
  expect(r.lines.join('\n')).not.toContain('fingerprint ok');
});

test('REQ-69 · version만 달라도 실패한다(잠금을 같은 PR에서 고친다)', () => {
  const r = decide({ base: LOCK, lock: LOCK, current: { ...LOCK, version: '0.1.1' } });
  expect(r.ok).toBe(false);
});

test('REQ-69 · 잠금 hash가 main과 다른데 version이 main과 같으면 실패한다(version까지 올리면 통과 — 양성 대조)', () => {
  const copied = { version: '0.1.0', iosHash: OTHER }; // CI 메시지의 새 hash만 옮겨 적은 잠금
  expect(decide({ base: LOCK, lock: copied, current: copied }).ok).toBe(false);
  const bumped = { version: '1.0.0', iosHash: OTHER };
  expect(decide({ base: LOCK, lock: bumped, current: bumped }).ok).toBe(true);
});

test('REQ-69 · main에 잠금 파일이 없으면(PR-E) main 대조를 건너뛴다', () => {
  const r = decide({ base: null, lock: LOCK, current: LOCK });
  expect(r.ok).toBe(true);
  expect(r.lines).toContain('main 잠금 없음 — main 대조 건너뜀');
});
