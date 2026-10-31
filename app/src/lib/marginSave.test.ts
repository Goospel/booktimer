// 여백 쪽지 판정(책생글 「여백 + 블로그」 A1 — 설계 §5.1 · F1 사양 §4.3 · §4.4). 화면은 이 함수들을 그대로 그린다.
import { ApiError, NetworkError } from '../api/client';
import type { MarginSummary } from '../api/types';
import * as marginSave from './marginSave';
import { booklessSection, canSaveMargin, hasQuoteField, javaStrip, keptBody, LEAVE_WAIT_MS, type LeaveAction, leaveAction, noteEditBody, runLeave, noteHead, noteHref, noteMaxLength, noteRoute, noteRow, pendingLeave, savedNotice, showsThought } from './marginSave';

test('REQ-210 · 입력칸 상한 = max(10,000, 불러온 글 길이)', () => {
  expect(noteMaxLength('')).toBe(10_000);
  expect(noteMaxLength('가'.repeat(9_999))).toBe(10_000);
  expect(noteMaxLength('가'.repeat(10_102))).toBe(10_102); // 옮긴 옛 글(제목 100 + 빈 줄 + 본문 10,000)은 그 길이까지
});

test('REQ-96 · 인용만으로 저장할 수 있다', () => {
  expect(canSaveMargin({ quote: '문장', thought: '   ' })).toBe(true);
  expect(canSaveMargin({ quote: '', thought: '생각' })).toBe(true);
  expect(canSaveMargin({ quote: '  ', thought: '\n ' })).toBe(false);
});

test('REQ-96 · 문장부호뿐인 생각은 옮긴 문장만 그린다', () => {
  for (const t of ['', ' ', '.', '…', '?!']) expect(showsThought(t)).toBe(false);
  for (const t of ['ㅎ', '좋다.', '😊']) expect(showsThought(t)).toBe(true);
});

test('남긴 뒤 알림 표시는 한 번만 읽힌다', () => {
  expect(savedNotice.take()).toBe(false);
  savedNotice.mark();
  expect(savedNotice.take()).toBe(true);
  expect(savedNotice.take()).toBe(false);
});

test('REQ-223 · marginSaveStep 확인 단계 없음 — 공개 책 확인 시트는 걷혔다(여백은 나만 봐요)', () => {
  expect(Object.keys(marginSave)).not.toContain('marginSaveStep');
});

test('REQ-226 · 옮긴 문장 칸 판정 — bookId 있음 · quoteSource만 있음 → 칸 있음 · 둘 다 없음 → 칸 없음(서버 Story.edit 조건과 같다)', () => {
  expect(hasQuoteField({ bookId: 3, quoteSource: null })).toBe(true);
  expect(hasQuoteField({ bookId: null, quoteSource: '『데미안』 · 헤르만 헤세' })).toBe(true);
  expect(hasQuoteField({ bookId: null, quoteSource: null })).toBe(false);
});

test('REQ-226 · 책을 지운 쪽지는 옮긴 문장을 보이고 PUT에 그대로 싣는다 — 칸이 없는 쪽지도 불러온 quote를 싣는다(null을 새로 만들지 않는다)', () => {
  // 칸이 있으면 칸 값
  expect(noteEditBody({ field: true, quote: '고친 문장', text: '생각', revision: 2, loadedQuote: '옛 문장' })).toEqual({ quote: '고친 문장', text: '생각', revision: 2 });
  // 칸이 없으면 불러온 값 그대로 — 빼면(undefined) · null로 보내면 서버 Story.edit가 옮긴 문장을 말없이 지운다(F1 §4.4-3)
  const kept = noteEditBody({ field: false, quote: '', text: '생각', revision: 5, loadedQuote: '남은 문장' });
  expect(kept).toEqual({ quote: '남은 문장', text: '생각', revision: 5 });
  expect('quote' in noteEditBody({ field: false, quote: '', text: '생각', revision: 0, loadedQuote: null })).toBe(true);
  // 내보낸 표시는 보냈을 때만 싣는다(C8)
  expect(noteEditBody({ field: true, quote: '', text: '생각', revision: 1, loadedQuote: null, exported: true })).toEqual({ quote: '', text: '생각', revision: 1, exported: true });
});

