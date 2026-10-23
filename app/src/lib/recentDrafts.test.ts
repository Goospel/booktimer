import type { DraftSummary } from '../api/types';
import { recentDrafts } from './recentDrafts';

// v1 §4.7 화면 2 — 홈 「이어 쓰기」는 최근 글 3편(GET /api/app/drafts는 최근 고친 순 20편).
const draft = (id: number, p: Partial<DraftSummary> = {}): DraftSummary => ({
  id, bookId: 1, bookTitle: '데미안', title: `글 ${id}`, preview: '', chars: 0, updatedAt: '2026-10-23T00:00:00Z', exported: false, ...p,
});

test('홈 이어 쓰기 · 4편이면 앞 3편만 홈에 싣는다', () => {
  expect(recentDrafts([draft(4), draft(3), draft(2), draft(1)]).map((r) => r.id)).toEqual([4, 3, 2]);
  expect(recentDrafts([])).toEqual([]); // 비면 빈 상태 문구
});

test('홈 이어 쓰기 · 제목이 없으면 첫 줄, 그것도 없으면 「제목 없는 글」 · 메타는 책 제목', () => {
  const [a, b, c] = recentDrafts([
    draft(1, { title: null, preview: '첫 문장' }),
    draft(2, { title: '  ', preview: '', bookTitle: null }),
    draft(3),
  ]);
  expect(a).toEqual({ id: 1, title: '첫 문장', meta: '데미안' });
  expect(b).toEqual({ id: 2, title: '제목 없는 글', meta: undefined }); // 책이 풀린 글
  expect(c.title).toBe('글 3');
});
