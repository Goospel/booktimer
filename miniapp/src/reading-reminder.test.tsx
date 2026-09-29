import { TDSMobileProvider } from '@toss/tds-mobile';
import type { ReactNode } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { ReadingReminder } from './api';
import { ApiError, saveReadingReminder } from './api';
import {
  READING_REMINDER_OFFER_KEY,
  READING_REMINDER_OFFER_TITLE,
  REMINDER_HOURS,
  acceptReminderOffer,
  agreementErrorMessage,
  changeReadingReminder,
  enableReadingReminder,
  hourLabel,
  offerPhaseFor,
  readOfferCache,
  reminderSummary,
  shouldOfferReadingReminder,
  writeOfferCache,
} from './readingReminder';
import type { OfferPhase } from './readingReminder';
import { ReadingReminderOffer } from './screens/Home';
import { ReadingReminderSection } from './screens/Settings';
import { stubLocalStorage, userAgent } from './test-fixtures';
import { requestNotificationAgreement, trackEvent } from './toss';

vi.mock('./toss', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./toss')>()),
  requestNotificationAgreement: vi.fn(),
  notificationAgreementSupported: vi.fn(() => true),
  trackEvent: vi.fn(),
}));
vi.mock('./api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./api')>()),
  saveReadingReminder: vi.fn(),
}));

const requestMock = vi.mocked(requestNotificationAgreement);
const saveMock = vi.mocked(saveReadingReminder);
const trackMock = vi.mocked(trackEvent);

/** 서버 `ReadingReminderService.View` 모양 — 기본은 「제안 대상」(가용·꺼짐·켠 적 없음·코드 있음). */
function reminder(extra: Partial<ReadingReminder> = {}): ReadingReminder {
  return { available: true, kind: 'OFF', hour: 20, everOn: false, agreementCode: 'booktimer-reading-reminder-rest', ...extra };
}

const wrap = (node: ReactNode) => renderToStaticMarkup(<TDSMobileProvider userAgent={userAgent}>{node}</TDSMobileProvider>);

beforeEach(() => {
  stubLocalStorage();
  requestMock.mockReset();
  saveMock.mockReset();
  trackMock.mockReset();
});

describe('시각·문구 (순수 함수)', () => {
  it('REQ-12 · 시각 라벨: 8→오전 8시, 11→오전 11시, 12→오후 12시, 13→오후 1시, 22→오후 10시', () => {
    expect([8, 11, 12, 13, 22].map(hourLabel)).toEqual(['오전 8시', '오전 11시', '오후 12시', '오후 1시', '오후 10시']);
  });

  it('REQ-12 · 시각 선택지는 8시부터 상한까지 한 시간 간격이고 그 밖은 없다', () => {
    // 서버 User.READING_REMINDER_MIN_HOUR=8 · MAX_HOUR=22와 짝이다 — 밖의 값은 서버가 400으로 거절한다.
    expect(REMINDER_HOURS).toEqual([8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22]);
    expect(REMINDER_HOURS).not.toContain(7);
    expect(REMINDER_HOURS).not.toContain(23);
  });

  it('REQ-12 · 요약 문구: 매일 / 3일 쉬면 / 꺼짐', () => {
    expect(reminderSummary({ kind: 'DAILY', hour: 19 })).toBe('매일 오후 7시까지 그날 독서 기록이 없으면 알려 드려요.');
    expect(reminderSummary({ kind: 'REST', hour: 20 })).toBe('독서 기록 없이 3일째가 되면 오후 8시에 한 번 알려 드려요.');
    expect(reminderSummary({ kind: 'OFF', hour: 20 })).toBe('알림이 꺼져 있어요.');
  });
});

describe('제안 조건 (shouldOfferReadingReminder)', () => {
  const base = { reminder: reminder(), supported: true, cached: null };

  it('REQ-10 · 제안 조건: 가용·꺼짐·켠 적 없음·코드 있음·지원·캐시 없음이면 참', () => {
    expect(shouldOfferReadingReminder(base)).toBe(true);
  });

  it.each([
    ['옛 서버(reminder 없음)', { ...base, reminder: undefined }],
    ['가용 아님', { ...base, reminder: reminder({ available: false }) }],
    ['이미 켬(DAILY)', { ...base, reminder: reminder({ kind: 'DAILY' }) }],
    ['켠 적 있음', { ...base, reminder: reminder({ everOn: true }) }],
    ['동의 요청 코드 없음', { ...base, reminder: reminder({ agreementCode: null }) }],
    ['미지원 토스앱', { ...base, supported: false }],
    ['이미 답함(괜찮아요)', { ...base, cached: 'dismissed' }],
  ])('REQ-10 · 제안 조건: %s이면 거짓', (_, args) => {
    expect(shouldOfferReadingReminder(args)).toBe(false);
  });
});

