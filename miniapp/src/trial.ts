import { ApiError, importSession } from './api';
import { trackEvent } from './toss';

/**
 * 로그인 전 체험 — 「읽기 시작」으로 잰 시간을 기기에 두었다가, 로그인 직후 서버 완료 세션으로 합류시킨다.
 *
 * <p>왜 있는가: 소개 화면 21명 중 「토스로 시작하기」를 누른 사람이 4명(19%)뿐이었다. 이탈은 토스
 * 동의창 <b>이전</b>, 회색 글 3줄뿐인 첫 화면에서 났다 — 첫 탭의 대가를 0으로 만들지 않으면 그 단계는
 * 안 움직인다. 그래서 먼저 재게 하고, 「기록을 남기려면」 시점에 로그인을 청한다.
 *
 * <p>보관은 localStorage 한 키다. 세션 도중 앱을 꺼도 시작 시각이 남아 재진입에서 이어 세고(서버 실측
 * 세션과 같은 약속), 로그인하면 {@link flushTrial}이 그 한 건을 올리고 지운다.
 */

export const TRIAL_KEY = 'booktimer.trial';

/** 서버 `ReadingSessionService.MAX_SESSION_DURATION` 거울 — 표시용 클램프다(서버가 다시 자른다). */
export const TRIAL_CAP_SECONDS = 6 * 3600;

/** 체험 1건 — 시각은 ISO 문자열이라 그대로 서버 `POST /api/sessions/import` 본문이 된다. */
export interface Trial {
  startedAt: string;
  endedAt: string | null;
}

export type TrialPhase = 'none' | 'running' | 'done';

export function trialPhase(trial: Trial | null): TrialPhase {
  if (trial === null) return 'none';
  return trial.endedAt === null ? 'running' : 'done';
}

/** 끝 시각을 박는다 — 6시간을 넘겼으면 상한으로 접는다(며칠 뒤 재진입 경로). */
export function stopTrial(trial: Trial, now: number): Trial {
  const cap = Date.parse(trial.startedAt) + TRIAL_CAP_SECONDS * 1000;
  return { startedAt: trial.startedAt, endedAt: new Date(Math.min(now, cap)).toISOString() };
}

/** 길이(초) — `done`일 때만 의미가 있다. */
export function trialDurationSeconds(trial: Trial): number {
  if (trial.endedAt === null) return 0;
  return Math.max(0, Math.floor((Date.parse(trial.endedAt) - Date.parse(trial.startedAt)) / 1000));
}

/**
 * 저장된 체험 — 없거나 읽을 수 없으면 null.
 *
 * <p>정적 렌더 하니스·서버 렌더에는 `localStorage`가 아예 없고, 깨진 값은 남의 키 충돌이나 옛 버전
 * 잔재다. 어느 쪽이든 여기서 던지면 <b>첫 화면이 통째로 죽는다</b> — 그래서 전부 「없음」으로 떨어뜨린다.
 */
export function readTrial(): Trial | null {
  if (typeof localStorage === 'undefined') return null;
  try {
    const raw = localStorage.getItem(TRIAL_KEY);
    if (raw === null) return null;
    const parsed = JSON.parse(raw) as Trial;
    return typeof parsed?.startedAt === 'string' ? parsed : null;
  } catch {
    return null;
  }
}

/** 저장·삭제(null이면 지운다). 읽기와 같은 이유로 실패를 삼킨다. */
export function writeTrial(trial: Trial | null): void {
  if (typeof localStorage === 'undefined') return;
  try {
    if (trial === null) localStorage.removeItem(TRIAL_KEY);
    else localStorage.setItem(TRIAL_KEY, JSON.stringify(trial));
  } catch {
    // 사파리 프라이빗 등 쓰기 불가 — 체험은 못 남지만 화면은 그대로 돈다.
  }
}

/** 「읽기 시작」 — 시작 시각을 남기고 눌렀다는 사실을 찍는다. */
export function beginTrial(now: number = Date.now()): Trial {
  const trial: Trial = { startedAt: new Date(now).toISOString(), endedAt: null };
  writeTrial(trial);
  trackEvent('trial_started');
  return trial;
}

/**
 * 「그만 읽기」 — 끝 시각을 박고 길이와 함께 찍는다.
 *
 * <p>상한 자동 종료(며칠 뒤 재진입)는 여기를 지나지 않는다 — `trial_completed`는 <b>사용자가 끝낸</b>
 * 체험만 세야 「재다 말고 떠난 사람」 비율이 뜻을 가진다.
 */
export function completeTrial(trial: Trial, now: number = Date.now()): Trial {
  const done = stopTrial(trial, now);
  writeTrial(done);
  trackEvent('trial_completed', { duration_seconds: trialDurationSeconds(done) });
  return done;
}

/**
 * 합류 — 끝난 체험이 있으면 서버로 올리고 지운다. 없으면 동기 no-op(`App.load()`의 매 호출이 이 문을 지난다).
 *
 * <p><b>어떤 실패도 밖으로 내지 않는다</b> — 모든 대시보드 로드가 이 뒤에 줄 서 있어서, 여기서 새는
 * 예외 하나가 앱 전체를 로딩에 묶는다. 실패의 갈래는 둘뿐이다:
 * <ul>
 *   <li>400 — 서버가 <b>판정</b>했다(너무 오래됐거나 모양이 틀림). 남기면 매 로드마다 같은 400을
 *       다시 맞으므로 지운다.</li>
 *   <li>그 밖(네트워크·5xx·404·401) — 아직 <b>판정되지 않았다</b>. 남겨 다음 로드에서 재시도하고,
 *       중복은 서버의 `(user, startedAt)` 멱등이 막는다. 404는 서버가 아직 옛 버전인 경우다.</li>
 * </ul>
 */
export type FlushResult = 'imported' | 'none' | 'kept';

/**
 * 진행 중인 합류 — 같은 체험이 두 번 올라가는 것을 막는 유일한 장치다.
 *
 * <p>인증 직후 `App.load()`는 <b>두 번</b> 돈다: `onAuthenticated`가 한 번 부르고, 그 setState가 만든
 * `view==='loading' && dashboard===null` 조합을 마운트 effect가 보고 또 한 번 부른다. 둘이 같은 틱에
 * storage를 읽으면 지우기 전이라 둘 다 「올릴 것이 있다」고 본다. 서버 `(user, startedAt)` 멱등이
 * <b>행은</b> 막지만 `trial_imported`는 두 번 찍혀 합류 비율이 200%로 읽힌다(목 모드 실측: 16초
 * 체험이 「오늘 읽은」을 32초 늘렸다, 2026-09-11).
 */
let inFlight: Promise<FlushResult> | null = null;

export function flushTrial(
  importFn: (trial: Trial) => Promise<void> = (trial) =>
    importSession({ startedAt: trial.startedAt, endedAt: trial.endedAt as string }),
): Promise<FlushResult> {
  inFlight ??= runFlush(importFn).finally(() => {
    inFlight = null;
  });
  return inFlight;
}

async function runFlush(importFn: (trial: Trial) => Promise<void>): Promise<FlushResult> {
  const trial = readTrial();
  if (trial === null || trial.endedAt === null) return 'none';

  try {
    await importFn(trial);
  } catch (e) {
    if (e instanceof ApiError && e.status === 400) {
      writeTrial(null);
      return 'none';
    }
    return 'kept';
  }

  writeTrial(null);
  trackEvent('trial_imported', { duration_seconds: trialDurationSeconds(trial) });
  return 'imported';
}
