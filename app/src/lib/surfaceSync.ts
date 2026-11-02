// 위젯 · Live Activity · 알림을 루트 한 곳에서, 한 번에 하나씩 맞춘다(타이머 오프라인 설계 2026-11-02 §6.8 · REQ-363).
// 두 탭이 각자 맞추면 겹친 두 판이 둘 다 「활동 없음」을 보고 Live Activity를 둘 띄운다(surfaces.ts getInstances → start).
import { measureStore } from './measureSync';
import { syncReminders } from './notify';
import { syncSurfaces } from './surfaces';
import { rollToDay } from './visibleDashboard';

let unsub: (() => void) | null = null;
let tail: Promise<void> = Promise.resolve();
let queued = false;

/** 한 판씩. 이미 다음 판이 줄에 있으면 거기 합류한다 — 그 판이 시작할 때 최신 값을 읽는다. 판은 맞추는 순간의 시각으로 굴려 계산한다. */
export function syncSurfacesNow(): Promise<void> {
  if (queued) return tail;
  queued = true;
  tail = tail
    .then(async () => {
      queued = false;
      const s = measureStore.get();
      const t = Date.now();
      if (!s?.base) return syncReminders(null); // 기준 값이 아예 없으면 지금처럼(못 읽으면 null — 다음 읽기가 고친다)
      await syncSurfaces(rollToDay(s.base, t).d, new Date(t));
    })
    .catch((e) => console.warn('surfaces', e));
  return tail;
}

/** 루트가 로그인 동안 켠다 — 가게가 바뀔 때마다 맞춘다 */
export function startSurfaceSync(): void {
  unsub?.();
  unsub = measureStore.subscribe(() => void syncSurfacesNow());
  void syncSurfacesNow();
}

export function stopSurfaceSync(): void {
  unsub?.();
  unsub = null;
}
