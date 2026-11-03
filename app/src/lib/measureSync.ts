// 측정 가게 · 대시보드 읽기 · 보내기 차례(타이머 오프라인 설계 2026-11-02 §6.5). 마지막 대시보드와 기기 원장을 사용자별 칸 하나에 두어
// 오프라인으로 켜도 홈이 그려지고(REQ-362), 앱 측정은 기기에서 재고 끝나면 마감 문으로 올린다(T2 — REQ-357 · 359).
// 화면 · 위젯 · 알림은 이 가게 하나를 읽는다.
import { ApiError, NetworkError, reading, study, UnauthorizedError } from '../api/client';
import type { KV } from './draftSync';
import { todayDashboard } from './goalProgress';
import { capActive, type DoneMeasure, drop, EMPTY_LEDGER, forgetSettled, type Ledger, markFailed, pending, settle } from './measureLedger';
import type { Base } from './visibleDashboard';

/** failedSeq = 이 실행에서 실패한 대시보드 요청의 가장 큰 순번(메모리만 — REQ-378) */
export interface MeasureState {
  userId: number;
  base: Base | null;
  ledger: Ledger;
  failedSeq: number;
}
/** 닫혔거나 다른 사용자로 열린 가게에 쓰려 했다(REQ-369) */
export class MeasureStoreClosed extends Error {}

/** 기준 값보다 늦게 보낸 읽기가 실패하지 않았다(REQ-378). 디스크 값(순번 0)도 실패 전까진 믿는다 */
export const confirmedOf = (s: MeasureState) => s.failedSeq <= (s.base?.seq ?? 0);

/** 대기 칸이 있고 앱이 앞에 있으면 루트가 이 간격으로 보낸다(조절 상수 — 역에 서는 30초 남짓 안에 한 번은 걸리게) */
export const RETRY_MS = 20_000;
const DAY_MS = 24 * 60 * 60 * 1000;

// 칸 이름에 userId가 있다 — 다른 사용자는 앞 사람 칸을 열지 않고, 로그아웃 · 탈퇴의 clearAll이 접두어 'timer'로 지운다(draftSync)
const stateKey = (u: number) => `timer:${u}:state`;
function parse(raw: string | null): { base?: Omit<Base, 'seq'>; ledger?: Ledger } | null {
  try {
    return raw === null ? null : JSON.parse(raw);
  } catch {
    return null;
  }
}
/** 원장이 없거나 깨졌으면 빈 원장(T0 칸엔 원장이 없다 — 옮길 것 없음) */
const ledgerOf = (l: Ledger | undefined): Ledger => (l && Array.isArray(l.done) ? { active: l.active ?? null, done: l.done } : EMPTY_LEDGER);

let state: MeasureState | null = null;
let kv: KV | null = null;
let tail: Promise<unknown> = Promise.resolve();
let seq = 0; // 이 실행의 대시보드 요청 순번 — 기기 시계가 뒤로 가도 늦게 보낸 쪽이 이긴다
const listeners = new Set<() => void>();
const notify = () => listeners.forEach((l) => l());

