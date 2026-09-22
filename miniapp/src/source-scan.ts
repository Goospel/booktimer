import { readdirSync } from 'node:fs';
import { join } from 'node:path';

/**
 * 소스 가드 공용 스캐너 — 제품 `.tsx`를 훑는 테스트들이 함께 쓴다
 * (`text-style-guard.test.ts`의 죽은 style 키 · `native-back-guard.test.ts`의 자체 뒤로가기).
 *
 * <p><b>왜 평범한 모듈인가</b>: 한쪽 테스트 파일이 다른 테스트 파일에서 import하면 vitest가 그 파일의
 * `describe`를 <b>가져다 쓰는 파일에도 등록</b>해 같은 테스트가 두 번 돈다(2026-09-02 실측: 4건짜리
 * 가드 파일이 9건으로 불었다). 공용 헬퍼는 테스트가 아닌 모듈에 둔다.
 */

/** 테스트·목·픽스처는 제품 마크업이 아니다. */
const SKIP_FILES = /\.test\.(ts|tsx)$|^dev-mock\.ts$|^test-fixtures\.ts$/;

/** 제품 `.tsx` 전부 — 화면 문구는 여기 산다. */
export function sourceFiles(dir: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) return sourceFiles(full);
    if (!/\.tsx$/.test(entry.name) || SKIP_FILES.test(entry.name)) return [];
    return [full];
  });
}

/**
 * `<Name …>` 여는 태그 전부 — 중괄호 깊이를 세어 속성 속 `=>`·`>`에 속지 않는다.
 *
 * <p>이름 경계로 접두사 동명이인을 뺀다: `Text`는 `<TextField>`·`<TextArea>`를, `Profile`은
 * `<ProfileCard>`를 안 집는다.
 */
export function openingTags(src: string, name: string): string[] {
  const tags: string[] = [];
  const re = new RegExp(`<${name}(?=[\\s/>])`, 'g');
  let match: RegExpExecArray | null;
  while ((match = re.exec(src)) !== null) {
    let depth = 0;
    let i = match.index;
    for (; i < src.length; i++) {
      const ch = src[i];
      if (ch === '{') depth++;
      else if (ch === '}') depth--;
      else if (ch === '>' && depth === 0) break;
    }
    tags.push(src.slice(match.index, i + 1));
    re.lastIndex = i + 1;
  }
  return tags;
}

/** 주석을 걷는다 — 주석 속 예시·경위 설명은 코드가 아니다. 줄머리 `//`만 본다(URL의 `//`를 살리려고). */
export function stripComments(src: string): string {
  return src
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .split('\n')
    .filter((line) => !line.trimStart().startsWith('//'))
    .join('\n');
}
