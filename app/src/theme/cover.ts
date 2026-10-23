// 천 표지(시각 보충 §2.4) — 표지 사진이 없는 책. 해시 입력은 제목이고 웹 books/pure.ts · 미니앱과 같은 식이다
// (isbn을 쓰면 같은 책이 채널마다 다른 색이 된다).
import { cover } from './tokens';

export function coverColor(seed: string): string {
  const key = seed ?? '';
  let h = 0;
  for (let i = 0; i < key.length; i++) h = (h * 31 + key.charCodeAt(i)) >>> 0;
  return cover.palette[h % cover.palette.length];
}

export function initialOf(title: string): string {
  const t = (title ?? '').trim();
  return t ? t.charAt(0) : '?';
}
