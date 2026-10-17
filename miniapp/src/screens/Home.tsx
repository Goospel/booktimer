import { Button } from '@toss/tds-mobile';
import type { CSSProperties, ReactNode } from 'react';
import { useEffect, useRef, useState } from 'react';

import type { BookOption, DashboardResponse, StudyBookRow, StudyState, TimerState, WaiveResponse } from '../api';
import { ApiError, waiveDebt } from '../api';
import type { TimerMode } from '../App';
import { useBackClose } from '../back';
import { Coachmark } from '../coachmark';
import { elapsedSeconds, formatClock, formatDuration, hasFinalConsonant } from '../format';
import {
  sessionGoalHandleLabel,
  sessionGoalSheetTarget,
  sessionGoalView,
  shouldHaptic,
  studySessionLine,
} from '../sessionGoal';
import {
  type AdSlotStatus,
  GOAL_MET_TEMPLATE_CODE,
  STUDY_GOAL_TEMPLATE_CODE,
  REWARD_AD_GROUP_ID,
  hapticOnce,
  notificationAgreementSupported,
  preloadAd,
  requestNotificationAgreement,
  useAdSlot,
  watchRewardAd,
} from '../toss';
import {
  Avatar,
  BookCover,
  CoverInitial,
  ARCH,
  ErrorMessage,
  GoalMedal,
  OUTLINE_PILL,
  RULE_ROW,
  SECTION_RULE,
  SERIF_VALUE,
  SPROUT_PATHS,
  Screen,
  SectionTitle,
  Sheet,
  TRACK,
  Text,
  sectionStyle,
} from '../ui';
import type { OfferPhase } from '../readingReminder';
import { READING_REMINDER_OFFER_TITLE, REST_DAYS, hourLabel } from '../readingReminder';
import { HomeFeedBox } from './HomeFeed';
import { SessionGoalSheet } from './SessionGoalSheet';

/** 알림 동의 결과 캐시 — 값은 토스가 준 결과 문자열 그대로. 정본은 토스이고 이건 카드 노출 스위치일 뿐이다. */
const AGREEMENT_KEY = 'booktimer.notificationAgreement';
/** 공부 동의문의 캐시 — 독서 키와 갈라야 독서에 이미 답한 사람에게도 공부 알림을 물을 수 있다. */
const STUDY_AGREEMENT_KEY = 'booktimer.notificationAgreement.studyGoal';

/**
 * 캐러셀 위치 점 — 지금 칸은 강조 잉크, 나머지는 선 색. 토큰이라 공부 모드에서 저절로 파랑이 된다.
 * 게스트 홈의 같은 점 줄도 이 둘을 쓴다(점 색이 두 화면에서 갈리지 않게).
 */
export const DOT_ON = 'var(--adaptiveBlue500, #1D1B18)';
export const DOT_OFF = 'var(--adaptiveGrey200, #D9D1C3)';

/**
 * 게이지 채움 — 강조 잉크 단색(톤 조율 A). 시안의 그라데이션은 재료를 하나 더 얹어 「쨍하다」의 한 몫이었다.
 * 토큰이라 공부 모드에서 파란 잉크가 된다. 트랙은 가는 선 색 막대(`TRACK`)다.
 */
const GAUGE_FILL = 'var(--adaptiveBlue500, #1D1B18)';

/**
 * 게이지 폭 — 아치 안쪽 폭(350 − 24×2 = 302)을 꽉 채우던 안 1 숫자에서 240(가운데)으로 줄였다(계획 §4 H1 · D5 추천).
 * 옆선 사이를 다 채우면 막대가 아치 밑선과 평행선 두 줄로 붙어 답답했다.
 */
const GAUGE_WIDTH = 240;

/**
 * 아치 그릇의 숫자(안 1 §7.1 + D5 추천) — 위 28 · 옆 24 · 아래 44(안 1 32 + 12), 최소 높이 252(안 1 240 + 12).
 * 늘어난 12는 머리띠 아래 간격(28 → 16)에서 되찾아 아치 아래 y가 안 1과 같다. 대안 ⓐ(폭만 240)는 아래 32 · 240 · 간격 28이다.
 * 피그마 숫자는 선 바깥에서 잰 값이라 CSS 패딩은 위아래 선 1px씩을 뺀다(27 · 43) — 그래야 표 줄 y가 안 1과 같다(목 모드 실측 346 → 344).
 */
const ARCH_PADDING = '27px 24px 43px';
const ARCH_MIN_HEIGHT = 252;
const MASTHEAD_GAP = 16;

/**
 * 시계 크기(D1 단계) — 「25:00」(5자)은 84, 한 시간을 넘겨 「01:25:00」(8자)이 되면 68. 84로 8자를 세우면 아치 안쪽 폭(302)을
 * 넘긴다(숫자 6 · 쌍점 2 — 계산 약 340, 실측은 U-5). 줄 높이는 크기 + 4(84/88 · 68/72).
 */
export function heroClockSize(text: string): number {
  return text.length > 5 ? 68 : 84;
}

/**
 * 아치 속 시계(D1) — 로그인 홈과 게스트 홈이 같은 것을 쓴다. 숫자는 한 자씩 1ch 칸에 세운다: Noto Serif KR엔 고정폭 숫자가 없어
 * `tabular-nums`가 먹지 않는다(U-4 실측 — 84px에서 「11:11」 177 · 「88:88」 211). 칸이 없으면 측정 중 매초 시계가 좌우로 흔들린다.
 * 칸마다 읽으면 「2, 5, …」로 끊기므로 읽기 이름은 그릇이 시계 글자 그대로 든다.
 */
export function HeroClock({ text, color }: { text: string; color: string }) {
  const size = heroClockSize(text);
  /*
   * 8자 시계는 좁은 폰에서 한 단 더 줄인다 — 아치 안쪽 폭(화면 − 바깥 여백 40 − 아치 옆 24×2 − 선 2)에 4.1em(8자 실측 폭
   * 273.9/68 ≈ 4.03em + 여유)이 들어가게. 390에선 68 그대로, 320에선 약 56(U-5 실측: 68 고정이면 320에서 옆선을 넘었다).
   * 칸(inline-block)이 줄바꿈 지점이 되므로 `nowrap`이 없으면 좁은 폭에서 시계가 두 줄로 꺾였다(같은 실측).
   */
  const fontSize = text.length > 5 ? `min(${size}px, calc((100vw - 90px) / 4.1))` : size;
  return (
    <div
      data-hero-clock=""
      role="timer"
      aria-label={text}
      style={{ ...SERIF_VALUE, fontSize, lineHeight: `${size + 4}px`, letterSpacing: '-0.02em', whiteSpace: 'nowrap', color }}
    >
      {[...text].map((ch, i) => (
        <span key={i} aria-hidden="true" style={/\d/.test(ch) ? { display: 'inline-block', width: '1ch', textAlign: 'center' } : undefined}>
          {ch}
        </span>
      ))}
    </div>
  );
}

/** 오늘 표 한 줄 — 아래 가는 선(RULE_ROW)이 줄을 가르고, 위 테두리는 아치 밑선이 잇는다. 왼쪽 말 · 오른쪽 값/손잡이. */
const TABLE_ROW = {
  ...RULE_ROW,
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'space-between',
  gap: 8,
  width: '100%',
  boxSizing: 'border-box',
} as const;

/**
 * 강조 잉크 — 통계 행의 ⓘ와 캡션 줄 「바꾸기 ›」가 쓰는 손잡이 색(웹 `--accent-hover`).
 *
 * <p>리터럴 `#4F6B4C`이 아니라 **토큰 경유**인 이유는 독서등(밤)이다 — 다만 **지금 이 자리에선
 * 픽셀이 같다**: 히어로 카드는 `LAMP_PAGE_CLASS`를 달고 있고 `global.css`의
 * `body.reading-lamp .lamp-page`가 `--adaptiveBlue700`을 낮값으로 도로 고정하기 때문이다(실측 확인).
 * 그러니 이건 「안 그러면 깨진다」가 아니라 **방어**다 — 이 조각이 등불 밖으로 옮겨지거나 그 예외가
 * 걷히는 날 리터럴이면 조용히 낮 색으로 남는다. 값이 같을 때 토큰을 쓰는 편이 나중을 안 망친다.
 */
const ACCENT = 'var(--adaptiveBlue700, #1D1B18)';

/**
 * 측정 중 안심 문구 — 이 앱의 핵심 계약은 "화면을 꺼도 서버가 센다"인데 측정 중 화면에 그 말이 없었다.
 * 첫 세션에 한정하지 않는다: 짧고 무해하며, 잊어버리는 건 신규 유저만이 아니다.
 */
export const ACTIVE_SESSION_RELIEF = '화면을 꺼도 측정은 계속돼요. 책 읽고 오세요.';

/** 공부 모드의 같은 말 — 계약은 같고 하는 일만 다르다. */
export const ACTIVE_STUDY_RELIEF = '화면을 꺼도 측정은 계속돼요. 공부하고 오세요.';

/**
 * 히어로 머리말 — 모드가 갈리고, 「달성」으로 한 번 더 갈리는 자리다.
 *
 * <p>`null`은 <b>새싹 머리말</b>(「오늘 목표 달성」)을 뜻한다 — 그 자리는 글자가 아니라 SVG를 품어
 * 문자열로 못 돌려준다. 새싹은 <b>독서 하루 목표</b>의 것이다 — 공부 하루 목표는 2026-09-13 책별 「회당 시간」으로
 * 대체돼 공부 모드에선 달성이 오지 않는다(호출부가 `false`를 넘긴다).
 */
export function heroOverline(mode: TimerMode, achieved: boolean): string | null {
  if (achieved) return null;
  return mode === 'study' ? '오늘 공부한 시간' : '오늘 읽은 시간';
}

/**
 * 아치 꼭대기 가운데의 「독서 | 공부」 세그먼트 — 이 앱에서 <b>모드를 바꾸는 유일한 손잡이</b>다.
 *
 * <p>안 1(에디토리얼 PR-2): 밑줄형 — 고른 칸 아래 강조 잉크 2px, 칸은 58×44. 옛 Soft의 눌린 트랙·찬 알약은 걷었다.
 * 세그먼트 버튼이 <b>그 자체로 44px</b>를 든다(손가락 최소치 44를 버튼 사각형이 직접 넘게, U-8).
 *
 * <p>선택 세그먼트의 색이 <b>토큰</b>인 것이 요점이다 — `body.study-mode`가 토큰을 갈아 끼우면 이 알약도
 * 코드 한 줄 없이 파랑으로 따라온다.
 *
 * <p>⚠️ 선택 배경은 <b>삼항 안에</b> 둔다. `typography.test`의 「채움 버튼 개수」 가드가 소스에서
 * `background: '` + `var(--adaptiveBlue700` 꼴을 채움 버튼으로 세므로, 그 꼴의 리터럴로 적으면 이 토글이
 * 홈의 두 번째 채움으로 오검출된다(홈의 채움은 탭바 원 하나다).
 *
 * <p>측정 중엔 `aria-disabled`로 잠근다(진짜 `disabled`가 아니다 — 그러면 클릭이 안 와서 <b>왜</b>
 * 못 바꾸는지 말할 기회가 사라진다. 탭 잠금과 같은 문법).
 */
export function ModeToggle({
  mode,
  locked,
  onChange,
  onBlocked,
}: {
  mode: TimerMode;
  /** 측정 중인가 — 재는 도중 모드를 갈면 어느 원장에 쌓이는지가 화면과 어긋난다. */
  locked: boolean;
  onChange: (mode: TimerMode) => void;
  onBlocked: () => void;
}) {
  const segment = (target: TimerMode, label: string) => {
    const selected = mode === target;
    return (
      <button
        type="button"
        aria-pressed={selected}
        aria-disabled={locked ? true : undefined}
        onClick={() => (locked ? onBlocked() : onChange(target))}
        style={{
          // flex라야 글자가 가운데 선다 — inline 흐름이면 상속 폰트의 line box strut이 글자를 아래로 민다.
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          minWidth: 58,
          minHeight: 44,
          padding: '0 12px',
          boxSizing: 'border-box',
          // 안 1 밑줄형(spec §7.1 modeToggle) — 고른 칸 아래 강조 잉크 2px + 강조 글자 500, 채움 없음(검정 채움은 주 동작 하나뿐).
          // 안 고른 칸도 같은 2px(투명)이라 고르기가 바뀌어도 칸 높이가 안 변한다. 강조 토큰이라 공부 모드에서 파란 잉크다.
          border: 0,
          borderBottom: `2px solid ${selected ? 'var(--adaptiveBlue700, #1D1B18)' : 'transparent'}`,
          borderRadius: 0,
          background: 'transparent',
          color: selected ? 'var(--adaptiveBlue700, #1D1B18)' : 'var(--adaptiveGrey600, #645E54)',
          fontFamily: 'inherit',
          fontSize: 14,
          fontWeight: selected ? 500 : 400,
          cursor: 'pointer',
        }}
      >
        {label}
      </button>
    );
  };

  return (
    <div
      data-mode-toggle=""
      style={{
        display: 'flex',
        flex: 'none',
        gap: 4,
        // 그릇의 면·테는 걷었다(에디토리얼 — 면 없음). 고른 칸의 잉크 테가 「여기」를 말한다.
        opacity: locked ? 0.4 : 1,
      }}
    >
      {segment('reading', '독서')}
      {segment('study', '공부')}
    </div>
  );
}

/**
 * 새싹 표식 — 줄기 하나에 잎 두 장짜리 획 SVG. 기본 이모지를 쓰던 자리를 대신한다.
 *
 * <p>기본 이모지가 불쾌한 이유는 형식이 아니라 <b>집어온 표식</b>이라는 데 있다(`no-emoji.test.ts`).
 * 그래서 카드의 ⓘ와 같은 문법으로 그린다 — 24 격자·2px 획·둥근 끝이라 옆에 서도 한 벌로 읽힌다.
 *
 * <p>색은 속성이 아니라 style로 준다 — 프레젠테이션 속성엔 `var()`가 안 먹어, 토큰이 죽으면 독서등에서
 * 이 표식만 낮 색으로 남는다(같은 파일 ⓘ 아이콘과 같은 이유).
 *
 * <p>`data-sprout`은 계측 손잡이다: TDS가 뿜는 emotion 클래스 사이에서 이 조각을 집을 유일한 수단이라
 * `data-cover-title` 관례를 따른다.
 */
function SproutMark({ size }: { size: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      data-sprout=""
      style={{ stroke: ACCENT, flex: 'none', verticalAlign: '-2px' }}
    >
      {SPROUT_PATHS.map((d) => (
        <path key={d} d={d} />
      ))}
    </svg>
  );
}

/**
 * 독서등이 켜져도 <b>밝게 남는 자리</b>의 표식 — 히어로 카드 하나뿐이다.
 *
 * <p>이 문자열이 js와 `global.css`를 잇는 매듭이라 한쪽만 고치면 카드가 밤 속으로 사라진다
 * (`home.test.tsx`가 css에 셀렉터가 실재하는지 본다). 스위치는 `App`의 {@link LAMP_CLASS}.
 */
export const LAMP_PAGE_CLASS = 'lamp-page';

/**
 * 첫 완료 축하 배너 — 서버 `firstCompletedSession`이 참인 그 한 번만. 잔디는 1초만 읽어도 lv1로
 * 점등되는데(`ContributionGraphBuilder.levelFor`) 그걸 보여 줄 자리가 홈에 없다 — 잔디 미리보기가
 * 피드 박스에 자리를 내줬으므로 **기록 탭**을 가리킨다(하이라이트 테두리는 가리킬 카드와 함께 사라졌다).
 *
 * <p>화면에서 꺼내 둔 이유는 늘 같다 — 하니스가 정적 렌더라 「측정 끝내기」를 눌러 켜진 상태에
 * 도달할 수 없다(`BookSheet`와 같은 처지).
 */
