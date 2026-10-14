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
 * 테스트도 안 죽는다(`native-back-guard.test.ts`와 같은 사정). 그래서 `toss.ts` 밖의 제품 `.ts`·`.tsx` 전부를 훑는다
 * — 새 `.ts` 파일이나 별칭 import(`loadFullScreenAd as loadAd`)도 토큰은 남는다.
 */
describe('광고 로드는 preloadAd 한 곳뿐이다 (비게임 체크리스트 ③)', () => {
  const root = new URL('.', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');
  const tossPath = join(root, 'toss.ts');
  const toss = stripComments(readFileSync(tossPath, 'utf8'));
  const others = sourceFiles(root, /\.tsx?$/).filter((file) => file !== tossPath);
  const calls = (src: string) => src.split('loadFullScreenAd(').length - 1;
  /** `preloadAd(`를 부르는 줄 — 정의 줄은 뺀다. */
  const preloadLines = (src: string) =>
    src.split('\n').filter((line) => line.includes('preloadAd(') && !line.includes('function preloadAd('));

  it('계측기가 겨눈 꼴을 잡고 주석은 놓아준다', () => {
    expect(calls(stripComments('loadFullScreenAd({ options })'))).toBe(1);
    expect(calls(stripComments('// 옛날엔 탭마다 loadFullScreenAd(…)를 불렀다\n'))).toBe(0);
  });

  it('스캔 대상을 실제로 찾았다 — 0개면 늘 통과하는 빈 가드다', () => {
    expect(others.length).toBeGreaterThan(10);
    expect(others.some((file) => file.endsWith('.ts'))).toBe(true);
    expect(others.some((file) => file.endsWith('.tsx'))).toBe(true);
    expect(toss).toContain('export function preloadAd(');
  });

  it('REQ-11 · 광고 로드 호출은 toss.ts의 preloadAd 안 한 곳뿐이다', () => {
    const start = toss.indexOf('export function preloadAd(');
    expect(start).toBeGreaterThan(-1);
    expect(calls(toss)).toBe(1);
    expect(calls(toss.slice(start, toss.indexOf('\nexport ', start + 1)))).toBe(1);
  });

  it('REQ-11 · toss.ts 밖의 제품 코드엔 loadFullScreenAd가 아예 없다 — 새 .ts·별칭 import도', () => {
    for (const file of others) expect(stripComments(readFileSync(file, 'utf8')), file).not.toContain('loadFullScreenAd');
  });

  it('REQ-05 · preloadAd를 저절로 부르는 자리는 useAdSlot의 shouldPreload 게이트 하나뿐이다 — 노필 루프 방지', () => {
    const inToss = preloadLines(toss);
    expect(inToss).toHaveLength(1);
    expect(inToss[0]).toMatch(/if \(shouldPreload\(prev\.current, status, wanted\)\) preloadAd\(adGroupId\)/);

    // 그 밖은 사람이 누르는 「광고 다시 불러오기」뿐이다(홈 지우개 · 책방 성향). effect·자동 재시도는 여기서 죽는다.
    const elsewhere = others.flatMap((file) => preloadLines(stripComments(readFileSync(file, 'utf8'))));
    expect(elsewhere).toHaveLength(2);
    for (const line of elsewhere) expect(line).toMatch(/action === 'retry' \? \(\) => preloadAd\(/);

    // 호출 구문 없이 참조로 넘기는 자동 재시도(`setTimeout(preloadAd, …)`)도 막는다 — 토큰은 import 2 + 다시 불러오기 2뿐이다.
    const tokens = others.reduce((n, file) => n + stripComments(readFileSync(file, 'utf8')).split('preloadAd').length - 1, 0);
    expect(tokens).toBe(4);
  });

  it('REQ-05 · useAdSlot effect 본문이 그대로다 — prev 갱신·순서·의존성이 루프와 무호출을 가른다', () => {
    // 실기기 onError는 비동기라 prev 갱신이 빠지면 failed마다 다시 부르는 노필 루프가 된다 — 목 모드(동기 throw)론
    // 안 보여서 행동 테스트가 못 잡는다. 갱신을 게이트 앞으로 옮기면 영영 안 부르고, deps에서 status를 빼면 다 본 뒤 안 부른다.
    const start = toss.indexOf('export function useAdSlot(');
    expect(start).toBeGreaterThan(-1);
    const body = toss
      .slice(start, toss.indexOf('\n}', start))
      .replace(/\/\/[^\n]*/g, '') // stripComments는 줄 끝 주석을 남긴다
      .replace(/\s+/g, ' ');
    expect(body).toContain(
      'useEffect(() => { if (!wanted) { prev.current = undefined; return; } if (shouldPreload(prev.current, status, wanted)) preloadAd(adGroupId); prev.current = status; }, [adGroupId, wanted, status]);',
    );
  });
});