test('REQ-226 · 쪽지 화면 책 줄 없음 — 책 없는 쪽지 머리 「책 없는 여백」 · 생각 칸 자리글자는 옮긴 문장 칸을 따라간다', () => {
  expect(noteHead({ bookTitle: '데미안', field: true })).toEqual({ title: '데미안', placeholder: '그 문장에 대해 든 생각을 남겨 보세요' });
  expect(noteHead({ bookTitle: null, field: true })).toEqual({ title: '책 없는 여백', placeholder: '그 문장에 대해 든 생각을 남겨 보세요' }); // 책을 지운 쪽지
  expect(noteHead({ bookTitle: null, field: false })).toEqual({ title: '책 없는 여백', placeholder: '떠오른 생각을 마음껏 적어 보세요' });
});

test('REQ-226 · 쪽지 주소 — 책 없는 쪽지는 margin/none · ?note=로 기존 쪽지를 연다', () => {
  expect(noteHref(4)).toBe('/margin/4');
  expect(noteHref(null, 12)).toBe('/margin/none?note=12');
  expect(noteHref(4, 9)).toBe('/margin/4?note=9');
  expect(noteRoute({ bookId: 'none', note: '12' })).toEqual({ bookId: null, noteId: 12 });
  expect(noteRoute({ bookId: '4' })).toEqual({ bookId: 4, noteId: null });
  expect(noteRoute({ bookId: '4', note: 'x' })).toEqual({ bookId: 4, noteId: null }); // 깨진 주소는 새 쪽지로
});

const summary = (p: Partial<MarginSummary> = {}): MarginSummary => ({
  id: 1, bookId: 4, quote: null, quoteSource: null, preview: '', chars: 0, hasAi: false, exported: false,
  createdAt: '2026-10-24T03:00:00Z', updatedAt: '2026-10-24T03:00:00Z', ...p,
});

test('REQ-220 · 질문을 받은 쪽지만 AI 표시 · 문장부호뿐인 생각은 그리지 않는다 · 접근성 라벨 한 줄', () => {
  const asked = noteRow(summary({ quote: '대답보다 질문이 오래 남는 밤이 있다', preview: '익숙한 길에서 벗어나기 퇴근길을 바꿔 본 건', hasAi: true }));
  expect(asked).toMatchObject({ quote: '대답보다 질문이 오래 남는 밤이 있다', thought: '익숙한 길에서 벗어나기 퇴근길을 바꿔 본 건', ai: true, date: '10월 24일' });
  expect(asked.label).toBe('옮긴 문장: 대답보다 질문이 오래 남는 밤이 있다, 내 생각: 익숙한 길에서 벗어나기 퇴근길을 바꿔 본 건, 10월 24일, AI 질문 받음');
  const plain = noteRow(summary({ quote: '옮긴 문장', preview: '…' }));
  expect(plain).toMatchObject({ thought: null, ai: false });
  expect(plain.label).toBe('옮긴 문장: 옮긴 문장, 10월 24일');
  expect(noteRow(summary({ quote: '  ', preview: '생각만' })).quote).toBeNull();
});

test('REQ-226 · 책 없는 여백 0개면 줄 없음 · 있으면 「N개」', () => {
  expect(booklessSection([])).toBeNull();
  expect(booklessSection([summary({ bookId: null }), summary({ id: 2, bookId: null })])).toEqual({ count: '2개' });
});

