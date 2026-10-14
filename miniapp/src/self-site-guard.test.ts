import { readFileSync } from 'node:fs';

import { describe, expect, it } from 'vitest';

import { sourceFiles, stripComments } from './source-scan';

/**
 * 자사 웹으로 보내는 주소·문구를 다시 세우지 않는다 — 앱인토스 서비스 오픈 정책 §2-2 · 비게임 체크리스트 UX
 * 「링크나 컴포넌트로 자사 서비스 이동 유도 금지」(2026-10 전수 점검 대비, 설정 「PC 웹에서 이어 보기」를 걷은 뒤).
 * 잡는 것은 BANNED의 낱말뿐이다 — 그 밖의 웹 안내 표현(「사이트」「인터넷에서」 등)은 리뷰 몫이다.
 * 허용은 법률상 고지 두 주소뿐이다. **따옴표째** 지워 접두 우회(`/privacy-old`)를 막는다.
 * ponytail: .tsx만 본다 — .ts 상수·환경변수로 주소를 조립하면 못 잡는다. 그런 일이 생기면 sourceFiles에 확장자를 넓힌다.
 */
const ALLOWED = ["'https://booktimer.app/privacy'", "'https://booktimer.app/terms'"];
const BANNED = ['booktimer.app', 'PC 웹', 'PC 브라우저', '웹사이트', '홈페이지'];

function hitsIn(src: string): string[] {
  let s = stripComments(src);
  for (const url of ALLOWED) s = s.split(url).join("''");
  return BANNED.filter((w) => s.includes(w));
}

describe('제품 화면은 자사 웹으로 보내지 않는다 (서비스 오픈 정책 §2-2)', () => {
  const root = new URL('.', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');
  const files = sourceFiles(root);

  it('계측기가 겨눈 꼴을 잡는다 — 주소 리터럴 · 안내 문구 · 허용 주소의 접두 우회', () => {
    expect(hitsIn(`<Button onClick={() => openExternal('https://booktimer.app/login')}>열기</Button>`)).toEqual([
      'booktimer.app',
    ]);
    expect(hitsIn(`<Text>PC 웹에서 이어 보기</Text>`)).toEqual(['PC 웹']);
    expect(hitsIn(`const X = 'https://booktimer.app/privacy-old';`)).toEqual(['booktimer.app']);
    expect(hitsIn(`<Text>북타이머 웹사이트에서도 이어서 쓸 수 있어요</Text>`)).toEqual(['웹사이트']);
    expect(hitsIn(`<Text>홈페이지에서 확인하세요</Text>`)).toEqual(['홈페이지']);
  });

  it('계측기가 허용 주소와 주석은 놓아준다', () => {
    expect(hitsIn(`const PRIVACY_URL = 'https://booktimer.app/privacy';`)).toEqual([]);
    expect(hitsIn(`  // 옛 「booktimer.app/login 열기」는 걷었다\n<div />`)).toEqual([]);
    expect(hitsIn(`{/* PC 웹 안내는 걷었다 */}<div />`)).toEqual([]);
  });

  it('스캔 대상을 실제로 찾았다 — 0개면 늘 통과하는 빈 가드다', () => {
    expect(files.length).toBeGreaterThan(10);
  });

  it('REQ-06 · 제품 화면은 자사 웹 주소와 PC 웹 안내를 싣지 않는다(법률 고지 두 주소만 예외)', () => {
    const hits = files.flatMap((file) =>
      hitsIn(readFileSync(file, 'utf8')).map((w) => `${file.split(/[\\/]/).slice(-2).join('/')}: ${w}`),
    );
    expect(hits, `자사 웹으로 보내는 주소·문구는 서비스 오픈 정책 §2-2 제한 대상이다:\n${hits.join('\n')}`).toEqual([]);
  });
});
