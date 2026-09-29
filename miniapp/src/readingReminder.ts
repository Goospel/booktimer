import type { ReadingReminder } from './api';
import { ApiError, NetworkError, saveReadingReminder } from './api';
import { requestNotificationAgreement, trackEvent } from './toss';

/**
 * 독서 알림(N3 — 사용자가 켜는 리마인더) — 홈 제안 카드·설정 섹션·App 배선이 함께 쓰는 순수 로직과 동의 흐름.
 *
 * <p>동의의 정본은 토스다(SDK에 동의 상태 조회가 없다). 서버는 사용자가 고른 방식·시각만 알고, 이 모듈은
 * 「동의가 성공한 뒤에만 켬을 저장한다」는 순서 하나를 지킨다.
 */

/** 서버 `User.READING_REMINDER_MIN_HOUR`(8)~`MAX_HOUR`(22)와 짝 — 밖의 값은 서버가 400으로 거절한다. */
export const REMINDER_HOURS = [8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22];
/** 서버 `ReadingReminderService.REST_DAYS`와 짝(문구용 — 판정은 서버가 한다). */
export const REST_DAYS = 3;
/** 홈 제안에 답한 흔적 — 'dismissed'(괜찮아요) | 'agreementRejected'(동의 창에서 거절). 켠 사람은 서버 `everOn`이 거둔다. */
export const READING_REMINDER_OFFER_KEY = 'booktimer.readingReminder.offer';
/** 제안 카드 제목 — `deploy.sh --expect` 마커다(소스에 따옴표째 있어 번들에 그대로 실린다). */
export const READING_REMINDER_OFFER_TITLE = '독서 알림을 받아 볼까요?';

/** 8→「오전 8시」 · 12→「오후 12시」 · 13→「오후 1시」 · 22→「오후 10시」. */
export function hourLabel(h: number): string {
  return h < 12 ? `오전 ${h}시` : `오후 ${h === 12 ? 12 : h - 12}시`;
}

export function reminderSummary(r: Pick<ReadingReminder, 'kind' | 'hour'>): string {
  if (r.kind === 'DAILY') return `매일 ${hourLabel(r.hour)}까지 그날 독서 기록이 없으면 알려 드려요.`;
  if (r.kind === 'REST') return `독서 기록 없이 ${REST_DAYS}일째가 되면 ${hourLabel(r.hour)}에 한 번 알려 드려요.`;
  return '알림이 꺼져 있어요.';
}

/** 캐시는 정본이 아니다 — 저장소가 막힌 환경(사생활 모드 등)이면 「답 안 함」으로 본다. */
export function readOfferCache(): string | null {
  try {
    return localStorage.getItem(READING_REMINDER_OFFER_KEY);
  } catch {
    return null;
  }
}

export function writeOfferCache(v: 'dismissed' | 'agreementRejected'): void {
  try {
    localStorage.setItem(READING_REMINDER_OFFER_KEY, v);
  } catch {
    // 못 적으면 다음 측정 종료 때 카드가 한 번 더 뜰 뿐이다.
  }
}

/** 측정 종료 순간 제안 대상인가 — 가용 ∧ 꺼짐 ∧ 켠 적 없음 ∧ 동의 요청 코드 있음 ∧ 지원 토스앱 ∧ 아직 답 안 함. */
export function shouldOfferReadingReminder(a: { reminder?: ReadingReminder; supported: boolean; cached: string | null }): boolean {
  const r = a.reminder;
  return r !== undefined && r.available && r.kind === 'OFF' && !r.everOn && r.agreementCode !== null && a.supported && a.cached === null;
}

export type EnableOutcome =
  | { status: 'on'; agreement: 'newAgreement' | 'alreadyAgreed'; reminder: ReadingReminder }
  | { status: 'rejected' }
  | { status: 'unsupported' };

/**
 * 동의 먼저, 성공이면 그다음 저장 — 이 순서가 「동의 없이 켜지는 사람 0」의 유일한 보장이다. 오류는 던진다.
 * 동의 이벤트는 여기 한 곳에서 finally로 정확히 한 번 쏜다(App·설정이 따로 쏘면 then/catch에서 두 번 샌다).
 */
export async function enableReadingReminder(
  kind: 'DAILY' | 'REST',
  hour: number,
  agreementCode: string | null,
  entry: 'after_stop' | 'settings',
): Promise<EnableOutcome> {
  if (agreementCode === null) return { status: 'unsupported' }; // 시도 전 — 이벤트 없음
  let result = 'error';
  try {
    const agreement = await requestNotificationAgreement(agreementCode);
    if (agreement === null) {
      result = 'unsupported';
      return { status: 'unsupported' };
    }
    if (agreement === 'agreementRejected') {
      result = agreement;
      return { status: 'rejected' };
    }
    const reminder = await saveReadingReminder({ kind, hour });
    result = agreement; // 동의했어도 저장이 실패하면 'error'로 남는다 — 켜지지 않았으니까
    return { status: 'on', agreement, reminder };
  } finally {
    trackEvent('reading_reminder_consent', { result, entry });
  }
}

/** SDK·API 오류 → 사용자 문구. 401(UnauthorizedError)은 호출부가 재로그인으로 보낸다(여기 오지 않는다). */
export function agreementErrorMessage(e: unknown): string {
  const code = typeof e === 'object' && e !== null ? (e as { code?: unknown }).code : undefined;
  if (code === 'TERMS_DISAGREED_MEMBER')
    return '토스 설정 > 약관 및 개인정보 처리 동의에서 「사용자 최적화 제품 동의」를 켜야 알림을 받을 수 있어요.';
  if (code === 'UNSUPPORTED_APP_VERSION') return '토스 앱을 최신으로 업데이트하면 알림을 켤 수 있어요.';
  if (e instanceof ApiError || e instanceof NetworkError) return e.message;
  return '알림을 켜지 못했어요. 잠시 후 다시 시도해 주세요.';
}

export type OfferPhase =
  | { kind: 'idle' }
  | { kind: 'busy' }
  | { kind: 'on'; summary: string }
  | { kind: 'rejected' }
  | { kind: 'error'; message: string };

/** 동의 흐름 결과 → 카드 상태. null = 카드를 치운다(미지원). */
export function offerPhaseFor(outcome: EnableOutcome): OfferPhase | null {
  if (outcome.status === 'on') return { kind: 'on', summary: reminderSummary(outcome.reminder) };
  if (outcome.status === 'rejected') return { kind: 'rejected' };
  return null;
}
