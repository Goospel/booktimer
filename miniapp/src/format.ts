/** 초 → "1시간 20분" / "45분" / "30초". 음수(밀린 시간)는 부호를 떼고 호출부가 문구로 표현한다. */
export function formatDuration(seconds: number): string {
  const total = Math.floor(Math.abs(seconds));
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  if (hours > 0) return minutes > 0 ? `${hours}시간 ${minutes}분` : `${hours}시간`;
  if (minutes > 0) return `${minutes}분`;
  return `${total}초`;
}

/**
 * 초 → "45:00" / 1시간부터 "01:20:05". 음수·NaN은 "00:00".
 *
 * <p>초 자리가 매초 바뀌어야 카운트업이 움직여 보인다 — `formatDuration`("45분")은 분 단위라
 * 1분에 한 번만 바뀌어 히어로가 멈춘 것처럼 읽힌다. 웹 `timerProgress.fmtMSS`와 같은 규칙이다.
 */
export function formatClock(seconds: number): string {
  if (isNaN(seconds) || seconds < 0) return '00:00';
  const total = Math.floor(seconds);
  const pad = (n: number) => String(n).padStart(2, '0');
  const mmss = `${pad(Math.floor((total % 3600) / 60))}:${pad(total % 60)}`;
  return total >= 3600 ? `${pad(Math.floor(total / 3600))}:${mmss}` : mmss;
}

/** 시작 시각(ISO) 기준 경과 초 — 진행 중 세션의 타이머 표시용. */
export function elapsedSeconds(startedAt: string, now: number): number {
  return Math.max(0, Math.floor((now - Date.parse(startedAt)) / 1000));
}
