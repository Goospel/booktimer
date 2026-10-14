import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

import { sourceFiles, stripComments } from './source-scan';

/**
 * 광고는 <b>누르기 전에</b> 불러 둔다 — 로드 호출은 `toss.ts`의 `preloadAd` 한 곳뿐이다.
 *
 * <p><a href="https://developers-apps-in-toss.toss.im/checklist/app-nongame">비게임 출시 체크리스트</a> 필수 항목
 * 「인앱 광고는 사전에 로딩돼 있어요. 광고 재생 시점에 실시간으로 로딩하지 않아요.」 — 2026-10-13 대조에서 세 지면
 * 모두 누르는 순간 불러오고 있었다(공식 문서의 「❌ 나쁜 예: 버튼 클릭 시 로드」).
 *
 * <p><b>왜 소스 가드인가</b>: 새 지면을 만드는 손이 탭 핸들러에서 `loadFullScreenAd`를 다시 부르면 어느 화면
 * 테스트도 안 죽는다(`native-back-guard.test.ts`와 같은 사정). 광고 SDK를 import하는 `.ts`는 `toss.ts`뿐이고
 * 화면은 `.tsx`다.
 */
describe('광고 로드는 preloadAd 한 곳뿐이다 (비게임 체크리스트 ③)', () => {
  const root = new URL('.', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');
  const toss = stripComments(readFileSync(join(root, 'toss.ts'), 'utf8'));
  const screens = sourceFiles(root); // 제품 .tsx — 광고 SDK를 import하는 .ts는 toss.ts뿐이다
  const calls = (src: string) => src.split('loadFullScreenAd(').length - 1;

  it('계측기가 겨눈 꼴을 잡고 주석은 놓아준다', () => {
    expect(calls(stripComments('loadFullScreenAd({ options })'))).toBe(1);
    expect(calls(stripComments('// 옛날엔 탭마다 loadFullScreenAd(…)를 불렀다\n'))).toBe(0);
  });

  it('스캔 대상을 실제로 찾았다 — 0개면 늘 통과하는 빈 가드다', () => {
    expect(screens.length).toBeGreaterThan(10);
    expect(toss).toContain('export function preloadAd(');
  });

  it('REQ-11 · 광고 로드 호출은 toss.ts의 preloadAd 안 한 곳뿐이다', () => {
    const start = toss.indexOf('export function preloadAd(');
    expect(start).toBeGreaterThan(-1);
    expect(calls(toss)).toBe(1);
    expect(calls(toss.slice(start, toss.indexOf('\nexport ', start + 1)))).toBe(1);
    for (const file of screens) expect(calls(stripComments(readFileSync(file, 'utf8'))), file).toBe(0);
  });
});