export function FirstSessionBanner({ show }: { show: boolean }) {
  if (!show) return null;

  return (
    // 에디토리얼 PR-2 — 면 없는 한 줄. 오늘 표 바로 밑에 붙어 표의 마지막 줄처럼 선다(위 선 = 표의 마지막 선, 아래 가는 선).
    // 위에도 선을 그으면 표의 마지막 선과 겹쳐 두 줄이 된다(목 모드 컷 실측).
    <div
      data-first-session-banner=""
      style={{
        padding: '12px 0',
        borderBottom: '1px solid var(--adaptiveGrey200, #D9D1C3)',
        textAlign: 'center',
      }}
    >
      <Text typography="st11" style={{ display: 'block', wordBreak: 'keep-all' }}>
        <SproutMark size={14} /> 첫 독서 기록이 심어졌어요! 기록 탭에 첫 칸이 생겼어요.
      </Text>
    </div>
  );
}

/** 카드 안 글자 버튼 — 채움도 테두리도 없는 보조 동작(「괜찮아요」·설정으로). 홈의 채움은 탭바 원 하나다. */
const TEXT_BUTTON: CSSProperties = {
  display: 'block',
  width: '100%',
  marginTop: 4,
  padding: '10px 0',
  border: 'none',
  background: 'none',
  font: 'inherit',
  cursor: 'pointer',
};

/**
 * 독서 알림 제안(N3) — <b>독서 측정을 끝낸 직후에만</b> 홈 안에 서는 카드. 권하는 것은 「3일 안 읽으면 한 번」
 * 하나다(시각을 추측하지 않는다 — 「매일」은 설정에서 시각을 고른 뒤 켠다).
 *
 * <p>화면을 덮지 않는다(T-183) — position·z-index 없는 섹션 한 장이고, 사용자가 「알림 받기」를 눌러야 토스 동의
 * 창이 뜬다. 상태(`phase`)를 프롭으로 받는 이유는 이 파일의 다른 카드와 같다: 정적 렌더 하니스가 클릭을 못 돌린다.
 */
export function ReadingReminderOffer({
  phase,
  hour,
  onPick,
  onDismiss,
  onGoSettings,
}: {
  phase: OfferPhase;
  /** 켤 때 쓸 시각 — 서버 기본값(20시). 본문이 이 값을 말한다. */
  hour: number;
  onPick: () => void;
  onDismiss: () => void;
  onGoSettings: () => void;
}) {
  // 한 장의 카드가 상태에 따라 속만 갈아 끼운다(카드가 사라졌다 다시 서면 자리가 흔들린다).
  return (
    <section style={sectionStyle}>
      {phase.kind === 'on' ? (
        <>
          <Text typography="st11" style={{ display: 'block', wordBreak: 'keep-all' }}>
            {phase.summary}
          </Text>
          <button type="button" onClick={onGoSettings} style={TEXT_BUTTON}>
            <Text typography="st12" color="grey600">
              매일 알림·시각은 설정에서 바꿀 수 있어요 ›
            </Text>
          </button>
        </>
      ) : phase.kind === 'rejected' ? (
        <Text typography="st12" color="grey600" style={{ display: 'block', wordBreak: 'keep-all' }}>
          동의하지 않아 알림을 켜지 않았어요. 설정에서 언제든 다시 켤 수 있어요.
        </Text>
      ) : (
        <>
          <Text typography="st11" fontWeight="medium" style={{ display: 'block' }}>
            {READING_REMINDER_OFFER_TITLE}
          </Text>
          <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 6, wordBreak: 'keep-all' }}>
            독서 기록 없이 {REST_DAYS}일째가 되면 {hourLabel(hour)}에 토스 알림으로 한 번 알려 드려요.
          </Text>
          <ErrorMessage message={phase.kind === 'error' ? phase.message : null} />
          <Button
            display="block"
            variant="weak"
            size="medium"
            style={{ marginTop: 14 }}
            disabled={phase.kind === 'busy'}
            onClick={onPick}
          >
            알림 받기
          </Button>
          <button type="button" onClick={onDismiss} disabled={phase.kind === 'busy'} style={TEXT_BUTTON}>
            <Text typography="st12" color="grey600">
              괜찮아요
            </Text>
          </button>
        </>
      )}
    </section>
  );
}

/**
 * 목표를 채운 순간의 히어로 속(시안 Soft-Goal) — 메달이 한 번 튀고, 연속 일수와 「내일도 N분이면」을 말한다.
 *
 * <p><b>카드 안</b>이다 — 화면을 덮는 시트·딤이 아니라 히어로의 속만 갈아 끼운다(T-183). 닫는 버튼이 따로
 * 없는 것도 그래서다: 「이어서 읽기」(측정 시작)나 탭 이동이 곧 닫기이고, 그 뒤엔 새싹 머리말이 달성을 말한다.
 *
 * <p>「기록 보기」는 채움이 아니라 외곽선이다 — 홈의 채움은 탭바 원 하나다(설계 D5).
 */
export function GoalReachedView({
  streak,
  goalSeconds,
  todayRead,
  onContinue,
  onGoHistory,
}: {
  /** 연속 일수 — stop 응답의 잔디가 `onGraphChange`로 이미 갱신돼 오늘이 들어 있다. */
  streak: number;
  goalSeconds: number;
  todayRead: number;
  onContinue: () => void;
  onGoHistory: () => void;
}) {
  const button = { height: 56, borderRadius: 999, fontFamily: 'inherit', fontSize: 17, fontWeight: 500, cursor: 'pointer' } as const;

  return (
    // 아치(가운데 정렬 flex) 속에서도 안쪽 폭을 다 쓰게 `alignSelf: stretch` — 아니면 줄·버튼이 글자 폭으로 줄어든다.
    <div style={{ alignSelf: 'stretch', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 16, paddingTop: 6 }}>
      <GoalMedal />
      <div style={{ textAlign: 'center', wordBreak: 'keep-all' }}>
        <span style={{ display: 'block', ...SERIF_VALUE, fontSize: 26 }}>오늘 목표를 채웠어요</span>
        <span style={{ display: 'block', marginTop: 6, fontSize: 17, lineHeight: 1.5, color: 'var(--adaptiveGrey700, #4A453E)' }}>
          연속 {streak}일째예요.
          <br />
          {/* 길이가 사용자 값이라 조사를 고정할 수 없다 — 「30분이면」/「1시간 30초면」(게스트 「잔디 첫 칸」과 같은 처방). */}
          내일도 {formatDuration(goalSeconds)}
          {hasFinalConsonant(formatDuration(goalSeconds)) ? '이면' : '면'} {streak + 1}일이 돼요.
        </span>
      </div>
      {/* 면 없는 줄 — 위아래 가는 선(오늘 표와 같은 문법). */}
      <div
        style={{
          alignSelf: 'stretch',
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          padding: '10px 0',
          borderTop: '1px solid var(--adaptiveGrey200, #D9D1C3)',
          borderBottom: '1px solid var(--adaptiveGrey200, #D9D1C3)',
          textAlign: 'left',
        }}
      >
        <span style={{ fontSize: 14, color: 'var(--adaptiveGrey600, #645E54)' }}>오늘 읽은 시간</span>
        <span style={{ marginLeft: 'auto', ...SERIF_VALUE, fontSize: 24 }}>{formatClock(todayRead)}</span>
        <svg width="26" height="26" viewBox="0 0 24 24" aria-hidden="true" style={{ flex: 'none' }}>
          <circle cx="12" cy="12" r="11" style={{ fill: 'var(--adaptiveBlue700, #1D1B18)' }} />
          <path d="M6.5 12.5l3.5 3.5 7.5-8" fill="none" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round" style={{ stroke: 'var(--filledInk, #F5F0E6)' }} />
        </svg>
      </div>
      <div style={{ alignSelf: 'stretch', display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: 10 }}>
        <button type="button" onClick={onContinue} style={{ ...OUTLINE_PILL, ...button }}>
          이어서 읽기
        </button>
        {/* 둘 다 외곽선 알약 — 홈의 검정 자리는 탭바 원 하나다(계획 PR-2 #9). */}
        <button type="button" onClick={onGoHistory} style={{ ...OUTLINE_PILL, ...button }}>
          기록 보기
        </button>
      </div>
    </div>
  );
}

/**
 * 처음 골라 둘 책 — 최근 읽은 책(=이어 읽기)이 읽는 중 목록에 있으면 그 책, 아니면 첫 책, 없으면 `null`.
 *
 * <p>웹 `BookPickForm`의 `defaultBook`과 같은 규칙이다 — <b>읽는 중 안에서만</b> 고른다(웹도 R2 PR-2부터 같다).
 * `recentBookId`가 목록 밖일 수 있는 건 그 책을 다 읽었거나 뺐기 때문이다 — 그때 아무것도 안 고른 채로 두면
 * "측정 시작"이 죽은 버튼이 된다.
 */
export function defaultBookId(readingBooks: { id: number }[], recentBookId: number | null): number | null {
  return readingBooks.find((b) => b.id === recentBookId)?.id ?? readingBooks[0]?.id ?? null;
}

/**
 * 측정이 시작할 책 — 홈 캐러셀 가운데 칸과 탭바 가운데 원이 <b>같은 값</b>을 본다(단일 출처).
 *
 * <p>캐러셀의 문법은 "가운데 온 것이 곧 측정 대상"이고 그 선택은 App이 든다(`homeBookId`). 다른 탭에서
 * 눌러도 홈에 지금 가운데 와 있는 그 책으로 시작하는 것이 같은 문법의 연장이다 — 서재에서 보던 책으로
 * 시작하지 않는 이유도 이것이다(측정 대상의 출처가 둘이 되면 무엇으로 시작될지 예측할 수 없다).
 *
 * <p>어떤 조합에서도 최소 `null`(「책 없이」)까지는 떨어져 **죽은 버튼이 될 수 없다**. 고른 책이 서재에서
 * 빠졌으면(stale id) 「책 없이」로 강등한다 — 홈도 이 값으로 캐러셀·여백 문을 세워 「화면이 가리키는 칸 =
 * ▶가 시작할 대상」이 된다(R2 P6-a). App이 아니라 여기 사는 이유는 순환 import 회피다(App이 re-export한다).
 */
export function timerStartBookId(
  readingBooks: { id: number }[],
  recentBookId: number | null,
  homeBookId: number | null | undefined,
): number | null {
  const picked = homeBookId === undefined ? defaultBookId(readingBooks, recentBookId) : homeBookId;
  return readingBooks.some((b) => b.id === picked) ? picked : null;
}

/**
 * 서재가 0권에서 생길 때 홈 선택 보정(R2 P6-b) — 0권에서 「책 없이」(`null`)를 고른 사람이 첫 책을 담으면
 * 「아직 안 고름」(`undefined`)으로 되돌려 기본 책(방금 담은 책)이 가운데 오게 한다. 그 밖엔 고른 값 그대로다 —
 * 책이 있는데 「책 없이」를 고른 것은 의도다.
 */
export function homePickAfterShelf(
  prevCount: number,
  nextCount: number,
  pick: number | null | undefined,
): number | null | undefined {
  return prevCount === 0 && nextCount > 0 && pick === null ? undefined : pick;
}

/** 시트 한 행 — 읽는 중이 아닌 후보엔 서재 탭과 같은 상태 라벨이 붙는다. */
export type SheetBook = BookOption & { statusLabel?: '읽고 싶어요' | '다 읽음' };

/**
 * 태깅·교체 시트의 독서 후보 — 캐러셀(「읽는 중」)보다 <b>넓다</b>(R2 결정 4). 순서는 읽는 중 → 읽고 싶어요 →
 * 다 읽음이고, 전환 직후 한 책이 두 목록에 잠깐 같이 있어도 <b>id로 한 행</b>이다(먼저 나온 상태가 이긴다).
 *
 * <p>`wantToReadBooks`가 선택인 것은 옛 서버의 `TimerState`가 안 싣기 때문이다 — 그때는 두 상태로 선다.
 */
export function taggableBooks(d: Pick<TimerState, 'readingBooks' | 'wantToReadBooks' | 'finishedBooks'>): SheetBook[] {
  const rows: SheetBook[] = [
    ...d.readingBooks,
    ...(d.wantToReadBooks ?? []).map((b) => ({ ...b, statusLabel: '읽고 싶어요' as const })),
    ...d.finishedBooks.map((b) => ({ ...b, statusLabel: '다 읽음' as const })),
  ];
  return rows.filter((b, i) => rows.findIndex((r) => r.id === b.id) === i);
}

/**
 * 홈 여백 문이 가리키는 책 — 없으면 `null`(문을 그리지 않는다).
 *
 * <p>「측정 시작」을 누르듯 <b>1탭에 작성 화면</b>으로 보내려면 지금 이 화면이 어느 책을 뜻하는지가
 * 먼저 정해져야 한다: 측정 중이면 그 책, 대기 중이면 캐러셀에서 고른 책이다. 활성 책 id는 대시보드에
 * 따로 없지만 `recentBookId`가 `startedAt desc`(활성 세션 포함)라 책을 걸고 측정 중이면 그게 활성 책이고,
 * 책 없이 측정 중인 경우는 `activeBookTitle === null`이 걸러 낸다.
 *
 * <p><b>공개 여부는 조건이 아니다</b> — 비공개 책의 여백은 나만 보는 메모다(설계 결정 2). 반면
 * 핸들(@아이디)이 없으면 그린다 해도 열리지 않는다: 서버가 여백 대상을 loginId로만 찾아 자기 여백에도
 * 닿을 수 없다(결정 A — 핸들 안내는 책방 탭 「아이디 만들기」가 이미 맡는다).
 */
export function marginDoorBook(dashboard: DashboardResponse, selectedBookId: number | null): BookOption | null {
  if (dashboard.loginId === null) return null;
  // 완독 책까지 보는 이유: 웹에선 다 읽은 책으로도 측정을 시작할 수 있어 활성 책이 그쪽에만 있을 수 있다.
  const pool = [...dashboard.readingBooks, ...dashboard.finishedBooks];
  const id = dashboard.hasActiveSession
    ? (dashboard.activeBookTitle === null ? null : dashboard.recentBookId)
    : selectedBookId;
  return pool.find((b) => b.id === id) ?? null;
}

/** 캐러셀 표지 한 장의 폭·간격 — 계산(가운데 인덱스)과 스타일이 같은 값을 봐야 스냅과 선택이 어긋나지 않는다. */
/**
 * 에디토리얼 PR-2(안 1 §7.3) — 옆 표지 88 · 사이 32. 고른 표지는 104로 커지며 양옆 8씩 먹어 보이는 간격이 24가 되고,
 * 판에 걸친 알약(144)이 이웃 표지에 닿지 않는 여유 4가 남는다.
 */
export const COVER_WIDTH = 88;
export const COVER_GAP = 32;

/** 표지 한 장의 높이 — `BookCover`·`CoverInitial`이 쓰는 식(폭 × 1.4) 그대로여야 여백 계산이 실제 표지를 따라간다. */
export const COVER_HEIGHT = Math.round(COVER_WIDTH * 1.4);

/** 고른 표지의 배율 — 88 → 104(안 1 `r5/cover-sel`). transform이라 레이아웃 폭이 안 변해 스냅·가운데 계산은 상수만 따른다. */
const PICKED_SCALE = 104 / 88;

/** 캐러셀 칸·표지 모서리 — 에디토리얼 2(표지는 물건이라 각이 선다). 옛 8은 후광과 동심이던 값이다. */
const CAROUSEL_RADIUS = 2;

/**
 * 트랙 위 여백 — 고른 표지가 **바닥을 기준으로** 커지므로(`transform-origin: bottom center`) 위로만
 * 높이 × (104/88 − 1) ≈ 22.4px 솟는다. 그 몫을 위에만 잡는다(아래는 0 — 판이 표지 바닥에 바로 붙는다).
 * 여기가 모자라면 커진 표지 윗변이 스크롤 영역에 잘린다.
 */
