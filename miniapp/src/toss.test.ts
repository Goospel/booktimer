import { Analytics, Device, Notification, loadFullScreenAd, showFullScreenAd } from '@apps-in-toss/web-framework';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  AD_LOAD_TIMEOUT_MS,
  type AdSlotStatus,
  BANNER_RENDER_TIMEOUT_MS,
  GOAL_MET_TEMPLATE_CODE,
  STUDY_GOAL_TEMPLATE_CODE,
  adSlotStatus,
  hapticOnce,
  marginBannerEnabled,
  notificationAgreementSupported,
  onAdSlotChange,
  preloadAd,
  requestNotificationAgreement,
  shouldPreload,
  subscribeNativeBack,
  trackEvent,
  trackScreen,
  useAdSlot,
  watchRewardAd,
} from './toss';

/**
 * 배너 SDK 목은 `vi.hoisted`로 **모듈 리셋을 넘어 살아남게** 만든다. 배너 초기화가 모듈 레벨 memo라
 * (「한 번만」 계약) 초기화 분기를 테스트마다 새로 밟으려면 `vi.resetModules()`로 `toss.ts`를 다시
 * 읽어야 하는데, 목 팩토리가 그때 또 돌아 `vi.fn()`을 새로 만들면 여기 잡아 둔 참조가 낡아 버린다.
 */
const { tossAdsMock } = vi.hoisted(() => {
  const supportable = () => Object.assign(vi.fn(), { isSupported: vi.fn() });
  return { tossAdsMock: { initialize: supportable(), attachBanner: supportable() } };
});

vi.mock('@apps-in-toss/web-framework', () => ({
  loadFullScreenAd: vi.fn(),
  showFullScreenAd: vi.fn(),
  TossAuth: { login: vi.fn() },
  // requestAgreement는 "함수 + isSupported 프로퍼티"라 실물 모양 그대로 흉내 낸다.
  Notification: { requestAgreement: Object.assign(vi.fn(), { isSupported: vi.fn() }) },
  Analytics: { log: vi.fn() },
  TossAds: tossAdsMock,
  Device: { triggerHaptic: vi.fn() },
}));

const loadMock = vi.mocked(loadFullScreenAd);
const showMock = vi.mocked(showFullScreenAd);
const agreementMock = vi.mocked(Notification.requestAgreement);
const supportedMock = vi.mocked(Notification.requestAgreement.isSupported);
const logMock = vi.mocked(Analytics.log);

type LoadParams = Parameters<typeof loadFullScreenAd>[0];
type ShowParams = Parameters<typeof showFullScreenAd>[0];

/** load는 즉시 `loaded`를 주고, show는 넘겨준 이벤트들을 순서대로 흘린다. */
function stubAd(events: Array<{ type: string; data?: unknown }>) {
  loadMock.mockImplementation((params: LoadParams) => {
    params.onEvent({ type: 'loaded' });
    return () => {};
  });
  showMock.mockImplementation((params: ShowParams) => {
    for (const event of events) params.onEvent(event as never);
    return () => {};
  });
}

beforeEach(() => {
  loadMock.mockReset();
  showMock.mockReset();
  agreementMock.mockReset();
  supportedMock.mockReset();
  supportedMock.mockReturnValue(true);
  logMock.mockReset();
  logMock.mockResolvedValue(undefined);
  vi.stubGlobal('window', {}); // 래퍼가 브라우저 밖(window 없음)을 미지원으로 접으므로, 앱 안 상황을 만든다
});

/** 테스트마다 새 그룹 ID — 슬롯이 모듈 상태라 테스트 사이에 남는다. */
let groupSeq = 0;
const fresh = () => `ad-group-${++groupSeq}`;

/** 새 그룹을 미리 불러 `ready`로 만든다 — show는 넘겨준 이벤트들을 흘린다. */
function readyAd(events: Array<{ type: string; data?: unknown }>): string {
  stubAd(events);
  const g = fresh();
  preloadAd(g);
  return g;
}

/**
 * 광고 슬롯 — 그룹(지면)마다 한 장을 <b>누르기 전에</b> 불러 둔다(비게임 체크리스트 ③ 「광고 재생 시점에 실시간으로
 * 로딩하지 않아요」). 앱인토스 계약을 옮긴 것이다: 같은 그룹은 한 번에 한 장, 띄운 장은 다시 못 띄운다(load → show → load).
 *
 * <p>응답 없는 load를 쓰는 테스트는 가짜 타이머 안에서 돈다 — 실제 60초 상한 타이머를 워커에 남기지 않는다.
 */
