import { readFileSync } from 'node:fs';

import { describe, expect, it } from 'vitest';

import { sourceFiles, stripComments } from './source-scan';

/**
 * TDS `Text`에 `style={{ textAlign }}`을 주지 않는다 — <b>DOM에 안 실리는 죽은 키</b>다
 * (T-198 → T-216 → T-219, 3회차 승격).
 *
 * <p>TDS `Text`는 넘긴 `style`을 그대로 싣지 않고 `display`·`textAlign`을 <b>자기 prop 값으로 덮어쓴다</b>
 * (`{...style, display: …, textAlign: textAlignProp}` — `@toss/tds-mobile` 2.5 `Text` 소스 실측). prop을 안 주면
 * `undefined`가 덮어 정렬이 조용히 부모 기본(왼쪽)으로 남는다. 에러도 경고도 없어 세 번을 전부 화면에서야 잡았다
 * (기록 화면 오른쪽 정렬 · 빈 상태 안내 5곳 · 공부 일정의 요일 머리글).
 *
 * <p>정렬은 `textAlign` <b>prop</b>으로 준다(`<Text textAlign="center">`) — TDS가 그 값을 인라인 스타일에 싣는다.
 * 바깥 div가 갖는 T-216 선례도 유효하다(부모가 이미 가운데면 키만 지운다).
 *
 * <p><b>왜 소스 가드인가</b>: T-216은 「부모 상속까지 봐야 죽은 키인지 정해져 정적 판정이 안 된다」며 승격을 접었다.
 * 그건 「화면이 틀렸는가」의 판정이다. 「이 키가 DOM에 실리는가」는 부모와 무관하게 <b>항상 아니오</b>라 정적으로
 * 정해진다 — 죽은 키를 지우는 것은 언제나 안전하고(부모가 가운데면 지워도 그대로), 산 정렬은 prop으로 옮긴다.
 *
 * <p>보지 않는 것: `style={someVar}`처럼 리터럴이 아닌 형태(세 번의 재발이 전부 `style={{ … }}` 리터럴이었다) ·
 * `display` 키(같은 방식으로 `inline-block`/`block`으로 덮이지만 2026-09-02 실측 90곳+가 남아 있어 정리 없이 넣으면
 * 가드가 아니라 소음이다 — 정리하는 날 `DEAD_KEYS`에 더한다).
 */

/** TDS `Text`가 자기 prop으로 덮어써 `style`에 적어도 DOM에 안 실리는 키. */
const DEAD_KEYS = ['textAlign'];

/**
 * `<Text …>` 여는 태그 전부 — 중괄호 깊이를 세어 속성 속 `=>`·`>`에 속지 않는다.
 * `<TextField>`·`<TextArea>` 같은 다른 이름은 이름 경계로 뺀다.
 */
export function textOpeningTags(src: string): string[] {
  const tags: string[] = [];
  const re = /<Text(?=[\s/>])/g;
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

/** 태그의 `style={{ … }}` 리터럴에 든 죽은 키. 리터럴이 없으면 빈 배열. */
export function deadStyleKeys(tag: string): string[] {
  const at = tag.indexOf('style={{');
  if (at === -1) return [];
  let depth = 0;
  let i = at + 'style='.length;
  for (; i < tag.length; i++) {
    if (tag[i] === '{') depth++;
    else if (tag[i] === '}' && --depth === 0) break;
  }
  const literal = tag.slice(at, i + 1);
  return DEAD_KEYS.filter((key) => new RegExp(`(^|[\\s{,])${key}\\s*:`).test(literal));
}

describe('TDS Text의 style에 죽은 키(textAlign)를 적지 않는다', () => {
  const root = new URL('.', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');
  const files = sourceFiles(root);
  const tags = files.flatMap((file) =>
    textOpeningTags(stripComments(readFileSync(file, 'utf8'))).map((tag) => ({ file, tag })),
  );

  /** 계측기 자체의 판별력 — 겨눈 꼴은 잡고, 옳은 꼴은 놓아줘야 가드다(T-212: 대상을 안 잡아도 초록인 계측기). */
  it('계측기가 겨눈 꼴을 잡는다 — style 리터럴의 textAlign', () => {
    expect(deadStyleKeys(`<Text typography="st12" style={{ display: 'block', textAlign: 'center' }}>`)).toEqual(['textAlign']);
    expect(deadStyleKeys(`<Text style={{ ...SERIF_VALUE, fontSize: 19, minWidth: 120, textAlign: 'center' }}>`)).toEqual([
      'textAlign',
    ]);
  });

  it('계측기가 옳은 꼴은 놓아준다 — prop으로 준 정렬 · 리터럴 없는 style · 다른 키', () => {
    expect(deadStyleKeys(`<Text textAlign="center" style={{ marginTop: 4 }}>`)).toEqual([]);
    expect(deadStyleKeys(`<Text style={centered}>`)).toEqual([]);
    expect(deadStyleKeys(`<Text style={{ wordBreak: 'keep-all' }}>`)).toEqual([]);
  });

  it('태그 추출이 속성 속 화살표·다른 이름에 속지 않는다', () => {
    const src = `<TextField style={{ textAlign: 'center' }} /><Text onClick={() => go(1 > 0)} style={{ textAlign: 'left' }}>a</Text>`;
    const tags = textOpeningTags(src);

    expect(tags).toHaveLength(1);
    expect(tags[0]).toContain(`onClick={() => go(1 > 0)}`);
    expect(deadStyleKeys(tags[0])).toEqual(['textAlign']);
  });

  it('스캔 대상을 실제로 찾았다 — 0개면 늘 통과하는 빈 가드다', () => {
    expect(files.length).toBeGreaterThan(10);
    expect(tags.length).toBeGreaterThan(100);
  });

  it('제품 소스의 어느 <Text>도 style 리터럴에 textAlign을 적지 않는다', () => {
    const hits = tags
      .filter(({ tag }) => deadStyleKeys(tag).length > 0)
      .map(({ file, tag }) => `${file.split(/[\\/]/).slice(-2).join('/')}  ${tag.replace(/\s+/g, ' ').slice(0, 110)}`);

    expect(hits, `TDS Text의 style.textAlign은 DOM에 안 실린다 — textAlign prop으로 옮긴다:\n${hits.join('\n')}`).toEqual([]);
  });
});