export const TRACK_V_PAD = Math.ceil(COVER_HEIGHT * (PICKED_SCALE - 1));

/**
 * 판 아래 넘침 자리 — 걸친 알약(보이는 44)의 3/4(33)이 판 아래로 내려온다(1/4 걸침 · 계획 §4 H2). 고르기가 바뀌어도
 * 아래 제목 줄이 들썩이지 않게 문이 없는 칸(「책 없이」)에서도 같은 자리를 둔다.
 */
const DOOR_HEIGHT = 44;
const DOOR_OVERLAP = 11;

/**
 * 첫·마지막 표지를 가운데까지 올려 주는 여백 — **트랙의 padding이 아니라 양끝 표지의 margin으로 준다.**
 *
 * <p>WebKit(iOS 웹뷰)은 스크롤 컨테이너의 **끝쪽 padding을 스크롤 영역에서 뺀다**. 그래서 padding으로 주면
 * 표지가 적을수록 밀 여지가 통째로 사라진다 — 폭 390 화면에서 2권이면 여지가 0이라 아예 안 밀렸고(실기기
 * 실측) 4권이면 절반만 밀렸다. flex 아이템의 margin은 어느 엔진에서나 스크롤 영역에 들어간다.
 *
 * <p>값이 같으니 i번째 표지가 가운데 오는 위치는 그대로 `i * (표지폭 + 간격)`이다.
 */
export const EDGE_SPACE = `calc(50% - ${COVER_WIDTH / 2}px)`;

/**
 * 스크롤 위치 → 가운데 온 책의 인덱스.
 *
 * <p>트랙 좌우에 `50% - 표지폭/2`의 여백을 둬서 i번째 표지가 가운데 오는 위치가 정확히
 * `i * (표지폭 + 간격)`이 된다 — 그래서 화면 폭이 이 식에서 빠진다(여백이 이미 그 몫을 했다).
 *
 * <p>양끝은 클램프한다: iOS 바운스로 음수가, 관성 스크롤로 마지막 칸을 넘긴 값이 들어오는데
 * 그대로 인덱스로 쓰면 목록 밖(`undefined`)을 찌른다.
 */
export function centeredIndex(scrollLeft: number, itemWidth: number, gap: number, count: number): number {
  const index = Math.round(scrollLeft / (itemWidth + gap));
  return Math.min(Math.max(index, 0), Math.max(count - 1, 0));
}

/**
 * 미끄러지는 이동을 CSS에 맡긴다 — `scrollTo({behavior})`는 옵션 객체를 통째로 무시하는 웹뷰가 있어
 * 실기기에서 표지가 제자리에 머물렀다. `scrollLeft` 대입은 어디서나 먹고, 애니메이션은 이 값이 맡는다.
 */
const TRACK_SCROLL_BEHAVIOR = 'smooth';

/**
 * 표지 하나를 가운데로 — 가운데 정렬 여백 덕에 목표 위치가 곧 `index * stride`다.
 *
 * <p>`instant`면 CSS 애니메이션을 잠시 꺼서 즉시 옮긴다(첫 진입은 처음부터 거기 있었던 것처럼 보여야 한다).
 */
function scrollToIndex(track: HTMLDivElement | null, index: number, instant = false): void {
  if (track === null) return;
  if (instant) track.style.scrollBehavior = 'auto';
  track.scrollLeft = index * (COVER_WIDTH + COVER_GAP);
  if (instant) track.style.scrollBehavior = TRACK_SCROLL_BEHAVIOR;
}

/**
 * 선택값 → 캐러셀 칸 인덱스. **0번은 언제나 「책 없이」**라 책은 한 칸씩 밀린다.
 *
 * <p>목록 밖 id(다 읽었거나 뺀 책이 stale하게 남는 경우)는 0번으로 떨어진다 — 화면 밖을 찌르느니
 * 「책 없이」가 가운데 온 상태가 정직하다(탭바 액션도 그때 `startSession(null)`을 보낸다 — `timerStartBookId`).
 */
export function carouselIndexOf(selectedId: number | null, books: BookOption[], offset = 1): number {
  if (selectedId === null) return 0;
  const index = books.findIndex((b) => b.id === selectedId);
  return index >= 0 ? index + offset : 0;
}

/**
 * 캐러셀 칸 인덱스 → 선택값. 0번(또는 범위 밖 방어)이면 `null` = 책 없이.
 *
 * <p>`offset`은 책 앞에 붙는 칸 수다 — 홈은 「책 없이」가 0번이라 1, 「책 없이」가 없는 서재는 0.
 */
export function selectionAt(index: number, books: BookOption[], offset = 1): number | null {
  return books[index - offset]?.id ?? null;
}

/**
 * 밖에서 바뀐 선택을 따라갈 칸 — 옮길 필요가 없으면 `null`.
 *
 * <p>트랙을 옮기는 길이 마운트 1회와 표지 탭뿐이라, 「펼쳐보기」 격자처럼 **밖에서** 선택이 바뀌면
 * 제목 줄만 그 책으로 갈리고 표지는 옛 자리에 남았다. 그 갈림을 여기서 메운다.
 *
 * <p>이미 그 칸이 가운데면 `null`이다 — 손가락으로 민 결과(스냅이 끝난 자리)에 또 스크롤을 걸면
 * 재스냅이 손가락에서 트랙을 빼앗는다(`SCROLL_SETTLE_MS` 주석과 같은 사고).
 */
export function recenterIndex(
  scrollLeft: number,
  selectedId: number | null,
  books: BookOption[],
  offset = 1,
): number | null {
  const target = carouselIndexOf(selectedId, books, offset);
  const current = centeredIndex(scrollLeft, COVER_WIDTH, COVER_GAP, books.length + offset);
  return current === target ? null : target;
}

/**
 * 「책 없이」 카드 아래 부제 — 0권 사용자에게는 이 한 줄이 서재로 가는 유일한 안내다
 * (「첫 책 추가하기」 빈 상태가 캐러셀에 흡수되며 사라졌고, 진입은 하단 탭바가 맡는다).
 *
 * <p>책이 있는데 「책 없이」를 고른 사람에겐 나중에 붙이는 자리를 알린다 — 기록 탭에서 측정 한 건씩 붙인다(R2).
 * 전 문구 「기록에 책이 남지 않아요」는 그 자리가 생기면서 거짓이 됐다.
 */
export function noBookSubtitle(bookCount: number): string {
  return bookCount > 0 ? '끝난 뒤 기록 탭에서 붙일 수 있어요' : '서재에 책을 추가하면 여기서 골라요';
}

/**
 * 스크롤이 멎었다고 보는 시간 — 미는 도중에 선택을 갱신하면 그 리렌더가 `scroll-snap: mandatory`의
 * 재스냅을 불러 손가락에서 스크롤을 빼앗는다(실기기에서 좌우로 밀리지 않던 원인). 손을 뗀 뒤 한 번만 고른다.
 */
export const SCROLL_SETTLE_MS = 120;

/**
 * 「읽는 중」 카드의 표지 폭 — 캐러셀(84)보다 작다. 그곳은 고르는 무대라 크게 서야 하지만,
 * 여기는 이미 정해진 한 권을 한 줄로 알려 주는 자리라 제목·저자와 높이가 맞아야 한다.
 */
export const READING_NOW_COVER = 64; // 에디토리얼 PR-2 — 옷 시안 M-HOME-06의 64×90

/**
 * 캐러셀 0번의 특수 칸 — 화면마다 뜻이 다르다. 홈은 「책 없이」(측정 대상의 한 갈래), 서재는
 * 「책 추가」(검색 화면으로 가는 문)다.
 *
 * <p>부품과 규약(<b>0번은 언제나 특수 칸</b>)은 공유하되 문구만 갈아 끼운다 — 두 화면이 같은
 * 문법을 쓰면 「가운데 온 것이 곧 대상」 한 문장으로 둘 다 설명된다.
 */
export interface LeadCard {
  /** 점선 상자 안 글자 — 짧아야 84px 안에서 안 접힌다. */
  label: string;
  /** 이 칸이 가운데일 때의 제목 자리이자 스크린리더가 읽는 이름. */
  title: string;
  /** 제목 아래 한 줄. 홈이 책 수에 따라 문구를 바꾸므로 값이 아니라 함수다. */
  subtitle: (bookCount: number) => string;
}

/** 홈의 0번 칸 — 측정 대상의 한 갈래라 「고른 값」(`null`)으로 남는다. */
export const NO_BOOK_CARD: LeadCard = {
  label: '책 없이',
  title: '책 없이 측정',
  subtitle: noBookSubtitle,
};

/**
 * 0번 칸의 자리 표지 — 표지와 **같은 크기의 점선 상자**다.
 *
 * <p>`CoverInitial`을 쓰지 않는다: 색 상자 + 첫 글자라 실제 표지와 구분이 안 돼 특수 칸이 책처럼 보인다.
 * `boxSizing`이 없으면 테두리 2px이 칸을 불려 스냅 위치(`i × stride`)가 이 카드부터 어긋난다.
 */
export function NoBookCard({
  width = COVER_WIDTH,
  label = '책 없이',
  radius = 2,
}: { width?: number; label?: string; /** 표지와 같은 모서리 — 에디토리얼 2 */ radius?: number } = {}) {
  return (
    <div
      style={{
        width,
        height: Math.round(width * 1.4),
        flex: '0 0 auto',
        boxSizing: 'border-box',
        // 시안 Soft-Home 「책 없이」 점선 — 옛 grey200 점선은 카드 면 위에서 거의 안 보였다. 중간 명도라
        // 독서등(밤)의 「읽는 중」 카드 위에서도 보인다. 시안의 옅은 면(#F3F6F1)은 두지 않는다 — 밤 카드에
        // 밝은 상자가 뜨고, 낮엔 옛 카드색과 거의 같아 투명과 구별되지 않는다. 굵기는 2px 그대로다
        // (Soft PR-3 서재 판정): 시안의 1.5px은 DPR 3 크롬에서 computed 1px로 떨어져 캔버스 위 「책 추가」 칸의
        // 점선이 흐려지고, 밤 카드 위에선 거의 사라졌다(목 모드 390×844@3 실측).
        // 에디토리얼 PR-2(spec §5.1) — 점선 1px muted(종이 5.65 · 밤 책상 6.31). 옛 2px은 카드 면 위에서 점선이 묻히던 때의 값이다.
        border: '1px dashed var(--adaptiveGrey600, #645E54)',
        borderRadius: radius,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 4,
      }}
    >
      {/* 점선 상자가 이미 「표지가 아님」을 말한다 — 시계 이모지는 그 위에 얹힌 군더더기였다(2026-08-18).
          세리프는 명시 opt-in이다(본문은 고운돋움) — 표지 자리의 글자라 표지 이니셜과 한 몸이다. */}
      <Text typography="st12" color="grey600" style={{ ...SERIF_VALUE }}>
        {label}
      </Text>
    </div>
  );
}

/**
 * 고른 칸의 체크 배지 — 표지 우상단 22px 원(이모지 대신 획 SVG). 지금은 강조 잉크 원 + 종이색 체크다(에디토리얼 —
 * 버튼이 아니라 표지 위 표식이라 검정 알약 셈 밖이다). 옛 H안(2026-09-24)은 밝은 원 + 진한 테였다.
 *
 * <p>시안은 표지 밖으로 7px 삐져나가지만 여기선 <b>안쪽에</b> 둔다: 트랙이 가로 스크롤 컨테이너라 삐져나간
 * 배지는 세로 넘침이 돼 잘리고 트랙이 위아래로 들썩인다(`TRACK_V_PAD` 주석과 같은 사고).
 */
export function CheckBadge() {
  return (
    <span
      data-check-badge=""
      aria-hidden="true"
      style={{
        position: 'absolute',
        top: 5,
        right: 5,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        width: 22,
        height: 22,
        boxSizing: 'border-box',
        borderRadius: '50%',
        // 에디토리얼 PR-2(spec §6.1 #17) — 잉크 원 + 미색 체크(체크/원 15.13). 토큰 500인 이유: `typography.test`의 채움 버튼
        // 가드가 `background: '` + Blue700 꼴을 센다 — 배지는 버튼이 아니다(값은 같은 잉크, 공부 모드에선 같은 파란 잉크).
        background: 'var(--adaptiveBlue500, #1D1B18)',
      }}
    >
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" strokeWidth="3.5" strokeLinecap="round" strokeLinejoin="round" style={{ stroke: 'var(--filledInk, #F5F0E6)' }}>
        <path d="M5 12.5l4.5 4.5L19 7.5" />
      </svg>
    </span>
  );
}

/**
 * 읽는 중 책 표지 캐러셀 — **가운데 온 것이 곧 측정 대상**이다.
 *
 * <p>칩 + 「바꾸기」 시트를 대신한다: 고르는 일이 시트를 여닫는 2단계에서 좌우로 미는 1단계가 되고,
 * 표지가 그대로 나오니 무슨 책인지 글자보다 빨리 읽힌다. 표지가 없는 책도 같은 크기 자리 표지로
 * 서서 줄이 무너지지 않는다.
 *
 * <p>**0번 칸은 언제나 「책 없이」 카드**다 — 보조 CTA로 떠 있던 그 갈래가 여기로 들어왔다.
 * 버튼 갈래로 두면 "가운데 온 것이 측정 대상"이라는 단일 문법이 깨지고, 책 0권 사용자에게는
 * 캐러셀 자리가 통째로 다른 화면(빈 상태)이 됐다.
 *
 * <p>선택 상태는 밖(App)이 들고 있다 — 탭바의 측정 액션이 같은 값을 써야 캐러셀과 시작 대상이 어긋나지 않는다.
 *
 * <p>서재도 같은 캐러셀을 쓴다(세로로 길던 3섹션 목록을 대체) — 거기선 0번이 「책 추가」 칸이고
 * (`leadCard`), 아래 한 줄에 읽은 시간·공개 여부까지 실으므로 `metaOf`로 그 줄을 바꿔 끼운다.
 */
