import {
  Analytics,
  Device,
  type LogParam,
  Notification,
  TossAuth,
  loadFullScreenAd,
  showFullScreenAd,
} from '@apps-in-toss/web-framework';

/**
 * 전환 이벤트 1건 발사 — 앱인토스 콘솔 「핵심 지표」가 고를 커스텀 이벤트를 남긴다(발사 후 망각).
 *
 * <p>콘솔의 전환 지표 「직접 조합하기」는 **실제로 발생한 적 있는 이벤트 목록**에서만 고를 수 있어,
 * 쏘기 시작해야 비로소 선택기에 나타난다. `params`는 SDK가 `anonymous_key`를 얹고 `undefined`를
 * 걸러 문자열로 정규화하며, 미지원 토스앱 버전(5.208.0 미만)에서는 조용히 무시된다.
 *
 * <p>**절대 던지지 않는다.** 이 호출은 측정 시작·종료 성공 경로 한가운데 있어서, 실패가 새면 지표가
 * 기능을 망가뜨린다. SDK는 두 가지로 실패한다 — 브라우저 dev 목 모드엔 호스트 주입 상수가 없어
 * **동기 TypeError**(`notificationAgreementSupported`의 가드와 같은 사정), 앱 안에서도 브릿지가 죽으면
 * **거부된 Promise**(아무도 안 받으니 unhandled rejection). try와 catch가 각각 그 하나씩을 맡는다.
 */
export function trackEvent(logName: string, params: Record<string, LogParam> = {}): void {
  try {
    void Analytics.log({ log_type: 'event', log_name: logName, params }).catch(() => {});
  } catch {
    // 지표 실패는 사용자에게 아무 의미가 없다 — 조용히 버린다.
  }
}

/**
 * 콘솔에서 발급받은 리워드 광고 그룹 ID. **빈 값이면 광고 기능 전체가 꺼진다**(config-gate) —
 * 광고 그룹 생성 후 구글 등록까지 최대 2시간이 걸리므로, 그 전 빌드에서도 버튼이 안 뜨게 해야 안전하다.
 * 전면형·리워드형은 같은 API를 쓰고 타입은 이 그룹 ID로 결정된다.
 */
export const REWARD_AD_GROUP_ID: string = import.meta.env.VITE_REWARD_AD_GROUP_ID ?? '';

/**
 * 「목표 바꾸기」 진입에 띄우는 **전면형** 광고 그룹 ID — 리워드와 다른 그룹이라 성과·정산이 분리된다.
 * 리워드와 같은 config-gate: 빈 값이면 안 뜬다(구글 반영 대기 중인 빌드·브라우저 목 모드).
 */
export const INTERSTITIAL_AD_GROUP_ID: string = import.meta.env.VITE_INTERSTITIAL_AD_GROUP_ID ?? '';

/**
 * 알림 동의문의 콘솔 발송(템플릿) 코드. 캠페인 2종(완독 축하·하루 목표 달성)이 **같은 동의문 한 장**을
 * 쓰므로 한 번만 물어보면 둘 다 커버된다 — 동의 단위는 캠페인이 아니라 동의문이다.
 */
export const GOAL_MET_TEMPLATE_CODE = 'booktimer-daily-goal-met';

/** 동의 화면의 세 가지 결말 — 동의 상태의 정본은 토스이고, 우리는 이 값만 캐시해 카드 노출을 끈다. */
export type AgreementResult = 'newAgreement' | 'alreadyAgreed' | 'agreementRejected';