describe('광고 슬롯 — preloadAd', () => {
  it('REQ-02 · 그룹 ID가 비면 SDK를 부르지 않는다', () => {
    preloadAd('');

    expect(loadMock).not.toHaveBeenCalled();
    expect(adSlotStatus('')).toBe('idle');
  });

  it('REQ-02 · 미리 부르면 그 그룹으로 load를 한 번 부르고 loaded 전엔 loading이다', () => {
    vi.useFakeTimers();
    try {
      loadMock.mockImplementation(() => () => {});
      const g = fresh();

      preloadAd(g);

      expect(loadMock).toHaveBeenCalledTimes(1);
      expect(loadMock.mock.calls[0][0].options).toEqual({ adGroupId: g });
      expect(adSlotStatus(g)).toBe('loading');
    } finally {
      vi.useRealTimers();
    }
  });

  it('REQ-02 · loaded가 오면 ready다', () => {
    const g = readyAd([]);

    expect(adSlotStatus(g)).toBe('ready');
  });

  it('REQ-03 · 불러오는 중·준비됨·재생 중엔 다시 불러도 load는 한 번뿐이다', () => {
    vi.useFakeTimers();
    try {
      loadMock.mockImplementation(() => () => {});
      showMock.mockImplementation(() => () => {}); // 재생 중 — dismissed가 아직 안 왔다
      const g = fresh();

      preloadAd(g);
      preloadAd(g);
      preloadAd(g); // 탭 왕복·재마운트
      expect(adSlotStatus(g)).toBe('loading');
      expect(loadMock).toHaveBeenCalledTimes(1);

      loadMock.mock.calls[0][0].onEvent({ type: 'loaded' });
      preloadAd(g);
      expect(adSlotStatus(g)).toBe('ready');
      expect(loadMock).toHaveBeenCalledTimes(1);

      void watchRewardAd(g);
      preloadAd(g);
      expect(adSlotStatus(g)).toBe('showing');
      expect(loadMock).toHaveBeenCalledTimes(1);
    } finally {
      vi.useRealTimers();
    }
  });

  it('REQ-04 · 로드 에러면 failed이고, 다시 부르면 load를 다시 한다', () => {
    loadMock.mockImplementation((params: LoadParams) => {
      params.onError(new Error('no fill'));
      return () => {};
    });
    const g = fresh();

    preloadAd(g);
    expect(adSlotStatus(g)).toBe('failed');

    preloadAd(g); // 「광고 다시 불러오기」
    expect(loadMock).toHaveBeenCalledTimes(2);
  });

  it('REQ-04 · SDK가 동기로 던져도 삼키고 failed다 — 앱 밖(목 모드)', () => {
    loadMock.mockImplementation(() => {
      throw new TypeError("Cannot read properties of undefined (reading 'loadFullScreenAd')");
    });
    const g = fresh();

    expect(() => preloadAd(g)).not.toThrow();
    expect(adSlotStatus(g)).toBe('failed');
  });

  it('REQ-04 · 60초 동안 답이 없으면 failed다 — 그 직전까진 loading', () => {
    vi.useFakeTimers();
    try {
      loadMock.mockImplementation(() => () => {});
      const g = fresh();

      preloadAd(g);
      vi.advanceTimersByTime(AD_LOAD_TIMEOUT_MS - 1);
      expect(adSlotStatus(g)).toBe('loading');
      vi.advanceTimersByTime(1);
      expect(adSlotStatus(g)).toBe('failed');
    } finally {
      vi.useRealTimers();
    }
  });

  it('REQ-04 · 시간 초과 뒤 다시 부른 로드를 옛 로드의 늦은 에러가 failed로 덮지 않는다', () => {
    vi.useFakeTimers();
    try {
      loadMock.mockImplementation(() => () => {});
      const g = fresh();

      preloadAd(g);
      vi.advanceTimersByTime(AD_LOAD_TIMEOUT_MS);
      preloadAd(g);
      expect(adSlotStatus(g)).toBe('loading');

      loadMock.mock.calls[0][0].onError(new Error('late'));
      expect(adSlotStatus(g)).toBe('loading');

      loadMock.mock.calls[1][0].onError(new Error('now')); // 양성 대조 — 지금 로드의 에러는 접는다
      expect(adSlotStatus(g)).toBe('failed');
    } finally {
      vi.useRealTimers();
    }
  });

  it('REQ-04 · 시간 초과 뒤에 늦게 온 loaded는 ready로 받는다', () => {
    vi.useFakeTimers();
    try {
      loadMock.mockImplementation(() => () => {});
      const g = fresh();

      preloadAd(g);
      vi.advanceTimersByTime(AD_LOAD_TIMEOUT_MS);
      expect(adSlotStatus(g)).toBe('failed');

      loadMock.mock.calls[0][0].onEvent({ type: 'loaded' });
      expect(adSlotStatus(g)).toBe('ready');
    } finally {
      vi.useRealTimers();
    }
  });

  it('REQ-04 · 다시 부른 로드가 도는 중에 옛 로드의 늦은 loaded는 무시한다', () => {
    vi.useFakeTimers();
    try {
      loadMock.mockImplementation(() => () => {});
      const g = fresh();

      preloadAd(g);
      vi.advanceTimersByTime(AD_LOAD_TIMEOUT_MS);
      expect(adSlotStatus(g)).toBe('failed');
      preloadAd(g);
      expect(adSlotStatus(g)).toBe('loading');

      loadMock.mock.calls[0][0].onEvent({ type: 'loaded' });
      expect(adSlotStatus(g)).toBe('loading'); // SDK엔 새 장이 아직 없을 수 있다

      loadMock.mock.calls[1][0].onEvent({ type: 'loaded' }); // 양성 대조 — 지금 로드의 loaded는 받는다
      expect(adSlotStatus(g)).toBe('ready');
    } finally {
      vi.useRealTimers();
    }
  });

  it('REQ-05 · 상태가 바뀔 때마다 구독자에게 알리고 해지하면 멈춘다', async () => {
    const g = fresh();
    const seen: AdSlotStatus[] = [];
    const unsubscribe = onAdSlotChange(() => seen.push(adSlotStatus(g)));
    stubAd([{ type: 'dismissed' }]);

    preloadAd(g);
    expect(seen).toEqual(['loading', 'ready']);

    unsubscribe();
    await watchRewardAd(g); // showing → idle
    expect(seen).toEqual(['loading', 'ready']);
  });

  it('REQ-05 · shouldPreload — 실패로 접힐 때는 부르지 않는다', () => {
    expect(shouldPreload('loading', 'failed', true)).toBe(false);
    expect(shouldPreload('failed', 'failed', true)).toBe(false);
    expect(shouldPreload('failed', 'loading', true)).toBe(false);
  });

  it('REQ-05 · shouldPreload — 지면이 막 서거나 다 보고 idle로 돌아오면 부른다', () => {
    expect(shouldPreload(undefined, 'idle', true)).toBe(true);
    expect(shouldPreload(undefined, 'failed', true)).toBe(true); // 상자를 다시 편 때
    expect(shouldPreload('showing', 'idle', true)).toBe(true);
    // 같은 판정의 대조 — 위 true 셋이 앞 테스트의 false들이 「늘 false인 함수」가 아님을 보인다.
    expect(shouldPreload('idle', 'loading', true)).toBe(false);
    expect(shouldPreload('loading', 'ready', true)).toBe(false);
    expect(shouldPreload('ready', 'showing', true)).toBe(false);
    expect(shouldPreload('showing', 'idle', false)).toBe(false);
  });

  it('REQ-07 · useAdSlot은 슬롯의 지금 상태를 읽는다 — 정적 렌더에서도', () => {
    const g = readyAd([]);
    const Probe = () => createElement('span', null, useAdSlot(g, false));

    expect(renderToStaticMarkup(createElement(Probe))).toBe('<span>ready</span>');
  });
});

