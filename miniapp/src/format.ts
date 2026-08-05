/** 초 → "1시간 20분" / "45분" / "30초". 음수(밀린 시간)는 부호를 떼고 호출부가 문구로 표현한다. */
export function formatDuration(seconds: number): string {
  const total = Math.floor(Math.abs(seconds));
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  if (hours > 0) return minutes > 0 ? `${hours}시간 ${minutes}분` : `${hours}시간`;
  if (minutes > 0) return `${minutes}분`;
  return `${total}초`;
}

/** 시작 시각(ISO) 기준 경과 초 — 진행 중 세션의 타이머 표시용. */
export function elapsedSeconds(startedAt: string, now: number): number {
  return Math.max(0, Math.floor((now - Date.parse(startedAt)) / 1000));
}
