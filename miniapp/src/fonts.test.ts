import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

import { FONT_CSS_URL, loadWebFonts } from './fonts';

const SRC = fileURLToPath(new URL('.', import.meta.url));
const read = (name: string) => readFileSync(join(SRC, name), 'utf8');

/** 링크를 세는 최소 가짜 document — head.appendChild · getElementById · createElement만 있으면 된다. */
function fakeDoc(readyState: string) {
  const head: { id?: string; rel?: string; href?: string }[] = [];
  return {
    readyState,
    head: { appendChild: (el: { id?: string }) => void head.push(el) },
    getElementById: (id: string) => head.find((el) => el.id === id) ?? null,
    createElement: () => ({}) as { id?: string; rel?: string; href?: string },
    links: head,
  };
}

function fakeWin() {
  const listeners = new Map<string, () => void>();
  return {
    listeners,
    addEventListener: (type: string, fn: () => void) => void listeners.set(type, fn),
    removeEventListener: (type: string, fn: () => void) => {
      if (listeners.get(type) === fn) listeners.delete(type);
    },
  };
}

const run = (doc: ReturnType<typeof fakeDoc>, win: ReturnType<typeof fakeWin>) =>
  loadWebFonts(doc as unknown as Document, win as unknown as Window);

describe('REQ-04 · 글꼴 CSS는 load 뒤 한 번만 붙는다', () => {
  it('로드 중이면 load 전엔 0개, load 뒤에 1개 — 앱 시작이 구글 응답을 기다리지 않는다', () => {
    const doc = fakeDoc('loading');
    const win = fakeWin();

    run(doc, win);
    expect(doc.links).toHaveLength(0);

    win.listeners.get('load')?.();
    expect(doc.links).toHaveLength(1);
    expect(doc.links[0]).toMatchObject({ id: 'bt-webfonts', rel: 'stylesheet', href: FONT_CSS_URL });
  });

  it('모듈 스크립트 시점(interactive)이면 load 전엔 0개 — 「DOM 준비」로 착각해 미리 붙이지 않는다', () => {
    const doc = fakeDoc('interactive');
    const win = fakeWin();

    run(doc, win);
    expect(doc.links).toHaveLength(0);

    win.listeners.get('load')?.();
    expect(doc.links).toHaveLength(1);
  });

  it('이미 로드가 끝났으면 바로 1개', () => {
    const doc = fakeDoc('complete');
    run(doc, fakeWin());
    expect(doc.links).toHaveLength(1);
  });

  it('두 번 불러도 1개 — StrictMode·재호출이 같은 CSS를 겹쳐 받지 않는다', () => {
    const doc = fakeDoc('complete');
    run(doc, fakeWin());
    run(doc, fakeWin());
    expect(doc.links).toHaveLength(1);
  });

  it('URL은 지금 고운 글꼴 그대로다 — 이 PR은 패밀리를 바꾸지 않는다', () => {
    expect(FONT_CSS_URL).toBe(
      'https://fonts.googleapis.com/css2?family=Gowun+Batang:wght@700&family=Gowun+Dodum&display=swap',
    );
  });

  it('global.css에 @import가 0개다 — 되살아나면 폰트 CSS가 다시 앱 시작의 선행 조건이 된다', () => {
    const css = read('global.css').replace(/\/\*[\s\S]*?\*\//g, '');
    expect(css.match(/@import/g) ?? []).toHaveLength(0);
  });

  it('index.html이 구글 폰트를 직접 부르지 않는다 — <link>·preload도 파서가 넣으면 렌더를 붙잡는다', () => {
    expect(read('../index.html')).not.toContain('fonts.googleapis.com');
  });

  it('main.tsx가 loadWebFonts(document, window)를 부른다 — 주석 처리된 호출은 치지 않는다', () => {
    const src = read('main.tsx')
      .replace(/\/\*[\s\S]*?\*\//g, '')
      .replace(/^\s*\/\/.*$/gm, '');
    expect(src).toMatch(/^loadWebFonts\(document, window\);\r?$/m); // main.tsx는 CRLF
  });
});
