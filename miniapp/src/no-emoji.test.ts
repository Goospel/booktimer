import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

/**
 * 제품 UI에 <b>기본 이모지를 쓰지 않는다</b>(2026-08-18 사용자 지정).
 *
 * <p>불쾌감의 원인은 이모지라는 형식이 아니라 <b>만들지 않고 집어왔다</b>는 표식이다 — 상용 앱은 컨셉에
 * 맞는 아이콘을 자체 제작하는데, 흔한 기본 이모지가 붙어 있으면 "AI가 만든 화면"으로 읽히고 사람들은 그
 * 인상을 불쾌하게 여긴다. 기능이 멀쩡해도 그 표식 하나로 신뢰가 깎인다.
 *
 * <p>그래서 자리마다 고치는 대신 <b>소스를 훑는 가드</b>를 둔다. 다섯 자리를 한 번 걷어내도 다음 기능에서
 * 무심코 다시 집으면 인상은 되돌아오기 때문이다 — 사람이 매번 기억하는 대신 이 테스트가 기억한다.
 *
 * <p>대안(자체 아이콘 세트 제작)을 지금 택하지 않은 이유: 만드는 비용이 아니라 <b>유지</b> 비용이 문제다.
 * 새 기능마다 같은 선 굵기·광학 크기로 계속 그려야 하고, 그 규율이 끊기면 어정쩡한 자체 아이콘이 기본
 * 이모지보다 더 아마추어로 보인다. 이 앱은 활자가 주인공이라(종이톤 + 세리프 제목) 아이콘을 덜어내는 쪽이
 * 컨셉과 같은 방향이기도 하다.
 */

/** 이모지로 읽히는 코드포인트대 — `⏱`(U+23F1)·`✅`(U+2705)처럼 기호대에 섞여 사는 것들도 함께 판다. */
const EMOJI = /[\u{1F300}-\u{1FAFF}\u{1F000}-\u{1F2FF}\u{2600}-\u{27BF}\u{2B00}-\u{2BFF}\u{23E9}-\u{23FA}\u{FE0F}]/u;

/**
 * 이 규칙에서 빼는 문자.
 *
 * <p>`✕`(U+2715)는 기호대에 살아 정규식에 걸리지만 <b>이모지가 아니다</b> — 닫기 표식이라 AI 표식으로
 * 읽히지 않는다.
 *
 * <p>`🌱🌿`는 2026-08-30에 <b>이행 완료</b>돼 목록에서 빠졌다 — 홈의 세 자리를 자체 새싹 SVG
 * (`SproutMark`, 획 기반 인라인)와 평문으로 바꿨다. 예외가 사라졌으므로 이제 제품 UI에 이모지는 0이다.
 */
const ALLOWED = new Set(['✕']);

/** 개발자만 보는 텍스트는 대상이 아니다 — 주석·목·테스트는 제품 UI가 아니다. */
const SKIP_FILES = /\.test\.(ts|tsx)$|^dev-mock\.ts$|^test-fixtures\.ts$/;

function sourceFiles(dir: string, base = dir): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) return sourceFiles(full, base);
    if (!/\.tsx?$/.test(entry.name) || SKIP_FILES.test(entry.name)) return [];
    return [full];
  });
}

/**
 * 주석을 걷어낸 줄 — 주석의 `⚠️`·`✅`는 개발자용이라 규칙 밖이다.
 *
 * <p>블록 주석은 이 레포가 전부 ` * ` 이음줄을 붙여 쓰므로 줄머리 판정으로 닿는다. 완벽한 파서가 아니라
 * 값싼 근사치인데, 놓치는 쪽(주석을 코드로 오판)이 아니라 <b>봐주는 쪽</b>으로만 틀리므로 안전하다.
 */
function codeOnly(line: string): string {
  const trimmed = line.trimStart();
  if (trimmed.startsWith('//') || trimmed.startsWith('*') || trimmed.startsWith('/*')) return '';
  return line.split('//')[0];
}

describe('제품 UI에 기본 이모지가 없다', () => {
  const files = sourceFiles(new URL('.', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1'));

  it('스캔 대상 파일을 실제로 찾았다 — 0개면 늘 통과하는 빈 가드다', () => {
    expect(files.length).toBeGreaterThan(10);
  });

  it('허용 목록 밖의 이모지가 소스에 남아 있지 않다', () => {
    const hits: string[] = [];

    for (const file of files) {
      readFileSync(file, 'utf8')
        .split('\n')
        .forEach((line, index) => {
          for (const char of codeOnly(line)) {
            if (EMOJI.test(char) && !ALLOWED.has(char)) {
              hits.push(`${file.split(/[\\/]/).slice(-2).join('/')}:${index + 1}  ${char}  ${line.trim().slice(0, 60)}`);
            }
          }
        });
    }

    expect(hits).toEqual([]);
  });

  it('가드가 살아 있다 — 이모지를 넣으면 잡힌다(돌연변이 사살)', () => {
    expect(EMOJI.test('🔍')).toBe(true);
    expect(EMOJI.test('✅')).toBe(true);
    expect(EMOJI.test('⏱')).toBe(true);
    expect(EMOJI.test('👋')).toBe(true);
    // 걸러야 할 것들 — 한글·기호는 이모지가 아니다.
    expect(EMOJI.test('책')).toBe(false);
    expect(EMOJI.test('›')).toBe(false);
  });

  it('주석 안의 이모지는 봐준다 — 개발자만 보는 텍스트다', () => {
    expect(codeOnly('  // ⚠️ 주의할 것')).toBe('');
    expect(codeOnly('   * <p>✅ 확인됨')).toBe('');
    expect(codeOnly("  const x = '값'; // ✅ 확인")).toBe("  const x = '값'; ");
    // 코드 자리의 이모지는 걷어내지 않는다 — 그래야 위 단언이 잡는다.
    expect(codeOnly("  <span>🔍</span>")).toContain('🔍');
  });
});