export const measureStore = {
  /** 칸을 읽고 연다(그 뒤에 알린다). 디스크 기준 값은 순번 0 — 이 실행의 어떤 새 응답에도 진다 */
  async open(store: KV, userId: number): Promise<void> {
    const saved = parse(await store.getItem(stateKey(userId)).catch(() => null)); // 못 읽어도 연다 — 안 열리면 홈이 「불러오는 중」에 멈춘다
    kv = store;
    state = { userId, base: saved?.base ? { ...saved.base, seq: 0 } : null, ledger: ledgerOf(saved?.ledger), failedSeq: 0 };
    notify();
  },
  /** 메모리만 닫는다(디스크 칸은 남는다 — 401 · 계정 바꾸기). 줄에 선 쓰기가 끝난 뒤 돌아온다 — 로그아웃은 그다음에 칸을 지운다(REQ-364) */
  close(): Promise<void> {
    state = null;
    notify();
    return tail.then(() => undefined);
  },
  /** 열리기 전 · 닫힌 뒤 null(REQ-369) */
  get: (): MeasureState | null => state,
  subscribe(l: () => void): () => void {
    listeners.add(l);
    return () => void listeners.delete(l);
  },
  /**
   * 한 줄 · 바뀐 때만 알린다. 기준 값 · 원장이 바뀌었으면 한 칸에 함께 쓴 뒤 알린다(앱이 죽어도 남게 · 디스크에서도 늘 한 쌍 — REQ-368).
   * 닫혔거나 다른 사용자면 MeasureStoreClosed. 네트워크는 줄 밖이다
   */
  update(f: (s: MeasureState) => MeasureState, userId: number): Promise<MeasureState> {
    const r = tail.then(async () => {
      const s = state;
      if (!s || s.userId !== userId || !kv) throw new MeasureStoreClosed();
      const n = f(s);
      if (n === s) return s;
      if (n.base !== s.base || n.ledger !== s.ledger) {
        try {
          const b = n.base && { d: n.base.d, at: n.base.at, sentAt: n.base.sentAt };
          await kv.setItem(stateKey(userId), JSON.stringify({ base: b, ledger: n.ledger }));
        } catch (e) {
          console.warn('measureStore', e); // 메모리로 계속 — 앱이 죽으면 그 변화만 잃는다(디스크엔 앞 쌍이 그대로)
        }
      }
      if (state !== s) throw new MeasureStoreClosed(); // 쓰는 사이 닫혔다 — 메모리를 되살리지 않는다
      state = n;
      notify();
      return n;
    });
    tail = r.catch(() => undefined);
    return r;
  },
};

const ledgerStep = (f: (l: Ledger) => Ledger) => (s: MeasureState) => {
  const l = f(s.ledger);
  return l === s.ledger ? s : { ...s, ledger: l };
};

/** 대시보드 읽기 — 화면 · 설정이 모두 여기를 지난다. 가게가 안 열렸으면(또는 그사이 닫혔으면) 아무것도 안 한다. 실패는 던진다. */
export async function loadDashboard(): Promise<void> {
  const uid = state?.userId;
  if (uid === undefined) return;
  await flushMeasures().catch(() => undefined); // 대기 마감을 먼저 — 이 응답이 그것을 반영하게
  const sentAt = Date.now();
  const mine = ++seq;
  let got: Awaited<ReturnType<typeof todayDashboard>>;
  try {
    got = await todayDashboard(reading.dashboard); // REQ-348 — 다른 길로 읽지 않는다(상한 30초)
  } catch (e) {
    // 확인 안 된 값(REQ-378) — 메모리만(칸에 쓰지 않는다). 더 늦게 보낸 요청이 이미 성공했으면 base.seq가 커서 확인이 지워지지 않는다
    await measureStore.update((s) => (s.failedSeq >= mine ? s : { ...s, failedSeq: mine }), uid).catch(() => undefined);
    throw e;
  }
  try {
    // 이 실행에서 더 늦게 보낸 요청만 남긴다. 디스크 값(순번 0)은 어떤 새 응답에도 진다. 진 응답은 s 그대로 — 칸에 쓰지도 알리지도 않는다.
    // 기준 값을 바꾸는 같은 update에서 그 요청 전에 받은 칸을 걷는다(한 스냅숏 — REQ-368)
    await measureStore.update(
      (s) => (s.base && s.base.seq > mine ? s : { ...s, base: { d: got.d, at: got.at, sentAt, seq: mine }, ledger: forgetSettled(s.ledger, sentAt) }),
      uid,
    );
  } catch (e) {
    if (!(e instanceof MeasureStoreClosed)) throw e; // 로그아웃 · 계정 바꾸기 사이에 늦게 온 응답 — 버린다
  }
}