describe('제안 캐시', () => {
  it('쓴 값을 그대로 읽는다 · 없으면 null', () => {
    expect(readOfferCache()).toBeNull();
    writeOfferCache('agreementRejected');
    expect(readOfferCache()).toBe('agreementRejected');
    expect(localStorage.getItem(READING_REMINDER_OFFER_KEY)).toBe('agreementRejected');
  });

  it('localStorage가 던져도 삼킨다 — 캐시는 정본이 아니다', () => {
    vi.stubGlobal('localStorage', {
      getItem: () => {
        throw new Error('blocked');
      },
      setItem: () => {
        throw new Error('blocked');
      },
    });
    expect(readOfferCache()).toBeNull();
    expect(() => writeOfferCache('dismissed')).not.toThrow();
  });
});

describe('동의 흐름 (enableReadingReminder)', () => {
  it('REQ-11 · 동의(newAgreement)면 {kind:\'REST\', hour}로 저장하고 저장 결과를 돌려준다', async () => {
    const saved = reminder({ kind: 'REST', everOn: true });
    requestMock.mockResolvedValue('newAgreement');
    saveMock.mockResolvedValue(saved);

    await expect(enableReadingReminder('REST', 20, 'code-rest', 'after_stop')).resolves.toEqual({
      status: 'on',
      agreement: 'newAgreement',
      reminder: saved,
    });
    expect(saveMock).toHaveBeenCalledTimes(1);
    expect(saveMock).toHaveBeenCalledWith({ kind: 'REST', hour: 20 });
  });

  it('REQ-11 · 이미 동의(alreadyAgreed)도 저장한다', async () => {
    requestMock.mockResolvedValue('alreadyAgreed');
    saveMock.mockResolvedValue(reminder({ kind: 'DAILY', hour: 9, everOn: true }));

    const outcome = await enableReadingReminder('DAILY', 9, 'code-rest', 'settings');

    expect(outcome.status).toBe('on');
    expect(saveMock).toHaveBeenCalledWith({ kind: 'DAILY', hour: 9 });
  });

  it('REQ-11 · 거절이면 저장을 부르지 않는다', async () => {
    // 양성 대조는 바로 위 두 건(동의 → 저장 1회) — 직접 부르는 async 함수라 T-149의 「안 돈 핸들러」 함정이 아니다.
    requestMock.mockResolvedValue('agreementRejected');

    await expect(enableReadingReminder('REST', 20, 'code-rest', 'after_stop')).resolves.toEqual({ status: 'rejected' });
    expect(saveMock).not.toHaveBeenCalled();
  });

  it('REQ-11 · 미지원(null)·코드 없음이면 저장하지 않고 unsupported', async () => {
    requestMock.mockResolvedValue(null);
    await expect(enableReadingReminder('REST', 20, 'code-rest', 'after_stop')).resolves.toEqual({ status: 'unsupported' });

    await expect(enableReadingReminder('REST', 20, null, 'after_stop')).resolves.toEqual({ status: 'unsupported' });
    expect(requestMock).toHaveBeenCalledTimes(1); // 코드가 없으면 동의 창도 안 띄운다
    expect(saveMock).not.toHaveBeenCalled();
  });

  it('REQ-11 · 동의 요청 코드는 서버가 준 agreementCode 그대로다', async () => {
    requestMock.mockResolvedValue(null);

    await enableReadingReminder('REST', 20, 'booktimer-reading-reminder-rest', 'after_stop');

    expect(requestMock).toHaveBeenCalledWith('booktimer-reading-reminder-rest');
  });

  it('REQ-11 · 동의나 저장이 오류로 끝나면 던진다', async () => {
    requestMock.mockRejectedValue(Object.assign(new Error('x'), { code: 'NOTIFICATION_AGREEMENT_FAILED' }));
    await expect(enableReadingReminder('REST', 20, 'c', 'after_stop')).rejects.toThrow('x');

    requestMock.mockResolvedValue('newAgreement');
    saveMock.mockRejectedValue(new ApiError(409, '토스 계정이 연결되어 있지 않아요'));
    await expect(enableReadingReminder('REST', 20, 'c', 'after_stop')).rejects.toBeInstanceOf(ApiError);
  });

  it.each([
    ['newAgreement', () => requestMock.mockResolvedValue('newAgreement'), 'newAgreement', 'after_stop'],
    ['agreementRejected', () => requestMock.mockResolvedValue('agreementRejected'), 'agreementRejected', 'settings'],
    ['unsupported(null)', () => requestMock.mockResolvedValue(null), 'unsupported', 'after_stop'],
    [
      'error(동의 뒤 저장 실패)',
      () => {
        requestMock.mockResolvedValue('newAgreement');
        saveMock.mockRejectedValue(new ApiError(500, 'boom'));
      },
      'error',
      'settings',
    ],
  ] as const)('REQ-14 · 동의 흐름은 결과마다 동의 이벤트를 정확히 한 번 남긴다 — %s', async (_, arrange, result, entry) => {
    saveMock.mockResolvedValue(reminder({ kind: 'REST', everOn: true }));
    arrange();

    await enableReadingReminder('REST', 20, 'c', entry).catch(() => {});

    expect(trackMock).toHaveBeenCalledTimes(1);
    expect(trackMock).toHaveBeenCalledWith('reading_reminder_consent', { result, entry });
  });
});

