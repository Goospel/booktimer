// 내보내기(v1 REQ-37) — 제목 + 빈 줄 + 본문뿐. 질문 기록은 받지도 않는다(내보내는 글은 전부 사람이 쓴 글 — R-12).
import { Share } from 'react-native';

export function buildExport(d: { title: string | null; body: string }): string {
  const title = d.title?.trim() ?? '';
  return title === '' ? d.body : `${title}\n\n${d.body}`;
}

/**
 * 쪽지 내보내기(C8 · J78) — 옮긴 문장(있으면 「…」 + 『책』 · 저자 줄) + 빈 줄 + 생각. AI 질문 · 설명은 받지도 않는다(v1 그대로).
 * 책 줄은 서버 quoteSource 스냅샷 — 옮긴 문장이 없으면 책 줄도 없다.
 */
export function buildNoteExport(n: { quote: string | null; quoteSource: string | null; text: string }): string {
  const quote = n.quote?.trim() ?? '';
  const head = quote === '' ? null : [`「${quote}」`, n.quoteSource?.trim()].filter(Boolean).join('\n');
  return buildExport({ title: head, body: n.text }).trimEnd();
}

/** 공유 시트가 「보냄」을 돌려줄 때만 내보낸 표시를 남긴다 — 시트를 연 것만으로는 아니다(비평 1-5). */
export function shouldMarkExported(r: { action: string }): boolean {
  return r.action === Share.sharedAction;
}