/**
 * 리워드 광고 재생 — <b>미리 불러 둔(ready) 광고만</b> 띄우고, 보상 판정은 SDK 이벤트 시퀀스를 boolean 하나로 접는다.
 *
 * <p>왜 테스트가 필요한가: 문서에 `userEarnedReward`와 `dismissed`의 **순서 계약이 없다**.
 * 그래서 플래그로 들고 있다가 `dismissed`에 확정하는 방식을 쓰는데, 그 방식이 두 순서 모두에서
 * 같은 답을 내는지가 이 기능의 신뢰 경계다(광고를 끝까지 안 본 유저에게 지급하면 안 된다).
 * 실기기 실측은 머지 게이트로 따로 남아 있고, 여기선 래퍼 로직만 격리 검증한다.
 */
describe('watchRewardAd', () => {
  it('REQ-01 · 준비된 광고가 없으면 load도 show도 부르지 않고 false다', async () => {
    const g = fresh();

    const result = watchRewardAd(g);

    expect(loadMock).not.toHaveBeenCalled(); // 재생 시점 로딩이 없다 — 옛 구현은 여기서 load를 불렀다
    expect(showMock).not.toHaveBeenCalled();
    await expect(result).resolves.toBe(false);
  });

  it('REQ-01 · 준비된 광고는 load 없이 곧장 show한다', async () => {
    const g = readyAd([{ type: 'dismissed' }]);
    expect(loadMock).toHaveBeenCalledTimes(1); // 미리 부른 한 번

    const result = watchRewardAd(g);

    expect(showMock).toHaveBeenCalledTimes(1);
    expect(loadMock).toHaveBeenCalledTimes(1);
    await result;
  });

  it('REQ-05 · 재생이 끝나면 idle로 돌아간다 — 네 갈래 모두', async () => {
    const dismissed = readyAd([{ type: 'dismissed' }]);
    await watchRewardAd(dismissed);
    expect(adSlotStatus(dismissed)).toBe('idle');

    const failedToShow = readyAd([{ type: 'failedToShow' }]);
    await watchRewardAd(failedToShow).catch(() => {});
    expect(adSlotStatus(failedToShow)).toBe('idle');

    const showError = readyAd([]);
    showMock.mockImplementation((params: ShowParams) => {
      params.onError(new Error('show failed'));
      return () => {};
    });
    await watchRewardAd(showError).catch(() => {});
    expect(adSlotStatus(showError)).toBe('idle');

    const showThrow = readyAd([]);
    showMock.mockImplementation(() => {
      throw new TypeError('no bridge');
    });
    await watchRewardAd(showThrow).catch(() => {});
    expect(adSlotStatus(showThrow)).toBe('idle');
  });

  it('시청 완료(userEarnedReward → dismissed)면 true', async () => {
    const g = readyAd([{ type: 'show' }, { type: 'impression' }, { type: 'userEarnedReward', data: { unitType: 'day', unitAmount: 1 } }, { type: 'dismissed' }]);

    await expect(watchRewardAd(g)).resolves.toBe(true);
  });

  it('순서가 뒤바뀌어도(dismissed 직전이 아니라 앞서 와도) 같은 답 — 문서에 순서 보장이 없다', async () => {
    const g = readyAd([{ type: 'userEarnedReward', data: { unitType: 'day', unitAmount: 1 } }, { type: 'show' }, { type: 'dismissed' }]);

    await expect(watchRewardAd(g)).resolves.toBe(true);
  });

  it('중간 이탈(userEarnedReward 없이 dismissed)이면 false — 지급하면 안 되는 경로', async () => {
    const g = readyAd([{ type: 'show' }, { type: 'clicked' }, { type: 'dismissed' }]);

    await expect(watchRewardAd(g)).resolves.toBe(false);
  });

  it('failedToShow면 reject', async () => {
    const g = readyAd([{ type: 'requested' }, { type: 'failedToShow' }]);

    await expect(watchRewardAd(g)).rejects.toThrow();
  });

  it('load·show 모두 같은 adGroupId를 받는다 — 다른 그룹을 보여주면 정산이 어긋난다', async () => {
    const g = readyAd([{ type: 'userEarnedReward', data: { unitType: 'day', unitAmount: 1 } }, { type: 'dismissed' }]);

    await watchRewardAd(g);

    expect(loadMock.mock.calls[0][0].options).toEqual({ adGroupId: g });
    expect(showMock.mock.calls[0][0].options).toEqual({ adGroupId: g });
  });
});

