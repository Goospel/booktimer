// 홈 「이어 쓰기」(v1 §4.7 화면 2) — 최근 고친 글 3편을 행(제목 · 메타)으로. 서버가 이미 최근 순으로 준다.
import type { DraftSummary } from '../api/types';

export interface DraftRow {
  id: number;
  title: string;
  meta: string | undefined;
}

export function recentDrafts(list: DraftSummary[]): DraftRow[] {
  return list.slice(0, 3).map((d) => ({
    id: d.id,
    title: d.title?.trim() || d.preview.trim() || '제목 없는 글',
    meta: d.bookTitle ?? undefined,
  }));
}
