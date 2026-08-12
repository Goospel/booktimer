import { Notification, loadFullScreenAd, showFullScreenAd } from '@apps-in-toss/web-framework';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { notificationAgreementSupported, requestNotificationAgreement, watchRewardAd } from './toss';

/**
 * 리워드 광고 래퍼 — SDK 이벤트 시퀀스를 "보상을 받았나"라는 boolean 하나로 접는다.
 *
 * <p>왜 테스트가 필요한가: 문서에 `userEarnedReward`와 `dismissed`의 **순서 계약이 없다**.
 * 그래서 플래그로 들고 있다가 `dismissed`에 확정하는 방식을 쓰는데, 그 방식이 두 순서 모두에서
 * 같은 답을 내는지가 이 기능의 신뢰 경계다(광고를 끝까지 안 본 유저에게 지급하면 안 된다).
 * 실기기 실측은 머지 게이트로 따로 남아 있고, 여기선 래퍼 로직만 격리 검증한다.
 */

vi.mock('@apps-in-toss/web-framework', () => ({
  loadFullScreenAd: vi.fn(),
  showFullScreenAd: vi.fn(),
  TossAuth: { login: vi.fn() },
  // requestAgreement는 "함수 + isSupported 프로퍼티"라 실물 모양 그대로 흉내 낸다.
  Notification: { requestAgreement: Object.assign(vi.fn(), { isSupported: vi.fn() }) },
}));

const loadMock = vi.mocked(loadFullScreenAd);
const showMock = vi.mocked(showFullScreenAd);
const agreementMock = vi.mocked(Notification.requestAgreement);
const supportedMock = vi.mocked(Notification.requestAgreement.isSupported);

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
  vi.stubGlobal('window', {}); // 래퍼가 브라우저 밖(window 없음)을 미지원으로 접으므로, 앱 안 상황을 만든다
});

describe('watchRewardAd', () => {
  it('시청 완료(userEarnedReward → dismissed)면 true', async () => {
    stubAd([{ type: 'show' }, { type: 'impression' }, { type: 'userEarnedReward', data: { unitType: 'day', unitAmount: 1 } }, { type: 'dismissed' }]);

    await expect(watchRewardAd('ad-group-1')).resolves.toBe(true);
  });

  it('순서가 뒤바뀌어도(dismissed 직전이 아니라 앞서 와도) 같은 답 — 문서에 순서 보장이 없다', async () => {
    stubAd([{ type: 'userEarnedReward', data: { unitType: 'day', unitAmount: 1 } }, { type: 'show' }, { type: 'dismissed' }]);

    await expect(watchRewardAd('ad-group-1')).resolves.toBe(true);
  });

  it('중간 이탈(userEarnedReward 없이 dismissed)이면 false — 지급하면 안 되는 경로', async () => {
    stubAd([{ type: 'show' }, { type: 'clicked' }, { type: 'dismissed' }]);

    await expect(watchRewardAd('ad-group-1')).resolves.toBe(false);
  });

  it('failedToShow면 reject', async () => {
    stubAd([{ type: 'requested' }, { type: 'failedToShow' }]);

    await expect(watchRewardAd('ad-group-1')).rejects.toThrow();
  });

  it('로드 실패(onError)면 reject하고 show를 부르지 않는다', async () => {
    loadMock.mockImplementation((params: LoadParams) => {
      params.onError(new Error('no fill'));
      return () => {};
    });

    await expect(watchRewardAd('ad-group-1')).rejects.toThrow('no fill');
    expect(showMock).not.toHaveBeenCalled();
  });

  it('load·show 모두 같은 adGroupId를 받는다 — 다른 그룹을 보여주면 정산이 어긋난다', async () => {
    stubAd([{ type: 'userEarnedReward', data: { unitType: 'day', unitAmount: 1 } }, { type: 'dismissed' }]);

    await watchRewardAd('reward-group-42');

    expect(loadMock.mock.calls[0][0].options).toEqual({ adGroupId: 'reward-group-42' });
    expect(showMock.mock.calls[0][0].options).toEqual({ adGroupId: 'reward-group-42' });
  });

  it('loaded 이전에는 show를 부르지 않는다 — 준비 안 된 광고를 노출하지 않는다', async () => {
    loadMock.mockImplementation(() => () => {});

    void watchRewardAd('ad-group-1');

    expect(showMock).not.toHaveBeenCalled();
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