/**
 * 달성 햅틱 — 홈 effect 안에서 불린다. 토스 밖(목 모드)엔 브릿지가 없어 동기로 던지고, 앱 안에서도
 * 거부할 수 있다 — 어느 쪽이든 새면 홈 렌더(또는 unhandled rejection)가 깨진다.
 */
describe('hapticOnce', () => {
  const hapticMock = vi.mocked(Device.triggerHaptic);

  it('성공 햅틱 1회를 요청한다', () => {
    hapticMock.mockReset();
    hapticMock.mockResolvedValue(undefined);

    hapticOnce();

    expect(hapticMock).toHaveBeenCalledTimes(1);
    expect(hapticMock).toHaveBeenCalledWith({ type: 'success' });
  });

  it('브릿지가 동기로 던져도(토스 밖) 삼킨다', () => {
    hapticMock.mockReset();
    hapticMock.mockImplementation(() => {
      throw new TypeError('no bridge');
    });

    expect(() => hapticOnce()).not.toThrow();
  });

  it('브릿지가 거부해도 삼킨다 — 아무도 안 받는 rejection이 새지 않는다', async () => {
    hapticMock.mockReset();
    const rejected = Promise.reject(new Error('denied'));
    const caught = vi.spyOn(rejected, 'catch');
    hapticMock.mockReturnValue(rejected);

    hapticOnce();

    expect(caught).toHaveBeenCalledTimes(1);
    await rejected.catch(() => {}); // 테스트 자신이 남긴 참조 정리
  });
});

/**
 * 알림 동의 래퍼 — 앱브릿지 콜백 API를 Promise 하나로 접는다.
 *
 * <p>계측 지점 셋: ① 결과 타입이 그대로 올라오는가 ② **결과를 받으면 반드시 cleanup을 부르는가**
 * (문서 요구 — 안 부르면 콜백이 앱브릿지에 남는다) ③ 미지원 토스앱(5.255.0 미만)에서 화면을
 * 띄우려 들지 않고 즉시 `null`인가. cleanup은 SDK가 콜백을 **동기로 부를 때도** 걸려야 한다.
 */
describe('requestNotificationAgreement', () => {
  /** 결과를 동기로 흘리는 SDK 흉내 — cleanup 자체를 spy로 돌려준다. */
  function stubAgreement(result: 'newAgreement' | 'alreadyAgreed' | 'agreementRejected') {
    const cleanup = vi.fn();
    agreementMock.mockImplementation((params) => {
      params.onEvent({ type: result });
      return cleanup;
    });
    return cleanup;
  }

  it('동의(newAgreement) 결과를 그대로 돌려준다', async () => {
    stubAgreement('newAgreement');

    await expect(requestNotificationAgreement('booktimer-daily-goal-met')).resolves.toBe('newAgreement');
  });

  it('이미 동의(alreadyAgreed)·거절(agreementRejected)도 구분해 돌려준다 — 카드 숨김 판단의 근거다', async () => {
    stubAgreement('alreadyAgreed');
    await expect(requestNotificationAgreement('t')).resolves.toBe('alreadyAgreed');

    stubAgreement('agreementRejected');
    await expect(requestNotificationAgreement('t')).resolves.toBe('agreementRejected');
  });

  it('콘솔 템플릿 코드를 그대로 넘긴다 — 코드가 어긋나면 다른 동의문이 뜬다', async () => {
    stubAgreement('newAgreement');

    await requestNotificationAgreement('booktimer-daily-goal-met');

    expect(agreementMock.mock.calls[0][0].options).toEqual({ templateCode: 'booktimer-daily-goal-met' });
  });

  it('결과를 받으면 cleanup을 부른다 — 동기 콜백에서도(cleanup 할당 전에 결과가 올 수 있다)', async () => {
    const cleanup = stubAgreement('newAgreement');

    await requestNotificationAgreement('t');

    expect(cleanup).toHaveBeenCalledTimes(1);
  });

  it('결과가 비동기로 와도 cleanup을 부른다', async () => {
    const cleanup = vi.fn();
    agreementMock.mockImplementation((params) => {
      setTimeout(() => params.onEvent({ type: 'alreadyAgreed' }), 0);
      return cleanup;
    });

    await expect(requestNotificationAgreement('t')).resolves.toBe('alreadyAgreed');
    expect(cleanup).toHaveBeenCalledTimes(1);
  });

  it('onError면 reject하고 cleanup도 부른다', async () => {
    const cleanup = vi.fn();
    agreementMock.mockImplementation((params) => {
      params.onError(new Error('bridge failed'));
      return cleanup;
    });

    await expect(requestNotificationAgreement('t')).rejects.toThrow('bridge failed');
    expect(cleanup).toHaveBeenCalledTimes(1);
  });

  it('미지원 토스앱이면 화면을 띄우지 않고 즉시 null', async () => {
    supportedMock.mockReturnValue(false);

    await expect(requestNotificationAgreement('t')).resolves.toBeNull();
    expect(agreementMock).not.toHaveBeenCalled();
  });

  it('브라우저 밖(window 없음)이면 SDK를 건드리지 않는다 — SDK가 window를 읽다 던진다', async () => {
    vi.stubGlobal('window', undefined);

    await expect(requestNotificationAgreement('t')).resolves.toBeNull();
    expect(supportedMock).not.toHaveBeenCalled();
  });
});

