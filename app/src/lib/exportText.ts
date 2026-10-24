// 내보내기(v1 REQ-37) — 제목 + 빈 줄 + 본문뿐. 질문 기록은 받지도 않는다(내보내는 글은 전부 사람이 쓴 글 — R-12).
import { Share } from 'react-native';

export function buildExport(d: { title: string | null; body: string }): string {
  const title = d.title?.trim() ?? '';
  return title === '' ? d.body : `${title}\n\n${d.body}`;
}

/** 공유 시트가 「보냄」을 돌려줄 때만 내보낸 표시를 남긴다 — 시트를 연 것만으로는 아니다(비평 1-5). */
export function shouldMarkExported(r: { action: string }): boolean {
  return r.action === Share.sharedAction;
}
