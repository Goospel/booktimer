import { TossAuth, loadFullScreenAd, showFullScreenAd } from '@apps-in-toss/web-framework';

/**
 * 콘솔에서 발급받은 리워드 광고 그룹 ID. **빈 값이면 광고 기능 전체가 꺼진다**(config-gate) —
 * 광고 그룹 생성 후 구글 등록까지 최대 2시간이 걸리므로, 그 전 빌드에서도 버튼이 안 뜨게 해야 안전하다.
 * 전면형·리워드형은 같은 API를 쓰고 타입은 이 그룹 ID로 결정된다.
 */
export const REWARD_AD_GROUP_ID: string = import.meta.env.VITE_REWARD_AD_GROUP_ID ?? '';

/** `appLogin()`이 주는 것 — 서버 `TossAuthRequest`와 같은 모양이라 그대로 실어 보낸다. */
export interface TossLoginResult {
  authorizationCode: string;
  referrer: 'DEFAULT' | 'SANDBOX';
}

/**
 * 토스 로그인 — 인가코드는 10분·일회성이라 인증이 필요한 시점마다 새로 부른다(무마찰).
 *
 * <p>SDK 3.x에서 최상위 `appLogin()`은 deprecated이고 `TossAuth.login`이 같은 시그니처의 후속이다.
 * 여기 한 함수로 감싸 둬서 SDK가 또 바뀌어도 고칠 곳이 한 줄이다.
 */
export function tossLogin(): Promise<TossLoginResult> {
  return TossAuth.login();
}

/**
 * 리워드 광고 1회 시청 — `true`=보상 조건 충족(끝까지 봄), `false`=중간 이탈.
 *
 * <p>보상 시점 신호는 `userEarnedReward` 클라이언트 이벤트뿐이다(SDK에 서버사이드 보상 검증이 없다).
 * 그런데 **문서에 `userEarnedReward`와 `dismissed`의 순서 계약이 없어서**, 플래그로 들고 있다가
 * 광고가 닫히는 `dismissed`에서 확정한다 — 두 순서 어느 쪽이 와도 같은 답이 나온다.
 *
 * <p>사전 로드 최적화(버튼 노출 시 미리 load)는 하지 않는다 — 클릭 시 로드로 시작하고,
 * 로드 지연이 실측으로 거슬리면 그때 옮긴다.
 */
export function watchRewardAd(adGroupId: string): Promise<boolean> {
  return new Promise((resolve, reject) => {
    let rewarded = false;
    loadFullScreenAd({
      options: { adGroupId },
      onError: reject,
      // LoadFullScreenAdEvent는 'loaded' 하나뿐이라 따로 분기하지 않는다.
      onEvent: () => {
        showFullScreenAd({
          options: { adGroupId },
          onError: reject,
          onEvent: (event) => {
            if (event.type === 'userEarnedReward') rewarded = true;
            else if (event.type === 'dismissed') resolve(rewarded);
            else if (event.type === 'failedToShow') reject(new Error('광고를 표시하지 못했어요'));
          },
        });
      },
    });
  });
}