// ── 알림 칸(REQ-366) — 어느 차례(화면 · 루트)에서 처리돼도 포커스된 측정 탭이 한 번 가져간다(savedNotice와 같은 꼴 + 구독) ──
let noted = { dropped: 0, trimmed: 0, stuck: false };
let noticeVersion = 0;
const noticeListeners = new Set<() => void>();
export const measureNotice = {
  add(p: { dropped?: number; trimmed?: number; stuck?: boolean }) {
    noted = { dropped: noted.dropped + (p.dropped ?? 0), trimmed: noted.trimmed + (p.trimmed ?? 0), stuck: noted.stuck || !!p.stuck };
    noticeVersion++;
    noticeListeners.forEach((l) => l());
  },
  /** 한 번 쓰고 비운다. 셋은 드물어 한 띠에 줄을 바꿔 잇는다 · 겹쳐 뺀 시간은 1분 이상일 때만 */
  take(): string | null {
    const n = noted;
    noted = { dropped: 0, trimmed: 0, stuck: false };
    const lines = [
      n.dropped > 0 && `올릴 수 없는 측정 ${n.dropped}개를 지웠어요 — 7일이 지났거나 기기 시계가 맞지 않았어요`,
      n.trimmed >= 60 && `다른 곳에서 재던 측정과 겹친 ${Math.floor(n.trimmed / 60)}분은 빼고 올렸어요`,
      n.stuck && '아직 올리지 못한 측정이 있어요 — 기기에 남겨 두고 다시 보낼게요',
    ].filter(Boolean);
    return lines.length > 0 ? lines.join('\n') : null;
  },
  subscribe(l: () => void): () => void {
    noticeListeners.add(l);
    return () => void noticeListeners.delete(l);
  },
  version: () => noticeVersion,
};

// ── 보내기 차례(REQ-359) — 한 번에 한 차례 · 시작 시각 순 ──
let flushTail: Promise<unknown> = Promise.resolve();
let next: Promise<void> | null = null; // 아직 시작하지 않은 다음 차례 — 그사이 부른 쪽은 여기 합류한다(차례는 시작할 때 원장을 읽는다)
let stuckSaid = false; // 앱 실행마다 한 번

export function flushMeasures(): Promise<void> {
  if (next) return next;
  const run = flushTail.then(() => {
    next = null;
    return flushOnce();
  });
  next = run;
  flushTail = run.catch(() => undefined);
  return run;
}

type Outcome = { ok: number } | { drop: true } | { stop: true } | { failed: true };

async function send(x: DoneMeasure): Promise<Outcome> {
  try {
    const r = await (x.mode === 'reading' ? reading : study).finish({ startedAt: x.startedAt, endedAt: x.endedAt, bookId: x.bookId });
    return { ok: r?.trimmedSeconds ?? 0 };
  } catch (e) {
    if (e instanceof ApiError && e.status === 422) return { drop: true }; // 날짜 정책 거절만 지운다 — 400(본문 형식 결함)은 남긴다
    if (e instanceof NetworkError || e instanceof UnauthorizedError) return { stop: true }; // 뒤 칸도 같은 까닭으로 실패한다 · 401은 루트가 가게를 닫는다
    return { failed: true }; // 400 · 409 · 404 · 429 · 5xx — 남기고 다음 칸(늘 실패하는 칸 하나가 뒤 칸을 막지 않게)
  }
}

async function flushOnce(): Promise<void> {
  const uid = state?.userId;
  if (uid === undefined) return;
  await measureStore.update(ledgerStep((l) => capActive(l, Date.now())), uid); // 6시간 넘게 켜진 기기 측정은 시작 + 6시간으로 끝내고 보낸다
  for (const x of pending(state?.ledger ?? EMPTY_LEDGER)) {
    if (state?.userId !== uid) return; // 보내는 도중 다른 계정 — 앞 사람 칸을 새 토큰으로 보내지 않는다
    const r = await send(x);
    if ('stop' in r) break;
    if ('ok' in r) {
      const at = Date.now();
      await measureStore.update(ledgerStep((l) => settle(l, x, at)), uid);
      if (r.ok > 0) measureNotice.add({ trimmed: r.ok });
    } else if ('drop' in r) {
      await measureStore.update(ledgerStep((l) => drop(l, x)), uid);
      measureNotice.add({ dropped: 1 });
    } else {
      const at = Date.now();
      await measureStore.update(ledgerStep((l) => markFailed(l, x, at)), uid);
    }
  }
  const now = Date.now();
  if (!stuckSaid && state?.userId === uid && pending(state.ledger).some((d) => d.failedSince !== null && d.failedSince <= now - DAY_MS)) {
    stuckSaid = true;
    measureNotice.add({ stuck: true });
  }
}
