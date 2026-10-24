// 홈 「이어 쓰기」(v1 §4.7 화면 2) — 최근 고친 글 3편을 행(제목 · 메타)으로. 서버가 이미 최근 순으로 준다.
import type { DraftSummary } from '../api/types';

export interface DraftRow {
  id: number;
  title: string;
  meta: string | undefined;
}

/** 목록 행 이름 — 제목, 없으면 첫 줄, 그것도 없으면 「제목 없는 글」(홈 · 책 화면 같은 식). */
export const draftTitle = (d: Pick<DraftSummary, 'title' | 'preview'>) => d.title?.trim() || d.preview.trim() || '제목 없는 글';

export function recentDrafts(list: DraftSummary[]): DraftRow[] {
  return list.slice(0, 3).map((d) => ({
    id: d.id,
    title: draftTitle(d),
    meta: d.bookTitle ?? undefined,
  }));
}