export function BookCarousel<T extends BookOption>({
  books,
  selectedId,
  onSelect,
  leadCard = NO_BOOK_CARD,
  metaOf,
  chipsOf,
  door,
}: {
  books: T[];
  selectedId: number | null;
  onSelect: (bookId: number | null) => void;
  /**
   * 0번 특수 칸 — 홈은 기본값(「책 없이」), 서재는 「책 추가」를 넘긴다. `null`이면 책만 세운다
   * (서재에서 서버가 검색을 껐을 때 — 눌러도 아무 데도 못 가는 칸을 남기지 않는다).
   */
  leadCard?: LeadCard | null;
  /** 제목 아래 한 줄 — 기본은 저자. */
  metaOf?: (book: T) => string;
  /**
   * 메타 아래 칩 줄 — 서재가 읽은 시간·공개 여부를 여기로 보낸다. 홈은 안 넘긴다(측정할 책을 <b>고르는</b>
   * 자리에서는 공개 여부가 군더더기다). 스타일까지 호출부가 정해 보내므로 캐러셀은 톤을 몰라도 된다.
   *
   * <p>{@code value}는 라벨의 <b>앞부분</b>이다 — 있으면 그만큼만 세리프로 그린다(시안 2c: 「2시간」은
   * 값이고 「 읽음」은 그게 무엇인지 말하는 꼬리다). 자리를 호출부가 아니라 <b>렌더가</b> 아는 이유는
   * 칩 마크업이 여기 하나뿐이라서다.
   */
  chipsOf?: (book: T) => { label: string; style: CSSProperties; value?: string }[];
  /**
   * 판에 걸치는 손잡이(홈의 「여백에 글 남기기」 — 안 1 §7.3 「알약 걸침」). 넘기면 판 아래 넘침 자리를 <b>늘</b> 잡는다 —
   * `null`(지금 고른 칸엔 문이 없음)이어도 자리는 남겨 고르기가 바뀔 때 제목 줄이 들썩이지 않는다. 안 넘기면(`undefined`) 자리도 없다.
   *
   * <p>트랙(가로 스크롤) <b>밖</b>에 가운데로 둔다: 안에 두면 판 아래로 내려온 몫을 스크롤 컨테이너가 잘라 낸다
   * (`overflow-x: auto`면 y도 auto로 계산된다). 가운데 온 칸 = 고른 책이라 손잡이도 가운데면 고른 표지에 걸친다.
   */
  door?: ReactNode;
}) {
  const trackRef = useRef<HTMLDivElement>(null);
  const settleRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const selected = books.find((b) => b.id === selectedId) ?? null;
  /** 0번이 특수 칸(`null`)이면 책이 한 칸 밀린다 — 칸이 없으면 책이 곧 0번이다. */
  const offset = leadCard !== null ? 1 : 0;
  const items: (T | null)[] = leadCard !== null ? [null, ...books] : books;

  // 첫 진입 — 기본 선택(이어 읽기)이 처음부터 가운데였던 것처럼 즉시 이동한다(애니메이션은 거짓 움직임이다).
  useEffect(() => {
    scrollToIndex(trackRef.current, carouselIndexOf(selectedId, books, offset), true);
    // 언마운트되며 남은 타이머가 사라진 화면의 선택을 건드리지 않게 한다.
    return () => {
      if (settleRef.current !== null) clearTimeout(settleRef.current);
    };
    // 마운트 1회 — 이후 위치는 손가락(스크롤)과 탭, 그리고 아래 동기화가 정한다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 밖에서 선택이 바뀌면(「펼쳐보기」 격자에서 고르기 등) 트랙도 그 책으로 옮긴다 —
  // 손가락으로 민 결과라면 이미 그 자리라 `recenterIndex`가 null을 줘 아무 일도 하지 않는다.
  useEffect(() => {
    const track = trackRef.current;
    if (track === null) return;
    const index = recenterIndex(track.scrollLeft, selectedId, books, offset);
    if (index !== null) scrollToIndex(track, index);
    // 위치를 정하는 건 선택값이다 — books 변동은 선택이 따라 바뀌며 함께 들어온다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedId]);

  return (
    <>
      {/* 판 그릇 — 트랙 · 판(잉크 1px) · 걸친 손잡이. 손잡이는 이 그릇 기준 절대 배치라 트랙 스크롤에 안 잘린다. */}
      <div style={{ position: 'relative', paddingBottom: door !== undefined ? DOOR_HEIGHT - DOOR_OVERLAP : 0 }}>
      <div
        ref={trackRef}
        onScroll={(e) => {
          const { scrollLeft } = e.currentTarget;
          if (settleRef.current !== null) clearTimeout(settleRef.current);
          settleRef.current = setTimeout(() => {
            const picked = selectionAt(
              centeredIndex(scrollLeft, COVER_WIDTH, COVER_GAP, items.length),
              books,
              offset,
            );
            if (picked !== selectedId) onSelect(picked);
          }, SCROLL_SETTLE_MS);
        }}
        style={{
          display: 'flex',
          // 표지 바닥 = 판 윗선(안 1 §7.3) — 크기가 달라도 바닥에 선다.
          alignItems: 'flex-end',
          gap: COVER_GAP,
          overflowX: 'auto',
          // 세로는 `overflow-y: hidden`이 아니라 여기서 막는다 — 그 값을 만나면 웹뷰가 이 트랙의
          // 터치 스크롤을 통째로 죽여 좌우로도 밀리지 않았다. 세로로 넘칠 여지는 이미 패딩이 0으로 만들어 뒀다.
          touchAction: 'pan-x',
          scrollBehavior: TRACK_SCROLL_BEHAVIOR,
          scrollSnapType: 'x mandatory',
          // 위 여백만 여기서(고른 표지가 위로만 솟는다) — 좌우(가운데 정렬) 여백은 양끝 표지의 margin이 맡는다(EDGE_SPACE 주석).
          padding: `${TRACK_V_PAD}px 0 0`,
          scrollbarWidth: 'none',
        }}
      >
        {items.map((item, index) => {
          const current = (item?.id ?? null) === selectedId;
          return (
            <button
              key={item?.id ?? 'no-book'}
              type="button"
              aria-label={item?.title ?? leadCard?.title}
              aria-current={current ? 'true' : undefined}
              // 계측용 표지 — TDS emotion 클래스 사이에서 "표지가 몇 장이고 어떤 책인가"를 집을 손잡이가 없다.
              // 0번 특수 칸은 따로 표시해 `data-cover-title`이 계속 "실제 책 목록"만 뜻하게 둔다.
              data-cover-title={item?.title}
              data-lead-card={item === null ? '' : undefined}
              onClick={() => {
                onSelect(item?.id ?? null);
                scrollToIndex(trackRef.current, index);
              }}
              style={{
                flex: '0 0 auto',
                scrollSnapAlign: 'center',
                // 양끝 칸만 화면 절반만큼 밀어 둔다 — 이 여백이 첫·마지막 칸을 가운데까지 데려온다.
                // (책 0권이면 카드 한 장이 처음이자 마지막이라 양쪽 다 붙는다.)
                marginLeft: index === 0 ? EDGE_SPACE : undefined,
                marginRight: index === items.length - 1 ? EDGE_SPACE : undefined,
                padding: 0,
                border: 'none',
                background: 'transparent',
                cursor: 'pointer',
                // 가운데 온 칸만 바닥 기준으로 크게(88 → 104) + 체크 배지. 후광 링은 걷었다(에디토리얼 — 이펙트 0). 안 고른
                // 칸은 .7 — .45로 흐리면 40대 이상 눈엔 「없는 책」으로 읽혀 흐림으로 지우지 않는다.
                // 0번 특수 칸(점선 + muted 글자)은 흐리지 않는다(spec §5.1 ⚠️) — .7이면 글자 대비가 3.03으로 4.5 아래다.
                position: 'relative',
                transform: current ? `scale(${PICKED_SCALE})` : 'scale(1)',
                transformOrigin: 'bottom center',
                opacity: current || item === null ? 1 : 0.7,
                borderRadius: CAROUSEL_RADIUS,
                transition: 'transform 0.2s ease, opacity 0.2s ease',
              }}
            >
              {item === null ? (
                <NoBookCard label={leadCard?.label} radius={CAROUSEL_RADIUS} />
              ) : (
                // 표지 없음·로드 실패 분기는 BookCover가 든다 — title을 주면 첫 글자 + 제목색으로 떨어진다.
                <BookCover url={item.coverUrl} title={item.title} width={COVER_WIDTH} radius={CAROUSEL_RADIUS} eager />
              )}
              {current && <CheckBadge />}
            </button>
          );
        })}
      </div>
      {/* 판 — 표지 바닥이 서는 잉크 선 하나(안 1 `r5/board`, 내용 폭 전체). */}
      <div data-board="" aria-hidden="true" style={{ borderTop: '1px solid var(--adaptiveGrey800, #1D1B18)' }} />
      {door !== undefined && door !== null && (
        // 판에 1/4 걸친 손잡이 — 그릇 끝에서 알약 높이만큼 위에 붙여 위 11px이 판 위로 올라온다. 아래(bottom:0)에 붙이면 여백
        // 안내 말풍선이 형제로 흘러들 때 그만큼 알약이 위로 밀려 고른 표지를 덮는다 — 위에 붙여 말풍선이 아래로 흐르게 한다.
        // z를 세우지 않는다: 쌓임 맥락이 생기면 안내 딤(fixed z101)이 그 안에 갇혀 탭바(z100)가 딤 위로 밝게 남는다. DOM 순서상
        // 표지 뒤라 z 없이도 표지 위에 칠해진다. 안쪽 가운데 정렬 — 말풍선 폭으로 넓어져도 알약은 표지 가운데에 선다.
        <div
          data-door=""
          style={{
            position: 'absolute',
            left: 0,
            right: 0,
            top: `calc(100% - ${DOOR_HEIGHT}px)`,
            display: 'flex',
            justifyContent: 'center',
            pointerEvents: 'none',
          }}
        >
          <div style={{ pointerEvents: 'auto', textAlign: 'center' }}>{door}</div>
        </div>
      )}
      </div>

      {/* 표지만으론 무슨 책인지 확정되지 않는다(비슷한 표지·자리 표지) — 가운데 온 것을 글자로 못 박는다. */}
      <div
        data-selected-book={selected?.title ?? leadCard?.title}
        style={{ marginTop: 14, textAlign: 'center' }}
      >
        {/* 제목 H1 — 명조 20/28(안 1 Carousel Caption). */}
        <Text
          typography="st10"
          fontWeight="medium"
          style={{ display: 'block', wordBreak: 'keep-all', ...SERIF_VALUE, fontSize: 20, lineHeight: '28px' }}
        >
          {selected?.title ?? leadCard?.title}
        </Text>
        {selected === null ? (
          <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 2 }}>
            {leadCard?.subtitle(books.length)}
          </Text>
        ) : (
          (metaOf?.(selected) ?? selected.author) !== null && (
            // 한 줄 = 저자다. 서재가 「읽은 시간 · 공개」를 줄바꿈으로 여기 얹던 시절엔 `pre-line`이
            // 필요했는데, 그 둘이 아래 칩 줄로 나가면서 이 자리는 다시 한 줄짜리가 됐다.
            <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 2 }}>
              {metaOf?.(selected) ?? selected.author}
            </Text>
          )
        )}
        {selected !== null && chipsOf !== undefined && (
          <div style={{ display: 'flex', justifyContent: 'center', flexWrap: 'wrap', gap: 5, marginTop: 6 }}>
            {chipsOf(selected).map(({ label, style, value }) => (
              <span key={label} data-book-chip="" style={style}>
                {value === undefined ? (
                  label
                ) : (
                  <>
                    <span style={SERIF_VALUE}>{value}</span>
                    {label.slice(value.length)}
                  </>
                )}
              </span>
            ))}
          </div>
        )}
      </div>

      {/* 몇 칸 중 몇 번째인지 — 표지가 화면 밖으로 잘려 있으면 목록의 크기가 안 보인다. */}
      <div aria-hidden="true" style={{ display: 'flex', justifyContent: 'center', gap: 6, marginTop: 10 }}>
        {items.map((item) => {
          const current = (item?.id ?? null) === selectedId;
          return (
            <span
              key={item?.id ?? 'no-book'}
              data-dot={current ? 'active' : 'idle'}
              style={{
                width: 6,
                height: 6,
                borderRadius: '50%',
                background: current ? DOT_ON : DOT_OFF,
              }}
            />
          );
        })}
      </div>
    </>
  );
}

/**
 * 히어로 파생값 — 웹 `frontend/src/dashboard/timerProgress.ts`의 `computeProgress`를 옮겼다.
 *
 * <p>웹은 UX 리뷰로 "오늘 남은 시간" 카운트다운을 **"오늘 읽은 시간" 카운트업**으로 뒤집었다(성취를
 * 세지, 빚을 세지 않는다). 남은 시간은 보조 메타로 강등된다. 미니앱도 같은 프레이밍을 쓴다.
 *
 * <p>**「오늘 읽은 시간」은 서버가 준 `todayReadSeconds`(완료 세션 합) + 경과다** — 부채에서 역산하지
 * 않는다. 서버 부채는 `max(0, 목표 − 읽은 양)`이라 0에서 바닥을 쳐, 역산하면 표시값이 목표에서 천장을
 * 친다: 목표를 넘겨 읽는 동안엔 클라가 스냅샷에서 경과를 빼 부채를 음수로 밀어 제대로 올라가지만,
 * **중지하는 순간 바닥친 스냅샷이 다시 와 정확히 목표값으로 되돌아갔다**(실사용자 제보 — 기록은 멀쩡한데
 * 타이머만 1시간이 됐다). 초과분은 과거 날 상환에 소비되어 응답 어디에도 남지 않아 역산이 불가능하다.
 *
 * <p>반면 **남은 시간·게이지는 그대로 부채 스냅샷(`remainingSeconds - elapsed`)에서 만든다** — 그쪽은
 * 카운트다운이라 0에서 멈추는 게 맞다. `carryover`면 밀린 시간은 오늘 몫이 아니라 바닥(floor)이다.
 *
 * <p>목표 미설정(0)이면 나눌 게 없어 `progress`는 `null`(게이지를 안 그린다)이고 달성이라 우기지도
 * 않는다 — 웹은 이 경우를 100% 달성으로 치지만, 미니앱은 목표 설정으로 유도하는 자리라 그대로 둔다.
 *
 * <p>**게이지의 최대치는 목표가 아니라 「목표 + 밀린 시간」**이다 — 이월 중이라면 오늘 실제로 채워야
 * 하는 양이 그거고, 목표만 분모로 쓰면 게이지가 꽉 찼는데도 남은 시간이 있는 상태가 생긴다.
 * 그래서 `remaining`은 곧 서버가 준 총 남은 시간(`remainingSeconds - elapsed`)이다 —
 * `목표 + 밀린 − 읽은` 을 다시 계산할 필요가 없다(항등식이라 값이 어긋날 자리도 없다).
 *
 * <p>반면 **`achieved`는 여전히 목표만 본다**(결정 2026-08-14). 밀린 시간은 벌이 아니라 이월이라,
 * 빚이 남았다고 오늘 목표 달성 축하를 미루면 톤이 차가워진다 — 그래서 「목표는 달성, 게이지는 아직」인
 * 구간이 의도적으로 존재한다. 그 구간에서 남은 몫은 아래 「남은시간」 줄이 말한다.
 */
export function todayProgress(
  timer: Pick<TimerState, 'remainingSeconds' | 'carriedDebtSeconds' | 'todayGoalSeconds' | 'todayReadSeconds' | 'carryover'>,
  elapsed: number,
): { todayRead: number; remaining: number; overflow: number; progress: number | null; achieved: boolean } {
  const { carriedDebtSeconds: floor, todayGoalSeconds: goal, carryover } = timer;
  const remainingNow = timer.remainingSeconds - elapsed;
  // 완료 세션 합(서버) + 진행 중 몫(클라). 둘 다 0 이상이라 바닥 클램프가 필요 없다.
  const todayRead = timer.todayReadSeconds + elapsed;
  const target = goal + (carryover ? floor : 0); // 오늘 채워야 할 총량 = 게이지 최대치
  return {
    todayRead,
    remaining: Math.max(0, remainingNow),
    overflow: Math.max(0, todayRead - goal),
    progress: target > 0 ? Math.min(1, todayRead / target) : null,
    achieved: goal > 0 && todayRead >= goal,
  };
}

/**
 * 목표 손잡이의 라벨 — 목표 화면으로 가는 진입점이 달고 있는 말.
 *
 * <p>**라벨은 값이 아니라 동작을 말한다**(2026-08-14 실기기 제보로 수정). 한때 「목표 30분 ›」처럼 값을
 * 실어 무엇을 바꾸는 버튼인지 스스로 밝히게 했지만, 손잡이가 「남은시간」 상자 안으로 들어오면서 **바로
 * 위 「오늘 목표 30분」 줄이 같은 값을 이미 말하게 됐다** — 그 자리에서 값을 되풀이하면 중복이고,
 * 정작 "눌러서 바꾸는 것"이라는 사실은 아무도 말하지 않는다. 설정 화면의 같은 버튼과도 말이 맞는다.
 *
 * <p>목표 0이면 바꿀 값이 없으니 정하러 가는 말이 맞다 — 그 상태에선 게이지 줄이 통째로 안 그려져
 * (`todayProgress`의 `progress === null`) 이 버튼이 목표를 정하러 가는 화면 유일의 길이다.
 */