/**
 * 동의 요청 코드는 콘솔에 등록된 발송 코드와 한 글자도 어긋나면 안 된다 — 토스는 이 코드로 동의문을 찾는다.
 * 홈 테스트는 이 값을 목으로 갈아 끼우므로, 실값은 여기서만 잰다.
 */
describe('알림 동의 템플릿 코드 — 콘솔 등록값', () => {
  it('공부는 공부 동의문(122175)에 묶인 booktimer-study-goal-met이다', () => {
    expect(STUDY_GOAL_TEMPLATE_CODE).toBe('booktimer-study-goal-met');
  });

  it('독서 코드와 다르다 — 같으면 공부 카드가 독서 동의문을 띄운다', () => {
    expect(STUDY_GOAL_TEMPLATE_CODE).not.toBe(GOAL_MET_TEMPLATE_CODE);
  });
});

describe('notificationAgreementSupported — 브라우저(SDK 부재) 가드', () => {
  /**
   * 실물 `isSupported()`는 `window.__appsInTossConstants[...]`를 읽는다 — 토스앱 **밖**(브라우저 dev 목
   * 모드)에는 그 주입 상수가 없어 TypeError다. `window`는 있으니 기존 `typeof window` 가드로는 안 막힌다.
   * 홈이 **렌더 중에**(`useState(notificationAgreementSupported)`) 이걸 부르므로, 던지면 미니앱 화면이
   * 통째로 하얘진다 = 브라우저 확인 루프가 첫 화면부터 죽는다.
   */
  it('SDK 주입 상수가 없어 던져도 미지원으로 접는다 — 렌더가 죽지 않게', () => {
    supportedMock.mockImplementation(() => {
      throw new TypeError("Cannot read properties of undefined (reading 'isNotificationRequestAgreementSupported')");
    });

    expect(notificationAgreementSupported()).toBe(false);
  });
});

/**
 * 전환 이벤트 래퍼 — 앱인토스 콘솔 「핵심 지표」가 고를 커스텀 이벤트를 쏜다.
 *
 * <p>계측 지점은 둘로 갈린다. ① **와이어 모양**: 콘솔 이벤트 선택기에 뜨는 이름이 `log_name`이고
 * 파라미터는 `params` 안에 들어가야 한다(SDK `EventLogParams`) — 여기가 어긋나면 에러 없이 조용히
 * 엉뚱한 이벤트가 쌓인다. ② **절대 던지지 않기**: 이 호출은 측정 시작·종료 성공 경로 한가운데에 있어
 * 실패가 새면 지표가 기능을 망가뜨린다. SDK는 동기 TypeError(브라우저엔 주입 상수가 없다)로도,
 * 거부된 Promise로도 실패하므로 둘 다 계측한다.
 */
describe('trackEvent', () => {
  it('log_type=event로 이름과 파라미터를 실어 보낸다 — 콘솔 선택기에 뜨는 이름이 이 값이다', () => {
    trackEvent('reading_session_completed', { duration_seconds: 1800 });

    expect(logMock).toHaveBeenCalledWith({
      log_type: 'event',
      log_name: 'reading_session_completed',
      params: { duration_seconds: 1800 },
    });
  });

  it('파라미터가 없으면 빈 params — params는 SDK가 요구하는 필수 필드다', () => {
    trackEvent('reading_session_started');

    expect(logMock).toHaveBeenCalledWith({ log_type: 'event', log_name: 'reading_session_started', params: {} });
  });

  it('SDK가 동기로 던져도 삼킨다 — 브라우저 목 모드엔 주입 상수가 없어 TypeError다', () => {
    logMock.mockImplementation(() => {
      throw new TypeError("Cannot read properties of undefined (reading 'log')");
    });

    expect(() => trackEvent('reading_session_started')).not.toThrow();
  });

  it('거부된 Promise에도 핸들러를 달아 둔다 — 발사 후 망각이라 아무도 안 받으면 unhandled rejection이 된다', () => {
    const rejected = Promise.reject(new Error('bridge failed'));
    // `process.on('unhandledRejection')`으로 재려다 실패했다: 워커 스레드라 그 이벤트가 안 잡혀
    // 돌연변이(핸들러 제거)가 살아남는 공허한 테스트가 됐다. 핸들러가 붙는지를 직접 본다.
    const attachHandler = vi.spyOn(rejected, 'catch');
    logMock.mockReturnValue(rejected);

    expect(() => trackEvent('reading_session_started')).not.toThrow();
    expect(attachHandler).toHaveBeenCalledTimes(1);

    rejected.catch(() => {}); // 단언이 실패해도 떠도는 거부를 러너에 남기지 않는다
  });
});

