// 아이콘 · 스플래시 PNG 규칙(PR-L 설계 REQ-169 · 170). PNG는 Node zlib로 직접 읽는다 — 새 의존성 0.
import { readFileSync } from 'fs';
import { join } from 'path';
import { inflateSync } from 'zlib';
import { color, font, night, palette } from './src/theme/tokens';

type Png = { width: number; height: number; colorType: number; rgba: Uint8Array };

/** 8비트 · 비월간 RGB(2) · RGBA(6)만 읽는다 — 그 밖이면 던진다(피그마 내보내기 = RGBA 8비트). */
function readPng(file: string): Png {
  const buf = readFileSync(join(__dirname, file));
  if (buf.readUInt32BE(0) !== 0x89504e47) throw new Error(`${file}: PNG가 아니다`);
  let off = 8,
    width = 0,
    height = 0,
    depth = 0,
    colorType = 0,
    interlace = 0;
  const idat: Buffer[] = [];
  while (off < buf.length) {
    const len = buf.readUInt32BE(off);
    const type = buf.toString('latin1', off + 4, off + 8);
    const data = buf.subarray(off + 8, off + 8 + len);
    if (type === 'IHDR') {
      width = data.readUInt32BE(0);
      height = data.readUInt32BE(4);
      depth = data[8];
      colorType = data[9];
      interlace = data[12];
    } else if (type === 'IDAT') idat.push(data);
    else if (type === 'IEND') break;
    off += 12 + len;
  }
  if (depth !== 8 || interlace !== 0 || (colorType !== 2 && colorType !== 6)) {
    throw new Error(`${file}: 지원하지 않는 PNG(깊이 ${depth} · 색 형식 ${colorType} · 월간 ${interlace})`);
  }
  const ch = colorType === 6 ? 4 : 3,
    stride = width * ch;
  const raw = inflateSync(Buffer.concat(idat));
  const rgba = new Uint8Array(width * height * 4);
  let prev = new Uint8Array(stride),
    cur = new Uint8Array(stride);
  for (let y = 0; y < height; y++) {
    const base = y * (stride + 1),
      filter = raw[base];
    if (filter > 4) throw new Error(`${file}: 필터 ${filter}`);
    for (let i = 0; i < stride; i++) {
      const a = i >= ch ? cur[i - ch] : 0,
        b = prev[i],
        c = i >= ch ? prev[i - ch] : 0;
      const p = a + b - c,
        pa = Math.abs(p - a),
        pb = Math.abs(p - b),
        pc = Math.abs(p - c);
      const pred =
        filter === 0 ? 0 : filter === 1 ? a : filter === 2 ? b : filter === 3 ? (a + b) >> 1 : pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
      cur[i] = (raw[base + 1 + i] + pred) & 0xff;
    }
    for (let x = 0; x < width; x++) {
      const o = (y * width + x) * 4,
        s = x * ch;
      rgba[o] = cur[s];
      rgba[o + 1] = cur[s + 1];
      rgba[o + 2] = cur[s + 2];
      rgba[o + 3] = ch === 4 ? cur[s + 3] : 255;
    }
    [prev, cur] = [cur, prev];
  }
  return { width, height, colorType, rgba };
}

const rgb = (hex: string) => [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16));
const px = (p: Png, x: number, y: number) => Array.from(p.rgba.subarray((y * p.width + x) * 4, (y * p.width + x) * 4 + 4));
const corners = (p: Png) => [px(p, 0, 0), px(p, p.width - 1, 0), px(p, 0, p.height - 1), px(p, p.width - 1, p.height - 1)];
const lum = (r: number, g: number, b: number) => 0.299 * r + 0.587 * g + 0.114 * b;
function share(p: Png, hit: (r: number, g: number, b: number, a: number) => boolean): number {
  let n = 0;
  for (let i = 0; i < p.rgba.length; i += 4) if (hit(p.rgba[i], p.rgba[i + 1], p.rgba[i + 2], p.rgba[i + 3])) n++;
  return n / (p.width * p.height);
}
function bbox(p: Png, minAlpha: number) {
  let x0 = p.width,
    y0 = p.height,
    x1 = -1,
    y1 = -1;
  for (let y = 0; y < p.height; y++)
    for (let x = 0; x < p.width; x++) {
      if (p.rgba[(y * p.width + x) * 4 + 3] >= minAlpha) {
        x0 = Math.min(x0, x);
        y0 = Math.min(y0, y);
        x1 = Math.max(x1, x);
        y1 = Math.max(y1, y);
      }
    }
  return { x: x0, y: y0, w: x1 - x0 + 1, h: y1 - y0 + 1 };
}
const ICON = { light: 'assets/icon-ios-light.png', dark: 'assets/icon-ios-dark.png', tinted: 'assets/icon-ios-tinted.png' } as const;