// 떠날 때 판정(F1 §4.4-7) — 화면 정리는 이 결과대로만 한다.
describe('leaveAction · 쪽지 화면을 떠날 때', () => {
  const at = (p: Partial<Parameters<typeof leaveAction>[0]>) => leaveAction({ loaded: true, empty: false, waiting: false, noteId: 9, creating: false, ...p });

  test('A1 리뷰 1 · 기존 쪽지를 불러오기 전(불러오는 중 · 불러오기 오류)에 떠나면 아무것도 하지 않는다 — 지우지도 보내지도 않는다', () => {
    expect(at({ loaded: false, empty: true })).toBe('none');
    expect(at({ loaded: false, empty: false })).toBe('none');
  });

  test('불러온 기존 쪽지 — 비었으면 지우고 · 글이 있거나 질문을 보내는 중이면 남은 저장을 보낸다', () => {
    expect(at({ empty: true })).toBe('delete');
    expect(at({ empty: true, waiting: true })).toBe('flush');
    expect(at({ empty: false })).toBe('flush');
  });

  test('새 쪽지 — 비었으면 칸만 버리고 · 글이 있으면 만든다', () => {
    expect(at({ noteId: null, empty: true })).toBe('drop-slot');
    expect(at({ noteId: null, empty: false })).toBe('create');
    expect(at({ noteId: null, empty: false, creating: true })).toBe('create');
  });

  test('A1 리뷰 7 · 첫 POST가 나가는 중에 글을 다 지우고 떠나면 만든 뒤 지운다(지운 글로 쪽지가 남지 않게)', () => {
    expect(at({ noteId: null, empty: true, creating: true })).toBe('delete-after-create');
  });
});

describe('A1 리뷰 12 · pendingLeave — 돌아간 목록은 떠날 때 저장을 상한까지만 기다린다(떠나는 화면은 막지 않는다)', () => {
  const tick = () => new Promise<void>((r) => setTimeout(r, 0));

  test('저장이 끝나면 곧바로 풀린다', async () => {
    let resolve!: () => void;
    pendingLeave.set(new Promise<void>((r) => (resolve = r)));
    let done = false;
    void pendingLeave.wait(5_000).then(() => (done = true));
    await tick();
    expect(done).toBe(false);
    resolve();
    await tick();
    expect(done).toBe(true);
  });

  test('저장이 끝나지 않으면 상한에서 풀린다 · 실패한 저장은 던지지 않는다', async () => {
    pendingLeave.set(new Promise<void>(() => undefined));
    const t0 = Date.now();
    await pendingLeave.wait(30);
    expect(Date.now() - t0).toBeGreaterThanOrEqual(25);
    pendingLeave.set(Promise.reject(new Error('offline')));
    await expect(pendingLeave.wait(1_000)).resolves.toBeUndefined();
  });

  test('기본 상한은 약 2초', () => {
    expect(LEAVE_WAIT_MS).toBe(2_000);
  });
});

// A1 리뷰 11 — 서버는 생각을 Java String.strip()으로 걷는다(Character.isWhitespace 집합). JS trim()과 집합이 달라 같은 글인데
// 「서버와 달라요」가 나면 칸이 영구 dirty · 매번 PUT · 알림 반복이다. 서버 값은 검증자가 JDK 25로 잰 strip 결과.
test('A1 리뷰 11 · 서버가 걷은 생각과 비교는 Java strip 집합으로 — 같으면 보낸 글을 그대로 둔다', () => {
  // 고치기 전(JS trim 비교)엔 셋 다 MISMATCH였다
  expect(keptBody('abc\u00A0 ', 'abc\u00A0')).toBe('abc\u00A0 '); // NBSP 뒤 공백 — Java는 NBSP를 남긴다
  expect(keptBody(' \u00A0abc', '\u00A0abc')).toBe(' \u00A0abc');
  expect(keptBody('abc\u001F', 'abc')).toBe('abc\u001F'); // U+001C~1F는 Java만 걷는다
  // 대조군 — 공백 · 개행은 두 쪽이 같이 걷는다(전에도 일치)
  expect(keptBody('abc \n', 'abc')).toBe('abc \n');
  // 정말 다르면 서버 글
  expect(keptBody('abc', 'abd')).toBe('abd');
  // \uc9d1\ud569 \uacbd\uacc4 \u2014 \ud0ed \u00b7 \uc904 \uad6c\ubd84\uc790 \u00b7 \uc804\uac01 \uacf5\ubc31\uc740 \uac77\uace0, \uc904\ubc14\uafc8 \uc5c6\ub294 \uacf5\ubc31(U+00A0 \u00b7 U+2007 \u00b7 U+202F) \u00b7 BOM\uc740 \ub0a8\uae34\ub2e4
  expect(javaStrip('\t\u2028abc\u3000')).toBe('abc');
  expect(javaStrip('abc\u3000\u2007')).toBe('abc\u3000\u2007');
  expect(javaStrip('\u202fabc\ufeff')).toBe('\u202fabc\ufeff');
});

