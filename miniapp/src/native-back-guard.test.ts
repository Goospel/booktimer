import { readFileSync } from 'node:fs';

import { describe, expect, it } from 'vitest';

import { sourceFiles, stripComments } from './source-scan';

/**
 * 자체 뒤로가기를 <b>다시 세우지 않는다</b> — 나가는 길은 토스 네이티브 내비게이션 바 하나다.
 *
 * <p>2026-09-02 심사 반려(T-220): 「내비게이션 바의 뒤로가기 버튼과 미니앱 자체 헤더 및 뒤로가기 버튼이
 * 함께 노출돼요」. <a href="https://developers-apps-in-toss.toss.im/checklist/app-nongame">비게임 출시
 * 체크리스트</a>의 <b>필수</b> 항목이라 판정 편차가 아니다 — 08-16에 알약을 세울 때(#830·#831) 체크리스트를
 * 대조하지 않았고, 그 뒤 열 번 통과한 것이 「허용」으로 읽혔다.
 *
 * <p><b>왜 소스 가드인가</b>: 화면별 렌더 테스트는 <b>그 화면</b>에만 걸린다. 이 규칙은 앱 전체에 걸리므로
 * 새 화면이 알약을 하나 더 세우면 아무 테스트도 안 죽는다 — 「새로 쓰는 손」을 막는 값이다(T-219 교훈).
 *
 * <p>주석은 걷는다(`stripComments`) — 이 반려의 경위를 설명하는 주석에 그 단어가 들어가는 것은 규칙 위반이
 * 아니다. 잡는 것은 <b>마크업에 실려 화면에 뜨는 리터럴</b>이다.
 *
 * <p>보지 않는 것: 「이전」·「뒤로」 같은 다른 문구(2026-09-02 실측 0건 — 지금 쓰는 말이 아닌데 먼저 넣으면
 * 가드가 아니라 소음이다) · `.ts`(화면 문구는 `.tsx`에 산다).
 */
describe('제품 화면에 자체 뒤로가기 문구를 세우지 않는다 (T-220)', () => {
  const root = new URL('.', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');
  const files = sourceFiles(root);

  /** 계측기 자체의 판별력 — 겨눈 꼴은 잡고, 주석은 놓아줘야 가드다(T-212: 대상을 안 잡아도 초록인 계측기). */
  it('계측기가 겨눈 꼴을 잡는다 — 마크업에 실린 리터럴', () => {
    expect(stripComments(`<Button onClick={onSkip}>돌아가기</Button>`)).toContain('돌아가기');
  });

  it('계측기가 주석은 놓아준다 — 경위를 적은 글까지 막으면 기록을 못 남긴다', () => {
    expect(stripComments(`  // 나가는 길은 네이티브다 — 옛 「돌아가기」 알약은 걷었다\n<div />`)).not.toContain(
      '돌아가기',
    );
    expect(stripComments(`/** 옛 「돌아가기」 알약(#830)은 걷었다 */\n<div />`)).not.toContain('돌아가기');
  });

  it('스캔 대상을 실제로 찾았다 — 0개면 늘 통과하는 빈 가드다', () => {
    expect(files.length).toBeGreaterThan(10);
  });

  it('제품 소스의 어느 화면도 「돌아가기」를 그리지 않는다', () => {
    const hits = files
      .filter((file) => stripComments(readFileSync(file, 'utf8')).includes('돌아가기'))
      .map((file) => file.split(/[\\/]/).slice(-2).join('/'));

    expect(
      hits,
      `자체 뒤로가기는 토스 네이티브 내비게이션 바와 중복이라 심사 필수 항목에 걸린다(T-220):\n${hits.join('\n')}`,
    ).toEqual([]);
  });
});

/**
 * `backEvent` 구독은 **앱에 하나뿐**이어야 한다 — 리스너가 여럿이면 한 번의 back에 서브뷰가 여럿 닫힌다
 * (`back.ts`의 모듈 popstate 리스너와 같은 이유). `back.ts`·`App.tsx` 주석이 이 불변식을 <b>선언만</b>
 * 하고 있어서 계측기를 붙인다.
 *
 * <p>정적 렌더 하니스라 effect가 안 돌아(T-149) 「구독이 몇 번 걸렸나」를 렌더로 볼 수 없다. 그래서
 * 이 레포 관례대로 <b>소스의 건수</b>로 잰다 — 존재 단언은 복제를 못 잡는다(T-218).
 */
describe('네이티브 뒤로가기 구독은 앱에 하나뿐이다', () => {
  it('구독은 앱에 하나뿐이다 — 여럿이면 한 번의 back에 서브뷰가 여럿 닫힌다', () => {
    const src = readFileSync(new URL('./App.tsx', import.meta.url), 'utf8');

    expect(src.match(/subscribeNativeBack\(nativeBack\)/g)).toHaveLength(1);
  });
});
