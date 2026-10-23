// 여백 저장 단계(v1 REQ-44 · REQ-96). 두 「생각」 판정은 일부러 다르다(PR-G 사양 §11-1):
// 확인 판정 = trim() !== ''(서버 REQ-93 — 「.」 하나도 남아 남에게 보인다) · 목록 그리기 = showsThought(서버 NO_THOUGHT).

const has = (s: string | null | undefined) => (s ?? '').trim() !== '';

/** 두 칸 중 하나라도 글자가 있으면 남길 수 있다 — 옮긴 문장만 있어도 된다. */
export function canSaveMargin(i: { quote: string; thought: string }): boolean {
  return has(i.quote) || has(i.thought);
}

/** 생각이 비었거나 문장부호 · 공백뿐이면 그리지 않는다 — 서버 WritingMaterial.NO_THOUGHT와 같은 식(이모지는 생각이다). */
export function showsThought(text: string | null | undefined): boolean {
  return !/^[\p{P}\p{Z}\s]*$/u.test(text ?? '');
}

/** 공개 책 && 생각 있음 && 그 책에 남에게 보이는 여백(생각 있는 것)이 아직 없음 → 확인 시트. */
export function marginSaveStep(p: { isPublic: boolean; thought: string; margins: { text: string | null }[] }): 'confirm' | 'save' {
  return p.isPublic && has(p.thought) && !p.margins.some((m) => has(m.text)) ? 'confirm' : 'save';
}

/** 남긴 뒤 돌아간 화면이 한 번 읽는 한 칸짜리 표시. */
let saved = false;
export const savedNotice = {
  mark() {
    saved = true;
  },
  take() {
    const v = saved;
    saved = false;
    return v;
  },
};
