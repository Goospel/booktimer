import { loadFullScreenAd, showFullScreenAd } from '@apps-in-toss/web-framework';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { watchRewardAd } from './toss';

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
}));

const loadMock = vi.mocked(loadFullScreenAd);
const showMock = vi.mocked(showFullScreenAd);

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