test('REQ-170 · 아이콘 PNG 셋은 1024×1024이고 투명 화소가 없다', () => {
  for (const [k, f] of Object.entries(ICON)) {
    const p = readPng(f);
    expect([k, p.width, p.height]).toEqual([k, 1024, 1024]);
    expect([k, share(p, (_r, _g, _b, a) => a < 255)]).toEqual([k, 0]); // 투명 자리는 Expo가 흰색으로 채운다(아이콘 사양 §13.1)
  }
});

test('REQ-170 · 바탕은 라이트 종이색 · 다크 책상색 · 틴트 검정이고 틴트는 회색조이며 표식 면적이 올빼미(안3)다', () => {
  const L = readPng(ICON.light),
    D = readPng(ICON.dark),
    T = readPng(ICON.tinted);
  expect(corners(L)).toEqual(Array(4).fill([...rgb(color.paper), 255]));
  expect(corners(D)).toEqual(Array(4).fill([...rgb(night.desk), 255]));
  expect(corners(T)).toEqual(Array(4).fill([0, 0, 0, 255]));
  expect(share(T, (r, g, b) => r !== g || g !== b)).toBe(0);
  // 안3 실측 — 라이트 20.00%(아이콘 사양 §13.1과 같다) · 다크 20.03% · 틴트 20.00%(설계 시점 1024 내보내기).
  // 안1 8.9% · 안2 7.0%는 라이트만 잰 값이다. 빈 그림 · 다른 안이면 벗어난다(읽기기 고장의 양성 대조 몫도 한다)
  for (const [k, p, markIsDark] of [
    ['light', L, true],
    ['dark', D, false],
    ['tinted', T, false],
  ] as const) {
    const s = share(p, (r, g, b) => (lum(r, g, b) < 128) === markIsDark);
    expect([k, s > 0.15 && s < 0.25]).toEqual([k, true]);
  }
});

test('REQ-169 · 스플래시 PNG는 1024×1024 RGBA이고 네 모서리가 투명하며 보이는 화소는 모두 잉크색이고 올빼미 자리가 아이콘과 같다', () => {
  const p = readPng('assets/splash-owl.png');
  expect([p.width, p.height, p.colorType]).toEqual([1024, 1024, 6]);
  expect(corners(p).map((c) => c[3])).toEqual([0, 0, 0, 0]); // 투명해야 한다 — 아이콘 테스트(투명 0)와 반대(아이콘 사양 §14.5 함정)
  const [ir, ig, ib] = rgb(color.ink);
  expect(share(p, (r, g, b, a) => a === 255 && (r !== ir || g !== ig || b !== ib))).toBe(0);
  const opaque = share(p, (_r, _g, _b, a) => a === 255);
  expect(opaque > 0.15 && opaque < 0.25).toBe(true); // 실측 19.8%
  const box = bbox(p, 128); // 실측 (172, 170) 681×700 — 자르거나 옮기면 스플래시 올빼미가 119.76pt · 가운데에서 벗어난다
  expect([Math.abs(box.x - 172) <= 3, Math.abs(box.y - 170) <= 3, Math.abs(box.w - 681) <= 3, Math.abs(box.h - 700) <= 3]).toEqual([
    true,
    true,
    true,
    true,
  ]);
});

/** 두 PNG에서 알파가 다른 화소 수 — 크기가 다르면 던진다(같은 실루엣의 계측). */
function alphaDiff(a: Png, b: Png): number {
  if (a.width !== b.width || a.height !== b.height) throw new Error(`크기가 다르다 ${a.width}×${a.height} · ${b.width}×${b.height}`);
  let n = 0;
  for (let i = 3; i < a.rgba.length; i += 4) if (a.rgba[i] !== b.rgba[i]) n++;
  return n;
}

