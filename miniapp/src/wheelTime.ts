/**
 * 시/분 휠 변환 — 독서 목표 화면(`Goal.tsx`)과 공부 「회당 시간」 시트가 함께 쓴다.
 *
 * <p>`Goal.tsx`에서 옮겨 왔고 그 파일은 재export만 한다(기존 테스트·독서 렌더 무변경). 상한만 파라미터로 열었다 —
 * 기본 12시간이 독서 그대로이고, 회당 시트는 6을 넘긴다.
 */

/**
 * 초 → 휠 표시값(시/분).
 *
 * <p>휠은 분 단위라 자투리 초는 버리고, 휠에 없는 칸을 가리키지 않도록 상한을 넘는 값은
 * 「상한 시간 59분」으로 붙인다. 음수 같은 이상값도 0으로 눌러 휠이 빈 칸을 가리키지 않게 한다.
 */
export function wheelIndices(seconds: number, maxHours = 12): { hours: number; minutes: number } {
  const totalMinutes = Math.max(0, Math.floor(seconds / 60));
  if (totalMinutes >= (maxHours + 1) * 60) {
    return { hours: maxHours, minutes: 59 };
  }
  return { hours: Math.floor(totalMinutes / 60), minutes: totalMinutes % 60 };
}

/** 휠 표시값(시/분) → 초. */
export function combineWheel(hours: number, minutes: number): number {
  return hours * 3600 + minutes * 60;
}
