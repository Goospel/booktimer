import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError, NetworkError } from './api';
import { stubLocalStorage } from './test-fixtures';
import { trackEvent } from './toss';
import {
  TRIAL_CAP_SECONDS,
  type Trial,
  beginTrial,
  completeTrial,
  flushTrial,
  readTrial,
  stopTrial,
  trialDurationSeconds,
  trialPhase,
  writeTrial,
} from './trial';

/**
 * 로그인 전 체험 — 「읽기 시작」을 눌러 잰 시간을 localStorage에 두었다가, 로그인 직후 서버 완료
 * 세션으로 흘려보낸다. 화면(정적 렌더)으로는 클릭도 effect도 못 돌리므로 **상태 전이와 합류 규칙을
 * 전부 이 순수 모듈에 꺼내 계측한다**(관례: `claimDebtWaiver`·`beginLogin`).
 *
 * <p>부정 단언(「호출 안 한다」)은 이 하니스에서 항상 통과라 쓰지 않는다(T-149) — 「올리지 않는다」는
 * <b>storage가 그대로 남아 있다</b>는 양성 단언으로 잰다.
 */

vi.mock('./toss', () => ({
  trackEvent: vi.fn(),
  tossLogin: vi.fn(), // api.ts가 쓴다 — 모듈을 통째로 대체하므로 여기도 채운다
}));

const trackEventMock = vi.mocked(trackEvent);

beforeEach(() => {
  stubLocalStorage();
  trackEventMock.mockReset();
});

const START = '2026-09-11T01:00:00.000Z';
const startedAtMs = Date.parse(START);
const running: Trial = { startedAt: START, endedAt: null };
const done: Trial = { startedAt: START, endedAt: '2026-09-11T01:07:00.000Z' }; // 7분

describe('체험 상태 (trialPhase)', () => {
  it('저장이 없으면 아직 아무것도 안 한 상태다', () => {
    expect(trialPhase(null)).toBe('none');
  });

  it('끝 시각이 없으면 재는 중이다 — 앱을 껐다 켜도 이 상태로 돌아온다', () => {
    expect(trialPhase(running)).toBe('running');
  });

  it('끝 시각이 있으면 남길지 물을 차례다', () => {
    expect(trialPhase(done)).toBe('done');
  });
});

describe('체험 종료 (stopTrial)', () => {
  it('상한 안이면 누른 시각이 그대로 끝이다', () => {
    expect(stopTrial(running, startedAtMs + 90_000).endedAt).toBe('2026-09-11T01:01:30.000Z');
  });

  it('상한을 넘겨 돌아왔으면 6시간으로 접는다 — 서버 클램프와 같은 규칙이라 표시가 어긋나지 않는다', () => {
    const folded = stopTrial(running, startedAtMs + (TRIAL_CAP_SECONDS + 3600) * 1000);

    expect(trialDurationSeconds(folded)).toBe(TRIAL_CAP_SECONDS);
  });

  it('정확히 6시간은 안 자른다 — 경계는 상한에 포함된다', () => {
    const exact = stopTrial(running, startedAtMs + TRIAL_CAP_SECONDS * 1000);

    expect(trialDurationSeconds(exact)).toBe(TRIAL_CAP_SECONDS);
  });

  it('길이는 초 단위 실측이다', () => {
    expect(trialDurationSeconds(done)).toBe(420);
  });
});

describe('보관 (readTrial / writeTrial)', () => {
  it('쓴 것을 그대로 읽는다', () => {
    writeTrial(done);

    expect(readTrial()).toEqual(done);
  });

  it('null을 쓰면 지운다 — 「기록 없이 둘게요」의 전부다', () => {
    writeTrial(done);
    writeTrial(null);

    expect(readTrial()).toBeNull();
  });

  it('localStorage가 없는 하니스·서버 렌더에서는 null이다 — 여기서 던지면 첫 화면이 통째로 죽는다', () => {
    vi.stubGlobal('localStorage', undefined);

    expect(readTrial()).toBeNull();
  });

  it('깨진 값은 없는 것으로 본다 — 남의 키 충돌·구버전 잔재가 앱을 막지 않는다', () => {
    localStorage.setItem('booktimer.trial', '{깨짐');

    expect(readTrial()).toBeNull();
  });

  /**
   * 모양(문자열)만 보면 `"garbage"`가 통과한다 — 그러면 경과가 NaN이 되고 「그만 읽기」의
   * `new Date(NaN).toISOString()`이 RangeError로 터져 사용자가 00:00에 갇힌다.
   */
  it('시각이 날짜가 아니면 없는 것으로 본다 — 문자열이기만 하면 통과하던 자리다', () => {
    localStorage.setItem('booktimer.trial', '{"startedAt":"garbage","endedAt":null}');

    expect(readTrial()).toBeNull();
  });

  it('끝 시각이 깨진 것도 없는 것으로 본다 — 길이가 NaN이면 합류 본문이 통째로 틀린다', () => {
    localStorage.setItem('booktimer.trial', '{"startedAt":"2026-09-11T01:00:00.000Z","endedAt":"garbage"}');

    expect(readTrial()).toBeNull();
  });
});

