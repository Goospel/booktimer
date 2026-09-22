import { readFileSync } from 'node:fs';

import { describe, expect, it } from 'vitest';

import { openingTags, sourceFiles, stripComments } from './source-scan';

/**
 * `<Profile>`의 <b>React 정체성은 loginId다</b> — 그래서 호출부마다 `key={loginId}`를 단다.
 *
 * <p><b>무엇이 고장났었나</b>(2026-09-22 목 모드 실측): 남의 책방(`nabi`)에서 상태 칩 「다 읽음」을 누르고
 * 뒤로 나오면, 그 자리에 선 <b>내 책방</b>이 `다 읽음` 눌린 채로 서고 소제목이 「다 읽음 3」인데 격자에는
 * <b>전체 3권</b>이 그대로 있었다 — 칩이 남은 게 아니라 <b>라벨이 내용과 모순</b>됐다.
 *
 * <p><b>왜 그랬나</b>: 「두 분기의 `<Profile>`은 서로 다른 JSX라 재마운트된다」가 틀렸다. 책방 셸은
 * 남의 책방을 <b>이른 return</b>(`<Profile/>` 단독)으로, 내 책방을 <b>프래그먼트</b>(`<>…</>`) 안에서
 * 그리는데, React는 <b>키 없는 최상위 프래그먼트를 배열로 풀어서</b> 맞춘다. 그러면 양쪽 다 「index 0,
 * key=null, type=Profile」이라 <b>같은 fiber로 이어지고</b> 상태가 살아남는다.
 *
 * <p><b>왜 각 상태를 비우는 대신 key인가</b>: `Profile`은 <b>마운트 시점에만 도는 초기화</b>에 기대어 설계됐다
 * — `useState(() => cacheGet(cacheKeyProfile(loginId)))`가 그 사람의 세션 캐시를 첫 렌더에 깔아 준다.
 * fiber가 이어지면 그 초기화가 새 loginId로 다시 돌지 않아, 재조회 1 RTT 동안 <b>앞사람의 헤더·격자</b>가
 * 새 사람 화면에 남는다. 필터 둘을 `load()`에서 비우는 처방은 그 뿌리를 안 건드리고 증상만 둘 덮는다
 * (마운트 스코프 상태는 지금도 열 개가 넘고, 새로 늘 때마다 잊는다). `key`는 React가 「정체성이 바뀌면
 * 상태를 통째로 버린다」고 내준 기본 수단이고, 이 레포엔 이미 선례가 있다 — `<BookMargin key={marginEpoch}>`,
 * `<MainTabs key={composeEpoch}>`.
 *
 * <p><b>⚠️ 이 가드가 확정하는 것과 아닌 것</b>(「통과는 증거가 아니다」): 통과하면 <b>모든 `<Profile>`
 * 호출부가 key를 달고 있고 그 값이 loginId와 같은 식</b>임이 확정된다. 실패하면 <b>어느 호출부가 그 규칙을
 * 어겼는지</b>가 배제된다. <b>「React가 실제로 재마운트한다」는 확정하지 못한다</b> — 이 하니스는
 * `renderToStaticMarkup`(jsdom 없음)이라 마운트/언마운트 전이를 원리상 계측할 수 없다. 그 판정은 목 모드
 * 실측이 진다(PR body의 재현 절차 · `aria-pressed` 수치).
 */

/**
 * 이 호출부가 규칙을 어겼는가 — 어겼으면 이유, 지켰으면 `null`.
 *
 * <p>`key`·`loginId` 둘 다 <b>중괄호 한 겹</b> 프롭만 본다. `onOpenMargin={(bookId) =>
 * setMargin({ loginId: open, bookId })}`처럼 <b>객체 속 `loginId:`</b>는 `=` 뒤 `{`가 아니라 안 걸린다.
 */
export function identityMismatch(tag: string): string | null {
  const key = tag.match(/(?:^|\s)key=\{([^{}]+)\}/)?.[1].trim() ?? null;
  const loginId = tag.match(/(?:^|\s)loginId=\{([^{}]+)\}/)?.[1].trim() ?? null;
  if (loginId === null) return 'loginId 프롭이 없다';
  if (key === null) return `key가 없다 (loginId={${loginId}})`;
  return key === loginId ? null : `key={${key}} ≠ loginId={${loginId}}`;
}

describe('<Profile>의 정체성은 loginId다 — 호출부마다 key={loginId}', () => {
  const root = new URL('.', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');
  const tags = sourceFiles(root).flatMap((file) =>
    openingTags(stripComments(readFileSync(file, 'utf8')), 'Profile').map((tag) => ({ file, tag })),
  );

  /** 계측기 자체의 판별력 — 겨눈 꼴을 잡아야 가드다(T-212: 대상을 안 잡아도 초록인 계측기). */
  it('계측기가 겨눈 꼴을 잡는다 — key 없음 · key와 loginId 불일치', () => {
    expect(identityMismatch(`<Profile loginId={open} onBack={() => setOpen(null)} />`)).toBe(
      'key가 없다 (loginId={open})',
    );
    expect(identityMismatch(`<Profile key={handle} loginId={open} />`)).toBe('key={handle} ≠ loginId={open}');
  });

  it('계측기가 옳은 꼴은 놓아준다 — 객체 속 loginId:에도 안 속는다', () => {
    expect(
      identityMismatch(`<Profile key={open} loginId={open} onOpenMargin={(bookId) => setMargin({ loginId: open, bookId })} />`),
    ).toBeNull();
  });

  it('태그 추출이 <ProfileCard>를 안 집는다 — 이름 경계', () => {
    expect(openingTags(`<ProfileCard loginId={x} profile={p} />`, 'Profile')).toEqual([]);
    expect(openingTags(`<Profile loginId={x} />`, 'Profile')).toHaveLength(1);
  });

  it('스캔 대상을 실제로 찾았다 — 0개면 늘 통과하는 빈 가드다', () => {
    expect(tags.length).toBeGreaterThanOrEqual(3);
  });

  it('제품 소스의 모든 <Profile> 호출부가 key={loginId}를 단다', () => {
    const hits = tags
      .map(({ file, tag }) => ({ file, tag, why: identityMismatch(tag) }))
      .filter(({ why }) => why !== null)
      .map(({ file, why }) => `${file.split(/[\\/]/).slice(-2).join('/')}  ${why}`);

    expect(
      hits,
      `<Profile>이 key 없이 서면 loginId가 바뀌어도 같은 fiber로 이어져 앞사람의 상태가 남는다:\n${hits.join('\n')}`,
    ).toEqual([]);
  });
});