// 재리뷰 A · H — 떠날 때 판정 → 효과(디스패치). 여기선 갈래마다 무엇을 어떤 순서로 부르는지 본다.
// 「돌려준 약속 = 마지막 효과의 끝」은 아래 한 건(flush)과 「3차 2」 갈래 표(create · delete · delete-after-create · drop-slot)가 본다.
describe('runLeave · 떠날 때 할 일을 실제로 부른다', () => {
  function fakes(p: { created?: 'saved' | 'offline'; delFails?: boolean; id?: number | null } = {}) {
    const calls: string[] = [];
    let id: number | null = p.id === undefined ? 9 : p.id;
    const d = {
      dropNewSlot: async () => void calls.push('dropNewSlot'),
      createFirst: async () => {
        calls.push('createFirst');
        if (p.created === 'saved') id = 70;
        return { kind: p.created ?? 'saved' };
      },
      del: async (n: number) => {
        calls.push(`del ${n}`);
        if (p.delFails) throw new Error('offline');
      },
      drop: async (n: number) => void calls.push(`drop ${n}`),
      flush: async (n: number) => void calls.push(`flush ${n}`),
      noteId: () => id,
    };
    return { calls, d };
  }

  test('none · drop-slot · flush', async () => {
    const none = fakes();
    await runLeave('none', none.d);
    expect(none.calls).toEqual([]);
    const drop = fakes({ id: null });
    await runLeave('drop-slot', drop.d);
    expect(drop.calls).toEqual(['dropNewSlot']);
    const flush = fakes();
    await runLeave('flush', flush.d);
    expect(flush.calls).toEqual(['flush 9']);
  });

  test('재리뷰 H · create — 만든 뒤 만드는 동안 더 쓴 것을 곧바로 보낸다(언마운트 뒤 1.5초 타이머에 맡기지 않는다) · 못 만들면 보내지 않는다', async () => {
    const ok = fakes({ id: null, created: 'saved' });
    await runLeave('create', ok.d);
    expect(ok.calls).toEqual(['createFirst', 'flush 70']);
    const off = fakes({ id: null, created: 'offline' });
    await runLeave('create', off.d);
    expect(off.calls).toEqual(['createFirst']);
  });

  test('delete-after-create — 칸을 버리고 · 만든 뒤 새 id를 지우고 칸도 지운다 · 못 만들면 지울 것이 없다', async () => {
    const ok = fakes({ id: null, created: 'saved' });
    await runLeave('delete-after-create', ok.d);
    expect(ok.calls).toEqual(['dropNewSlot', 'createFirst', 'del 70', 'drop 70']);
    const off = fakes({ id: null, created: 'offline' });
    await runLeave('delete-after-create', off.d);
    expect(off.calls).toEqual(['dropNewSlot', 'createFirst']);
  });

  test('delete — 서버가 지운 뒤에만 칸을 지운다 · 실패는 삼킨다(칸은 남는다)', async () => {
    const ok = fakes();
    await runLeave('delete', ok.d);
    expect(ok.calls).toEqual(['del 9', 'drop 9']);
    const bad = fakes({ delFails: true });
    await expect(runLeave('delete', bad.d)).resolves.toBeUndefined();
    expect(bad.calls).toEqual(['del 9']);
  });

  test('flush 갈래 — 돌려준 약속은 flush가 끝난 뒤에 풀린다(pendingLeave가 기다릴 그것)', async () => {
    let release!: () => void;
    const calls: string[] = [];
    const p = runLeave('flush', {
      dropNewSlot: async () => undefined,
      createFirst: async () => ({ kind: 'saved' }),
      del: async () => undefined,
      drop: async () => undefined,
      flush: () => new Promise<void>((r) => (release = () => (calls.push('flushed'), r()))),
      noteId: () => 9,
    });
    let done = false;
    void p.then(() => (done = true));
    await new Promise((r) => setTimeout(r, 0));
    expect(done).toBe(false);
    release();
    await p;
    expect([done, calls]).toEqual([true, ['flushed']]);
  });
});

