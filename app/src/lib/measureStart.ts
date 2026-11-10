// 측정 시작 · 정지(타이머 오프라인 설계 2026-11-02 §6.6 — T2) — 홈(독서) · 공부 탭 · 위젯 · Option+P가 같은 함수를 쓴다.
// 앱 측정은 기기 원장에 먼저 적고 서버를 기다리지 않는다 — 시작은 서버에 보내지 않고(Q1), 끝난 칸은 보내기 차례가 마감 문으로 올린다.
// 웹에서 시작해 서버에 열린 측정의 정지만 지금처럼 서버 정지다(온라인 전용 — REQ-376 · REQ-151).
import { ApiError, failText } from '../api/client';
import { begin, capActive, end, type Ledger, type MeasureMode } from './measureLedger';
import { MeasureStoreClosed, type MeasureState, type measureStore } from './measureSync';

export const BUSY_ELSEWHERE = '다른 곳에서 재는 측정이 있어요 — 그 측정을 멈추면 시작할 수 있어요';
export const ALREADY_STOPPED = '이미 끝난 측정이에요';
export const STOP_FAILED = '측정을 끝내지 못했어요 — 잠시 뒤 다시 눌러 주세요';

/** load = 훅의 load(실패를 loadError로) — 기다리지 않는다 */
export interface MeasureDeps {
  store: Pick<typeof measureStore, 'get' | 'update'>;
  load: () => Promise<unknown>;
  now?: () => number;
}

/** 원장만 고친다. 가게가 닫혔으면 조용히 끝난다 — 닫히면 보이는 대시보드가 먼저 null이 되어 단추가 사라진다(REQ-369) */
async function write(deps: MeasureDeps, f: (l: Ledger) => Ledger): Promise<boolean> {
  const s = deps.store.get();
  if (!s) return false;
  try {
    await deps.store.update((x: MeasureState) => {
      const l = f(x.ledger);
      return l === x.ledger ? x : { ...x, ledger: l };
    }, s.userId);
    return true;
  } catch (e) {
    if (e instanceof MeasureStoreClosed) return false;
    throw e;
  }
}

/** 원장(디스크)에 적힌 순간 돌아온다. blocked = 보이는 대시보드에 다른 모드가 재는 중(이 기기 원장 · 확인된 서버 측정) */
export async function startMeasure(deps: MeasureDeps, p: { mode: MeasureMode; bookId: number | null; bookTitle: string | null; blocked: boolean }): Promise<{ error?: string }> {
  if (!deps.store.get()) return {};
  if (p.blocked) {
    void deps.load().catch(() => undefined); // 그 측정이 아직 있는지 다시 확인(지금 409 뒤 다시 읽기와 같다 — 지하면 실패해 확인 안 된 값이 된다)
    return { error: BUSY_ELSEWHERE };
  }
  const at = (deps.now ?? Date.now)();
  if (await write(deps, (l) => begin(l, { mode: p.mode, at, bookId: p.bookId, bookTitle: p.bookTitle }))) void deps.load().catch(() => undefined); // 대기 마감을 이 기회에
  return {};
}

/** 이 기기 원장(6시간 지난 진행 중은 끝낸 뒤)의 진행 중이 그 모드면 기기에서, 아니면 서버에 열린(웹 시작) 측정 — 서버 정지 */
export async function stopMeasure(deps: MeasureDeps & { serverStop: () => Promise<unknown> }, mode: MeasureMode): Promise<{ error?: string; info?: string }> {
  const s = deps.store.get();
  if (!s) return {};
  const now = (deps.now ?? Date.now)();
  if (capActive(s.ledger, now).active?.mode === mode) {
    if (await write(deps, (l) => end(capActive(l, now), now))) void deps.load().catch(() => undefined); // 보내기 → 다시 읽기
    return {};
  }
  let r: { error?: string; info?: string } = {};
  try {
    await deps.serverStop();
  } catch (e) {
    r = e instanceof ApiError && e.status === 409 ? { info: ALREADY_STOPPED } : { error: failText(e, STOP_FAILED) };
  }
  await deps.load().catch(() => undefined);
  return r;
}