/**
 * 화면 진입 래퍼 — 퍼널(어디까지 왔나)을 콘솔 카탈로그에서 화면 단위로 읽게 하는 유일한 관측점이다.
 *
 * <p>{@link trackEvent}와 계측 지점이 같되 두 가지가 더 있다. ① **`log_type`이 `'screen'`**이어야 콘솔이
 * SCREEN 타입으로 분류한다(토스 자체 헬퍼가 `log_type`을 분류 키로 쓴다는 방증뿐이라 확정은 배포 후지만,
 * `'event'`로 새면 그때 전환 이벤트 4종과 한 통에 섞여 카탈로그가 무너진다). ② **`screen_` 접두사** —
 * 카탈로그 검색 한 번에 화면 로그 전부가 묶이는 규약이라, 접두가 빠지면 이름이 흩어진다.
 */
describe('trackScreen', () => {
  it('log_type=screen에 screen_ 접두를 붙여 보낸다 — 콘솔이 이 두 값으로 화면 로그를 묶는다', () => {
    trackScreen('home');

    expect(logMock).toHaveBeenCalledTimes(1);
    expect(logMock).toHaveBeenCalledWith({ log_type: 'screen', log_name: 'screen_home', params: {} });
  });

  it('SDK가 동기로 던져도 삼킨다 — 이 호출은 App 마운트 경로라 새면 앱이 통째로 안 뜬다', () => {
    logMock.mockImplementation(() => {
      throw new TypeError("Cannot read properties of undefined (reading 'log')");
    });

    expect(() => trackScreen('home')).not.toThrow();
  });

  it('거부된 Promise에도 핸들러를 달아 둔다 — 발사 후 망각이라 아무도 안 받으면 unhandled rejection이 된다', () => {
    const rejected = Promise.reject(new Error('bridge failed'));
    const attachHandler = vi.spyOn(rejected, 'catch');
    logMock.mockReturnValue(rejected);

    expect(() => trackScreen('home')).not.toThrow();
    expect(attachHandler).toHaveBeenCalledTimes(1);

    rejected.catch(() => {}); // 단언이 실패해도 떠도는 거부를 러너에 남기지 않는다
  });
});

/**
 * 「여백」 배너 래퍼 — 컴포넌트에 남는 건 `useEffect`와 ref뿐이고, 판단은 전부 여기 있다.
 *
 * <p>왜 이렇게 갈랐나: 이 하니스엔 jsdom이 없어 `useEffect`가 아예 안 돈다(T-149). 배너의 위험은
 * 죄다 effect 안쪽 — 초기화 순서·실패 접힘·언마운트 정리 — 이라, SDK를 목으로 잡을 수 있는
 * `toss.ts`로 내려야 계측이 가능하다.
 *
 * <p>계측 지점 셋: ① **자리를 만들 자격**(`marginBannerEnabled`) — 그룹 미설정·브라우저·구버전 토스앱을
 * 렌더 전에 거른다 ② **초기화 1회 계약** — 두 지면이 같은 memo를 공유해도 `initialize`는 한 번
 * ③ **죽음의 모든 갈래가 `setAlive(false)` 하나로 접히는가** — 안 접히면 빈 96px 구멍이 화면에 영구히 남는다.
 * <b>콜백 침묵까지</b> 포함한다: SDK 번들 전체에 `setTimeout`이 스크립트 로더 하나뿐이라(실측), 네이티브
 * 브릿지가 답을 안 주면 `onNoFill`도 `onAdFailedToRender`도 오지 않는다 — 우리 쪽 상한이 유일한 벨트다.
 */
describe('marginBannerEnabled — 자리를 만들 자격', () => {
  beforeEach(() => {
    tossAdsMock.attachBanner.isSupported.mockReset();
    tossAdsMock.attachBanner.isSupported.mockReturnValue(true);
  });

  it('그룹 ID가 비면 SDK를 아예 안 건드린다 — 점등 전 빌드·목 모드가 여기 걸린다', () => {
    expect(marginBannerEnabled('')).toBe(false);
    expect(tossAdsMock.attachBanner.isSupported).not.toHaveBeenCalled();
  });

  it('브라우저 밖(window 없음)이면 false — SDK가 window를 읽다 던진다', () => {
    vi.stubGlobal('window', undefined);

    expect(marginBannerEnabled('ait.dummy')).toBe(false);
    expect(tossAdsMock.attachBanner.isSupported).not.toHaveBeenCalled();
  });

  it('지원하는 토스앱이면 true, 미지원(구버전)이면 false — 구버전 「빈 화면」의 1차 방어', () => {
    expect(marginBannerEnabled('ait.dummy')).toBe(true);

    tossAdsMock.attachBanner.isSupported.mockReturnValue(false);
    expect(marginBannerEnabled('ait.dummy')).toBe(false);
  });

  it('isSupported가 동기로 던져도 false — 일반 브라우저엔 window는 있고 주입 상수만 없다', () => {
    tossAdsMock.attachBanner.isSupported.mockImplementation(() => {
      throw new TypeError("Cannot read properties of undefined (reading 'isAttachBannerSupported')");
    });

    expect(marginBannerEnabled('ait.dummy')).toBe(false);
  });
});

