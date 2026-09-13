/**
 * 세션 캐시 — 화면이 언마운트돼도 마지막 성공 응답을 들고 있다가, 재마운트 첫 렌더에 내준다(SWR).
 *
 * <p><b>요청을 아끼는 캐시가 아니다</b> — fetch는 지금처럼 매번 나가고, 이 캐시는 그 왕복 동안
 * `<Loading/>`을 안 그리게만 한다. 그래서 stale 노출이 최대 1 RTT다.
 *
 * <p>모듈 메모리라 새로고침·앱 재시작이 곧 초기화다(dev-mock과 같은 수명). 에러 응답은 절대 넣지
 * 않는다 — 넣는 자리는 각 fetch의 성공 경로뿐이라, 실패가 캐시로 고착될 길이 없다.
 */

// ponytail: 크기 상한 없음 — 한 세션에 방문한 책·프로필 수 × 수 KB라 WebView 세션 수명에서 무해.
// 실사용에서 문제가 실측되면 LRU로 승격한다.
const store = new Map<string, unknown>();

/** 캐시 키 — 문자열 리터럴이 화면마다 흩어지지 않게 여기 모은다. */
export const CACHE_SHELF = 'shelf';
/** 공부 서재 — 독서 서재와 <b>다른 키</b>다(원장이 갈린 만큼 캐시도 갈린다). */
export const CACHE_STUDY_SHELF = 'study-shelf';
export const CACHE_FEED = 'home-feed';
export const CACHE_HISTORY = 'history';
/**
 * 공부 기록 — 독서 `CACHE_HISTORY`와 <b>다른 키</b>다. 공유하면 모드를 바꾼 직후 첫 렌더에
 * 상대 모드의 기록이 선다(두 화면 모두 캐시를 초기 state로 쓴다).
 */
export const CACHE_STUDY_HISTORY = 'study-history';
export const cacheKeyProfile = (loginId: string): string => `profile:${loginId}`;
export const cacheKeyProfileBooks = (loginId: string): string => `profile-books:${loginId}`;
export const cacheKeyMargin = (loginId: string, bookId: number): string => `margin:${loginId}:${bookId}`;

export function cacheGet<T>(key: string): T | undefined {
  return store.get(key) as T | undefined;
}

export function cachePut(key: string, value: unknown): void {
  store.set(key, value);
}

/** 접두사 일치를 전부 버린다 — 여백 쓰기처럼 「이 종류 전부 낡았다」를 아는 자리에서 쓴다. */
export function cacheDrop(prefix: string): void {
  for (const key of store.keys()) if (key.startsWith(prefix)) store.delete(key);
}

/** 전부 버린다 — 로그아웃·401·탈퇴(`token.clear`) 전용. 테스트 `beforeEach`에서도 쓴다. */
export function cacheClear(): void {
  store.clear();
}
