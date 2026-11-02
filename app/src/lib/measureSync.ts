// 측정 가게 · 대시보드 읽기(타이머 오프라인 설계 2026-11-02 §6.5 — T0). 마지막 대시보드를 사용자별 칸 하나에 두어
// 오프라인으로 켜도 홈이 그려진다(REQ-362). 화면 · 위젯 · 알림은 이 가게 하나를 읽는다. T2가 같은 칸에 기기 원장을 더한다.
import { reading } from '../api/client';
import type { KV } from './draftSync';
import { todayDashboard } from './goalProgress';
import type { Base } from './visibleDashboard';

export interface MeasureState {
  userId: number;
  base: Base | null;
}
/** 닫혔거나 다른 사용자로 열린 가게에 쓰려 했다(REQ-369) */
export class MeasureStoreClosed extends Error {}

// 칸 이름에 userId가 있다 — 다른 사용자는 앞 사람 칸을 열지 않고, 로그아웃 · 탈퇴의 clearAll이 접두어 'timer'로 지운다(draftSync)
const stateKey = (u: number) => `timer:${u}:state`;
function parse(raw: string | null): { base?: Omit<Base, 'seq'> } | null {
  try {
    return raw === null ? null : JSON.parse(raw);
  } catch {
    return null;
  }
}

let state: MeasureState | null = null;
let kv: KV | null = null;
let tail: Promise<unknown> = Promise.resolve();
let seq = 0; // 이 실행의 대시보드 요청 순번 — 기기 시계가 뒤로 가도 늦게 보낸 쪽이 이긴다
const listeners = new Set<() => void>();
const notify = () => listeners.forEach((l) => l());

export const measureStore = {
  /** 칸을 읽고 연다(그 뒤에 알린다). 디스크 기준 값은 순번 0 — 이 실행의 어떤 새 응답에도 진다 */
  async open(store: KV, userId: number): Promise<void> {
    const saved = parse(await store.getItem(stateKey(userId)));
    kv = store;
    state = { userId, base: saved?.base ? { ...saved.base, seq: 0 } : null };
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
  /** 한 줄 · 바뀐 때만 칸에 쓴 뒤 알린다(앱이 죽어도 남게). 닫혔거나 다른 사용자면 MeasureStoreClosed. 네트워크는 줄 밖이다 */
  update(f: (s: MeasureState) => MeasureState, userId: number): Promise<MeasureState> {
    const r = tail.then(async () => {
      const s = state;
      if (!s || s.userId !== userId || !kv) throw new MeasureStoreClosed();
      const n = f(s);
      if (n === s) return s;
      try {
        const b = n.base && { d: n.base.d, at: n.base.at, sentAt: n.base.sentAt };
        await kv.setItem(stateKey(userId), JSON.stringify({ base: b }));
      } catch (e) {
        console.warn('measureStore', e); // 메모리로 계속 — 앱이 죽으면 그 변화만 잃는다
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

/** 대시보드 읽기 — 화면 · 설정이 모두 여기를 지난다. 가게가 안 열렸으면(또는 그사이 닫혔으면) 아무것도 안 한다. 실패는 던진다. */
export async function loadDashboard(): Promise<void> {
  const uid = state?.userId;
  if (uid === undefined) return;
  const sentAt = Date.now();
  const mine = ++seq;
  const got = await todayDashboard(reading.dashboard); // REQ-348 — 다른 길로 읽지 않는다(상한 30초)
  try {
    // 이 실행에서 더 늦게 보낸 요청만 남긴다. 디스크 값(순번 0)은 어떤 새 응답에도 진다
    await measureStore.update((s) => ({ ...s, base: s.base && s.base.seq > mine ? s.base : { d: got.d, at: got.at, sentAt, seq: mine } }), uid);
  } catch (e) {
    if (!(e instanceof MeasureStoreClosed)) throw e; // 로그아웃 · 계정 바꾸기 사이에 늦게 온 응답 — 버린다
  }
}
