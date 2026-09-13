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

/** 주석을 걷는다 — 주석 속 예시·경위 설명은 코드가 아니다. 줄머리 `//`만 본다(URL의 `//`를 살리려고). */
export function stripComments(src: string): string {
  return src
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .split('\n')
    .filter((line) => !line.trimStart().startsWith('//'))
    .join('\n');
}
