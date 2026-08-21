import { beforeEach, describe, expect, it } from 'vitest';

import {
  CACHE_FEED,
  CACHE_SHELF,
  cacheClear,
  cacheDrop,
  cacheGet,
  cacheKeyMargin,
  cacheKeyProfile,
  cacheKeyProfileBooks,
  cachePut,
} from './cache';

/**
 * 세션 캐시 — 화면이 언마운트돼도 마지막 성공 응답을 들고 있다가 재마운트 첫 렌더에 내준다(SWR).
 * 모듈 메모리라 테스트 간에도 새므로, 캐시를 만지는 파일은 전부 `beforeEach(cacheClear)`가 규율이다.
 */
beforeEach(cacheClear);

describe('세션 캐시', () => {
  it('넣은 값을 그대로 돌려준다 — 캐시가 하는 일의 전부다', () => {
    cachePut(CACHE_SHELF, { books: ['데미안'] });

    expect(cacheGet<{ books: string[] }>(CACHE_SHELF)).toEqual({ books: ['데미안'] });
  });

  it('없는 키는 undefined — 「값이 없다」와 「null이 캐시됐다」가 섞이지 않는다', () => {
    expect(cacheGet(CACHE_FEED)).toBeUndefined();
  });

  it('접두사 드롭은 그 종류만 버린다 — 여백을 쓰면 여백 스냅만 낡는다', () => {
    cachePut(cacheKeyMargin('me', 1), 'A');
    cachePut(cacheKeyMargin('you', 2), 'B');
    cachePut(CACHE_SHELF, 'shelf');

    cacheDrop('margin:');

    expect(cacheGet(cacheKeyMargin('me', 1))).toBeUndefined();
    expect(cacheGet(cacheKeyMargin('you', 2))).toBeUndefined();
    expect(cacheGet(CACHE_SHELF)).toBe('shelf');
  });

  it('전체 비우기는 하나도 안 남긴다 — 로그아웃 뒤 남의 데이터가 첫 렌더에 서면 안 된다', () => {
    cachePut(CACHE_SHELF, 'shelf');
    cachePut(cacheKeyProfile('me'), 'profile');
    cachePut(cacheKeyMargin('me', 1), 'margin');

    cacheClear();

    expect(cacheGet(CACHE_SHELF)).toBeUndefined();
    expect(cacheGet(cacheKeyProfile('me'))).toBeUndefined();
    expect(cacheGet(cacheKeyMargin('me', 1))).toBeUndefined();
  });

  it('키 헬퍼는 접두사를 공유한다 — 드롭 한 번이 그 종류를 다 잡는 근거다', () => {
    expect(cacheKeyMargin('me', 1)).toBe('margin:me:1');
    expect(cacheKeyProfile('me')).toBe('profile:me');
    expect(cacheKeyProfileBooks('me')).toBe('profile-books:me');
  });

  it('사용자가 다르면 키도 다르다 — 남의 책방 캐시가 내 화면에 서면 안 된다', () => {
    cachePut(cacheKeyProfile('me'), 'mine');

    expect(cacheGet(cacheKeyProfile('you'))).toBeUndefined();
  });
});