export function goalHandleLabel(goalSeconds: number): string {
  return goalSeconds > 0 ? '하루 목표 바꾸기' : '목표 정하기';
}

/**
 * 목표 손잡이 — 이제 서는 자리는 **하나**다: 목표가 0이라 게이지 줄이 통째로 안 그려질 때의 히어로 카드 안.
 * 평상시 자리(「남은시간」 상자 안)는 히어로 캡션 줄의 「바꾸기 ›」가 가져갔다 — 같은 일을 하는 문이
 * 둘이면 어느 쪽이 진짜인지 사용자가 고민한다.
 *
 * <p>**자작 칩이 아니라 TDS `Button`이다**(2026-08-14 실기기 제보). 작고 테두리 없는 인라인 칩이라
 * 같은 상자에 선 「광고 보고 밀린 하루 지우기」와 나란히 놓였을 때 **버튼으로 읽히지 않았다** — 광고 쪽과
 * 같은 꼴(풀폭 `weak`)로 맞춰 "눌러서 바꾸는 것"임을 모양이 말하게 한다.
 */
function GoalHandle({ goalSeconds, onGoGoal }: { goalSeconds: number; onGoGoal: () => void }) {
  return (
    <Button display="block" variant="weak" size="small" style={{ marginTop: 10 }} onClick={onGoGoal}>
      {goalHandleLabel(goalSeconds)}
    </Button>
  );
}

/**
 * ⓘ 이월 설명 툴팁 — 「남은 시간」 라벨 옆 ⓘ를 탭하면 통계 행 아래로 펼쳐진다.
 *
 * <p>**역할이 하나다: 설명.** 한때 이 상자는 설명과 이동(목표 바꾸기)을 겸했고, 그 문은 "남은시간 :
 * 15:00 ⓘ" 라는 대시 밑줄 한 줄 뒤에 숨어 있었다 — ⓘ는 설명으로 읽히지 이동으로 읽히지 않는다
 * (UX 감사 3e). 이동은 목표 캡션 줄의 「바꾸기 ›」가 가져가고, 여기 남는 건 규칙 설명뿐이다.
 *
 * <p>**이월 규칙은 빚이 없어도 말한다** — 규칙을 가장 먼저 알아야 할 사람은 아직 못 채운 적 없는
 * 사람이다. 반면 내역 세 줄(목표 + 밀린 = 남은시간)은 밀린 게 있을 때만 온다: 빚이 0이면 그 합은
 * 바로 위 통계 행이 이미 말한 값의 되풀이이고, 「밀린 시간 0분」은 없는 빚을 상기시키는 줄이다.
 *
 * <p>**광고 손잡이는 여전히 여기 산다** — 시안 4a가 자리를 말하지 않은 유일한 요소라, 목표 문이
 * 이 상자 밖으로 옮겨 갈 때 함께 쓸려 나갈 뻔했다. 죄책감(밀린 시간)이 뜬 이 상자가 그 버튼의 집이다.
 * 상자는 표시만 맡고 배선은 `children`으로 받는다: 광고의 busy·전면광고 대기 같은 상태를 상자가
 * 알기 시작하면 표시와 배선이 한 덩어리가 된다.
 *
 * <p>화면에서 꺼내 둔 이유는 늘 같다 — 하니스가 정적 렌더라 탭해서 펼친 상태에 도달할 수 없다(T-149).
 */
export function RemainingNote({
  goalSeconds,
  debtSeconds,
  remainingSeconds,
  carryover,
  children,
}: {
  goalSeconds: number;
  debtSeconds: number;
  remainingSeconds: number;
  /**
   * 이월 설정 — `debtSeconds`만으로는 「이월 켬 · 빚 없음」과 「이월 끔」이 구별되지 않는다(둘 다 0).
   * 규칙 문장이 정반대라 설정을 따로 받아야 한다.
   */
  carryover: boolean;
  /** 내역 아래 손잡이 자리 — 광고 버튼이 여기 선다. */
  children?: ReactNode;
}) {
  const row = (label: string, value: string, strong = false) => (
    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '2px 0' }}>
      <Text typography="st12" color={strong ? undefined : 'grey600'}>
        {label}
      </Text>
      <Text typography="st12" color={strong ? undefined : 'grey600'}>
        {value}
      </Text>
    </div>
  );

  return (
    <div style={{ position: 'relative', marginTop: 14, textAlign: 'left' }}>
      {/* 캐럿 — 이 상자가 어느 손잡이에서 나왔는지를 그림으로 말한다. 「남은 시간」 라벨 밑 고정 자리다(타일
          패딩 14 + 라벨 글자 중간 부근). 옛 25%는 두 열 중 왼쪽 열의 가운데였는데 타일이 하나가 되며 뜻을 잃었다.
          폰트 폴백으로 라벨 폭이 ±10px 흔들려도 라벨 밑에 있으면 족하다. */}
      <div
        style={{
          position: 'absolute',
          left: 41,
          top: -6,
          width: 10,
          height: 10,
          background: 'var(--adaptiveBackground, #F5F0E6)',
          borderLeft: '1px solid var(--adaptiveGrey200, #D9D1C3)',
          borderTop: '1px solid var(--adaptiveGrey200, #D9D1C3)',
          transform: 'rotate(45deg)',
        }}
      />
      <div
        style={{
          position: 'relative',
          padding: '9px 12px',
          borderRadius: 2,
          background: 'var(--adaptiveBackground, #F5F0E6)',
          /* 선이 있어야 떨어진다 — 이 상자의 배경은 종이와 같은 색이다. 에디토리얼 PR-2: 가는 선 1px · 모서리 2(표 줄과 같은
             재료). 캐럿도 같은 선을 두른다. 밤엔 `.lamp-page`가 낮 토큰을 재선언하므로 이 선도 낮 값으로 선다. */
          border: '1px solid var(--adaptiveGrey200, #D9D1C3)',
        }}
      >
        {/* ⓘ가 답해야 할 유일한 질문 — "못 채우면 어떻게 되나". 빚이 있든 없든 규칙은 같으므로 늘
            말하되, 이월을 끈 사람에겐 **반대로** 말한다(그 사람에게 이월 문구는 거짓이다). */}
        <Text
          typography="st12"
          style={{ display: 'block', color: 'var(--adaptiveGrey700, #4A453E)', wordBreak: 'keep-all' }}
        >
          {carryover ? (
            <>
              오늘 못 채운 시간은 사라지지 않고 <strong>내일 남은 시간에 더해져요.</strong>
            </>
          ) : (
            <>
              오늘 못 채운 시간은 <strong>내일로 넘어가지 않아요.</strong> 오늘 몫은 오늘까지예요.
            </>
          )}
        </Text>
        {debtSeconds > 0 && (
          <>
            <div
              style={{ marginTop: 8, paddingTop: 8, borderTop: '1px solid var(--adaptiveGrey200, #D9D1C3)' }}
            >
              {row('오늘 목표', formatDuration(goalSeconds))}
              {row('밀린 시간', formatDuration(debtSeconds))}
              <div style={{ marginTop: 4, paddingTop: 4, borderTop: '0.5px solid var(--adaptiveGrey600, #645E54)' }}>
                {row('남은시간', formatClock(remainingSeconds), true)}
              </div>
            </div>
            <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 8, wordBreak: 'keep-all' }}>
              밀린 시간은 목표보다 더 읽거나, 광고를 보고 하루씩 지울 수 있어요.
            </Text>
          </>
        )}
        {children}
      </div>
    </div>
  );
}

/**
 * 리워드 광고 버튼을 노출할지 — 셋 다 참이어야 한다.
 *
 * <p>① 밀린 시간이 있다(=죄책감이 화면에 뜬 순간, 보상이 필요한 바로 그 지점) ② 서버가 지금 지급
 * 가능하다고 했다(=지울 밀린 날이 남았다 — 일일 1회 상한은 2026-08-14 폐지) ③ 광고 그룹 ID가 설정됐다
 * (config-gate). **부채가 없으면 광고의 존재 자체가 안 보인다** — 입문자에게 "광고 보는 앱" 인상을 주지
 * 않으려는 배치다(설계 §3). 지울 날이 남아 있는 한 버튼은 시청 후에도 계속 보인다.
 */
export function showWaiverButton(
  carriedDebtSeconds: number,
  debtWaiverAvailable: boolean,
  adGroupId: string,
): boolean {
  return carriedDebtSeconds > 0 && debtWaiverAvailable && adGroupId !== '';
}

/**
 * 리워드 광고 버튼이 지금 말할 것과 누르면 할 일 — 광고 상태가 곧 버튼 상태다(준비된 광고만 띄운다).
 * 문구에 「광고」를 넣는 규율(광고 위장 금지)은 세 상태 모두 지킨다. 책방 성향 버튼도 이 함수를 쓴다.
 */
export function rewardAdButton(
  status: AdSlotStatus,
  readyLabel: string,
): { label: string; action: 'show' | 'retry' | null } {
  if (status === 'ready') return { label: readyLabel, action: 'show' };
  if (status === 'failed') return { label: '광고 다시 불러오기', action: 'retry' };
  if (status === 'showing') return { label: readyLabel, action: null };
  return { label: '광고 불러오는 중…', action: null }; // idle(곧 부른다)·loading
}

/**
 * 광고 시청 → 지급. 끝까지 안 봤으면 **지급 API를 부르지 않고** `null`(조용히 원상태).
 *
 * <p>클릭 흐름을 화면에서 꺼내 둔 이유: 테스트 하니스가 정적 렌더라 클릭이 안 돌아,
 * "보상 없이 지급 요청을 보내지 않는다"는 이 기능의 신뢰 경계를 함수로만 계측할 수 있다.
 */
export async function claimDebtWaiver(adGroupId: string): Promise<WaiveResponse | null> {
  const rewarded = await watchRewardAd(adGroupId);
  return rewarded ? waiveDebt() : null;
}

/**
 * 알림 동의 카드를 띄울지 — 아직 한 번도 답하지 않았고(캐시 없음) 지원되는 토스앱(5.255.0+)일 때만.
 *
 * <p>거절(`agreementRejected`)도 캐시라 카드가 사라진다 — 거절한 사람을 다시 조르지 않는다.
 * 다른 기기에서 이미 동의했다면 캐시가 없어 카드가 한 번 더 보이지만, 누르면 `alreadyAgreed`가
 * 와서 캐시되고 사라진다(무해).
 */
export function shouldShowNotificationCard(cached: string | null, supported: boolean): boolean {
  return supported && cached === null;
}

/**
 * 동의 화면을 띄우고 결과를 캐시한다 — 이 캐시가 카드를 끄는 유일한 스위치다.
 *
 * <p>미지원 기기(`null`)에서는 **캐시를 남기지 않는다** — 남기면 나중에 최신 토스앱에서 열어도
 * 영영 안 묻는다. 클릭 흐름을 화면 밖으로 꺼낸 이유는 광고 쪽과 같다(정적 렌더 하니스라 클릭이 안 돈다).
 */
export async function askNotificationAgreement(mode: TimerMode): Promise<string | null> {
  const { templateCode, storageKey } = notificationAgreementTarget(mode);
  const result = await requestNotificationAgreement(templateCode);
  if (result !== null) localStorage.setItem(storageKey, result);
  return result;
}

/**
 * 모드가 고르는 동의 대상 — 동의문이 두 장(독서 114526 · 공부 122175)이라 요청 코드·캐시 키·카드 문구가 함께 갈린다.
 *
 * <p>문구는 그 동의문이 실제로 덮는 것만 말한다: 독서 카드가 「공부 시간」을 약속하면 거짓이 된다
 * (독서 동의문은 공부 푸시를 덮지 않는다 — 콘솔 AI 검수가 그 조합을 거부해 갈렸다, 2026-09-13).
 */
export function notificationAgreementTarget(mode: TimerMode): { templateCode: string; storageKey: string; copy: string } {
  return mode === 'study'
    ? { templateCode: STUDY_GOAL_TEMPLATE_CODE, storageKey: STUDY_AGREEMENT_KEY, copy: '정한 공부 시간을 채우면 토스 알림으로 알려드려요' }
    : { templateCode: GOAL_MET_TEMPLATE_CODE, storageKey: AGREEMENT_KEY, copy: '목표 달성과 완독 소식을 토스 알림으로 받아보세요' };
}

/**
 * 실패 문구 — 서버가 준 평문(409 "오늘은 이미 사용했어요" 등)은 그대로 쓰고, SDK가 준 광고 에러는
 * 영문·기술 문구라 그대로 띄우면 안 되므로 안내로 바꾼다.
 */
export function waiverErrorMessage(error: Error): string {
  return error instanceof ApiError ? error.message : '광고를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.';
}

/**
 * 측정 종료 후 태깅 바텀시트 — 딤 + 하단 패널. 행은 표지 자리 + 제목이라 버튼 나열에 없던 시각 위계가 생긴다.
 *
 * <p>한때 시작 자리(`start` 모드)를 겸했지만 고르기는 캐러셀이 가져갔다 — 지금 이 시트가 서는 자리는
 * "방금 끝낸 세션에 무슨 책이었는지 붙이기" 하나뿐이라 문구도 하나다.
 *
 * <p>딤·패널 껍데기는 `ui.Sheet`이 맡는다(서재의 「펼쳐보기」·「관리」와 같은 것) — 여기 남은 건 내용뿐이다.
 *
 * <p>화면에서 꺼내 둔 이유는 늘 같다: 하니스가 정적 렌더라 「바꾸기」를 눌러 열린 상태에 도달할 수 없어,
 * 시트 자체는 여기서 직접 렌더해야 계측된다.
 */
export function BookSheet({
  books,
  title,
  hint,
  error = null,
  disabled,
  onPick,
  onSkip,
  onClose,
}: {
  /** 후보 — 읽는 중이 아닌 책은 `statusLabel`이 행 오른쪽에 선다({@link taggableBooks}). */
  books: SheetBook[];
  /** 물음 — 안 주면 독서 문구다. 기본값이 옛 리터럴이라 독서 렌더는 이 프롭이 생겨도 바이트 불변이다. */
  title?: string;
  /** 물음 밑 한 줄 안내 — 없으면 그리지 않는다. */
  hint?: string;
  /**
   * 실패 문구 — <b>시트 안</b>에 선다. 탭바 위 액션 스트립(z 100)은 불투명 시트 패널(z 201)에 가려,
   * 거기 띄우면 눌렀는데 아무 일도 없는 화면이 된다(R2 P7).
   */
  error?: string | null;
  disabled: boolean;
  onPick: (book: SheetBook) => void;
  onSkip: () => void;
  onClose: () => void;
}) {
  return (
    <Sheet title={title ?? '무슨 책을 읽으셨나요?'} onClose={onClose}>
      {hint !== undefined && (
        <div style={{ marginTop: -4, marginBottom: 10, fontSize: 13, color: 'var(--adaptiveGrey600, #645E54)', wordBreak: 'keep-all' }}>
          {hint}
        </div>
      )}
      <ErrorMessage message={error} />
      {books.map((book) => (
          <button
            key={book.id}
            type="button"
            // 계측용 표지 — TDS가 뿜는 emotion 클래스 사이에서 "행이 몇 개고 어떤 책인가"를 집을 손잡이가 없다.
            data-book-title={book.title}
            disabled={disabled}
            onClick={() => onPick(book)}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 12,
              width: '100%',
              marginBottom: 8,
              padding: 10,
              border: 'none',
              borderRadius: 10,
              background: 'transparent',
              // 정렬은 여기가 갖는다 — `Text`에 주면 TDS가 `textAlign`을 걸러 UA 기본(버튼=가운데)이 남는다(T-216).
              textAlign: 'left',
              cursor: 'pointer',
            }}
          >
            <CoverInitial title={book.title} width={28} />
            {/* 한글 제목이 flex 자식이라 minWidth:0이 없으면 줄바꿈 대신 행을 밀어낸다. */}
            <Text typography="st11" style={{ flex: 1, minWidth: 0, wordBreak: 'keep-all' }}>
              {book.title}
            </Text>
            {book.statusLabel !== undefined && (
              <Text typography="st12" color="grey600" style={{ flex: 'none' }}>
                {book.statusLabel}
              </Text>
            )}
          </button>
        ))}
      <Button display="block" variant="weak" size="medium" style={{ marginTop: 8 }} disabled={disabled} onClick={onSkip}>
        건너뛰기
      </Button>
    </Sheet>
  );
}