test('REQ-445 · 다크 스플래시 PNG는 라이트와 크기 · 알파가 같고(같은 실루엣) 보이는 화소는 palette.dark.ink다 — 알파를 한 화소 바꾼 견본은 잡힌다', () => {
  const L = readPng('assets/splash-owl.png'),
    D = readPng('assets/splash-owl-dark.png');
  expect([D.width, D.height, D.colorType]).toEqual([L.width, L.height, 6]);
  expect(alphaDiff(L, D)).toBe(0);
  const [ir, ig, ib] = rgb(palette.dark.ink);
  expect(share(D, (r, g, b, a) => a > 0 && (r !== ir || g !== ig || b !== ib))).toBe(0);
  expect(share(D, (_r, _g, _b, a) => a === 255)).toBeGreaterThan(0.15); // 빈 그림이면 위 두 줄이 공허하다(실측 19.8%)
  const planted = { ...D, rgba: D.rgba.slice() };
  planted.rgba[(512 * D.width + 512) * 4 + 3] ^= 0xff;
  expect(alphaDiff(L, planted)).toBe(1);
});

// ── Plex 이름(REQ-470) — 이름 오타는 New Arch에서 로그 없이 시스템 글꼴로 떨어진다(RCTFontUtils.mm) ──
const FONT_FILES: Record<keyof typeof font, string> = {
  sans400: 'assets/fonts/IBMPlexSansKR-Regular.otf',
  sans500: 'assets/fonts/IBMPlexSansKR-Medium.otf',
  sans600: 'assets/fonts/IBMPlexSansKR-SemiBold.otf',
  mono500: 'assets/fonts/IBMPlexMono-Medium.otf',
};

/** OTF name 표의 PostScript 이름(nameID 6) — 플랫폼마다 한 벌씩 나오므로 중복을 걷는다. 머리 · 표 목록 · name 표만 읽는다. */
function postScriptNames(file: string): string[] {
  const buf = readFileSync(join(__dirname, file));
  const tag = buf.toString('latin1', 0, 4);
  if (tag !== 'OTTO' && buf.readUInt32BE(0) !== 0x00010000) throw new Error(`${file}: OpenType이 아니다(${tag})`);
  const numTables = buf.readUInt16BE(4);
  let name = -1;
  for (let i = 0; i < numTables; i++) if (buf.toString('latin1', 12 + i * 16, 16 + i * 16) === 'name') name = buf.readUInt32BE(12 + i * 16 + 8);
  if (name < 0) throw new Error(`${file}: name 표가 없다`);
  const count = buf.readUInt16BE(name + 2),
    strings = name + buf.readUInt16BE(name + 4);
  const out = new Set<string>();
  for (let i = 0; i < count; i++) {
    const r = name + 6 + i * 12;
    const [platform, nameId, len, off] = [buf.readUInt16BE(r), buf.readUInt16BE(r + 6), buf.readUInt16BE(r + 8), buf.readUInt16BE(r + 10)];
    if (nameId !== 6) continue;
    const raw = buf.subarray(strings + off, strings + off + len);
    out.add(platform === 1 ? raw.toString('latin1') : Buffer.from(raw).swap16().toString('utf16le'));
  }
  return [...out];
}

/** font 표의 이름이 그 파일의 PostScript 이름과 다른 칸 — 실제 · 심은 표가 같은 함수를 지난다. */
function fontNameMisses(names: Record<keyof typeof font, string>): string[] {
  return (Object.keys(FONT_FILES) as (keyof typeof font)[]).filter((k) => {
    const ps = postScriptNames(FONT_FILES[k]);
    return ps.length !== 1 || ps[0] !== names[k];
  });
}

test('REQ-470 · font 넷의 이름은 실은 OTF의 PostScript 이름과 같다 — OTF name 표(nameID 6)를 읽어 대조, 한 글자 바꾼 이름은 잡힌다', () => {
  expect(Object.keys(font).sort()).toEqual(Object.keys(FONT_FILES).sort());
  expect(fontNameMisses(font)).toEqual([]);
  expect(fontNameMisses({ ...font, sans500: font.sans500.slice(0, -1) + 'n' })).toEqual(['sans500']); // 끝 한 글자만 바꾼 견본
  expect(fontNameMisses({ ...font, sans400: 'IBMPlexSansKR-Regular' })).toEqual(['sans400']); // 파일 이름을 PostScript 이름으로 착각한 견본
});
