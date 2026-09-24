import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

/**
 * 톤 조율 A 「따뜻한 Soft」(2026-09-24) — 옛 값이 소스에 한 자리도 남지 않는다.
 *
 * <p>진짜 실패: 토큰 블록만 바꾸고 인라인 리터럴·폴백·의사요소(`.book-owned::after`, 휠 안개, 시트 그림자)를
 * 빠뜨리면 따뜻한 바탕 위에 차가운 초록빛 조각이 섞인다 — 사용자가 「쨍하다, 색이 안 어울린다」고 한
 * 바로 그 온도 충돌이다. 그리고 손글씨(개구)가 한 자리라도 살아나면 「글꼴 두 벌」이 다시 세 벌이 된다.
 *
 * <p>스캔 대상은 `src`의 제품 소스(.ts·.tsx·.css)다. <b>테스트 파일은 뺀다</b> — 이 파일 자체가 옛 값 목록을
 * 들고 있어서, 빼지 않으면 가드가 자기 자신에 걸려 영영 빨갛다.
 */
const SRC = fileURLToPath(new URL('.', import.meta.url));

function productFiles(dir: string, out: string[] = []): string[] {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) productFiles(full, out);
    else if (/\.(tsx?|css)$/.test(entry.name) && !/\.test\.tsx?$/.test(entry.name)) out.push(full);
  }
  return out;
}

/** `index.html`도 본다 — 웹폰트 `<link>`가 여기로 재유입되면 css만 보는 스캔은 못 잡는다(리뷰). */
const INDEX_HTML = fileURLToPath(new URL('../index.html', import.meta.url));

const files = [...productFiles(SRC), INDEX_HTML].map((path) => ({
  name: path === INDEX_HTML ? 'index.html' : path.slice(SRC.length).replace(/\\/g, '/'),
  text: readFileSync(path, 'utf8'),
}));

/** 패턴이 걸린 자리를 `파일:줄  내용`으로 — 실패 메시지가 곧 고칠 목록이다. */
function hits(pattern: RegExp): string[] {
  const out: string[] = [];
  for (const { name, text } of files) {
    text.split('\n').forEach((line, i) => {
      if (pattern.test(line)) out.push(`${name}:${i + 1}  ${line.trim()}`);
    });
  }
  return out;
}

/** 옛 Soft(차가운 초록빛) 값 — 바탕·면·선·잉크·옅은 타일·잔디 0단·그림자 틴트, 공부 파랑 그림자·히어로 틴트, 초록 밤. */
const OLD = [
  '#EEF2EB', '#F9FBF7', '#E6ECE3', '#D6DFD2', // 바탕 · 면 · 눌린 면 · 선
  '#4E5A4B', '#3A4637', '#232C21', '#1B221A', // 잉크 사다리
  '#DCE8D6', '#C9DAC4', '#A9C7A1', '#A3C09B', // 옅은 타일 · 세이지 사다리 100~300
  '#E3E9E0', '#F2E8C6', '#EFF3F6', // 잔디 0단 · 버터 · 공부 히어로 틴트
  '#1F251E', '#2A3128', '#232A22', '#3A443A', // 초록 밤 — 바탕·면·눌린 면·선
  '#DCE4D6', '#A8B4A3', '#C2CCBE', '#E8EEE3', // 초록 밤 — 잉크 사다리(리뷰: 목록에서 빠져 있었다)
];
/**
 * 옛 색의 rgb 세 값 — 쉼표 문법(`rgba(94, 122, 90, .2)`)과 CSS4 공백 문법(`rgb(94 122 90 / .2)`)을 둘 다 잡는다
 * (리뷰 M3b: 공백 문법이 살아남았다). 함수 이름도 rgb·rgba 둘 다.
 */
const rgbTriple = (r: number, g: number, b: number) => new RegExp(`rgba?\\(\\s*${r}(?:\\s*,\\s*|\\s+)${g}(?:\\s*,\\s*|\\s+)${b}\\b`);
const OLD_RGBA = [
  rgbTriple(249, 251, 247), // 휠 안개(옛 카드 면)
  rgbTriple(94, 122, 90), // 세이지 그림자 틴트
  rgbTriple(160, 140, 70), // 옛 버터 타일 그림자
  rgbTriple(80, 105, 125), // 공부 파랑 그림자 틴트
  rgbTriple(35, 44, 33), // 옛 입력칸 힌트(옛 잉크의 알파)
];