/**
 * 계정 진입점 — **홈 맨 위**에서 프로필·설정 화면으로 간다.
 *
 * <p>예전엔 여기가 "계정 관리·상세 설정은 booktimer.app에서" + 로그아웃이었다. 그런데 <b>토스로 가입한
 * 계정은 비밀번호가 없어 그 웹에 로그인 자체가 불가능</b>하다 — 실행할 수 없는 죽은 안내였다(핸들 배너가
 * 앓던 것과 같은 병). 이제 그 자리에서 실제로 닿을 수 있는 곳(설정 화면)으로 보낸다. 로그아웃 2단 확인도
 * 거기로 이사했다 — 두 자리에 두면 확인 단계가 갈라진다.
 *
 * <p>자리가 맨 아래에서 맨 위로 올라왔다(사용자 결정 2026-08-14) — 피드 박스는 세로로 자라는 상자라
 * 그 뒤에 두면 스크롤 끝에 묻히는데, 소셜 기능이 늘수록 프로필의 무게는 반대로 커진다.
 *
 * <p>모양은 <b>인사말 + 아바타 원</b>이다(사용자 지적 2026-08-17). 앞서 쓰던 「흰 채움 + 테두리」 알약은
 * 버튼으로는 잘 읽혔지만 <b>화면에서 가장 밝은 것</b>이 되어, 가장 안 중요한 손잡이가 첫 시선을 받았다
 * (홈에 제목이 없어 그 알약이 사실상 헤더 노릇을 하고 있었다). 그래서 같은 행을 <b>헤더로</b> 만든다 —
 * 왼쪽에 내가 누구인지 적고, 손잡이는 대비가 낮은 아바타로 줄인다. 행 전체가 탭 대상이라 손가락 표적은 오히려 커졌다.
 *
 * <p>덤이 하나 있다: 기본 닉네임(「토스유저」)인 사람이 <b>매일 자기 이름을 보게 되어</b> 바꿀 이유가 생긴다 —
 * 이 화면이 존재하는 이유와 맞물린다. 아바타는 책방 프로필과 <b>같은 이니셜 원</b>이라 같은 사람이 같은 색으로 선다.
 */
export function AccountSection({
  nickname,
  loginId,
  onGoSettings,
}: {
  nickname: string;
  /** @아이디 — 온보딩 전이면 `null`이라 그 줄을 아예 그리지 않는다(「@」만 남는 줄이 생기지 않게). */
  loginId: string | null;
  onGoSettings: () => void;
}) {
  return (
    <button
      type="button"
      aria-label="프로필·설정"
      onClick={onGoSettings}
      // 머리띠(안 1 `mini/Account Header`) — 계측 손잡이. 아래 간격 16은 아치가 12 늘어난 몫을 되찾는 자리다(D5 추천).
      data-masthead=""
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        width: '100%',
        minHeight: 52,
        marginBottom: MASTHEAD_GAP,
        padding: 0,
        border: 'none',
        background: 'transparent',
        textAlign: 'left',
        cursor: 'pointer',
      }}
    >
      <span style={{ flex: 1, minWidth: 0 }}>
        {/* 긴 닉네임이 아바타를 밀어내지 않도록 한 줄로 자른다(서버는 20자까지 받는다). */}
        <span
          style={{
            display: 'block',
            // 시안 2a — 인사말은 값이다(「누구의 화면인가」). 그래서 이름이 아니라 값으로 조판한다.
            // 시안 25는 계단(typography.test `SCALE`)에 없어 t3(26)로 **올려** 붙였다 — A가 정한
            // 「섹션 제목은 올림, 내리면 시안 의도가 죽는다」 그대로다. 홈엔 `Screen` 제목이 없어
            // t3와 부딪히지 않는다. (책방 닉네임 Profile.tsx는 19 — 그 화면은 2a~2e 밖이다.)
            // 에디토리얼 PR-2 — H2 22/30(안 1 §7.1 머리띠). 홈의 표제는 아치 속 시계라 이름은 한 단 내려선다.
            ...SERIF_VALUE,
            fontSize: 22,
            lineHeight: '30px',
            color: 'var(--adaptiveGrey900, #1D1B18)',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {nickname}님
        </span>
        {loginId !== null && (
          <span
            data-handle={loginId}
            style={{ display: 'block', marginTop: 2, fontSize: 14, lineHeight: '20px', color: 'var(--adaptiveGrey600, #645E54)' }}
          >
            @{loginId}
          </span>
        )}
      </span>
      <Avatar nickname={nickname} size={38} />
    </button>
  );
}

/**
 * 측정 중 「읽는 중」 카드 — <b>캐러셀이 서 있던 자리</b>를 그대로 물려받는다.
 *
 * <p>예전엔 측정을 시작하면 그 섹션이 통째로 사라져 표지가 없어지고 히어로의 「측정 중 12분 · 데미안」
 * 한 줄만 남았다(사용자 지적 2026-08-19: 「책 사진이 사라지고 텍스트로 바뀐다」). 고를 게 없으니
 * <b>캐러셀일 이유가 없을 뿐</b>, 지금 읽는 책을 보여 줄 이유는 그대로였다.
 *
 * <p>책 없이 측정 중이면(`book === null`) 카드는 그대로 서고 표지 자리만 「책 없이」가 된다 — 미태깅
 * 측정은 정상 경로이고, 여기서 카드를 감추면 시작·종료 때마다 화면이 세로로 튄다.
 *
 * <p>{@link children}는 카드 바닥의 손잡이 자리다 — 캐러셀 카드가 이 카드로 바뀌어도 <b>「지금 이
 * 화면이 가리키는 책」에 딸린 손잡이는 따라와야</b> 하기 때문이다(지금은 여백 문 하나).
 *
 * <p>{@link onChangeBook}은 측정 대상 교체 문이다(R2 D-2) — 시작 토스트의 [바꾸기]는 5초 뒤 사라져
 * 문이 되지 못한다. 제목 줄 밑에 두는 이유: 바꾸는 대상이 바로 그 제목이다.
 */
export function ReadingNowCard({
  book,
  totalSeconds,
  onChangeBook,
  children,
}: {
  book: BookOption | null;
  totalSeconds: number;
  onChangeBook?: () => void;
  children?: ReactNode;
}) {
  return (
    <section style={sectionStyle}>
      <SectionTitle style={{ marginBottom: 10, paddingBottom: 9, borderBottom: SECTION_RULE }}>읽는 중</SectionTitle>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        {book === null ? (
          <NoBookCard width={READING_NOW_COVER} />
        ) : (
          <BookCover url={book.coverUrl} title={book.title} width={READING_NOW_COVER} eager />
        )}
        <div style={{ minWidth: 0 }}>
          {/* H3 18/26(옷 시안 M-HOME-06) — 명조 제목. */}
          <Text typography="t7" fontWeight="medium" style={{ display: 'block', wordBreak: 'keep-all', ...SERIF_VALUE, fontSize: 18, lineHeight: '26px' }}>
            {book === null ? '책 없이 측정 중' : book.title}
          </Text>
          {book?.author != null && (
            <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 2 }}>
              {book.author}
            </Text>
          )}
          {/* 누적은 이미 오는 값이라 공짜다 — 「이 책을 얼마나 읽었나」가 측정 중에 가장 궁금한 수다. */}
          {book !== null && totalSeconds > 0 && (
            <Text typography="st12" color="blue500" style={{ display: 'block', marginTop: 6 }}>
              이 책 누적 {formatDuration(totalSeconds)}
            </Text>
          )}
          {onChangeBook !== undefined && (
            <Button variant="weak" size="small" style={{ marginTop: 8 }} onClick={onChangeBook}>
              책 바꾸기
            </Button>
          )}
        </div>
      </div>
      {children}
    </section>
  );
}

/**
 * 공부 측정 중 히어로 줄 — 「측정 중 N분 · 제목」 + 회당 시간 손잡이 + [책 바꾸기](R2 D-4).
 *
 * <p>공부 측정 중 홈엔 「읽는 중」 같은 카드를 세우지 않는다(히어로가 이미 제목을 말한다) — 그래서 교체 문은
 * 측정을 말하는 이 줄 옆에 선다(웹 `StudyTimerCard`와 같은 자리). 회당 시간 손잡이와 달리 <b>책 없이 재는
 * 중에도 선다</b> — 책을 붙이는 것도 교체다.
 *
 * <p>훅이 없는 조각으로 꺼낸 이유는 테스트다: 정적 렌더는 `onClick`을 못 보므로 함수로 불러 라벨↔핸들러를 잰다.
 */
export function StudyMeasuringLine({
  elapsed,
  title,
  goal,
  onChangeBook,
}: {
  elapsed: number;
  /** 재는 책 제목 — 책 없이 재면 `null`. */
  title: string | null;
  /** 회당 시간 — 재는 책이 있을 때만(`null`이면 줄·손잡이 둘 다 없다). */
  goal: { line: string | null; label: string; onOpen: () => void } | null;
  onChangeBook: () => void;
}) {
  return (
    // 에디토리얼 PR-2 — 오늘 표의 한 줄(왼쪽 정렬). 공부엔 남은 시간·하루 목표 줄이 없어 이 줄이 표의 첫 줄이다.
    // 정렬은 감싼 div가 든다 — TDS `Text`는 style의 textAlign을 거른다(T-216).
    <div data-today-row="measuring" style={{ ...TABLE_ROW, display: 'block', padding: '12px 0', textAlign: 'left' }}>
      <Text typography="st11" fontWeight="medium" style={{ display: 'block', wordBreak: 'keep-all' }}>
        측정 중 {formatDuration(elapsed)}
        {title !== null && ` · ${title}`}
      </Text>
      {goal?.line != null && (
        <Text typography="st11" color="blue700" style={{ display: 'block', marginTop: 4 }}>
          {goal.line}
        </Text>
      )}
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginTop: 8 }}>
        {goal !== null && (
          <Button variant="weak" size="small" onClick={goal.onOpen}>
            {goal.label}
          </Button>
        )}
        <Button variant="weak" size="small" onClick={onChangeBook}>
          책 바꾸기
        </Button>
      </div>
      <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 8 }}>
        {ACTIVE_STUDY_RELIEF}
      </Text>
    </div>
  );
}

/**
 * 타이머 홈 — `/api/dashboard` 렌더(계정 진입 · 오늘 진행률 · 읽는 중 책 · 피드 박스).
 * 서재 관리·검색·정원은 웹이 본진이라 미니앱에 두지 않는다(설계 §2.5).
 *
 * <p><b>시작·종료 버튼은 여기 없다</b> — 하단 탭바 가운데 원이 유일한 자리다(어느 탭에서든 눌러야 하므로
 * 액션은 `MainTabs`가 든다). 홈이 측정에 대해 말하는 건 히어로의 「측정 중 N분」과 안심 문구까지다.
 */
