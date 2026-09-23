/**
 * Soft 재테마 계측 조각 — 화면 테스트들이 「그 자리 태그」의 표면을 잴 때 함께 쓴다.
 *
 * <p>테스트 파일끼리 import하면 vitest가 가져다 쓰는 쪽에도 `describe`를 등록해 같은 테스트가 두 번 돈다
 * (`source-scan.ts` 머리 주석). 그래서 테스트가 아닌 평범한 모듈에 둔다.
 */

/**
 * 마크업에서 `marker`를 품은 여는 태그 하나 — 없으면 `''`. 마커는 <b>그 여는 태그 안</b>에 있어야 한다:
 * 속성(`aria-label="대화함"` · `data-bio-card=""`)이거나 태그 머리(`<textarea`). 글자 내용을 마커로 넘기면
 * 닫는 태그까지 딸려 온다(쓰지 않는다). React는 속성 값의 `<`·`>`를 이스케이프하므로 태그 경계가 흔들리지 않는다.
 *
 * <p>호출부는 먼저 `''`이 아님을 단언한다 — 빈 문자열에 부재 단언을 걸면 공허하게 통과한다.
 */
export function tagWith(markup: string, marker: string): string {
  const at = markup.indexOf(marker);
  if (at < 0) return '';
  const open = markup.lastIndexOf('<', at);
  const close = markup.indexOf('>', at);
  return open < 0 || close < 0 ? '' : markup.slice(open, close + 1);
}

/**
 * 이 태그의 `box-shadow`가 <b>큰 흐림</b>인가 — 판정은 <b>닫힌 쪽</b>이다(모르면 크다고 본다):
 *
 * <ul>
 *   <li>그림자 변수(`var(--…shadow…)`)는 **`--dentShadow`만** 허용한다. 부푼 그림자든 새로 생긴 다른 그림자
 *       변수든 값을 여기서 알 수 없으니 큰 것으로 본다.</li>
 *   <li>`calc()`는 값을 못 풀므로 큰 것으로 본다.</li>
 *   <li>그 밖의 변수(`var(--adaptiveBlue700)` 같은 색)는 걷고 <b>폴백 속 그림자까지</b> 잰다 —
 *       `var(--x, 0 8px 24px …)`이 새지 않게.</li>
 *   <li>길이는 px 그대로, rem·em은 16px로 환산해 흐림(세 번째 길이)이 8px 이상이면 참. 대소문자는 무시한다.</li>
 * </ul>
 *
 * <p>반복 요소(목록 행·말풍선·피드 행·격자 칸)엔 이게 붙으면 안 된다: 요소 수 × 큰 blur는 저사양
 * 안드로이드 WebView에서 스크롤 중 타일 재래스터를 부른다(설계 §6 그림자 예산). 변수 경유와 리터럴을
 * <b>둘 다</b> 본다 — PR-3 가드는 리터럴만 봐서 `var()` 경유가 사각이었다.
 */
export function bigShadow(tag: string): boolean {
  const value = (tag.match(/box-shadow:([^;"]*)/i)?.[1] ?? '').toLowerCase();
  if (value.includes('calc(')) return true;
  const shadowVars = [...value.matchAll(/var\(\s*(--[\w-]*shadow[\w-]*)/g)].map((m) => m[1]);
  if (shadowVars.some((name) => name !== '--dentshadow')) return true;
  // 그림자 하나씩 — 색 함수 속 쉼표(`rgba(0, 0, 0, .2)`)를 먼저 걷고, `var(--x,` 머리와 닫는 괄호를 지워 폴백을
  // 드러낸 뒤 쉼표로 가른다. 길이는 `0`처럼 단위 없는 것도 있어(`0 8px 24px`) 순서로 센다: x · y · blur · spread.
  const flat = value
    .replace(/(rgba?|hsla?)\([^)]*\)/g, '')
    .replace(/var\(\s*--[\w-]+\s*,?/g, '')
    .replace(/\)/g, '');
  return flat.split(',').some((shadow) => {
    const lengths = shadow
      .trim()
      .split(/\s+/)
      .map((t) => t.match(/^(-?\d*\.?\d+)(px|rem|em)?$/))
      .filter((m): m is RegExpMatchArray => m !== null);
    const blur = lengths[2];
    if (blur === undefined) return false;
    return parseFloat(blur[1]) * (blur[2] === 'rem' || blur[2] === 'em' ? 16 : 1) >= 8;
  });
}
