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

/**
 * 피드용 상대 시각 — "방금 전" / "N분 전" / "N시간 전" / "어제" / "N일 전"(2~13) / "M월 D일".
 *
 * <p>자정 경계가 아니라 **24시간 단위 근사**다(피드 톤이라 이 정도면 충분하고, 서버 타임존과
 * 씨름할 이유가 없다). 2주가 넘으면 "20일 전"이 오히려 안 읽혀 절대 날짜로 넘어간다.
 *
 * <p>미래 시각은 0으로 클램프한다 — 기기 시계가 조금 앞서면 방금 생긴 소식이 "-3분 전"이 된다.
 */
export function relativeTime(iso: string, now: number): string {
  const diff = Math.max(0, now - Date.parse(iso));
  const minutes = Math.floor(diff / 60_000);
  if (minutes < 1) return '방금 전';
  if (minutes < 60) return `${minutes}분 전`;
  const hours = Math.floor(diff / 3_600_000);
  if (hours < 24) return `${hours}시간 전`;
  const days = Math.floor(diff / 86_400_000);
  if (days === 1) return '어제';
  if (days < 14) return `${days}일 전`;
  const date = new Date(iso);
  return `${date.getMonth() + 1}월 ${date.getDate()}일`;
}

/** 시작 시각(ISO) 기준 경과 초 — 진행 중 세션의 타이머 표시용. */
export function elapsedSeconds(startedAt: string, now: number): number {
  return Math.max(0, Math.floor((now - Date.parse(startedAt)) / 1000));
}