describe('합류 (flushTrial)', () => {
  it('올릴 것이 없으면 아무 일도 없다 — 토큰 보유자의 매 로드가 이 문을 지난다', async () => {
    await expect(flushTrial(async () => {})).resolves.toBe('none');
  });

  it('아직 재는 중이면 올리지 않고 그대로 둔다 — 끝나지 않은 시간은 기록이 아니다', async () => {
    writeTrial(running);

    await flushTrial(async () => {});

    expect(readTrial()).toEqual(running);
  });

  it('끝난 체험은 시작·끝 시각 그대로 한 번 올리고 지운다', async () => {
    writeTrial(done);
    const sent: Trial[] = [];

    const result = await flushTrial(async (t) => void sent.push(t));

    expect(sent).toEqual([done]);
    expect(result).toBe('imported');
    expect(readTrial()).toBeNull();
  });

  it('합류했다는 사실을 길이와 함께 남긴다 — 배선 결함(400·네트워크)을 콘솔에서 가르는 유일한 점이다', async () => {
    writeTrial(done);

    await flushTrial(async () => {});

    expect(trackEventMock).toHaveBeenCalledWith('trial_imported', { duration_seconds: 420 });
  });

  it('400은 서버가 거부한 것 — 지운다. 남기면 매 로드마다 같은 400을 다시 맞는다', async () => {
    writeTrial(done);

    await flushTrial(async () => {
      throw new ApiError(400, '너무 오래된 기록이에요');
    });

    expect(readTrial()).toBeNull();
  });

  it('네트워크 실패는 남긴다 — 다음 로드에서 재시도하고 서버 멱등이 중복을 막는다', async () => {
    writeTrial(done);

    const result = await flushTrial(async () => {
      throw new NetworkError();
    });

    expect(result).toBe('kept');
    expect(readTrial()).toEqual(done);
  });

  it('404(엔드포인트 없는 옛 서버)도 남긴다 — 서버가 올라오면 저절로 합류한다', async () => {
    writeTrial(done);

    const result = await flushTrial(async () => {
      throw new ApiError(404, '없음');
    });

    expect(result).toBe('kept');
    expect(readTrial()).toEqual(done);
  });

  /**
   * 인증 직후 `load()`는 <b>두 번</b> 돈다 — `onAuthenticated`가 한 번 부르고, 그 setState가 만든
   * `view==='loading' && dashboard===null` 조합을 App의 마운트 effect가 보고 또 한 번 부른다.
   * 둘이 같은 틱에 storage를 읽으면 같은 체험이 두 번 올라간다. 서버 멱등이 <b>행은</b> 막아도
   * `trial_imported`는 두 번 찍혀 §6.2의 합류 비율이 200%로 읽힌다 — 목 모드 실측에서
   * 16초 체험이 「오늘 읽은」을 32초 늘렸다(2026-09-11).
   */
  it('같은 틱에 두 번 불려도 한 번만 올린다 — 인증 직후 load()가 두 번 돈다', async () => {
    writeTrial(done);
    let calls = 0;
    const importFn = async () => {
      calls += 1;
      await Promise.resolve();
    };

    await Promise.all([flushTrial(importFn), flushTrial(importFn)]);

    expect(calls).toBe(1);
  });

  it('합류 사실도 한 번만 남는다 — 두 번 찍히면 합류 비율이 200%로 읽힌다', async () => {
    writeTrial(done);

    await Promise.all([flushTrial(async () => {}), flushTrial(async () => {})]);

    expect(trackEventMock.mock.calls.filter(([name]) => name === 'trial_imported')).toHaveLength(1);
  });

  it('어떤 실패도 밖으로 내지 않는다 — 모든 대시보드 로드가 이 문 뒤에 줄 서 있다', async () => {
    writeTrial(done);

    await expect(
      flushTrial(() => {
        throw new Error('예상 못 한 것');
      }),
    ).resolves.toBe('kept');
  });
});

describe('체험 시작·완료 (beginTrial / completeTrial)', () => {
  it('「읽기 시작」은 시작 시각을 남긴다 — 앱을 꺼도 이 값이 타이머의 진실이다', () => {
    const started = beginTrial(startedAtMs);

    expect(started).toEqual(running);
    expect(readTrial()).toEqual(running);
  });

  it('「읽기 시작」을 눌렀다는 사실을 남긴다 — 화면이 안 눌리는 것과 로그인이 안 되는 것을 가른다', () => {
    beginTrial(startedAtMs);

    expect(trackEventMock).toHaveBeenCalledWith('trial_started', { source: 'hero' });
  });

  /**
   * 어느 손잡이로 시작했나 — 히어로 버튼이냐 탭바 가운데 원이냐. <b>▶ 발견율</b>을 공짜로 잰다:
   * 로그인 뒤 홈에서도 측정은 그 원으로 시작하므로, 여기서 안 눌리면 온보딩에도 같은 구멍이 있다.
   */
  it('어느 손잡이로 시작했는지 함께 남긴다 — 탭바 ▶가 발견되는지가 여기 실린다', () => {
    beginTrial(startedAtMs, 'play');

    expect(trackEventMock).toHaveBeenCalledWith('trial_started', { source: 'play' });
  });

  it('「그만 읽기」는 끝 시각을 박고 길이와 함께 남긴다 — 측정 중 이탈을 이 비율이 가른다', () => {
    beginTrial(startedAtMs);

    const completed = completeTrial(running, startedAtMs + 420_000);

    expect(completed).toEqual(done);
    expect(readTrial()).toEqual(done);
    expect(trackEventMock).toHaveBeenCalledWith('trial_completed', { duration_seconds: 420 });
  });
});
