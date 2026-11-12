// 홈 판정(책생글 A2 — D14 · D15 · F1 §4.1). 화면은 이 결과를 그대로 그린다.
import type { BlogPostRow, Dashboard } from '../api/types';
import { dateLabel } from './format';
import { cachedBook } from './marginSave';

/** 홈 맨 위 박스 — bookId = 「여백에 글쓰기」 · 측정 시작이 쓸 책(null = 책 없는 쪽지 · 책 없이 재기) · cover = 표지 자리를 그린다 */
export interface HomeBox {
  bookId: number | null;
  title: string;
  coverUrl: string | null;
  cover: boolean;
}

/**
 * 측정 중 = 재는 책(서재에 없는 책이면 원장 제목만 — 천 표지 · 쪽지는 책 없는 쪽지로, 책 없이 재면 표지 없이 「읽는 중」).
 * 측정 전 = 최근에 잰 책(세 목록 — 다 읽은 책도 책 화면에서 다시 잰다) → 읽는 중 → 읽고 싶어요 → 다 읽음 첫 책. 책이 하나도 없으면 null = 빈 홈(F1 §7-2).
 */
export function homeBox(d: Dashboard, measuring: boolean): HomeBox | null {
  if (measuring) {
    const a = d.activeBook;
    if (a) return { bookId: a.id, title: a.title, coverUrl: a.coverUrl, cover: true };
    return d.activeBookTitle ? { bookId: null, title: d.activeBookTitle, coverUrl: null, cover: true } : { bookId: null, title: '읽는 중', coverUrl: null, cover: false };
  }
  const b = (d.recentBookId === null ? null : cachedBook(d, d.recentBookId)) ?? d.readingBooks[0] ?? d.wantToReadBooks[0] ?? d.finishedBooks?.[0] ?? null;
  return b ? { bookId: b.id, title: b.title, coverUrl: b.coverUrl, cover: true } : null;
}

/** 홈 블로그 칸(D14 · REQ-273 · F1 §4.1-3) — 최신 3편(제목 · 「이름 · 날짜」). 0편이면 빈 문구 · 못 읽었으면(null) 줄도 문구도 없다 — 「더 보기」는 화면이 늘 단다 */
export function blogSection(posts: BlogPostRow[] | null): { rows: { id: number; title: string; meta: string }[]; empty: string | null } {
  if (!posts) return { rows: [], empty: null };
  const rows = posts.slice(0, 3).map((p) => ({ id: p.id, title: p.title, meta: [p.authorName, dateLabel(new Date(p.publishedAt))].filter(Boolean).join(' · ') }));
  return { rows, empty: posts.length === 0 ? '아직 올라온 글이 없어요' : null };
}
