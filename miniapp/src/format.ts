/**
 * 마지막 글자에 받침이 있는가 — 조사(을/를 · 이/가)를 고르는 유일한 기준.
 *
 * <p>한글 음절은 유니코드 자모 조합이라 `(코드 - 가) % 28`이 곧 종성 인덱스이고, 0이면 받침이 없다.
 * 숫자는 <b>읽는 소리</b>를 따르고(1984 → "사" → 받침 없음), 그 밖(영문·기호·빈 문자열)은 받침 없음으로
 * 떨어뜨린다 — 어느 쪽이든 문장이 깨지지 않는 게 우선이다.
 *
 * <p>피드 문장(을/를)과 성장 문구(이/가)가 <b>같은 판정</b>을 쓴다. 두 벌로 두면 한쪽만 고쳐진다.
 */
export function hasFinalConsonant(word: string): boolean {
  const last = word.at(-1);
  if (last === undefined) return false;

  const code = last.charCodeAt(0);
  if (code >= 0xac00 && code <= 0xd7a3) return (code - 0xac00) % 28 !== 0;

  // 영/일/이/삼/사/오/육/칠/팔/구 — 받침 있는 소리만 true.
  const DIGIT_HAS_FINAL = [true, true, false, true, false, false, true, true, true, false];
  if (last >= '0' && last <= '9') return DIGIT_HAS_FINAL[Number(last)];

  return false;
}

/** 주격 조사 — 받침이 있으면 「이」, 없으면 「가」. */
export function subjectParticle(word: string): string {
  return hasFinalConsonant(word) ? '이' : '가';
}

/** 목적격 조사 — 받침이 있으면 「을」, 없으면 「를」. */
export function objectParticle(word: string): string {
  return hasFinalConsonant(word) ? '을' : '를';
}

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
