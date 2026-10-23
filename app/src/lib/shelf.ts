// 서재 세 단(REQ-121 · 122). 낱말 · 순서는 미니앱 Library.tsx SECTIONS와 같다 — 서버 statusLabel(「완독」 · 「읽고 싶음」)은 쓰지 않는다.
import type { ShelfStatus } from '../api/types';
import { durationLabel } from './format';

export const SECTIONS: readonly { status: ShelfStatus; title: string; empty: string }[] = [
  { status: 'READING', title: '읽는 중', empty: '읽는 중인 책이 없어요' },
  { status: 'FINISHED', title: '다 읽음', empty: '다 읽은 책이 없어요' },
  { status: 'WANT_TO_READ', title: '읽고 싶어요', empty: '읽고 싶은 책이 없어요' },
];

/** 언제나 셋 — 책이 없는 단도 남는다(나타났다 사라지면 아래가 들썩인다). 단 안은 서버 순서 그대로. */
export function shelfSections<T extends { status: ShelfStatus }>(books: T[]) {
  return SECTIONS.map((s) => ({ ...s, books: books.filter((b) => b.status === s.status) }));
}

/** 「3시간 12분 읽음 · 여백 4」 — 0인 조각은 빼고, 둘 다 없으면 undefined. */
export function bookMeta(b: { seconds: number; storyCount: number }): string | undefined {
  const parts = [b.seconds >= 60 ? `${durationLabel(b.seconds)} 읽음` : null, b.storyCount > 0 ? `여백 ${b.storyCount}` : null];
  return parts.filter(Boolean).join(' · ') || undefined;
}
