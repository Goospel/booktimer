import { Analytics, Notification, loadFullScreenAd, showFullScreenAd } from '@apps-in-toss/web-framework';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  BANNER_RENDER_TIMEOUT_MS,
  INTERSTITIAL_TIMEOUT_MS,
  marginBannerEnabled,
  notificationAgreementSupported,
  requestNotificationAgreement,
  showInterstitialAd,
  trackEvent,
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
  Analytics: { log: vi.fn() },
  TossAds: tossAdsMock,
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
 * 전면 광고 래퍼 — 「목표 바꾸기」 진입에 한 장. 보상은 없지만 **끝을 알려 준다**(Promise).
 *
 * <p>발사 후 망각이던 것을 2026-08-14에 바꿨다: 부르는 쪽이 기다리지 않으면 화면이 먼저 전환돼,
 * 1~2초 뒤 로드가 끝난 광고가 **그때 떠 있는 아무 화면 위에** 덮였다(실기기 실측 — 목표 화면에서
 * 빠져나온 뒤 메인에서 터졌다). 노출 시점이 로드 속도에 좌우되는 경주 상태였다.
 *
 * <p>계측 지점: ① config-gate(그룹 ID가 비면 SDK를 아예 안 건드린다 — 구글 반영 전·브라우저 목 모드)
 * ② 넘긴 그룹으로 load·show가 나가는가(다른 그룹이면 정산이 어긋난다) ③ **어떤 실패에도 resolve하는가** —
 * 이 호출은 화면 전환 앞을 막고 있어서, 안 끝나면 목표 화면 진입 자체가 영영 막힌다.
 */
describe('showInterstitialAd', () => {
  it('그룹 ID가 비면 SDK를 건드리지 않는다 — 구글 반영 전 빌드·브라우저 목 모드가 여기 걸린다', () => {
    showInterstitialAd('');

    expect(loadMock).not.toHaveBeenCalled();
  });

  it('그룹 ID가 있으면 그 그룹으로 load·show를 부른다', () => {
    stubAd([{ type: 'show' }, { type: 'dismissed' }]);

    showInterstitialAd('interstitial-group-7');

    expect(loadMock.mock.calls[0][0].options).toEqual({ adGroupId: 'interstitial-group-7' });
    expect(showMock.mock.calls[0][0].options).toEqual({ adGroupId: 'interstitial-group-7' });
  });

  // 노 필·미등록 그룹은 흔한 정상 경로다. 여기가 새면 광고가 안 붙는 날 목표 화면에 못 들어간다.
  // (거부된 Promise에 핸들러가 붙는지는 이 하니스로 못 잰다 — 워커 스레드라 unhandledRejection이
  //  안 잡힌다. trackEvent 쪽에서 실측한 함정이라 같은 공허한 단언을 다시 쓰지 않는다.)
  it('로드가 실패해도(onError) 던지지 않는다 — 광고 실패가 화면 전환을 막으면 안 된다', () => {
    loadMock.mockImplementation((params: LoadParams) => {
      params.onError(new Error('no fill'));
      return () => {};
    });

    expect(() => showInterstitialAd('interstitial-group-7')).not.toThrow();
  });

  it('SDK가 동기로 던져도 삼킨다 — 토스앱 밖엔 주입 상수가 없어 TypeError다', () => {
    loadMock.mockImplementation(() => {
      throw new TypeError("Cannot read properties of undefined (reading 'loadFullScreenAd')");
    });

    expect(() => showInterstitialAd('interstitial-group-7')).not.toThrow();
  });

  // --- 끝을 알린다 (2026-08-14) — 부르는 쪽이 광고가 닫힌 뒤에 화면을 전환할 수 있어야 한다 ---

  it('광고가 닫히면(dismissed) resolve한다 — 이 시점이 곧 "이제 화면 전환해도 된다"', async () => {
    stubAd([{ type: 'show' }, { type: 'dismissed' }]);

    await expect(showInterstitialAd('interstitial-group-7')).resolves.toBeUndefined();
  });

  it('닫히기 전에는 resolve하지 않는다 — 이게 깨지면 광고가 화면 위로 덮이던 옛 버그로 되돌아간다', async () => {
    // show까지 갔지만 dismissed는 아직 안 왔다(사용자가 광고를 보는 중).
    loadMock.mockImplementation((params: LoadParams) => {
      params.onEvent({ type: 'loaded' });
      return () => {};
    });
    showMock.mockImplementation((params: ShowParams) => {
      params.onEvent({ type: 'show' } as never);
      return () => {};
    });

    let settled = false;
    void showInterstitialAd('interstitial-group-7').then(() => {
      settled = true;
    });
    await Promise.resolve();
    await Promise.resolve();

    expect(settled).toBe(false);
  });

  it('그룹 ID가 비면 즉시 resolve — 광고가 없는 빌드에서 진입이 막히면 안 된다', async () => {
    await expect(showInterstitialAd('')).resolves.toBeUndefined();
  });

  it('로드 실패(onError)에도 resolve — 노 필인 날 목표 화면에 못 들어가면 안 된다', async () => {
    loadMock.mockImplementation((params: LoadParams) => {
      params.onError(new Error('no fill'));
      return () => {};
    });

    await expect(showInterstitialAd('interstitial-group-7')).resolves.toBeUndefined();
  });

  it('SDK가 동기로 던져도 resolve — 토스앱 밖(브라우저)에서도 진입은 살아 있어야 한다', async () => {
    loadMock.mockImplementation(() => {
      throw new TypeError("Cannot read properties of undefined (reading 'loadFullScreenAd')");
    });

    await expect(showInterstitialAd('interstitial-group-7')).resolves.toBeUndefined();
  });

  it('SDK가 아무 콜백도 안 부르면 타임아웃으로 resolve — 진입이 영구히 막히는 것이 최악이다', async () => {
    vi.useFakeTimers();
    try {
      loadMock.mockImplementation(() => () => {}); // 로드가 영영 끝나지 않는다

      let settled = false;
      void showInterstitialAd('interstitial-group-7').then(() => {
        settled = true;
      });

      await vi.advanceTimersByTimeAsync(INTERSTITIAL_TIMEOUT_MS - 1);
      expect(settled).toBe(false); // 아직은 광고를 기다린다
      await vi.advanceTimersByTimeAsync(2);
      expect(settled).toBe(true);
    } finally {
      // 실패해도 반드시 되돌린다 — 가짜 타이머가 새면 뒤따르는 테스트가 통째로 타임아웃 난다.
      vi.useRealTimers();
    }
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
   * `onAdFailedToRender`도 오지 않아 96px이 그 마운트 내내 빈 구멍으로 굳는다 — 전면 광고가 이미
   * {@link INTERSTITIAL_TIMEOUT_MS}로 같은 실패 모드를 막고 있다.
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