describe('attachMarginBanner — 초기화 후 부착, 실패는 전부 접힘', () => {
  /** 초기화 콜백을 붙잡아 둔다 — 「해소 전/후」를 갈라 봐야 순서 계약을 잴 수 있다. */
  let settleInit: { ok: () => void; fail: () => void };

  /**
   * 모듈을 새로 읽어 memo(`adsInitialized`)를 비운다 — 안 비우면 첫 테스트의 초기화 결과가
   * 뒤따르는 모든 테스트에 눌러앉아 실패 분기를 영영 못 밟는다.
   */
  async function freshAttach() {
    vi.resetModules();
    return (await import('./toss')).attachMarginBanner;
  }

  /** 마이크로태스크(초기화 Promise → attach)를 흘려보낸다. */
  const flush = () => new Promise((resolve) => setTimeout(resolve, 0));

  beforeEach(() => {
    tossAdsMock.initialize.mockReset();
    tossAdsMock.initialize.isSupported.mockReset();
    tossAdsMock.initialize.isSupported.mockReturnValue(true);
    tossAdsMock.attachBanner.mockReset();
    tossAdsMock.attachBanner.mockReturnValue({ destroy: vi.fn() });
    tossAdsMock.attachBanner.isSupported.mockReset();
    tossAdsMock.attachBanner.isSupported.mockReturnValue(true);
    settleInit = { ok: () => {}, fail: () => {} };
    tossAdsMock.initialize.mockImplementation((options) => {
      settleInit = {
        ok: () => options.callbacks?.onInitialized?.(),
        fail: () => options.callbacks?.onInitializationFailed?.(new Error('init failed')),
      };
    });
  });

  it('초기화가 끝나기 전에는 부착하지 않는다 — 초기화 전 attach의 동작이 문서에 없다', async () => {
    const attachMarginBanner = await freshAttach();

    attachMarginBanner('ait.margin', {} as HTMLElement, () => {});
    await flush();

    expect(tossAdsMock.attachBanner).not.toHaveBeenCalled();

    settleInit.ok();
    await flush();

    expect(tossAdsMock.attachBanner).toHaveBeenCalledTimes(1);
  });

  it('초기화 실패면 부착하지 않고 자리를 접는다 — 빈 96px 구멍이 남으면 안 된다', async () => {
    const attachMarginBanner = await freshAttach();
    const setAlive = vi.fn();

    attachMarginBanner('ait.margin', {} as HTMLElement, setAlive);
    settleInit.fail();
    await flush();

    expect(tossAdsMock.attachBanner).not.toHaveBeenCalled();
    expect(setAlive).toHaveBeenCalledWith(false);
  });

  it('두 지면이 연달아 붙어도 initialize는 1회 — 탭 왕복(사람축 ↔ 책축)이 정확히 이 시나리오다', async () => {
    const attachMarginBanner = await freshAttach();

    attachMarginBanner('ait.margin', {} as HTMLElement, () => {});
    settleInit.ok();
    await flush();
    attachMarginBanner('ait.book-margin', {} as HTMLElement, () => {});
    await flush();

    expect(tossAdsMock.initialize).toHaveBeenCalledTimes(1);
    expect(tossAdsMock.attachBanner).toHaveBeenCalledTimes(2);
  });

  it.each([['ait.margin'], ['ait.book-margin']])(
    '넘긴 그룹(%s)과 확정 옵션이 그대로 SDK로 간다 — 그룹이 어긋나면 정산이, 옵션이 어긋나면 디자인이 어긋난다',
    async (adGroupId) => {
      const attachMarginBanner = await freshAttach();
      const target = {} as HTMLElement;

      attachMarginBanner(adGroupId, target, () => {});
      settleInit.ok();
      await flush();

      const [passedGroup, passedTarget, options] = tossAdsMock.attachBanner.mock.calls[0];
      expect(passedGroup).toBe(adGroupId);
      expect(passedTarget).toBe(target);
      expect(options).toMatchObject({ theme: 'light', tone: 'grey', variant: 'card' });
    },
  );

  it.each([['onNoFill'], ['onAdFailedToRender']])(
    '%s면 자리를 접는다 — isSupported가 true로 새는 구버전을 받는 2차 벨트다',
    async (callback) => {
      const attachMarginBanner = await freshAttach();
      const setAlive = vi.fn();

      attachMarginBanner('ait.margin', {} as HTMLElement, setAlive);
      settleInit.ok();
      await flush();

      const options = tossAdsMock.attachBanner.mock.calls[0][2];
      expect(setAlive).not.toHaveBeenCalled(); // 접히기 전이 기준선이라 이 부정 단언은 공허하지 않다
      options.callbacks[callback]({ slotId: 's', adGroupId: 'ait.margin', adMetadata: {} });

      expect(setAlive).toHaveBeenCalledWith(false);
    },
  );

  it('부착 뒤 cleanup이면 슬롯을 destroy한다 — 탭 왕복의 유령 슬롯·누수 방어선', async () => {
    const attachMarginBanner = await freshAttach();
    const destroy = vi.fn();
    tossAdsMock.attachBanner.mockReturnValue({ destroy });

    const cleanup = attachMarginBanner('ait.margin', {} as HTMLElement, () => {});
    settleInit.ok();
    await flush();
    cleanup();

    expect(destroy).toHaveBeenCalledTimes(1);
  });

  it('초기화를 기다리는 중에 cleanup되면 부착 자체를 건너뛴다 — 빠른 탭 왕복의 경주', async () => {
    const attachMarginBanner = await freshAttach();

    const cleanup = attachMarginBanner('ait.margin', {} as HTMLElement, () => {});
    cleanup(); // 초기화가 끝나기 전에 화면이 갈렸다
    settleInit.ok();
    await flush();

    expect(tossAdsMock.attachBanner).not.toHaveBeenCalled();
  });

  /**
   * 렌더 상한 — 부착 <b>이후</b>의 침묵을 받는 벨트. 실측 근거: SDK 번들
   * (`@apps-in-toss/web-framework/dist/index.js`) 전체에서 `setTimeout`은 **스크립트 로더 하나뿐**이라
   * 광고 요청(`customAdFetcher` → 네이티브 브릿지) 자체엔 상한이 없다. 브릿지가 침묵하면 `onNoFill`도
   * `onAdFailedToRender`도 오지 않아 96px이 그 마운트 내내 빈 구멍으로 굳는다.
   */
  const payload = { slotId: 'slot-1', adGroupId: 'ait.margin', adMetadata: { creativeId: 'c', requestId: 'r' } };

  it('아무 콜백도 안 오면 상한에서 접는다 — 브릿지 침묵이 빈 구멍으로 굳지 않게', async () => {
    const attachMarginBanner = await freshAttach();
    const setAlive = vi.fn();
    vi.useFakeTimers();
    try {
      attachMarginBanner('ait.margin', {} as HTMLElement, setAlive);
      settleInit.ok();

      await vi.advanceTimersByTimeAsync(BANNER_RENDER_TIMEOUT_MS - 1);
      expect(setAlive).not.toHaveBeenCalled(); // 아직은 광고를 기다린다(기준선이 있으니 공허하지 않다)

      await vi.advanceTimersByTimeAsync(2);
      expect(setAlive).toHaveBeenCalledTimes(1);
      expect(setAlive).toHaveBeenCalledWith(false);
    } finally {
      vi.useRealTimers(); // 가짜 타이머가 새면 뒤따르는 테스트가 통째로 타임아웃 난다
    }
  });

  it('광고가 그려지면 상한을 걷는다 — 멀쩡히 뜬 배너를 상한이 뒤늦게 접으면 안 된다', async () => {
    const attachMarginBanner = await freshAttach();
    const setAlive = vi.fn();
    vi.useFakeTimers();
    try {
      attachMarginBanner('ait.margin', {} as HTMLElement, setAlive);
      settleInit.ok();
      await vi.advanceTimersByTimeAsync(0);

      // 상한이 **아직 살아 있는 상태**에서 그려진다 — 앞에 접힘이 있으면 그때 이미 걷혀서
      // 이 경로가 안 재진다(돌연변이 실측으로 잡은 사각: onNoFill 뒤에 재면 항상 통과한다).
      tossAdsMock.attachBanner.mock.calls[0][2].callbacks.onAdRendered(payload);
      expect(setAlive).toHaveBeenLastCalledWith(true);

      await vi.advanceTimersByTimeAsync(BANNER_RENDER_TIMEOUT_MS * 2);

      expect(setAlive).toHaveBeenCalledTimes(1); // 걷힌 상한이 뒤늦게 다시 접지 않는다
    } finally {
      vi.useRealTimers();
    }
  });

  it('접힌 뒤 자동 갱신이 채우면 자리를 되살린다 — 안 열면 안 보이는 광고의 노출만 집계된다', async () => {
    const attachMarginBanner = await freshAttach();
    const setAlive = vi.fn();

    attachMarginBanner('ait.margin', {} as HTMLElement, setAlive);
    settleInit.ok();
    await flush();
    const { callbacks } = tossAdsMock.attachBanner.mock.calls[0][2];

    callbacks.onNoFill({ slotId: 'slot-1', adGroupId: 'ait.margin', adMetadata: {} });
    expect(setAlive).toHaveBeenLastCalledWith(false);

    // 슬롯은 `autoLoad: true`로 계속 갱신한다(SDK 실측) — 접힌 채로 두면 나중에 채워진 광고가
    // `height:0` 뒤에서 렌더돼 사용자는 못 보는데 노출만 잡힌다(무효 트래픽).
    callbacks.onAdRendered(payload);

    expect(setAlive).toHaveBeenLastCalledWith(true);
  });

  it('cleanup은 상한 타이머도 걷는다 — 사라진 화면에 뒤늦게 접힘 신호를 쏘지 않는다', async () => {
    const attachMarginBanner = await freshAttach();
    const setAlive = vi.fn();
    vi.useFakeTimers();
    try {
      const cleanup = attachMarginBanner('ait.margin', {} as HTMLElement, setAlive);
      settleInit.ok();
      await vi.advanceTimersByTimeAsync(0);
      cleanup();

      await vi.advanceTimersByTimeAsync(BANNER_RENDER_TIMEOUT_MS * 2);

      expect(setAlive).not.toHaveBeenCalled();
    } finally {
      vi.useRealTimers();
    }
  });
});

/**
 * 네이티브 뒤로가기 구독 — 앱 밖(목 모드·일반 브라우저)에는 호스트 브리지가 없어 SDK가 던진다.
 * 그때도 **조용히 no-op 해제 함수**를 돌려줘야 한다: 이 구독은 `App`의 마운트 effect 한가운데라,
 * 실패가 새면 목 모드에서 앱이 통째로 안 뜬다(`trackEvent`·`openExternal`과 같은 사정).
 *
 * <p>이 파일의 SDK 목엔 `graniteEvent`가 없다 — 그래서 이 테스트는 **실제 실패 경로**를 탄다
 * (접근하는 순간 던진다). try/catch를 걷어내면 여기서 죽는다.
 */
describe('네이티브 뒤로가기 구독', () => {
  it('SDK가 없으면 조용히 no-op 해제 함수를 준다 — 목 모드에서 콘솔 에러 0', () => {
    const unsubscribe = subscribeNativeBack(() => {});

    expect(typeof unsubscribe).toBe('function');
    expect(() => unsubscribe()).not.toThrow();
  });
});
