// 담기 본문(REQ-126). ⚠️ author는 검색 행의 원문 — authorShort(「… 외 32명」)를 보내면 그대로 DB에 영구 저장된다.
import type { AddRequest, SearchRow, ShelfStatus } from '../api/types';

export function addBody(row: SearchRow, status: ShelfStatus = 'READING'): AddRequest {
  return {
    title: row.title,
    author: row.author,
    isbn13: row.isbn13,
    coverUrl: row.coverUrl,
    publisher: row.publisher,
    purchaseLink: row.purchaseLink,
    category: row.category,
    pubDate: row.pubDate,
    status,
  };
}