describe('오류 문구 (agreementErrorMessage)', () => {
  it('REQ-11 · 오류 문구: TERMS_DISAGREED_MEMBER · UNSUPPORTED_APP_VERSION · ApiError 평문 · 그 밖', () => {
    expect(agreementErrorMessage({ code: 'TERMS_DISAGREED_MEMBER' })).toBe(
      '토스 설정 > 약관 및 개인정보 처리 동의에서 「사용자 최적화 제품 동의」를 켜야 알림을 받을 수 있어요.',
    );
    expect(agreementErrorMessage({ code: 'UNSUPPORTED_APP_VERSION' })).toBe('토스 앱을 최신으로 업데이트하면 알림을 켤 수 있어요.');
    expect(agreementErrorMessage(new ApiError(400, '알림 설정 값이 올바르지 않아요.'))).toBe('알림 설정 값이 올바르지 않아요.');
    expect(agreementErrorMessage(new Error('Bridge failed'))).toBe('알림을 켜지 못했어요. 잠시 후 다시 시도해 주세요.');
  });

  it('REQ-12 · 켜기가 아닌 저장(끄기·시각 변경) 실패는 「켜지 못했어요」가 아니라 저장 실패 문구다 — 서버 평문은 그대로', () => {
    expect(agreementErrorMessage(new Error('boom'), false)).toBe('알림 설정을 저장하지 못했어요. 잠시 후 다시 시도해 주세요.');
    expect(agreementErrorMessage(new ApiError(400, '알림 설정 값이 올바르지 않아요.'), false)).toBe('알림 설정 값이 올바르지 않아요.');
  });
});

describe('거절 캐시는 동의 흐름 한 곳이 쓴다', () => {
  it('REQ-10 · enableReadingReminder가 거절이면 제안 캐시에 agreementRejected를 남긴다 — 카드·설정 어느 쪽 거절이든 다시 조르지 않는다', async () => {
    requestMock.mockResolvedValue('agreementRejected');

    await enableReadingReminder('DAILY', 20, 'c', 'settings');

    expect(readOfferCache()).toBe('agreementRejected');
  });

  it('REQ-10 · 동의·미지원이면 캐시를 건드리지 않는다(양성 대조: 위 거절만 적는다)', async () => {
    requestMock.mockResolvedValue('newAgreement');
    saveMock.mockResolvedValue(reminder({ kind: 'REST', everOn: true }));
    await enableReadingReminder('REST', 20, 'c', 'after_stop');
    requestMock.mockResolvedValue(null);
    await enableReadingReminder('REST', 20, 'c', 'after_stop');

    expect(readOfferCache()).toBeNull();
  });
});