export function Home({
  dashboard,
  mode,
  study,
  onChangeMode,
  onBlockedModeChange,
  guide,
  selectedBookId: picked,
  onSelectBook,
  selectedStudyBookId,
  // 독서 렌더를 재는 기존 하니스는 공부 재료를 안 넘긴다 — 기본값이 있어야 그 화면이 종전 그대로 선다.
  onSelectStudyBook = () => {},
  onTimerChange,
  celebrate,
  onGoGoal,
  onGoSettings,
  onError,
  onOpenMargin,
  onComposeMargin,
  // 독서 렌더를 재는 기존 하니스는 이 문을 안 넘긴다 — 공부 갈래에서만 쓰여 기본값이면 족하다.
  onSetSessionGoal = () => Promise.resolve(),
  chatUnread,
  onOpenChat,
  goalReached = false,
  onContinueReading = () => {},
  onGoHistory = () => {},
  onChangeActiveBook = () => {},
  readingReminderOffer = null,
}: {
  dashboard: DashboardResponse;
  /** 지금 재는 것 — 히어로 한 장이 이 값으로 두 얼굴을 갖는다(파생은 App이 한다). */
  mode: TimerMode;
  /** 공부 원장 — 독서(`dashboard`)와 따로 온다. */
  study: StudyState;
  onChangeMode: (mode: TimerMode) => void;
  /** 측정 중 토글을 눌렀다 — 안내는 탭 잠금과 같은 스트립이 맡는다(`MainTabs`). */
  onBlockedModeChange: () => void;
  /**
   * 첫 사용 안내로 들어오는 배너 — **자리만 여기가 정한다**(헤더 바로 아래). 만드는 쪽은 흐름을 든
   * `MainTabs`다: 안 본 길 안내가 있을 때만 노드가 오고, 없으면 `null`이라 빈 줄도 남지 않는다.
   */
  guide?: ReactNode;
  /**
   * 캐러셀에서 고른 책 — 상태는 **App이 든다**(홈은 여백·목표·설정이 열리면 언마운트된다).
   * `undefined`는 아직 고르지 않음(여기서 {@link defaultBookId}로 정한다), `null`은 「책 없이」를 고른 것.
   */
  selectedBookId: number | null | undefined;
  onSelectBook: (bookId: number | null) => void;
  /**
   * 공부 캐러셀에서 고른 책 — 위 독서 선택과 <b>별개 슬롯</b>이다(id 공간이 다르다). 상태는 App이 든다.
   *
   * <p>선택 프롭인 이유는 독서 렌더를 재는 기존 하니스들 때문이다 — 공부 재료를 안 넘겨도 독서 홈이
   * 종전 그대로 서야 한다(공부 갈래에서만 쓰이므로 없어도 렌더가 깨지지 않는다).
   */
  selectedStudyBookId?: number | null | undefined;
  onSelectStudyBook?: (bookId: number | null) => void;
  onTimerChange: (timer: TimerState) => void;
  /** 첫 완료 축하가 떠 있는지 — 상태는 측정 액션과 함께 `MainTabs`가 든다(다른 탭에서 끝내도 여기 뜨도록). */
  celebrate: boolean;
  onGoGoal: () => void;
  /** 홈 맨 위의 계정 진입 — 닉네임·@아이디·목표·로그아웃은 전부 설정 화면이 맡는다. */
  onGoSettings: () => void;
  onError: (error: Error) => void;
  /** 소식의 여백 줄 탭 — 그 사람의 그 책 여백을 전체 화면으로 연다(전이는 App이 든다). */
  onOpenMargin: (loginId: string, bookId: number) => void;
  /** 여백 문 — 지금 이 화면이 가리키는 책의 **작성 화면으로 직행**한다(측정 시작과 같은 1탭). */
  onComposeMargin: (book: BookOption) => void;
  /**
   * 공부 책의 회당 시간 저장(`null` = 해제) — 요청·응답 반영은 App이 든다. 성공하면 resolve, 실패하면 reject해
   * 홈이 시트를 연 채 그 안에서 실패를 말한다(액션 스트립은 시트 패널에 가린다).
   */
  onSetSessionGoal?: (bookId: number, seconds: number | null) => Promise<void>;
  /**
   * 미읽음이 있는 대화방 수 — App이 `GET /api/chat/me`로 받는다. 대화가 꺼져 있으면(404·실패) `undefined`다.
   * 카드는 <b>1 이상일 때만</b> 선다 — 대화함이 비어 있는 것이 기본 상태라 빈 기능이 홈을 차지하지 않게(설계 §7-1).
   */
  chatUnread?: number;
  onOpenChat?: () => void;
  /**
   * 방금 하루 목표를 넘겼다 — 상태는 `MainTabs`가 든다(측정 종료 응답 한 번으로 켜고, 시작·탭 이동에 끈다).
   * 선택 프롭인 이유는 옛 하니스들이다 — 안 넘기면 메달이 없는 평소 홈이다.
   */
  goalReached?: boolean;
  /** 메달 화면의 「이어서 읽기」 — 탭바 원과 같은 동작(측정 시작)이다. */
  onContinueReading?: () => void;
  /** 메달 화면의 「기록 보기」 — 기록 탭으로 간다(탭 이동이 메달을 끈다). */
  onGoHistory?: () => void;
  /** 측정 중 [책 바꾸기] — 교체 시트는 App이 연다(토스트의 [바꾸기]와 같은 시트). 옛 하니스는 안 넘긴다. */
  onChangeActiveBook?: () => void;
  /**
   * 독서 알림 제안(N3) — 상태는 `MainTabs`가 든다(독서 측정 종료 응답에서만 켜고, 다음 시작에 끈다).
   * `null`이면 카드가 없다. 선택 프롭인 이유는 옛 하니스들이다.
   */
  readingReminderOffer?: { phase: OfferPhase; hour: number; onPick: () => void; onDismiss: () => void } | null;
}) {
  /**
   * 측정할 책 — 탭바 원이 시작할 대상과 <b>같은 함수</b>로 정한다(아직 안 골랐으면 이어 읽기, 서재에서 빠진 id면
   * 「책 없이」). 고른 값은 App이 들어 화면을 나갔다 와도 남는다.
   */
  const selectedBookId = timerStartBookId(dashboard.readingBooks, dashboard.recentBookId, picked);
  /** 공부 서재 — 옛 서버(이 필드를 안 주는)는 빈 목록이라 캐러셀에 「책 없이」 칸만 선다. */
  const studyBooks = study.books ?? [];
  /** 공부 캐러셀의 가운데 — 독서와 같은 규칙(최근 공부한 책 → 첫 책 → 「책 없이」). */
  const studySelectedId = timerStartBookId(studyBooks, study.recentBookId ?? null, selectedStudyBookId);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [now, setNow] = useState(() => Date.now());
  /** 방금 지운 부채(초) — 성공 직후 한 줄 안내용. */
  const [waived, setWaived] = useState<number | null>(null);
  /** 남은시간 설명 상자 — 접힌 채로 시작한다(궁금한 사람만 편다). */
  const [showNote, setShowNote] = useState(false);
  /** 알림 동의 캐시·지원 여부 — 렌더마다 다시 묻지 않게 초기값으로 한 번만 읽는다. */
  const [agreements, setAgreements] = useState<Record<TimerMode, string | null>>(() => ({
    reading: localStorage.getItem(AGREEMENT_KEY),
    study: localStorage.getItem(STUDY_AGREEMENT_KEY),
  }));
  const [agreementSupported] = useState(notificationAgreementSupported);

  // 어느 쪽을 재든 시계는 매초 올라야 한다 — 조건을 모드별로 갈라 물으면 한쪽이 멈춘 채로 남는다.
  const measuring = dashboard.hasActiveSession || study.hasActiveSession;
  useEffect(() => {
    if (!measuring) return;
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, [measuring]);

  /** 광고 보고 밀린 하루 지우기 — 중간 이탈(null)이면 아무 일도 없었던 것처럼 둔다. */
  const claimWaiver = () => {
    setBusy(true);
    setError(null);
    claimDebtWaiver(REWARD_AD_GROUP_ID)
      .then((result) => {
        if (result === null) return;
        onTimerChange(result.timer); // 부채·버튼 노출이 재조회 없이 갱신된다
        setWaived(result.waivedSeconds);
      })
      .catch((e: Error) => setError(waiverErrorMessage(e)))
      .finally(() => setBusy(false));
  };

  /** 지우개 광고 — ⓘ 상자가 열려 있고 버튼이 설 자격이 있을 때만 미리 불러 둔다(받아 놓고 안 쓰는 광고를 줄인다). */
  const waiverAdStatus = useAdSlot(
    REWARD_AD_GROUP_ID,
    showNote && showWaiverButton(dashboard.carriedDebtSeconds, dashboard.debtWaiverAvailable, REWARD_AD_GROUP_ID) && !busy,
  );
  const waiverAd = rewardAdButton(waiverAdStatus, '광고 보고 밀린 하루 지우기');

  /** 알림 동의 요청 — 결과(동의·이미동의·거절)가 캐시되면 카드가 사라진다. 미지원(null)이면 그대로 둔다. */
  const askNotification = () => {
    const asked = mode; // 답이 오는 사이 모드가 바뀌어도 물었던 쪽 카드에 적는다
    setBusy(true);
    setError(null);
    askNotificationAgreement(asked)
      .then((result) => setAgreements((prev) => ({ ...prev, [asked]: result })))
      .catch(() => setError('알림 동의를 요청하지 못했어요. 잠시 후 다시 시도해 주세요.'))
      .finally(() => setBusy(false));
  };

  const goal = dashboard.todayGoalSeconds;
  const elapsed =
    dashboard.hasActiveSession && dashboard.activeStartedAt !== null
      ? elapsedSeconds(dashboard.activeStartedAt, now)
      : 0;
  // 측정 중이면 elapsed가 매초 늘어 todayRead도 매초 늘어난다 — 카운트업의 동력이 이 한 줄이다.
  const { todayRead, remaining, overflow, progress, achieved } = todayProgress(dashboard, elapsed);
  /** 메달은 독서 하루 목표의 것이고, 다시 재기 시작하면 제 역할을 다했다(히어로가 측정 중 화면으로 돌아간다). */
  const showMedal = goalReached && mode === 'reading' && !dashboard.hasActiveSession;
  /** 공부 경과 — 서버가 준 완료 합에 진행 중 몫을 클라가 매초 얹는다(독서 히어로와 같은 분업). */
  const studyElapsed =
    study.hasActiveSession && study.activeStartedAt !== null ? elapsedSeconds(study.activeStartedAt, now) : 0;
  /** 아치 속 시계 — 모드마다 다른 원장의 오늘 합. 크기는 글자 수가 정한다(한 시간을 넘기면 한 단 작게 — `HeroClock`). */
  const clockText = formatClock(mode === 'study' ? study.todaySeconds + studyElapsed : todayRead);
  /**
   * 회당 시간 판정 — 측정 중인 책의 <b>현재 값</b>이 기준이라, 측정 중 값을 바꾸면 곧바로 새 값으로 그린다.
   * 매초 `now`가 오르니 따로 도는 타이머가 없다.
   */
  const studyGoalView = sessionGoalView(study.activeBook?.sessionGoalSeconds, studyElapsed);
  const studyReached = study.hasActiveSession && studyGoalView.kind === 'reached';
  /**
   * 회당 시간 손잡이가 여는 책 — 측정 중이면 재는 책, 대기 중이면 캐러셀에서 고른 책(「책 없이」·서재에서 빠진
   * id면 없음). 라벨과 여는 대상이 <b>같은 값</b>을 봐야 「보이는 책 ≠ 여는 책」이 생기지 않는다.
   */
  const goalHandleBook = sessionGoalSheetTarget(
    study.hasActiveSession,
    study.activeBook ?? null,
    studyBooks.find((b) => b.id === studySelectedId) ?? null,
  );
  /** 회당 시간 시트 — 사용자가 손잡이를 눌렀을 때만 열린다(진입 직후 덮지 않는다). 연 순간의 책 행을 든다. */
  const [goalSheetBook, setGoalSheetBook] = useState<StudyBookRow | null>(null);
  const [goalSaving, setGoalSaving] = useState(false);
  const [goalError, setGoalError] = useState<string | null>(null);
  useBackClose(goalSheetBook !== null, () => setGoalSheetBook(null));
  const openGoalSheet = (book: StudyBookRow) => {
    setGoalError(null);
    setGoalSheetBook(book);
  };
  /** 저장 — 응답이 성공한 뒤에 닫는다. 실패면 시트를 연 채 그 안에서 말한다(401은 App의 재로그인으로). */
  const pickSessionGoal = (book: StudyBookRow, seconds: number | null) => {
    setGoalSaving(true);
    setGoalError(null);
    onSetSessionGoal(book.id, seconds)
      .then(() => setGoalSheetBook(null))
      .catch((e: Error) => (e.name === 'UnauthorizedError' ? onError(e) : setGoalError(e.message)))
      .finally(() => setGoalSaving(false));
  };

  /** 달성 햅틱 — 판정은 {@link shouldHaptic}(바뀌는 순간만). 여기선 이전 값을 들고 있을 뿐이다. */
  const wasReached = useRef<boolean | null>(null);
  useEffect(() => {
    if (shouldHaptic(wasReached.current, studyReached)) hapticOnce();
    wasReached.current = studyReached;
  }, [studyReached]);
  // 여백 문이 가리키는 책 — 측정 중이면 그 책, 대기 중이면 캐러셀에서 고른 책(없으면 문을 안 그린다).
  const doorBook = marginDoorBook(dashboard, selectedBookId);

  /**
   * 여백 문 — <b>지금 이 화면이 어느 책을 뜻하는지 말하는 카드 안</b>에 산다(대기 중이면 캐러셀 카드,
   * 측정 중이면 「읽는 중」 카드). 상태는 둘이지만 규칙은 하나라 노드도 하나다.
   *
   * <p>예전엔 카드 <b>밖</b>에 홀로 서 있었는데, 그 자리에선 보이지 않았다(사용자 지적 2026-08-21:
   * 「여백 버튼이 별로 눈에 안 띄네」). 이 앱의 `weak` 버튼은 css가 연필 테두리를 얹으므로, 연필
   * 테두리 카드들 사이에 홀로 두면 버튼이 아니라 <b>글자 한 줄만 든 빈 카드</b>로 읽힌다 — 위아래와
   * 테두리가 똑같으니 눈이 그냥 지나간다. 카드 안으로 들어오면 바깥 진한 선(카드) 안의 흐린 선(버튼)이
   * 되어 위계가 서고, 「책을 고른다 → 그 책의 여백에 적는다」가 한 흐름으로 읽힌다.
   *
   * <p>`weak`는 유지한다 — 화면의 주 동작(탭바의 초록 원)보다 낮은 무게가 맞다.
   */
  const withDoorGuide = (door: ReactNode) => (
    // 첫 방문 안내는 문이 실제로 선 자리에서만 뜬다 — 책 0권이면 문 자체가 없으므로, 이 안내는
    // 책을 담고 측정을 해 본 뒤에야 저절로 차례가 온다(순서를 제어하는 코드가 없다).
    <Coachmark
      name="margin"
      after="bookshop" // 탭바 투어를 마친 뒤에 — 딤 두 장이 겹치지 않게
      title="읽다가 떠오른 생각을 여백에"
      detail="문장·감상을 몇 줄 남겨 두는 자리예요"
    >
      {door}
    </Coachmark>
  );
  /** 측정 중 「읽는 중」 카드 바닥의 문 — 옷 시안 M-HOME-06 그대로 전폭 외곽선(weak). */
  const marginDoor =
    doorBook !== null &&
    withDoorGuide(
      <Button display="block" variant="weak" size="medium" style={{ marginTop: 16 }} onClick={() => onComposeMargin(doorBook)}>
        여백에 글 남기기
      </Button>,
    );
  /**
   * 대기 중 고르기 선반의 문 — 고른 표지 아래 가장자리에 1/4 걸친 외곽선 알약(안 1 §7.3 + 계획 §4 H2). 표지를 덮으므로 바탕은
   * 종이로 칠한다 — TDS `weak`는 css가 바탕을 투명으로 못 박아(`!important`) 표지가 비쳐서 맨 버튼에 외곽선 알약 상수를 쓴다.
   * 보이는 높이 44 = 누름 영역.
   */
  const marginPill =
    doorBook === null
      ? null
      : withDoorGuide(
          <button
            type="button"
            onClick={() => onComposeMargin(doorBook)}
            style={{
              ...OUTLINE_PILL,
              background: 'var(--adaptiveGrey100, #F5F0E6)',
              width: 144,
              height: DOOR_HEIGHT,
              padding: 0,
              fontFamily: 'inherit',
              fontSize: 16,
              fontWeight: 500,
              cursor: 'pointer',
            }}
          >
            여백에 글 남기기
          </button>,
        );

  return (
    <Screen>
      {/* 머리띠 — 화면 맨 위 한 줄이 곧 이 화면의 헤더다(인사말 + 아바타, 행 전체 → 설정). */}
      <AccountSection nickname={dashboard.nickname} loginId={dashboard.loginId} onGoSettings={onGoSettings} />

      {/* 아치 + 오늘 표 한 그릇 — 독서등(밤)이 켜지면 이 그릇이 책상 위에 펼쳐진 미색 페이지로 남는다. 표식만 붙이고 켤지
          말지·색은 `body` 클래스와 css가 든다(그래서 측정 여부와 무관하게 늘 붙어 있다). 그릇 자신은 칠하지 않는다 — 아치와 표가
          각자 종이 토큰으로 칠해, 밤엔 아치 반경대로 잘린 미색 페이지가 된다(계획 §6-2 PR-2 단계). */}
      <div className={LAMP_PAGE_CLASS}>
        <div
          data-arch=""
          style={{
            ...ARCH,
            boxSizing: 'border-box',
            minHeight: ARCH_MIN_HEIGHT,
            padding: ARCH_PADDING,
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            textAlign: 'center',
          }}
        >
          {/* 첫 사용 안내·메달은 아치 <b>속을 통째로</b> 가져간다 — 처음 온 사람에게 이 자리는 타이머가 아니라 안내 시작
              버튼이다(M-1, 2026-08-23 실기기 제보). 아치(껍데기·독서등 표식)는 남아 자리가 안 흔들리고 옆선이 늘어날 뿐이다.
              만드는 쪽은 흐름을 든 `MainTabs`다 — 안 본 길 안내가 있고 측정 중이 아닐 때만 노드가 오므로 측정 줄이 안내에
              덮이는 일은 없다. 주의: 메달이 안내보다 <b>먼저</b>다 — 안내 기록이 없는 기기는 측정을 끝내는 순간 안내가 아치를
              되찾는데, 순서가 반대면 그 순간의 메달이 가려지고 나중에 ✕를 누를 때 맥락 없이 튄다. */}
          {showMedal ? (
            <GoalReachedView
              streak={dashboard.graph.currentStreak}
              goalSeconds={goal}
              todayRead={todayRead}
              onContinue={onContinueReading}
              onGoHistory={onGoHistory}
            />
          ) : (
            guide ?? (
              <>
                {/* 독서·공부 — 아치 꼭대기 가운데(안 1 §7.1 modeToggle). 이 앱에서 모드를 바꾸는 유일한 손잡이다. */}
                <ModeToggle mode={mode} locked={measuring} onChange={onChangeMode} onBlocked={onBlockedModeChange} />
                {/* 눈썹 — L1 14/20 muted, 시계 바로 위 가운데. 달성일 때만 새싹이 선다(독서 하루 목표의 것 — 공부엔 오지 않는다). */}
                <span style={{ display: 'block', marginTop: 12, fontSize: 14, lineHeight: '20px', color: 'var(--adaptiveGrey600, #645E54)' }}>
                  {heroOverline(mode, mode === 'reading' && achieved) ?? (
                    <>
                      <SproutMark size={15} /> 오늘 목표 달성
                    </>
                  )}
                </span>
                {/* 시계 — 이 화면이 답하려는 유일한 수(D1 84/88, 8자면 68). 공부 모드에선 강조 잉크가 「파란 펜」이 된다(토큰 경유). */}
                <HeroClock
                  text={clockText}
                  color={mode === 'study' ? 'var(--adaptiveBlue700, #1D1B18)' : 'var(--adaptiveGrey800, #1D1B18)'}
                />
                {/* 게이지 — 독서 하루 목표가 있을 때만(공부 하루 목표는 2026-09-13 책별 「회당 시간」으로 대체돼 걷었다 · Q6).
                    가는 선 트랙(TRACK, 높이 4) 위 강조 잉크 채움. 폭 240 가운데(D5 추천). */}
                {mode === 'reading' && progress !== null && (
                  <div
                    data-gauge-track=""
                    role="progressbar"
                    aria-label="오늘 목표 진행률"
                    aria-valuemin={0}
                    aria-valuemax={100}
                    aria-valuenow={Math.round(progress * 100)}
                    aria-valuetext={`${Math.round(progress * 100)}%`}
                    style={{ ...TRACK, position: 'relative', width: GAUGE_WIDTH, maxWidth: '100%', height: 4, marginTop: 12 }}
                  >
                    <div
                      style={{
                        position: 'absolute',
                        left: 0,
                        top: 0,
                        bottom: 0,
                        width: `calc(100% * ${progress})`,
                        borderRadius: 2,
                        background: GAUGE_FILL,
                      }}
                    />
                  </div>
                )}
              </>
            )
          )}
        </div>

        {/* 오늘 표 — 아치 밑선이 위 테두리다(안 1 §7.1 table). 줄마다 아래 가는 선. 아치와 같은 종이 토큰으로 칠해 밤엔 페이지의
            아랫부분이 된다. 메달·안내가 아치를 가져간 동안엔 숫자 줄을 세우지 않는다(옛 히어로 카드와 같은 규칙). */}
        <div data-today-table="" style={{ background: 'var(--adaptiveGrey100, #F5F0E6)' }}>
          {mode === 'reading' && !showMedal && guide == null && progress !== null && (
            <>
              {/* 줄 1 — 「남은 시간 ⓘ」 ↔ 값(D3 24/30). ⓘ = 설명(툴팁) · 줄 2 「바꾸기 ›」 = 이동으로 역할을 가른다(UX 감사 3e).
                  주의 — 목표가 있으면 남은 시간이 0이어도 그린다(옛 `remaining > 0` 잠금은 달성한 사람의 목표 문을 닫았다). */}
              {/* 줄 높이는 아래 선 1px을 포함해 48(안 1 — 선이 줄 안쪽에 그어진다) → 아래 패딩이 1 적다. */}
              <div data-today-row="remaining" style={{ ...TABLE_ROW, minHeight: 48, padding: '9px 0 8px' }}>
                {/* ⓘ는 라벨에 붙는다 — 값이 아니라 「남은 시간」이라는 개념을 설명하는 손잡이다. */}
                <button
                  type="button"
                  onClick={() => setShowNote((open) => !open)}
                  aria-expanded={showNote}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 4,
                    // 손가락 몫 44 — 위아래 -10 마진으로 바깥 높이가 24라 줄 높이(48)를 안 부풀린다.
                    minHeight: 44,
                    margin: '-10px 0',
                    padding: 0,
                    border: 0,
                    background: 'transparent',
                    color: 'var(--adaptiveGrey600, #645E54)',
                    fontFamily: 'inherit',
                    fontSize: 14,
                    cursor: 'pointer',
                  }}
                >
                  남은 시간
                  {/* 색은 속성이 아니라 style로 준다 — 프레젠테이션 속성엔 `var()`가 안 먹는다. */}
                  <svg
                    width="15"
                    height="15"
                    viewBox="0 0 24 24"
                    fill="none"
                    strokeWidth="2"
                    strokeLinecap="round"
                    style={{ stroke: 'currentColor', flex: 'none' }}
                  >
                    <circle cx="12" cy="12" r="9" />
                    <path d="M12 11v5M12 7.5v.5" />
                  </svg>
                </button>
                <div style={{ ...SERIF_VALUE, fontSize: 24, lineHeight: '30px' }}>{formatClock(remaining)}</div>
              </div>
              {showNote && (
                <RemainingNote
                  goalSeconds={goal}
                  // 이월이 꺼져 있으면 밀린 시간은 남은시간 합계에 안 들어간다 — 그때 이 줄을 그리면 합이 어긋난다.
                  debtSeconds={dashboard.carryover ? dashboard.carriedDebtSeconds : 0}
                  remainingSeconds={remaining}
                  carryover={dashboard.carryover}
                >
                  {/* 광고는 죄책감이 뜬 이 자리에만 나타난다. 문구에 "광고"를 명시해 광고 위장 금지 조항을 지킨다(수익 경로 — 배선 무변경). */}
                  {showWaiverButton(dashboard.carriedDebtSeconds, dashboard.debtWaiverAvailable, REWARD_AD_GROUP_ID) && (
                    <Button
                      display="block"
                      variant="weak"
                      size="small"
                      style={{ marginTop: 8 }}
                      disabled={busy || waiverAd.action === null}
                      onClick={waiverAd.action === 'retry' ? () => preloadAd(REWARD_AD_GROUP_ID) : claimWaiver}
                    >
                      {waiverAd.label}
                    </Button>
                  )}
                  {waived !== null && (
                    <Text typography="st12" color="blue500" style={{ display: 'block', marginTop: 8 }}>
                      밀린 {formatDuration(waived)}을 지웠어요. 잔디는 그대로예요.
                    </Text>
                  )}
                </RemainingNote>
              )}
              {/* 줄 2 — 「하루 목표 30분」 ↔ 「바꾸기 ›」. 가운데 점(·)은 뺐다 — 「바꾸기」가 줄 오른쪽 끝으로 가 가를 것이 없다(대조표 #11).
                  목표는 게이지의 분모라 세리프 값이 아니라 흐린 말 한 줄이고, 초과분은 그 아래 한 줄이다. */}
              <div data-today-row="goal" data-goal-caption="" style={{ ...TABLE_ROW, minHeight: 44, padding: '2px 0' }}>
                <div style={{ minWidth: 0, fontSize: 14, lineHeight: '20px', color: 'var(--adaptiveGrey600, #645E54)' }}>
                  {/* 한 텍스트 노드로 — 「0초」가 아니라 「없음」: 목표 0 + 밀린 시간이면 이 줄이 선다(웹은 0 저장이 된다). */}
                  <span style={{ display: 'block', whiteSpace: 'nowrap' }}>{`하루 목표 ${goal > 0 ? formatDuration(goal) : '없음'}`}</span>
                  {overflow > 0 && <span style={{ display: 'block', fontSize: 13, lineHeight: '18px' }}>+{formatDuration(overflow)} 더 읽었어요</span>}
                </div>
                {/* 손가락 몫 44는 음수 여백 + 패딩으로 든다. */}
                <button
                  type="button"
                  onClick={onGoGoal}
                  // 보이는 「바꾸기」는 옆 말에 기대는 한 단어라 버튼만 훑는 스크린리더엔 뜻이 없다.
                  aria-label={goal > 0 ? '하루 목표 바꾸기' : '하루 목표 정하기'}
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: 1,
                    flex: 'none',
                    minHeight: 44,
                    padding: '10px 6px',
                    margin: '-10px -6px',
                    border: 0,
                    background: 'transparent',
                    fontFamily: 'inherit',
                    fontSize: 14,
                    fontWeight: 500,
                    color: 'var(--adaptiveBlue700, #1D1B18)',
                    cursor: 'pointer',
                  }}
                >
                  {goal > 0 ? '바꾸기' : '정하기'}
                  <svg
                    width="14"
                    height="14"
                    viewBox="0 0 24 24"
                    fill="none"
                    strokeWidth="2.2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    aria-hidden="true"
                    style={{ stroke: 'currentColor', flex: 'none' }}
                  >
                    <path d="M9 6l6 6-6 6" />
                  </svg>
                </button>
              </div>
            </>
          )}
          {mode === 'reading' && !showMedal && guide == null && progress === null && (
            // 목표 0 — 줄 1·2 대신 「목표 정하기」 외곽선 알약. 목표를 정하러 가는 유일한 손잡이가 여기 남는다.
            <div data-today-row="set-goal" style={{ ...TABLE_ROW, display: 'block', padding: '12px 0' }}>
              <GoalHandle goalSeconds={goal} onGoGoal={onGoGoal} />
            </div>
          )}
          {mode === 'reading' && dashboard.hasActiveSession && (
            // 측정 줄 — 이 앱의 핵심 계약(측정은 서버 권위)을 측정 중 화면에서 말하지 않으면, 사용자는 화면을 켜 둬야 하는 줄
            // 알고 몇 초 만에 끈다(운영 실측에서 완료 세션 대부분이 1분 미만이었다).
            <div data-today-row="measuring" style={{ ...TABLE_ROW, display: 'block', padding: '12px 0' }}>
              <Text typography="st11" fontWeight="medium" style={{ display: 'block', wordBreak: 'keep-all' }}>
                측정 중 {formatDuration(elapsed)}
                {dashboard.activeBookTitle !== null && ` · ${dashboard.activeBookTitle}`}
              </Text>
              <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 4 }}>
                {ACTIVE_SESSION_RELIEF}
              </Text>
            </div>
          )}
          {mode === 'study' && study.hasActiveSession && (
            // 회당 시간 — 남은 시간/달성 한 줄(스톱워치면 없음) + 같은 시트를 여는 손잡이. 책 없이 재면 둘 다 없다.
            <StudyMeasuringLine
              elapsed={studyElapsed}
              title={study.activeBook?.title ?? null}
              goal={
                goalHandleBook === null
                  ? null
                  : {
                      line: studySessionLine(studyGoalView),
                      label: sessionGoalHandleLabel(goalHandleBook.sessionGoalSeconds, true),
                      onOpen: () => openGoalSheet(goalHandleBook),
                    }
              }
              onChangeBook={onChangeActiveBook}
            />
          )}
          {/* 줄 3 — 읽지 않은 대화(미읽음이 있을 때만 · 설계 §7-1). 줄 전체가 버튼이고 눌러야 대화함이 열린다(진입 직후 덮지 않는다, T-183). */}
          {chatUnread !== undefined && chatUnread > 0 && onOpenChat !== undefined && (
            <button
              type="button"
              data-today-row="unread"
              onClick={onOpenChat}
              style={{
                ...TABLE_ROW,
                minHeight: 48,
                padding: '12px 0 11px', // 아래 선 1px 포함 48(줄 1과 같은 셈)
                color: 'inherit',
                font: 'inherit',
                textAlign: 'left',
                cursor: 'pointer',
              }}
            >
              <Text typography="st11" fontWeight="medium">
                읽지 않은 대화 {chatUnread}
              </Text>
              <Text typography="st12" color="grey600">
                대화함 열기 ›
              </Text>
            </button>
          )}
        </div>
      </div>

      {/* 축하는 <b>독서</b> 기록에 대한 말이다(「기록 탭에 첫 칸이 생겼어요」 — 공부는 그 탭에 안 남는다).
          `celebrate`는 `MainTabs`가 들어 탭 전환에 살아남으므로, 켜진 채 토글만 넘기면 공부 화면에 떴다. */}
      {mode === 'reading' && <FirstSessionBanner show={celebrate} />}

      {/* 독서 알림 제안 — 측정을 끝낸 뒤 화면 안에 선다(진입 직후 덮는 것 금지, T-183). 눌러야 동의 창이 뜬다. */}
      {mode === 'reading' && readingReminderOffer !== null && (
        <ReadingReminderOffer {...readingReminderOffer} onGoSettings={onGoSettings} />
      )}

      {/* 알림 동의 — 발송은 동의한 유저에게만 가능하고, 동의를 받는 주체는 미니앱이다(콘솔 심사 조건). */}
      {/* 독서 모드에서 새 제안 카드가 서 있는 동안만 숨긴다 — 「알림 받기」 두 장이 뜻이 다른 채 나란히 서지 않게(N3 Q6).
          옛 카드는 모드별이라 공부 모드의 공부 동의 카드는 그대로 둔다. */}
      {shouldShowNotificationCard(agreements[mode], agreementSupported) && !(mode === 'reading' && readingReminderOffer !== null) && (
        <section style={sectionStyle}>
          <Text typography="st11" color="grey600" style={{ display: 'block', marginBottom: 10 }}>
            {notificationAgreementTarget(mode).copy}
          </Text>
          <Button display="block" variant="weak" size="medium" disabled={busy} onClick={askNotification}>
            알림 받기
          </Button>
        </section>
      )}

      {/* 두 모드가 <b>같은 자리</b>에서 대상을 고른다 — 다만 목록도 문구도 갈린다(원장이 갈렸으니).
          공부엔 「읽는 중」 카드·여백 문이 없어 측정 중엔 아무것도 안 선다(표의 측정 줄이 이미 제목을 말한다).
          고르기 선반의 머리는 아래 선이 없다 — 판이 그 일을 맡는다(안 1 §7.1). */}
      {mode === 'study' ? (
        study.hasActiveSession ? null : (
          <section data-picker="" style={sectionStyle}>
            <SectionTitle style={{ marginBottom: 16 }}>무엇을 공부할까요?</SectionTitle>
            <BookCarousel books={studyBooks} selectedId={studySelectedId} onSelect={onSelectStudyBook} />
            {/* 고른 책의 회당 시간 손잡이 — 시작 전에 정하라고 강요하지 않는다(없으면 스톱워치). */}
            {goalHandleBook !== null && (
              <Button
                display="block"
                variant="weak"
                size="small"
                style={{ marginTop: 12 }}
                onClick={() => openGoalSheet(goalHandleBook)}
              >
                {sessionGoalHandleLabel(goalHandleBook.sessionGoalSeconds)}
              </Button>
            )}
          </section>
        )
      ) : dashboard.hasActiveSession ? (
        <ReadingNowCard
          book={dashboard.activeBook ?? null}
          totalSeconds={dashboard.activeBookTotalSeconds}
          onChangeBook={onChangeActiveBook}
        >
          {marginDoor}
        </ReadingNowCard>
      ) : (
        <section data-picker="" style={sectionStyle}>
          {/* 상태와 무관한 고정 문구다 — 「책 없이」가 가운데면 "이 책으로"는 틀린 말이고, 상태별로
              갈아끼우면 헤더가 나타났다 사라지며 캐러셀이 세로로 들썩인다. */}
          {/* 머리 아래 16 = 안 1의 머리 아래 패딩 2 + 간격 14(판 y가 안 1과 같다). */}
          <SectionTitle style={{ marginBottom: 16 }}>무엇으로 측정할까요?</SectionTitle>
          {/* 좌우로 밀어 고른다 — 가운데 온 칸이 곧 측정 대상이다(0번은 「책 없이」라 책 0권도 같은 화면).
              여백 문은 고른 표지에 걸친다(문이 없는 칸이어도 넘침 자리는 남는다 — `door`). */}
          <BookCarousel books={dashboard.readingBooks} selectedId={selectedBookId} onSelect={onSelectBook} door={marginPill} />
        </section>
      )}

      <ErrorMessage message={error} />

      {/* 잔디 미리보기가 서 있던 자리는 피드 박스가 통째로 쓴다 — 기록(잔디·연속일·총 시간)은 기록 탭이
          이미 전부 그리고 그 탭은 하단 탭바에서 한 번에 닿으므로, 홈에 진입 손잡이를 또 두지 않는다. */}
      <HomeFeedBox onError={onError} onOpenMargin={onOpenMargin} />

      {goalSheetBook !== null && (
        <SessionGoalSheet
          book={goalSheetBook}
          busy={goalSaving}
          error={goalError}
          onPick={(seconds) => pickSessionGoal(goalSheetBook, seconds)}
          onClose={() => setGoalSheetBook(null)}
        />
      )}
    </Screen>
  );
}

