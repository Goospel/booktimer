// 공부 서재(v1 REQ-99 · PR-S1 사양 REQ-148 · REQ-150). 회독 · 회당 시간은 다루지 않는다(v1 REQ-98 — 사용자 결정).
import { failText } from '../api/client';
import type { SearchRow, StudyAddRequest } from '../api/types';
import { durationLabel } from './format';

/** 막대의 「지금 책」 — 고른 책 → 최근 공부한 책 → 첫 책(웹 defaultStudyBookOf와 같은 순서). 목록에 없는 id는 건너뛴다. */
export function defaultStudyBook<T extends { id: number }>(books: T[], recentBookId: number | null, pickedId: number | null): T | null {
  return books.find((b) => b.id === pickedId) ?? books.find((b) => b.id === recentBookId) ?? books[0] ?? null;
}

/** 행 메타 「3시간 12분 공부」 — 1분 미만이면 줄 없음. */
export function studyMeta(totalSeconds: number): string | undefined {
  return totalSeconds >= 60 ? `${durationLabel(totalSeconds)} 공부` : undefined;
}

/** 검색 응답의 owned는 독서 서재 기준이다 — 공부 서재 isbn 집합으로 덮어쓴다(isbn 없음 · 집합 모름이면 false). */
export function studyOwned<T extends { isbn13: string | null; owned: boolean }>(rows: T[], isbns: ReadonlySet<string> | null): T[] {
  return rows.map((r) => ({ ...r, owned: r.isbn13 !== null && isbns !== null && isbns.has(r.isbn13) }));
}

/** 공부 담기 본문 6필드 — author는 원문(authorShort를 보내면 「… 외 N명」이 영구 저장된다). */
export function studyAddBody(row: SearchRow): StudyAddRequest {
  return {
    title: row.title,
    author: row.author,
    isbn13: row.isbn13,
    coverUrl: row.coverUrl,
    publisher: row.publisher,
    purchaseLink: row.purchaseLink,
  };
}

export const REMOVE_FAILED = '서재에서 빼지 못했어요 — 잠시 뒤 다시 해 주세요';

/** 서재에서 빼기 — 409(필기 있음)는 서버 평문 그대로, 문구 없는 실패는 REMOVE_FAILED. 성공일 때만 다시 읽는다. */
export async function removeStudyBook(
  deps: { remove: (id: number) => Promise<unknown>; reload: () => Promise<unknown> },
  id: number,
): Promise<{ error?: string }> {
  try {
    await deps.remove(id);
  } catch (e) {
    return { error: failText(e, REMOVE_FAILED) };
  }
  await deps.reload();
  return {};
}