describe('설정 저장 경로 (changeReadingReminder)', () => {
  it('REQ-11 · 꺼짐→켬 + 동의면 동의를 먼저 묻고 저장 1회', async () => {
    const saved = reminder({ kind: 'DAILY', hour: 9, everOn: true });
    requestMock.mockResolvedValue('newAgreement');
    saveMock.mockResolvedValue(saved);

    await expect(changeReadingReminder(reminder(), { kind: 'DAILY', hour: 9 }, 'settings')).resolves.toEqual({
      status: 'saved',
      reminder: saved,
    });
    expect(requestMock).toHaveBeenCalledTimes(1);
    expect(saveMock).toHaveBeenCalledTimes(1);
    expect(saveMock).toHaveBeenCalledWith({ kind: 'DAILY', hour: 9 });
  });

  it('REQ-11 · 꺼짐→켬 + 거절이면 저장 0회이고 캐시가 agreementRejected다', async () => {
    requestMock.mockResolvedValue('agreementRejected');

    await expect(changeReadingReminder(reminder(), { kind: 'REST', hour: 20 }, 'settings')).resolves.toEqual({ status: 'rejected' });
    expect(saveMock).not.toHaveBeenCalled();
    expect(readOfferCache()).toBe('agreementRejected');
  });

  it('REQ-12 · 꺼진 채 시각만 바꾸면 동의 요청 0회 · {kind:OFF, hour} 저장', async () => {
    saveMock.mockResolvedValue(reminder({ hour: 21 }));

    const outcome = await changeReadingReminder(reminder(), { kind: 'OFF', hour: 21 }, 'settings');

    expect(outcome.status).toBe('saved');
    expect(requestMock).not.toHaveBeenCalled();
    expect(saveMock).toHaveBeenCalledWith({ kind: 'OFF', hour: 21 });
  });

  it.each([
    ['켠 채 방식 전환(매일→3일)', 'DAILY', { kind: 'REST', hour: 20 }],
    ['끄기', 'REST', { kind: 'OFF', hour: 20 }],
    ['켠 채 시각만 변경', 'DAILY', { kind: 'DAILY', hour: 19 }],
  ] as const)('REQ-12 · %s는 같은 동의문 안이라 동의 요청 0회 · 저장 1회', async (_, from, next) => {
    saveMock.mockResolvedValue(reminder({ ...next, everOn: true }));

    await changeReadingReminder(reminder({ kind: from, everOn: true }), next, 'settings');

    expect(requestMock).not.toHaveBeenCalled();
    expect(saveMock).toHaveBeenCalledTimes(1);
    expect(saveMock).toHaveBeenCalledWith(next);
  });
});

describe('제안 카드 「알림 받기」 (acceptReminderOffer)', () => {
  it('REQ-11 · 동의 먼저 — 동의하면 서버 기본 시각으로 「3일 쉬면」을 저장하고 켠 설정과 요약 상태를 돌려준다', async () => {
    const saved = reminder({ kind: 'REST', hour: 20, everOn: true });
    requestMock.mockResolvedValue('newAgreement');
    saveMock.mockResolvedValue(saved);

    await expect(acceptReminderOffer(reminder({ hour: 20 }))).resolves.toEqual({
      phase: { kind: 'on', summary: '독서 기록 없이 3일째가 되면 오후 8시에 한 번 알려 드려요.' },
      reminder: saved,
    });
    expect(requestMock).toHaveBeenCalledWith('booktimer-reading-reminder-rest');
    expect(saveMock).toHaveBeenCalledWith({ kind: 'REST', hour: 20 });
    expect(trackMock).toHaveBeenCalledWith('reading_reminder_consent', { result: 'newAgreement', entry: 'after_stop' });
  });

  it('REQ-11 · 거절이면 저장 없이 거절 상태 · 캐시 agreementRejected', async () => {
    requestMock.mockResolvedValue('agreementRejected');

    await expect(acceptReminderOffer(reminder())).resolves.toEqual({ phase: { kind: 'rejected' }, reminder: null });
    expect(saveMock).not.toHaveBeenCalled();
    expect(readOfferCache()).toBe('agreementRejected');
  });

  it('REQ-11 · 미지원이면 카드를 치운다(null) · 저장 없음', async () => {
    requestMock.mockResolvedValue(null);

    await expect(acceptReminderOffer(reminder())).resolves.toEqual({ phase: null, reminder: null });
    expect(saveMock).not.toHaveBeenCalled();
  });
});

describe('흐름 결과 → 카드 상태 (offerPhaseFor)', () => {
  it('REQ-10 · 흐름 결과 → 카드 상태: on이면 요약, rejected면 거절, unsupported면 카드를 치운다(null)', () => {
    const on = reminder({ kind: 'REST', hour: 21, everOn: true });
    expect(offerPhaseFor({ status: 'on', agreement: 'newAgreement', reminder: on })).toEqual({
      kind: 'on',
      summary: '독서 기록 없이 3일째가 되면 오후 9시에 한 번 알려 드려요.',
    });
    expect(offerPhaseFor({ status: 'rejected' })).toEqual({ kind: 'rejected' });
    expect(offerPhaseFor({ status: 'unsupported' })).toBeNull();
  });
});

