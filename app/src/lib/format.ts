// 화면 · 위젯이 같이 쓰는 글자 모양. 날짜는 기기 시간대다(진행 중 몫과 같은 기준 — 범위 보충 §3.4).

const pad = (n: number) => String(n).padStart(2, '0');

/** 측정 시계 — 1시간 전엔 M:SS, 그 뒤엔 H:MM:SS. */
export function clockLabel(seconds: number): string {
  const s = Math.max(0, Math.floor(seconds));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  return h > 0 ? `${h}:${pad(m)}:${pad(s % 60)}` : `${m}:${pad(s % 60)}`;
}

/** 「12분」 · 「1시간」 · 「1시간 5분」 — 분 아래는 버린다. */
export function durationLabel(seconds: number): string {
  const total = Math.max(0, Math.floor(seconds / 60));
  const h = Math.floor(total / 60);
  const m = total % 60;
  if (h === 0) return `${m}분`;
  return m === 0 ? `${h}시간` : `${h}시간 ${m}분`;
}

/**
 * 목적격 조사 — 끝 글자에 받침이 있으면 「을」, 없으면 「를」(미니앱 format.ts objectParticle 이식).
 * 끝의 괄호 · 문장부호 · 공백은 떼고 본다(「데미안 (큰글자책)」 → 책 → 을). 숫자는 읽는 소리, 그 밖은 받침 없음.
 */
export function objectParticle(word: string): string {
  const last = word.replace(/[^\p{L}\p{N}]+$/u, '').at(-1);
  if (last === undefined) return '를';
  const code = last.charCodeAt(0);
  if (code >= 0xac00 && code <= 0xd7a3) return (code - 0xac00) % 28 !== 0 ? '을' : '를';
  // 영/일/이/삼/사/오/육/칠/팔/구 — 받침 있는 소리만 「을」
  const digitFinal = [true, true, false, true, false, false, true, true, true, false];
  if (last >= '0' && last <= '9') return digitFinal[Number(last)] ? '을' : '를';
  return '를';
}

/** 「10월 23일」 */
export function dateLabel(d: Date): string {
  return `${d.getMonth() + 1}월 ${d.getDate()}일`;
}