/**
 * 알림 동의 API를 쓸 수 있는 토스앱인가(5.255.0+). 구버전엔 눌러도 아무 일 없는 버튼을 띄우지 않는다.
 *
 * <p>`window` 확인이 앞에 있는 이유: SDK가 앱 버전을 `window`에서 읽어 브라우저 밖(테스트의 정적 렌더)에선
 * 던진다. 홈이 렌더 중에 이걸 부르므로, 없으면 "미지원"으로 접어 화면이 통째로 깨지지 않게 한다.
 *
 * <p>`try`가 필요한 이유는 한 겹 더 있다: 실물 `isSupported()`는 호스트가 주입하는
 * `window.__appsInTossConstants`를 읽는데, **일반 브라우저에는 `window`는 있고 그 상수만 없다**(dev 목
 * 모드가 정확히 이 상황) — `typeof window` 가드를 통과한 뒤 TypeError로 렌더가 죽는다. 미지원으로 접는다.
 */
export function notificationAgreementSupported(): boolean {
  if (typeof window === 'undefined') return false;
  try {
    return Notification.requestAgreement.isSupported();
  } catch {
    return false;
  }
}

/**
 * 알림 동의 화면 요청 — 미지원 토스앱이면 화면을 띄우지 않고 즉시 `null`.
 *
 * <p>결과를 받으면 **반드시 cleanup**을 불러 앱브릿지 콜백을 해제해야 한다(SDK 문서 요구).
 * 콜백이 동기로 올 수도 있어 cleanup이 아직 할당 전일 수 있으므로, 플래그로 해제 시점을 맞춘다.
 */
export function requestNotificationAgreement(templateCode: string): Promise<AgreementResult | null> {
  if (!notificationAgreementSupported()) return Promise.resolve(null);
  return new Promise((resolve, reject) => {
    let cleanup: (() => void) | null = null;
    let settled = false;
    const release = () => {
      settled = true;
      cleanup?.();
    };
    cleanup = Notification.requestAgreement({
      options: { templateCode },
      onEvent: (result) => {
        release();
        resolve(result.type);
      },
      onError: (error) => {
        release();
        reject(error);
      },
    });
    if (settled) cleanup(); // 동기 콜백이었다면 여기서 해제한다
  });
}

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
 * 외부 주소 열기 — 기기의 기본 브라우저·관련 앱으로 넘긴다(홈 피드의 「책 뉴스」 줄).
 *
 * <p>최상위 `openURL`은 deprecated라 `Device.openURL`을 쓴다. 실패 두 갈래를 `trackEvent`·
 * `showInterstitialAd`와 같은 모양으로 처리하되, 여기서는 **삼키는 대신 브라우저로 떨어뜨린다** —
 * 목 모드(SDK 없음)에서 뉴스 줄이 죽은 링크가 되면 브라우저 확인 자체가 불가능해진다.
 */
export function openExternal(url: string): void {
  const fallback = () => void window.open(url, '_blank', 'noopener');
  try {
    // 앱 밖에서는 SDK가 **동기 TypeError**(try가 받는다), 앱 안 브릿지 실패는 **거부된 Promise**(catch가 받는다).
    void Device.openURL(url).catch(fallback);
  } catch {
    fallback();
  }
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

/**
 * 전면 광고 1회 — **발사 후 망각**(결과도 보상도 없다). 부르는 쪽은 기다리지 않고 화면을 전환한다:
 * 로드에 1~2초가 걸려서, 기다리면 탭이 먹통으로 느껴지고 로드가 막히면 진입 자체가 막힌다.
 */
export function showInterstitialAd(adGroupId: string = INTERSTITIAL_AD_GROUP_ID): void {
  if (adGroupId === '') return;
  try {
    // 전면형은 `userEarnedReward`를 안 쏘므로 리워드 래퍼를 그대로 쓰고 결과(false)만 버린다.
    // 실패 두 갈래를 둘 다 삼킨다 — 앱 밖에서는 SDK가 **동기 TypeError**(try가 받는다),
    // 앱 안에서는 노 필·미등록 그룹이 **거부된 Promise**(catch가 받는다. 안 붙이면 unhandled rejection).
    void watchRewardAd(adGroupId).catch(() => {});
  } catch {
    // 광고가 안 뜬 걸 사용자에게 알릴 이유가 없다 — 조용히 넘어가고 화면은 그대로 전환된다.
  }
}