describe('측정 종료 제안 카드 (ReadingReminderOffer)', () => {
  const offer = (phase: OfferPhase, hour = 20) =>
    wrap(<ReadingReminderOffer phase={phase} hour={hour} onPick={() => {}} onDismiss={() => {}} onGoSettings={() => {}} />);

  it('REQ-10 · 제안 카드(idle)는 「알림 받기」와 「괜찮아요」를 그리고, 본문에 「3일째」와 고른 시각 라벨이 들어간다', () => {
    const markup = offer({ kind: 'idle' }, 21);

    expect(markup).toContain(READING_REMINDER_OFFER_TITLE);
    expect(markup).toContain('알림 받기');
    expect(markup).toContain('괜찮아요');
    expect(markup).toContain('3일째');
    expect(markup).toContain('오후 9시');
  });

  it('REQ-10 · 제안 카드(on)는 요약과 「설정에서 바꿀 수 있어요」를 그린다', () => {
    const markup = offer({ kind: 'on', summary: '독서 기록 없이 3일째가 되면 오후 8시에 한 번 알려 드려요.' });

    expect(markup).toContain('독서 기록 없이 3일째가 되면 오후 8시에 한 번 알려 드려요.');
    expect(markup).toContain('설정에서 바꿀 수 있어요');
    expect(markup).not.toContain('알림 받기');
  });

  it('REQ-10 · 제안 카드(rejected)는 거절 문구', () => {
    expect(offer({ kind: 'rejected' })).toContain('동의하지 않아 알림을 켜지 않았어요. 설정에서 언제든 다시 켤 수 있어요.');
  });

  it('REQ-10 · 제안 카드(busy)는 버튼이 disabled · (error)는 문구를 띄운다', () => {
    // 「알림 받기」·「괜찮아요」 둘 다 잠긴다 — 동의 창이 뜬 사이 「괜찮아요」가 눌리면 캐시와 결과가 엇갈린다
    expect(offer({ kind: 'busy' }).match(/<button[^>]*disabled/g)).toHaveLength(2);
    expect(offer({ kind: 'idle' })).not.toMatch(/<button[^>]*disabled/); // 대조: idle은 누를 수 있다
    expect(offer({ kind: 'error', message: '알림을 켜지 못했어요. 잠시 후 다시 시도해 주세요.' })).toContain(
      '알림을 켜지 못했어요. 잠시 후 다시 시도해 주세요.',
    );
  });

  it('REQ-13 · 제안 카드 마크업에 position:fixed·z-index가 없다', () => {
    // 양성 대조: 같은 직렬화기가 fixed 요소를 `position:fixed`로 내보내는 것은 app.test.tsx(renderStep)가 확인한다.
    for (const phase of [{ kind: 'idle' }, { kind: 'on', summary: 's' }, { kind: 'rejected' }] as OfferPhase[]) {
      const markup = offer(phase);
      expect(markup).not.toMatch(/position:\s*fixed/);
      expect(markup).not.toMatch(/z-index/);
    }
  });

  it('REQ-13 · 제안 카드 버튼은 전부 옅은(weak) 버튼이다 — 홈의 채움은 탭바 원 하나', () => {
    for (const phase of [{ kind: 'idle' }, { kind: 'on', summary: 's' }] as OfferPhase[]) {
      const markup = offer(phase);
      // TDS는 variant를 인라인 커스텀 프로퍼티로 박는다 — weak = rgba(100, 168, 255, 0.15), fill = #3182f6(ui.test 실측).
      // 모든 TDS 버튼의 배경 값이 weak 값이어야 한다(개수 일치). 맨 <button>은 배경이 없는 글자 버튼뿐이다.
      const all = markup.match(/--button-background-color:[^;"]*/g) ?? [];
      const weak = markup.match(/--button-background-color:rgba\(100, 168, 255, 0\.15\)/g) ?? [];
      if (phase.kind === 'idle') expect(weak.length).toBeGreaterThan(0); // 양성 대조: 「알림 받기」가 잡힌다
      expect(all.length).toBe(weak.length);
    }
  });
});

describe('설정 섹션 (ReadingReminderSection)', () => {
  const section = (r: ReadingReminder | undefined, supported = true, extra: { error?: string | null; notice?: string | null } = {}) =>
    wrap(
      <ReadingReminderSection
        reminder={r}
        supported={supported}
        busy={false}
        error={extra.error ?? null}
        notice={extra.notice ?? null}
        onPickKind={() => {}}
        onPickHour={() => {}}
      />,
    );

  it('REQ-12 · 설정: 가용이 아니거나 옛 서버면 섹션이 없다', () => {
    // 빈 문자열 비교는 못 쓴다 — TDS Provider가 전역 스타일을 먼저 싣는다. 섹션의 마크를 본다.
    expect(section(undefined)).not.toContain('<section');
    expect(section(reminder({ available: false }))).not.toContain('<section');
    expect(section(reminder())).toContain('<section'); // 대조: 가용이면 선다
    expect(section(reminder())).toContain('독서 알림');
  });

  it('REQ-12 · 설정: 꺼짐+지원이면 방식 칩 셋(「끄기」만 aria-pressed=true)·시각 select·「알림이 꺼져 있어요.」, 꺼짐+미지원이면 업데이트 안내만 있고 칩이 없다', () => {
    const markup = section(reminder());

    expect(markup.match(/aria-pressed="true"[^>]*>끄기</g)).toHaveLength(1);
    expect(markup.match(/aria-pressed="true"/g)).toHaveLength(1);
    expect(markup).toContain('>매일<');
    expect(markup).toContain('>3일 쉬면<');
    expect(markup).toContain('<select');
    expect(markup).toContain('알림이 꺼져 있어요.');

    const unsupported = section(reminder(), false);
    expect(unsupported).toContain('이 토스 앱에서는 알림을 켤 수 없어요. 토스 앱을 최신으로 업데이트해 주세요.');
    expect(unsupported).not.toContain('aria-pressed');
    expect(unsupported).not.toContain('<select');
  });

  it('REQ-12 · 설정: 켬(매일)이면 「매일」만 pressed이고 select 값이 저장된 시각이며 토스 설정 각주가 있다', () => {
    const markup = section(reminder({ kind: 'DAILY', hour: 19, everOn: true }));

    expect(markup.match(/aria-pressed="true"/g)).toHaveLength(1);
    expect(markup).toMatch(/aria-pressed="true"[^>]*>매일</);
    expect(markup).toMatch(/<option value="19" selected="">오후 7시<\/option>/);
    expect(markup).toContain('토스 앱 설정에서도 알림을 끌 수 있어요.');
    expect(markup).toContain('매일 오후 7시까지 그날 독서 기록이 없으면 알려 드려요.');
  });

  it('REQ-12 · 설정: 켬 + 미지원 토스앱이면 업데이트 안내가 아니라 정상 섹션이다 — 켠 사람은 끌 수 있어야 한다', () => {
    const markup = section(reminder({ kind: 'DAILY', everOn: true }), false);

    expect(markup).toMatch(/aria-pressed="false"[^>]*>끄기</);
    expect(markup).toContain('<select');
    expect(markup).not.toContain('이 토스 앱에서는 알림을 켤 수 없어요');
  });

  it('REQ-12 · 설정: 켬(3일 쉬면)이면 「3일 쉬면」만 pressed이고 요약이 3일째 문구다', () => {
    const markup = section(reminder({ kind: 'REST', hour: 20, everOn: true }));

    expect(markup.match(/aria-pressed="true"/g)).toHaveLength(1);
    expect(markup).toMatch(/aria-pressed="true"[^>]*>3일 쉬면</);
    expect(markup).toContain('독서 기록 없이 3일째가 되면 오후 8시에 한 번 알려 드려요.');
  });

  it('REQ-12 · 설정: 시각 select의 선택지는 REMINDER_HOURS뿐이다', () => {
    const values = [...section(reminder()).matchAll(/<option value="(\d+)"/g)].map((m) => Number(m[1]));
    expect(values).toEqual(REMINDER_HOURS);
  });

  it('REQ-11 · 설정: 거절 안내·오류 문구가 섹션 안에 선다', () => {
    expect(section(reminder(), true, { notice: '동의하지 않아 알림을 켜지 않았어요.' })).toContain('동의하지 않아 알림을 켜지 않았어요.');
    expect(section(reminder(), true, { error: '토스 앱을 최신으로 업데이트하면 알림을 켤 수 있어요.' })).toContain(
      '토스 앱을 최신으로 업데이트하면 알림을 켤 수 있어요.',
    );
  });
});