describe('계측기 자기검증 — 스캔이 비면 아래 부재 단언은 전부 공허하다', () => {
  it('제품 소스를 실제로 읽는다 — global.css·ui.tsx·index.html이 목록에 있고, 테스트 파일은 없다', () => {
    const names = files.map((f) => f.name);
    expect(names).toContain('global.css');
    expect(names).toContain('ui.tsx');
    expect(names).toContain('index.html');
    expect(names.some((n) => n.includes('.test.'))).toBe(false);
  });

  it('지금 값(새 바탕 · 세리프)은 잡힌다 — 양성 대조', () => {
    expect(hits(/#F3EFE5/i).length).toBeGreaterThan(0);
    expect(hits(/Gowun Batang/).length).toBeGreaterThan(0);
  });

  it('rgb 패턴이 두 문법을 다 잡고 이웃 값은 안 잡는다 — 양성·음성 대조', () => {
    const tint = rgbTriple(94, 122, 90);
    expect(tint.test('rgba(94, 122, 90, .18)')).toBe(true);
    expect(tint.test('rgba(94,122,90,.18)')).toBe(true);
    expect(tint.test('rgb(94 122 90 / .18)')).toBe(true);
    expect(tint.test('rgba(94, 122, 905, .18)')).toBe(false);
    expect(tint.test('rgba(194, 122, 90, .18)')).toBe(false);
  });
});

describe('톤 조율 A — 옛 값이 소스에 없다', () => {
  it('손글씨(Gaegu)가 0회다 — 웹폰트 @import도, 호출부도, 주석도', () => {
    expect(hits(/Gaegu/i)).toEqual([]);
    expect(hits(/HANDWRITING/)).toEqual([]);
  });

  it('차가운 바탕·잉크·밤 hex가 0회다 — 폴백 리터럴까지', () => {
    expect(hits(new RegExp(OLD.join('|'), 'i'))).toEqual([]);
  });

  it('옛 그림자 틴트 rgba가 0회다 — 세이지·공부 파랑·옛 버터, 그리고 휠 안개', () => {
    for (const pattern of OLD_RGBA) expect(hits(pattern)).toEqual([]);
  });

  it('그라데이션 막대가 없다 — 게이지·하루 막대는 단색이다', () => {
    expect(hits(/linear-gradient\(90deg/)).toEqual([]);
  });
});

describe('톤 조율 A — 토큰 정의와 폴백', () => {
  const css = readFileSync(join(SRC, 'global.css'), 'utf8').replace(/\/\*[\s\S]*?\*\//g, '');
  const root = css.slice(css.indexOf('html:root {'), css.indexOf('}', css.indexOf('html:root {')));

  /**
   * `--tileShadow`는 폴백 없이 `var()`로만 읽힌다(홈 2열 타일 · 기록 스탯 타일). 정의가 빠지면 선언이 무효가 돼
   * 타일 네 곳의 그림자가 <b>조용히</b> 사라진다(리뷰 M5 — 정의를 지워도 초록이었다).
   */
  it('--tileShadow가 html:root에 정의돼 있고 작은 부풂(5px 5px 12px)이다', () => {
    expect(root).toContain('--adaptiveGrey100:'); // 블록을 제대로 잘랐다
    expect(root).toMatch(/--tileShadow:\s*5px 5px 12px rgba\(/);
  });

  /**
   * 강조 반투명 두 겹의 인라인 폴백 = 낮 토큰 값. 폴백은 토큰이 안 풀릴 때만 보이지만, 어긋나 있으면 「새 값이 어딘가
   * 적용 안 된」 자리처럼 읽혀 다음 정리 때 헷갈린다(리뷰: A에서 .25/.2 → .22/.16으로 바꾸며 폴백이 옛 값에 남았다).
   */
  it.each(['--accentPill', '--accentRing'])('%s의 인라인 폴백이 전부 낮 토큰 값과 같다', (name) => {
    const value = new RegExp(`${name}:\\s*([^;]+);`).exec(root)?.[1].replace(/\s+/g, '');
    expect(value).toMatch(/^rgba\(/);
    const fallbacks = files.flatMap(({ name: file, text }) =>
      [...text.matchAll(new RegExp(`var\\(${name},\\s*(rgba\\([^)]*\\))\\)`, 'g'))].map((m) => ({ file, fallback: m[1] })),
    );
    expect(fallbacks.length).toBeGreaterThan(0); // 폴백 자리가 실제로 있다(아래 전수 비교가 공허하지 않음)
    const norm = (s: string) => s.replace(/\s+/g, '').replace(/,0\./g, ',.');
    expect(fallbacks.filter((f) => norm(f.fallback) !== norm(value!))).toEqual([]);
  });
});