// 3차 확인 5 — 만드는 중 adopt 창(noteId는 섰고 creating 중)에 떠나도 새 쪽지 갈래다(아직 없는 id 칸을 flush하지 않는다).
test('3차 5 · 만드는 중이면 noteId가 섰어도 새 쪽지 갈래 — 비었으면 만든 뒤 지움 · 아니면 만든 뒤 보냄', () => {
  const at = (p: Partial<Parameters<typeof leaveAction>[0]>) => leaveAction({ loaded: true, empty: false, waiting: false, noteId: 80, creating: true, ...p });
  expect(at({ empty: true })).toBe('delete-after-create');
  expect(at({ empty: false })).toBe('create');
});

// 3차 확인 2 — 「돌려준 약속 = 효과의 끝」을 갈래마다. 게이트를 연 뒤에야 runLeave 약속이 풀려야 한다.
describe('3차 2 · runLeave 약속은 그 갈래의 마지막 효과까지 기다린다', () => {
  const gate = () => {
    let open!: () => void;
    const p = new Promise<void>((r) => (open = r));
    return { p, open };
  };
  const ticks = async () => {
    await new Promise((r) => setTimeout(r, 0));
    await new Promise((r) => setTimeout(r, 0));
  };
  type Dep = 'dropNewSlot' | 'del' | 'drop' | 'flush';
  const cases: { act: LeaveAction; id: number | null; on: Dep; gone?: boolean }[] = [
    { act: 'create', id: null, on: 'flush' },
    { act: 'delete', id: 9, on: 'del' },
    { act: 'delete', id: 9, on: 'drop' },
    { act: 'delete', id: 9, on: 'drop', gone: true }, // 4차 — DELETE 404 갈래의 drop도 기다린다
    { act: 'delete-after-create', id: null, on: 'dropNewSlot' },
    { act: 'delete-after-create', id: null, on: 'del' },
    { act: 'delete-after-create', id: null, on: 'drop' },
    { act: 'drop-slot', id: null, on: 'dropNewSlot' },
    { act: 'flush', id: 9, on: 'flush' },
  ];
  test.each(cases)('$act · $on을 붙잡으면 그동안 안 풀리고 · 열면 풀린다(404: $gone)', async ({ act, id: start, on, gone }) => {
    const g = gate();
    let id = start;
    const dep = (name: Dep) => async () => (name === on ? g.p : undefined);
    const p = runLeave(act, {
      dropNewSlot: dep('dropNewSlot'),
      createFirst: async () => ((id = id ?? 70), { kind: 'saved' }),
      del: gone
        ? async () => {
            throw new ApiError(404, '글을 찾을 수 없습니다');
          }
        : dep('del'),
      drop: dep('drop'),
      flush: dep('flush'),
      noteId: () => id,
    });
    let done = false;
    void p.then(() => (done = true));
    await ticks();
    expect(done).toBe(false);
    g.open();
    await ticks();
    expect(done).toBe(true);
  });
});

// 3차 확인 4 — 떠날 때 지우기도 「404 = 이미 지워짐」(deleteNote · flushOnce와 같은 정의): 칸을 지우고 던지지 않는다.
test('3차 4 · runLeave delete — 404면 칸을 지우고 · 그 밖의 실패는 칸을 남긴다 · 둘 다 던지지 않는다', async () => {
  const run = async (err: unknown) => {
    const calls: string[] = [];
    await expect(
      runLeave('delete', {
        dropNewSlot: async () => undefined,
        createFirst: async () => ({ kind: 'saved' }),
        del: async () => {
          throw err;
        },
        drop: async (n) => void calls.push(`drop ${n}`),
        flush: async () => undefined,
        noteId: () => 9,
      }),
    ).resolves.toBeUndefined();
    return calls;
  };
  expect(await run(new ApiError(404, '글을 찾을 수 없습니다'))).toEqual(['drop 9']);
  expect(await run(new ApiError(500, '요청에 실패했어요 (500)'))).toEqual([]);
  expect(await run(new NetworkError())).toEqual([]);
});
