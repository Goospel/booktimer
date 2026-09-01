import { Button } from '@toss/tds-mobile';
import { useCallback, useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';

import type { BookOption, DashboardResponse, MarginBook, StudyState, TimerState } from './api';
import { IDLE_STUDY, changeActiveBook, fetchDashboard, startSession, startStudy, stopSession, stopStudy, tagBook, token } from './api';
import { useBackClose } from './back';
import {
  CoachmarkBubble,
  coachmarkSeen,
  dismissCoachmark,
  onCoachmarkChange,
  setCoachmarkWalking,
} from './coachmark';
import { elapsedSeconds } from './format';
import { Bookshop } from './screens/Bookshop';
import { Goal } from './screens/Goal';
import { History } from './screens/History';
import { BookSheet, Home, defaultBookId } from './screens/Home';
import { Library } from './screens/Library';
import { LinkAccount } from './screens/LinkAccount';
import { LoginBridge } from './screens/LoginBridge';
import { Profile } from './screens/Profile';
import { Settings } from './screens/Settings';
import { StudyCalendar } from './screens/StudyCalendar';
import { BookMargin, BookMarginAll, StoryComposer } from './screens/Story';
import { showInterstitialAd, trackEvent } from './toss';
import { CoverInitial, ErrorMessage, Loading, PENCIL_FRAME, SERIF_VALUE, Screen, Sheet } from './ui';

/**
 * 메인 탭 — 이 순서가 곧 탭바 순서다(index↔화면 대응의 단일 출처).
 *
 * <p>`icon`은 24×24 뷰박스의 단색 스트로크 path다. 아이콘 라이브러리를 새로 달지 않고 네 개를 직접
 * 그린다 — 탭바가 쓰는 아이콘은 이게 전부라, 의존성 하나가 이 네 줄보다 비싸다.
 */
export const TABS = [
  { key: 'home', label: '홈', icon: 'M3 10.2 12 3l9 7.2M5.5 9v11h13V9' },
  {
    key: 'library',
    label: '서재',
    icon: 'M12 6.5C9.6 4.9 6.9 4.2 4 4.2v14c2.9 0 5.6.7 8 2.3 2.4-1.6 5.1-2.3 8-2.3v-14c-2.9 0-5.6.7-8 2.3Zm0 0v14',
  },
  {
    // 사람 아이콘을 버리고 차양 달린 가게(storefront)로 — 이 탭의 정체성이 "소셜"이 아니라 "내 책방"이다.
    // 지붕 물결 3칸 + 문. 서재(펼친 책)와 실루엣이 확실히 갈린다.
    key: 'bookshop',
    label: '책방',
    icon: 'M4 10.5 5.2 4h13.6L20 10.5M4 10.5c0 1.4 1.2 2.5 2.7 2.5S9.3 11.9 9.3 10.5c0 1.4 1.2 2.5 2.7 2.5s2.7-1.1 2.7-2.5c0 1.4 1.2 2.5 2.7 2.5S20 11.9 20 10.5M5.5 13v7h13v-7M10 20v-4.5h4V20',
  },
  { key: 'history', label: '기록', icon: 'M4 20.5V12M9.3 20.5V5M14.7 20.5v-6M20 20.5V9' },
] as const;

/** 탭바 한 칸의 최소 높이 — 손가락 최소치(44px)를 넘기고, 본문 하단 여백도 이 값에서 계산한다. */
export const TAB_BAR_HEIGHT = 56;

/** 떠 있는 탭바의 좌우 여백 — 화면 가장자리에서 이만큼 떨어져야 "부착"이 아니라 "플로팅"으로 읽힌다. */
export const TAB_BAR_MARGIN = 16;

/** 떠 있는 탭바의 층 — 시트·딤은 이 위로 올라가야 한다(홈의 책 고르기 시트가 이 값을 넘겨 쓴다). */
export const TAB_BAR_Z_INDEX = 100;

/**
 * 떠 있는 탭바가 먹는 하단 공간 — 본문 끝이 알약에 숨지 않게 이만큼 비운다(바 높이 + 띄운 높이 +
 * 홈 인디케이터 + 숨 쉼 여백). 탭 화면과 여백이 <b>같은 값</b>을 써야 여백에서만 끝이 잠기는 일이 없다.
 */
export const TAB_BAR_SPACE = `calc(${TAB_BAR_HEIGHT}px + 12px + env(safe-area-inset-bottom) + 16px)`;

/**
 * 공부 모드에서 <b>책방 자리에</b> 서는 탭 — 그 달의 일정(지킨 날)을 표시하는 달력이다.
 *
 * <p>책방을 내주는 이유(사용자 확정): 책방은 여백 글이 전시되는 사회면이라 「공부 중에 쓰지 않는」
 * 화면이고, 서재(내 책)·기록(잔디)은 공부 모드에서도 이름이 뚜렷하다.
 *
 * <p>아이콘은 기존 넷과 같은 문법이다 — 24 격자의 단색 스트로크 path(달력 몸통 + 머리줄 + 고리 둘).
 */
export const CALENDAR_TAB = {
  key: 'calendar',
  label: '일정',
  icon: 'M4.5 6.5h15v13h-15zM4.5 10.5h15M8.5 4v4M15.5 4v4',
} as const;

/**
 * 공부 모드 탭바 — {@link TABS}에서 <b>책방 한 칸만</b> 갈아 끼운 것이다.
 *
 * <p>길이를 4로 지킨 것이 이 설계의 요점이다: {@link TIMER_ACTION_SLOT}·{@link slotCenter}가 값
 * 그대로 남아 가운데 원과 말풍선 좌표를 한 줄도 안 고친다.
 */
export const STUDY_TABS = TABS.map((tab) => (tab.key === 'bookshop' ? CALENDAR_TAB : tab));

export type TabKey = (typeof TABS)[number]['key'] | (typeof CALENDAR_TAB)['key'];

/** 탭바 한 칸의 모양 — 두 목록이 같은 꼴이라 그리는 쪽은 어느 목록인지 몰라도 된다. */
export interface TabItem {
  key: TabKey;
  label: string;
  icon: string;
}

/** 이 모드의 탭 목록. 독서는 {@link TABS} <b>그 객체</b>를 돌려준다(독서 탭바는 픽셀 불변이다). */
export function tabsFor(mode: TimerMode): readonly TabItem[] {
  return mode === 'study' ? STUDY_TABS : TABS;
}

/**
 * 지금 모드의 탭바에 없는 탭이면 홈으로 — <b>setState 없는 파생값</b>이라 동기화 코드가 0줄이다.
 *
 * <p>사용자 손으로는 이 조합이 안 생긴다(모드 토글이 홈에만 있다). 생기는 길은 원격뿐이다:
 * 다른 기기에서 공부를 시작하면 {@link effectiveMode}가 study를 강제하는데, 그때 책방 탭에 서 있었으면
 * 존재하지 않는 칸을 가리키게 된다. 반대 방향(달력에 있는데 reading 강제)도 같은 줄이 처리한다.
 */
export function reconcileTab(tab: TabKey, mode: TimerMode): TabKey {
  return tabsFor(mode).some((t) => t.key === tab) ? tab : 'home';
}

/**
 * 탭바가 주는 index를 탭 키로 옮기는 핸들러 — 목록 순서가 곧 탭바 순서라는 계약이 여기 한 줄에 모인다.
 * 정적 렌더 하니스로는 클릭을 못 잡으므로, 이 변환만 따로 꺼내 단위로 계측한다.
 *
 * <p>목록을 인자로 받는 것이 공부 탭바를 지탱한다 — 그리는 목록과 <b>같은 배열</b>로 index를 풀어야
 * 「눌린 칸과 바뀌는 탭」이 어긋나지 않는다(기본값은 독서 목록).
 */
export const tabChangeHandler =
  (onTabChange: (tab: TabKey) => void, tabs: readonly TabItem[] = TABS) =>
  (index: number): void =>
    onTabChange(tabs[index].key);

/**
 * 측정 액션이 서는 시각적 자리 — `TABS` index가 아니라 **렌더 배열의 위치**다.
 *
 * <p>액션을 `TABS`에 넣지 않는 이유: 그 목록은 index↔화면 대응의 단일 출처라, 화면이 없는 동작을
 * 끼우면 `tabChangeHandler`의 계약이 통째로 흔들린다. 여기서는 다 만든 탭 버튼 배열에 끼워 넣기만 한다.
 */
export const TIMER_ACTION_SLOT = 2;

/**
 * 가운데 액션이 시작할 책 — 홈 「측정 시작」이 쓰던 규칙 그대로다(단일 출처).
 *
 * <p>캐러셀의 문법은 "가운데 온 것이 곧 측정 대상"이고 그 선택은 App이 든다(`homeBookId`). 다른 탭에서
 * 눌러도 홈에 지금 가운데 와 있는 그 책으로 시작하는 것이 같은 문법의 연장이다 — 서재에서 보던 책으로
 * 시작하지 않는 이유도 이것이다(측정 대상의 출처가 둘이 되면 무엇으로 시작될지 예측할 수 없다).
 *
 * <p>어떤 조합에서도 최소 `null`(「책 없이」)까지는 떨어져 **죽은 버튼이 될 수 없다**.
 */
export function timerStartBookId(
  readingBooks: BookOption[],
  recentBookId: number | null,
  homeBookId: number | null | undefined,
): number | null {
  const picked = homeBookId === undefined ? defaultBookId(readingBooks, recentBookId) : homeBookId;
  // 고른 책이 서재에서 빠졌으면(stale id) 「책 없이」로 강등한다 — 옛 홈 배선과 같은 처리다.
  return readingBooks.some((b) => b.id === picked) ? picked : null;
}

/** ▶(채움 삼각형) — 탭 아이콘들(스트로크)과 달리 원 위 흰 채움이 작게도 또렷하다. */
const PLAY_ICON = 'M9.5 6.8v10.4l8.5-5.2z';
/** ■(채움 사각형) */
const STOP_ICON = 'M8 8h8v8H8z';

/**
 * 액션 버튼의 두 얼굴 — 시작은 브랜드 세이지 ▶, 측정 중은 토스 danger 빨강 ■.
 *
 * <p>빨강은 옛 홈 「측정 끝내기」가 쓰던 TDS `color="danger"`와 같은 값이라 색 연속성이 유지된다.
 * 전환에 애니메이션을 두지 않는다 — 표지를 재래스터화하는 발광·펄스는 실기기에서 실측된 사고 클래스다(T-176).
 */
/**
 * 독서등이 켜졌을 때 `document.body`에 붙는 클래스 — 색은 전부 `global.css`가 든다.
 *
 * <p>이 문자열이 <b>js와 css를 잇는 유일한 매듭</b>이라 한쪽만 고치면 기능이 조용히 죽는다.
 * `app.test.tsx`가 css에 이 셀렉터가 실재하는지 본다.
 */
export const LAMP_CLASS = 'reading-lamp';

/**
 * 타이머가 무엇을 재고 있나 — 독서(기본)냐 공부냐. <b>타이머의 모드</b>이지 화면의 모드가 아니다
 * (서재·책방·기록은 어느 쪽이든 독서 도메인 그대로다).
 */
export type TimerMode = 'reading' | 'study';

/**
 * 공부 모드일 때 `document.body`에 붙는 클래스 — 색은 전부 `global.css`가 든다(독서등과 같은 수법).
 *
 * <p>{@link LAMP_CLASS}와 마찬가지로 이 문자열이 <b>js와 css를 잇는 유일한 매듭</b>이라,
 * 한쪽만 고치면 기능이 조용히 죽는다. `study-mode.test.tsx`가 css에 이 셀렉터가 실재하는지 본다.
 */
export const STUDY_CLASS = 'study-mode';

/** 모드 저장 키 — 기기 로컬이다(모드는 데이터가 아니라 UI 선호라 기기 간 비대칭을 수용한다). */
export const MODE_KEY = 'booktimer.timerMode';

/** 저장된 모드 — 미지값·손상·증발은 전부 독서로 떨어진다(기본 모드가 독서다). */
export function readMode(): TimerMode {
  return localStorage.getItem(MODE_KEY) === 'study' ? 'study' : 'reading';
}

/**
 * 지금 보여 줄 모드 — <b>서버 진실이 저장값을 이긴다</b>.
 *
 * <p>진행 중 측정이 있으면 그 측정의 모드가 무조건 이긴다(웹에서 시작한 독서가 공부 화면에 가려지면
 * 「끝내는 법」이 사라진다). 그래서 「재진입하면 모드가 유지된다」와 「측정 중 화면이 어긋나지 않는다」를
 * 상태 동기화 코드 없이 <b>이 한 줄이 함께</b> 책임진다.
 */
export function effectiveMode(readingActive: boolean, studyActive: boolean, stored: TimerMode): TimerMode {
  if (readingActive) return 'reading';
  if (studyActive) return 'study';
  return stored;
}

/**
 * 독서등을 켤 자리인가 — <b>측정 중인 홈</b>일 때만 참이다(사용자 결정 2026-08-19: 「홈만」).
 *
 * <p>드는 것이 「지금 어느 탭인가 · 지금 측정 중인가 · 어느 모드인가」 셋뿐이라 <b>상태가 없다</b>. 그래서
 * 앱을 나갔다 와도 대시보드가 다시 「측정 중」이라 말하는 순간 화면이 어두운 채로 열린다 —
 * 「방금 눌렀는지」를 기억하는 코드가 0줄이라 재진입 규칙이 공짜로 따라온다.
 *
 * <p><b>공부 모드에선 켜지 않는다</b>(1차 결정): 독서등은 「독서」 브랜드 장치고, 공부용 밤 팔레트는
 * 1차 가치 대비 비용이 크다. 덕분에 `body.study-mode`와 `body.reading-lamp`가 <b>동시에 붙을 수 없어</b>
 * (밤 세이지 vs 파랑) 명시도 싸움이 통째로 사라진다.
 */
export function lampOn(tab: TabKey, hasActiveSession: boolean, mode: TimerMode): boolean {
  return tab === 'home' && hasActiveSession && mode === 'reading';
}

/** 잠긴 탭을 눌렀을 때의 안내 — 말없이 무반응이면 고장으로 읽힌다. */
export const TAB_LOCK_HINT = '측정을 끝내면 이동할 수 있어요';

/** 그 안내가 떠 있는 시간 — 읽고 남을 만큼만. */
export const TAB_LOCK_HINT_MS = 1800;

/** 시작 토스트가 떠 있는 시간 — 읽고 [바꾸기]까지 누를 만큼. */
export const START_TOAST_MS = 5000;

/** 시작 토스트가 말할 내용 — `book === null`은 「책 없이」, `changed`는 시작이 아니라 교체 확인. */
export interface StartToastState {
  book: BookOption | null;
  changed: boolean;
  /** 공부 측정이면 `'study'` — 책 은유가 통째로 빠진다(생략하면 독서). */
  mode?: TimerMode;
}

/**
 * 토스트 문구.
 *
 * <p>⚠️ <b>조사가 제목 뒤에 붙지 않는 꼴로 고정</b>한다. 「『용기』로」 같은 자리에 받침 판정이 끼면
 * 「용기로 / 용기으로」가 갈리는데 제목은 사용자 데이터라 그 판정을 이길 수 없다 — 그래서 조사는
 * 제목이 아니라 <b>「측정」</b>이 받는다(『제목』 측정<b>을</b> / 『제목』 측정<b>으로</b>).
 */
/**
 * 이 토스트가 <b>책 장치</b>(표지 자리 · [바꾸기])를 다는가 — 공부 측정이면 달지 않는다.
 *
 * <p>남겨 두면 [바꾸기]가 <b>죽은 컨트롤</b>이 된다: 누르면 교체 시트가 열리고, 진행 중 독서 세션이
 * 없어 서버가 409 「진행 중인 측정이 없습니다」로 끝낸다 — 사용자에겐 이유 없는 에러다. 표지 자리도
 * 공부엔 가리킬 것이 없어 점선 네모만 남는다.
 *
 * <p>판단을 함수로 꺼낸 이유는 늘 같다 — 하니스가 정적 렌더라 클릭 경로로는 못 잰다(T-149).
 */
export function toastHasBookControls(toast: StartToastState): boolean {
  return toast.mode !== 'study';
}

export function startToastMessage(toast: StartToastState): string {
  // 공부엔 책이 없다 — 「책 없이 측정을 시작했어요」는 여기서 거짓말이 된다(빠진 것이 아니라 무관하다).
  if (toast.mode === 'study') return '공부 측정을 시작했어요';
  const target = toast.book === null ? '책 없이' : `『${toast.book.title}』`;
  return toast.changed ? `${target} 측정으로 바꿨어요` : `${target} 측정을 시작했어요`;
}

/**
 * 토스트 노출 판정 — 토스트·액션 실패·잠금 안내 <b>셋이 같은 fixed 좌표</b>(탭바 위 8px)를 쓰므로
 * 한 장만 세운다. 상태 간 clear 배선을 만드는 대신 게이트 하나로 가리는 이유는, 배선은 늘어날수록
 * 빠뜨린 조합이 생기기 때문이다.
 *
 * <p>우선순위 = 에러 &gt; 잠금 안내 &gt; 토스트. 뒤 둘은 <b>항상 토스트보다 새 사건</b>이다 —
 * 토스트는 5초를 버티는데 그동안 사용자가 한 다른 행동에 대한 답이 밀리면 안 된다.
 *
 * <p><b>홈에선 안 띄운다</b> — 「읽는 중」 카드가 이미 그 책을 표지째 말한다. 이 규칙이 시작 시점이
 * 아니라 <b>여기</b>(매 렌더) 있는 이유: 토스트는 5초를 버티므로 그 사이 홈으로 건너가면 시작 시점
 * 판정만으로는 중복이 그대로 따라온다(목 모드 실측으로 드러난 자리다).
 */
export function startToastVisible(
  toast: StartToastState | null,
  actionError: string | null,
  lockHint: boolean,
  tab: TabKey,
): toast is StartToastState {
  return toast !== null && actionError === null && !lockHint && tab !== 'home';
}

/**
 * 측정 중 잠기는 탭인가 — <b>홈만 빼고</b> 잠긴다(사용자 지시 2026-08-19 「읽는 도중엔 다른 화면으로
 * 이동 못하게」, 범위는 탭만).
 *
 * <p><b>홈을 빼는 것이 이 설계의 요점이다.</b> 넷 다 잠그면 탈출구가 사라진다 — 코치마크 투어는
 * {@link flowTabChange} effect가 `onTabChange`를 <b>직접</b> 부르므로 이 잠금을 안 탄다. 투어가
 * 사용자를 서재에 데려다 놓은 상태에서 넷 다 잠겨 있으면 측정을 끊기 전엔 홈으로 못 돌아온다.
 * 홈을 열어 두면 그 덫이 원천적으로 없고, 「내가 있을 곳은 여기」라는 표시도 함께 된다.
 */
export function tabLocked(key: TabKey, hasActiveSession: boolean): boolean {
  return hasActiveSession && key !== 'home';
}

export function timerActionView(active: boolean, mode: TimerMode = 'reading'): {
  label: string;
  background: string;
  ring: string;
  icon: string;
} {
  // ⚠️ **링은 배경과 한 쌍으로 여기 산다** — 설계 D7이 값과 함께 그 이유까지 적어 뒀다:
  // 「색 쌍이 흩어지면 다음 손질 때 한쪽만 바뀐다」. 실제로 링을 `TimerActionButton`에 무조건으로
  // 박았더니 **빨간 정지 버튼에 초록 후광**이 남았다(독립 리뷰 적발). 쌍으로 두면 못 어긋난다.
  return active
    ? {
        label: '측정 끝내기',
        background: 'var(--adaptiveRed500, #F04452)',
        ring: '0 0 0 3px rgba(240,68,82,.2)',
        icon: STOP_ICON,
      }
    : {
        // 시안의 「독서 시작하기 / 공부 시작하기」가 실물에서 서는 자리 — 이 앱엔 시작 버튼 글자가 없고
        // 원 하나뿐이라, 무엇을 재기 시작하는지는 이 라벨과 시작 토스트가 말한다.
        label: mode === 'study' ? '공부 측정 시작' : '독서 측정 시작',
        background: 'var(--adaptiveBlue700, #4F6B4C)',
        // 링·배경 둘 다 토큰이라 공부 모드 색 전환이 css 한 벌로 따라온다(빨강 링은 모드 무관 — danger).
        ring: '0 0 0 3px var(--accentRing, rgba(110,138,106,.25))',
        icon: PLAY_ICON,
      };
}

/**
 * 그 탭이 서는 <b>렌더 슬롯</b> — 가운데 액션이 끼어들어 `TABS` index와 어긋난다.
 *
 * <p>index를 그대로 쓰면 책방(index 2)을 가리키려다 액션 원(슬롯 2)을 가리킨다.
 */
export function tabSlot(key: TabKey): number {
  const index = TABS.findIndex((t) => t.key === key);
  return index >= TIMER_ACTION_SLOT ? index + 1 : index;
}

/**
 * 그 슬롯 칸의 가로 중앙 — <b>순수 CSS 식</b>이다(ref·`getBoundingClientRect`·리사이즈 관측 0).
 *
 * <p>탭바가 좌우 같은 여백의 `fixed`고 칸이 `flex: 1` 균등 분할이라, 칸 중앙이 화면 폭의
 * 함수로 떨어진다 — 그래서 코치마크가 무엇을 가리킬지 알기 위해 화면을 재지 않아도 된다.
 */
export function slotCenter(slot: number): string {
  const share = (slot + 0.5) / (TABS.length + 1);
  return `calc(${TAB_BAR_MARGIN}px + (100vw - ${TAB_BAR_MARGIN * 2}px) * ${share})`;
}

/**
 * 첫 진입 흐름 — 다섯 걸음을 <b>한 줄로 이어</b> 걷는다. 배열 순서가 곧 걸음 순서다.
 *
 * <p><b>걸음마다 그 화면으로 먼저 데려간다</b>(`tab`) — 딤이 반투명이라 「서재는 …」을 읽는 동안
 * 실제 서재가 뒤에 보인다. 「다음을 누르면 이동」이 아니라 「이동한 뒤 설명」인 이유가 이것이다.
 * 첫 진입 1회짜리라 이 정도의 강제 흐름은 방해가 아니라 안내다(사용자 결정 2026-08-17).
 *
 * <p>`bubble`이 있으면 탭바 칸을 가리키는 걸음이고, 없으면 <b>그 화면 안의 인라인 안내</b>가 맡는다
 * (`Coachmark`가 앞 걸음을 `after`로 물고 스스로 뜬다 — 그래서 여기엔 문구가 없다).
 *
 * <p>마지막이 홈인 덕에 걷기가 <b>출발한 자리로 돌아와</b> 끝난다. 신규 사용자는 책이 0권이라
 * 홈 여백 문 자체가 없어 그 걸음은 조용히 지나가고, 키가 남지 않아 책을 담은 뒤에 뜬다.
 *
 * <p>첫 걸음 `timer`는 #833으로 이미 나갔다 — <b>걸음마다 자기 키</b>라 이미 본 사람은 서재 걸음부터
 * 이어 걷는다(마이그레이션 0).
 */
export interface FlowStep {
  name: string;
  /** 이 걸음을 설명할 화면 — 걸음에 들어갈 때 여기로 데려간다. */
  tab: TabKey;
  /** 탭바 칸을 가리키는 걸음의 말풍선. 없으면 인라인 안내가 맡는다. */
  bubble?: { slot: number; title: string; detail: string };
}

export const COACHMARK_FLOW: FlowStep[] = [
  {
    name: 'timer',
    tab: 'home',
    bubble: {
      slot: TIMER_ACTION_SLOT,
      title: '여기를 눌러 독서 시간을 재요',
      detail: '읽던 책은 홈에서 고를 수 있어요',
    },
  },
  {
    name: 'library',
    tab: 'library',
    bubble: {
      slot: tabSlot('library'),
      title: '서재는 내가 읽는 책을 담는 곳',
      detail: '여기에 담아 둔 책으로 시간을 재요',
    },
  },
  // 서재를 설명한 그 화면에서 곧바로 「여기서 담아요」로 이어진다 — 한 호흡이라 이동이 없다.
  { name: 'add-book', tab: 'library' },
  {
    name: 'bookshop',
    tab: 'bookshop',
    bubble: {
      slot: tabSlot('bookshop'),
      title: '책방은 남에게 보여주는 곳',
      detail: '내 책과 글이 전시되고, 남의 책방도 구경해요',
    },
  },
  { name: 'margin', tab: 'home' },
];

/** 아직 안 본 첫 걸음(`-1`이면 흐름 끝) — 시작과 진행이 같은 한 줄을 쓴다. */
export function nextFlowStep(): number {
  return COACHMARK_FLOW.findIndex((step) => !coachmarkSeen(step.name));
}

/** 그 걸음을 설명할 화면으로 옮겨야 하나 — 이미 그 화면이면 `null`(헛 전환을 만들지 않는다). */
export function flowTabChange(step: FlowStep | undefined, tab: TabKey): TabKey | null {
  return step === undefined || step.tab === tab ? null : step.tab;
}

/**
 * 걷기를 포기할 때 봤다고 남길 걸음 — <b>길 안내(탭바 걸음)만</b>이다.
 *
 * <p>사용자가 스스로 탭을 옮겼다면 길은 이미 스스로 찾는 중이니 그 안내는 물러난다. 반면 책 담기·여백은
 * 대상이 있는 화면에 섰을 때가 제 차례라 남겨 둔다(그 화면에 가면 인라인 안내가 스스로 뜬다).
 */
/**
 * 안내 카드를 띄울까 — 첫 사용 안내는 <b>사용자가 눌러서</b> 시작한다.
 *
 * <p>2026-08-17 토스 심사 반려: 「미니앱 접속 직후 바텀시트가 바로 노출돼요」. 우리가 만든 것은 툴팁이지만
 * 렌더 결과가 <b>전체 딤 + 하단 와이드 패널</b>이라 시트와 구별되지 않았다(2026-08-12 「서비스 설명 없이 즉시
 * 로그인 유도」 반려와 같은 계열 — 심사는 <b>진입 직후 들이대는 것</b>을 막는다). 안내 자체는 그대로 두고
 * <b>시작 트리거만</b> 자동 → 수동으로 뒤집었다.
 *
 * <p>기준은 <b>길 안내(탭바 걸음)</b>뿐이다. 「전 걸음 완료」로 판정하면, 책이 0권이라 홈 여백 문이 없는
 * 신규 사용자는 마지막 걸음 키가 영영 안 남아 배너가 평생 뜬다. 덕분에 #833 안내만 본 옛 사용자에게도
 * 제대로 뜬다(서재·책방을 아직 모른다).
 *
 * <p>⚠️ {@link closed}가 따로 있는 이유(실 브라우저 실측): ✕는 기록(키 3개)을 남기지만 그 기록만으로는
 * <b>화면이 갱신되지 않는다</b> — 배너만 보고 있을 때 커서는 이미 `-1`이라 `setFlowIndex(-1)`이 같은 값이고
 * React가 리렌더를 건너뛴다. 그래서 즉시 반영은 이 상태가, 다음 진입의 판정은 키가 맡는다(역할이 다르다).
 */
export function shouldShowGuideHero(running: boolean, closed: boolean, measuring: boolean): boolean {
  return !running && !closed && !measuring && flowStepsOnAbandon().some((name) => !coachmarkSeen(name));
}

export function flowStepsOnAbandon(): string[] {
  return COACHMARK_FLOW.filter((step) => step.bubble !== undefined).map((step) => step.name);
}

/** 종료 직후 태깅 대상 — 책 없이 측정한 세션에 나중에 책을 붙인다. */
interface Untagged {
  sessionId: number;
}

/** 탭 밖 전역 상태 — 인증·연결·목표·에러는 탭바 없이 화면 전체를 차지한다. */
type View =
  | 'auth'
  | 'link'
  | 'loading'
  | 'main'
  /** 독서 목표 화면. */
  | 'goal'
  /**
   * 공부 목표 화면 — 파생 `mode`로 렌더를 가르지 않고 <b>진입 시점 스냅샷</b>으로 둔다. 원격에서 모드가
   * 플립되면(다른 기기에서 측정 시작) 편집 중이던 목표 화면이 통째로 갈아타는 엣지가 생기기 때문이다.
   */
  | 'studyGoal'
  | 'settings'
  | 'error';

/**
 * 열린 여백 — 「누구의 + 어느 책」 두 축이 곧 서버 계약이고, `composeBook`이 있으면 그 책의 **작성
 * 화면**이 선다(홈·서재 문은 이 값을 채워 1탭에 작성으로 직행한다).
 *
 * <p>여백을 <b>탭 밖 전역 뷰</b>로 든 이유는 뒤로가기다: 예전엔 책방 탭으로 점프해 열었더니 뒤로 가면
 * 책방이 나왔다 — 홈에서 들어온 사람에게 그건 남의 전시장으로 밀려나는 것이라 문의 취지가 무너진다.
 * 여기서 들면 열었던 탭이 그대로 뒤에 남는다(`goal`·`settings`와 같은 자리).
 */
export interface MarginState {
  /** 사람축 좌표의 절반 — 책축(검색에서 낯선 책)으로 들어오면 `null`이다. */
  loginId: string | null;
  /**
   * 작성 화면 **아래에 깔린** 여백 화면 — `null`이면 깔린 것이 없어, 작성을 닫으면 열었던 탭으로 곧장 돌아간다.
   *
   * <p>예전엔 작성 직행(홈·서재의 「여백에 글쓰기」)도 여백 상세를 깔고 그 위에 섰다. 그래서 쓰고 나오면
   * 출발한 탭이 아니라 <b>한 번도 요청한 적 없는 여백 상세</b>에 떨어졌다.
   */
  bookId: number | null;
  /**
   * 책축 좌표 — 사람 없이 <b>책 하나(isbn13)</b>로 서는 화면(2026-08-22 개방). 사람축 좌표와 함께
   * 실릴 수 있다: 내 여백 화면의 「모두」 탭이 그 조합이고, 그때는 사람축이 이긴다({@link marginScreen}).
   */
  isbn13: string | null;
  composeBook: MarginBook | null;
  /**
   * 여기 들어오느라 측정을 끝냈는가 — 화면이 그 사실을 한 줄로 밝힌다({@link TIMER_STOPPED_NOTICE}).
   *
   * <p>상태에 드는 이유는 작성 화면과 여백 상세가 한 몸이기 때문이다 — `closeCompose`가 값을
   * 물려주므로, 여백을 열면서 끝낸 측정은 그 안에서 글을 쓰고 나와도 같은 사실로 남는다.
   */
  timerStopped?: boolean;
}

/**
 * 작성 화면을 닫으면 남을 상태 — 출발지로 돌려보낸다. 배선은 클릭·뒤로가기라 판정만 순수하게 계측한다.
 */
export function closeCompose(margin: MarginState): MarginState | null {
  return margin.bookId === null ? null : { ...margin, composeBook: null };
}

/** 여백이 설 수 있는 화면 — 작성 · 사람축(누구의 어느 책) · 책축(이 책의 여백). */
export type MarginScreen = 'compose' | 'person' | 'book';

/**
 * 어느 여백 화면을 세울까 — 좌표계가 둘이 되면서 셸의 분기가 셋이 됐다. 배선은 렌더라 판정만
 * 순수하게 빼 계측한다({@link closeCompose} 관례).
 *
 * <p><b>순서가 곧 규칙이다</b>: 작성이 가장 위, 그다음 사람축, 마지막이 책축. 사람축이 앞서야
 * 「내 여백」 탭에서 연 화면이 책축으로 미끄러지지 않는다(둘 다 들고 있는 조합이 정상이다).
 * 좌표가 모자라면 `null` — 막다른 길 대신 열었던 탭으로 떨어진다.
 */
export function marginScreen(margin: MarginState): MarginScreen | null {
  if (margin.composeBook !== null) return 'compose';
  if (margin.loginId !== null && margin.bookId !== null) return 'person';
  return margin.isbn13 !== null ? 'book' : null;
}

/**
 * 작성 시트 <b>아래에 깔릴</b> 화면 — 작성이 바텀시트가 되면서(2026-08-29) 밑 화면이 살아 있어야 한다.
 *
 * <p>「작성이 가장 위」라는 {@link marginScreen}의 첫 줄을 한 칸 걷어낸 것이 전부다. `null`은 막다른
 * 길이 아니라 <b>깔린 여백 화면이 없다</b>는 뜻이다 — 홈·서재에서 직행한 작성이 그것이고, 그때 시트
 * 뒤에는 열었던 탭 화면이 그대로 선다.
 */
export function underCompose(margin: MarginState): MarginScreen | null {
  return marginScreen({ ...margin, composeBook: null });
}

/** 포커스 복귀 재조회의 최소 간격 — 미니앱은 앱 전환이 잦아 복귀마다 받으면 서버를 두들긴다. */
export const REFRESH_THROTTLE_MS = 60_000;

/**
 * 지금 다시 받을 때인가. 판정만 순수하게 빼 계측한다(배선은 effect라 하니스가 못 잡는다).
 *
 * <p>`force`는 **이 앱 안에서 방금 무언가를 바꾼 경우**다(서재에서 책 상태 변경 등) — 스로틀은 잦은
 * 포커스 복귀를 누르려는 것이지 내가 만든 변화를 1분간 감추라는 뜻이 아니다.
 */
export function shouldRefresh(lastAt: number, now: number, force = false): boolean {
  return force || now - lastAt >= REFRESH_THROTTLE_MS;
}

/**
 * 딥링크로 착지할 탭 — 푸시 알림의 이동 URL(`intoss://<앱이름>/?tab=history`)이 화면을 지목한다.
 *
 * <p><b>모르는 값은 전부 홈이다.</b> 이 문자열은 우리 코드 밖(콘솔에 손으로 적은 캠페인 URL)에서
 * 오므로 오타·옛 링크·탭이 아닌 이름이 얼마든지 온다. 그때 필요한 건 오류가 아니라 홈이다.
 *
 * <p>목록을 하드코딩하지 않고 {@link TABS}에서 확인하는 이유는 탭이 늘 때 여기를 같이 고치는 걸
 * 잊으면 <b>조용히</b> 홈으로 떨어지기 때문이다 — 탭바 순서의 단일 출처를 그대로 쓴다.
 */
export function initialTab(search: string): TabKey {
  const key = new URLSearchParams(search).get('tab');
  return TABS.some((tab) => tab.key === key) ? (key as TabKey) : 'home';
}

/**
 * 라우터 없이 상태 두 개로 화면을 정한다 — `view`(탭 밖 오케스트레이션) × `tab`(메인 탭).
 *
 * <p>인증 실패(401)는 어디서 나든 토큰이 이미 폐기된 상태라 로그인 브릿지로 되돌린다.
 */
export function App() {
  const [view, setView] = useState<View>(() => (token.get() === null ? 'auth' : 'loading'));
  // 첫 렌더에서 한 번만 읽는다 — 딥링크는 진입 시점의 사실이라, 이후 탭 이동을 되돌리면 안 된다.
  // `window` 가드는 테스트의 정적 렌더용이다(이 하니스엔 jsdom이 없다 — `toss.ts`와 같은 사정).
  const [tab, setTab] = useState<TabKey>(() =>
    initialTab(typeof window === 'undefined' ? '' : window.location.search),
  );
  const [dashboard, setDashboard] = useState<DashboardResponse | null>(null);
  /**
   * 공부 원장 — 대시보드와 <b>따로</b> 든다. start/stop 응답(`StudyState`)이 대시보드를 통째로 갈아치우지
   * 않고 이 한 칸만 갱신하므로, 독서 상태와 서로를 덮어쓸 자리가 없다.
   */
  const [study, setStudy] = useState<StudyState>(IDLE_STUDY);
  /** 사용자가 고른 모드(기기 로컬) — 실제로 보여 줄 모드는 {@link effectiveMode}가 정한다. */
  const [storedMode, setStoredMode] = useState<TimerMode>(() =>
    typeof localStorage === 'undefined' ? 'reading' : readMode(),
  );
  const [firstRun, setFirstRun] = useState(false);
  const [error, setError] = useState<string | null>(null);
  /** 목표 바꾸기 전면광고를 기다리는 중 — 버튼을 "준비 중"으로 바꾸고 중복 진입을 막는다. */
  const [goalAdPending, setGoalAdPending] = useState(false);
  /** 열린 여백 — 탭 위에 전체 화면으로 선다(홈 문·서재 문·홈 소식이 모두 이 한 자리로 온다). */
  const [margin, setMargin] = useState<MarginState | null>(null);
  /**
   * 글을 남긴 횟수 — 밑에 깔린 여백 상세를 다시 마운트시키는 열쇠다.
   *
   * <p>작성이 전체 화면이던 시절엔 닫는 순간 밑 화면이 <b>새로 마운트</b>되고 그 재조회가 곧 "방금
   * 남긴 글이 보인다"였다. 시트로 겹치면서 밑 화면이 안 죽으니 그 재조회도 저절로 안 일어난다 —
   * 남긴 뒤에만 이 값을 올려 그때만 다시 받는다(취소는 올리지 않는다: 바뀐 것이 없다).
   */
  const [composeEpoch, setComposeEpoch] = useState(0);
  /**
   * 열린 남의 책방 — 여백의 좋아요 명단에서 사람을 눌렀을 때 선다.
   *
   * <p>여백과 <b>같은 자리</b>(탭 밖 전역 뷰)에 든다. 책방 탭으로 점프시키면 뒤로 갈 때 출발한
   * 탭이 아니라 책방이 나오는데, 그게 바로 여백을 여기로 올린 이유였다(위 {@link MarginState}).
   */
  const [shop, setShop] = useState<string | null>(null);
  /**
   * 홈 캐러셀에서 고른 책 — **홈이 아니라 여기서 든다**. 여백·목표·설정은 탭 위에 전체 화면으로 서서
   * 홈을 언마운트하므로, 홈에 두면 여백에 글 한 편 쓰고 돌아올 때마다 고른 책이 기본 책으로 되돌아갔다.
   *
   * <p>`undefined`(아직 안 고름)와 `null`(「책 없이」를 고름)은 다른 값이다 — 하나로 합치면 「책 없이」를
   * 고른 사람이 돌아올 때마다 이어 읽기 책으로 끌려간다.
   */
  const [homeBookId, setHomeBookId] = useState<number | null | undefined>(undefined);

  const toLogin = useCallback(() => {
    token.clear();
    setDashboard(null);
    setView('auth');
  }, []);

  const lastFetchedAt = useRef(0);

  const load = useCallback(
    (next: View = 'main') => {
      setView('loading');
      lastFetchedAt.current = Date.now();
      fetchDashboard()
        .then((data) => {
          setDashboard(data);
          setStudy(data.study ?? IDLE_STUDY); // 옛 서버(필드 없음)는 「공부 기록 없음」으로 떨어진다
          setView(next);
        })
        .catch((e: Error) => {
          if (e.name === 'UnauthorizedError') toLogin();
          else {
            setError(e.message);
            setView('error');
          }
        });
    },
    [toLogin],
  );

  useEffect(() => {
    // 저장된 토큰이 있으면 로그인 화면을 건너뛰고 바로 대시보드를 받는다(재방문 경로).
    if (view === 'loading' && dashboard === null) load();
  }, [view, dashboard, load]);

  /**
   * 조용히 다시 받는다 — 화면은 그대로 두고 **성공했을 때만** 갈아끼운다: 실패로 멀쩡한 화면을 에러로
   * 바꾸지 않는다. 측정 중이어도 서버 응답이 진실이라 그대로 덮는다.
   *
   * <p>부르는 자리는 둘이다. ① 앱 복귀(웹·다른 기기에서 바꾼 값이 재진입 전까지 안 보이던 문제 — 잦아서
   * 스로틀) ② **서재에서 책을 바꾼 직후**(`force`) — 대시보드는 여기 App이 들고 있어 서재가 자기 책장만
   * 다시 받으면 홈 캐러셀은 옛 목록 그대로다. 앱을 나갔다 와야 반영되던 게 이것이다.
   */
  const silentRefresh = useCallback(
    (force = false) => {
      if (token.get() === null) return;
      if (!force && document.visibilityState !== 'visible') return;
      if (!shouldRefresh(lastFetchedAt.current, Date.now(), force)) return;
      lastFetchedAt.current = Date.now(); // 응답 전에 찍는다 — 연속 복귀가 요청을 겹쳐 쌓지 않게
      fetchDashboard()
        .then((data) => {
          setDashboard(data);
          setStudy(data.study ?? IDLE_STUDY); // 공부도 서버가 진실이다 — 다른 기기에서 시작했을 수 있다
        })
        .catch((e: Error) => {
          if (e.name === 'UnauthorizedError') toLogin(); // 토큰이 폐기됐으면 조용히 넘어갈 수 없다
        });
    },
    [toLogin],
  );

  useEffect(() => {
    const onVisible = () => silentRefresh();
    document.addEventListener('visibilitychange', onVisible);
    return () => document.removeEventListener('visibilitychange', onVisible);
  }, [silentRefresh]);

  // 탭 밖 전체 화면도 뒤로가기로 나갈 수 있다 — 각 화면의 「돌아가기」와 같은 자리로 돌려보낸다.
  useBackClose(view === 'link', () => setView('auth'));
  useBackClose(view === 'goal', () => {
    setFirstRun(false);
    setView('main');
  });
  useBackClose(view === 'studyGoal', () => setView('main'));
  useBackClose(view === 'settings', () => setView('main'));
  // 작성 화면이 위면 그것만 닫는다 — 단 밑에 깔린 여백 화면이 없으면 통째로 닫아 출발한 탭으로 돌아간다.
  useBackClose(margin?.composeBook != null, () => setMargin((m) => (m === null ? null : closeCompose(m))));
  useBackClose(margin !== null && margin.composeBook === null, () => setMargin(null));
  useBackClose(shop !== null, () => setShop(null));

  const handleError = useCallback(
    (e: Error) => {
      if (e.name === 'UnauthorizedError') toLogin();
    },
    [toLogin],
  );

  const applyTimer = useCallback((timer: TimerState) => {
    setDashboard((prev) => (prev === null ? prev : { ...prev, ...timer }));
  }, []);

  const applyGraph = useCallback((graph: DashboardResponse['graph']) => {
    setDashboard((prev) => (prev === null ? prev : { ...prev, graph }));
  }, []);

  /** 여백 문을 열면서 도는 종료 왕복 — 연타하면 두 번째 stop을 서버가 409로 거절한다. */
  const stoppingForMargin = useRef(false);

  /**
   * 목표 바꾸기 진입 — 홈 손잡이와 설정 화면이 **같은 경로**를 탄다(둘이 갈라지면 광고 규칙도 갈라진다).
   *
   * <p>전면 광고는 이 경로에만 붙는다 — 신규 계정의 첫 목표 설정은 로그인 브릿지에서 곧장 `load('goal')`로
   * 들어와 여기를 거치지 않으므로, 첫 인상이 광고가 되는 일은 없다.
   *
   * <p><b>광고가 끝난 뒤에 전환한다</b>(2026-08-14 수정). 예전엔 기다리지 않고 바로 전환해서, 1~2초 뒤
   * 로드가 끝난 광고가 목표 화면이 아니라 **그때 떠 있는 아무 화면 위에** 덮였다(실기기 실측: 목표 화면을
   * 지나 메인으로 돌아온 뒤 노출). `showInterstitialAd`는 못 뜨거나 무응답이어도 반드시 resolve하므로
   * 여기서 진입이 막힐 일은 없다.
   *
   * <p>기다리는 1~2초 동안 `goalAdPending`으로 버튼을 "준비 중"으로 바꾼다 — 겉보기 무반응이면 사용자가
   * 다시 누른다. 중복 호출 자체는 이 플래그가 먼저 막아 광고가 두 장 쌓이지 않는다.
   */
  const goToGoal = useCallback(async () => {
    if (goalAdPending) return;
    setGoalAdPending(true);
    await showInterstitialAd();
    setGoalAdPending(false);
    setView('goal');
  }, [goalAdPending]);

  /**
   * 공부 목표 바꾸기 진입 — 위와 <b>같은 몸, 같은 광고 그룹</b>이다(가는 화면만 다르다).
   *
   * <p>전면 지면을 새로 만들지 않는 이유: SDK에서 광고 그룹은 재고 단위지 진입점 단위가 아니라, 같은
   * 그룹을 두 자리에서 요청하는 데 제약이 없다. 무엇보다 「목표 바꾸기 = 광고 1회」 규칙이 모드와
   * 무관해야 사용자가 규칙을 하나만 배운다(독서만 광고면 「왜 독서만?」이 된다).
   *
   * <p>`goalAdPending`도 <b>공유</b>한다 — 모드는 한 번에 하나라 두 경로가 동시에 눌릴 수 없고,
   * 공유해야 설정 화면 버튼까지 함께 「준비 중」이 된다.
   */
  const goToStudyGoal = useCallback(async () => {
    if (goalAdPending) return;
    setGoalAdPending(true);
    await showInterstitialAd();
    setGoalAdPending(false);
    setView('studyGoal');
  }, [goalAdPending]);

  switch (view) {
    case 'auth':
      return (
        <LoginBridge
          onAuthenticated={() => load('main')}
          onNewAccount={() => {
            setFirstRun(true); // 신규 계정은 목표 설정을 먼저 유도한다(설계 §2.5-5).
            load('goal');
          }}
          onLinkAccount={() => setView('link')}
        />
      );

    case 'link':
      return <LinkAccount onLinked={() => load('main')} onBack={() => setView('auth')} />;

    case 'error':
      return (
        <Screen title="문제가 생겼어요">
          <ErrorMessage message={error} />
          <Button display="block" style={{ marginTop: 24 }} onClick={() => load()}>
            다시 시도
          </Button>
        </Screen>
      );

    default:
      break;
  }

  if (dashboard === null) return <Loading />;

  if (view === 'goal') {
    return (
      <Goal
        current={dashboard.todayGoalSeconds}
        firstRun={firstRun}
        onSaved={() => {
          setFirstRun(false);
          load('main');
        }}
        onSkip={() => {
          setFirstRun(false);
          setView('main');
        }}
      />
    );
  }

  if (view === 'studyGoal') {
    return (
      <Goal
        variant="study"
        // 옛 서버(필드 없음)는 「목표 없음」으로 떨어진다 — 휠은 0시간 0분에서 시작한다.
        current={study.goalSeconds ?? 0}
        firstRun={false} // 공부엔 온보딩이 없다 — 첫 진입 유도 문구가 설 자리가 아니다.
        onSaved={() => load('main')}
        onSkip={() => setView('main')}
      />
    );
  }

  if (view === 'settings') {
    return (
      <Settings
        dashboard={dashboard}
        onBack={() => setView('main')}
        // 닉네임·핸들이 바뀌면 대시보드가 옛 값을 들고 있다 — 홈 인사말·소셜이 같은 값을 봐야 한다.
        onProfileChanged={() => silentRefresh(true)}
        onGoGoal={goToGoal}
        goalAdPending={goalAdPending}
        onLogout={toLogin}
        onError={handleError}
      />
    );
  }

  /**
   * 측정 시작 — 문이 둘이라(탭바 원 · 여백 탭바) 구현을 여기 한 자리에 둔다.
   *
   * <p><b>실패 처리는 부르는 쪽 몫</b>이라 삼키지 않고 그대로 넘긴다 — 탭바는 바 위 스트립으로,
   * 여백은 전역 에러로 다르게 말한다(409 중복 시작을 어디서 보이느냐가 같지 않다).
   */
  const startTimer = () =>
    startSession(timerStartBookId(dashboard.readingBooks, dashboard.recentBookId, homeBookId)).then((timer) => {
      applyTimer(timer);
      trackEvent('reading_session_started');
      // 토스트가 「무슨 책으로 시작됐나」를 그리려면 서버가 확정한 값이 필요하다 — 부르는 쪽으로 흘린다.
      return timer;
    });

  /** 지금 보여 줄 모드 — 진행 중 측정이 저장값을 이긴다(재진입·다른 기기 시작을 한 줄이 함께 처리한다). */
  const mode = effectiveMode(dashboard.hasActiveSession, study.hasActiveSession, storedMode);

  /**
   * 지금 <b>보여 줄</b> 탭 — 모드가 바뀌어 사라진 칸에 서 있으면 홈으로 떨어진다({@link reconcileTab}).
   *
   * <p>상태(`tab`)는 그대로 둔다 — 원격 플립이 되돌아가면 원래 있던 탭으로 돌아오는 편이 옳고,
   * 무엇보다 파생값이라 동기화 effect가 0줄이다.
   */
  const shownTab = reconcileTab(tab, mode);

  /** 모드 전환 — 저장은 기기 로컬 한 줄이고, 화면은 위 파생값이 알아서 따라온다. */
  const changeMode = (next: TimerMode) => {
    localStorage.setItem(MODE_KEY, next);
    setStoredMode(next);
  };

  /**
   * 여백에서 탭바를 눌렀다 — 여백을 닫고 그 탭으로 나간다. 뒤에 깔린 남의 책방도 함께 닫는다
   * — 탭을 눌렀는데 책방이 나오면 「나갔다」가 아니다.
   *
   * <p>가운데 원(측정 시작)도 이 문을 지난다 — 그래서 「시작은 됐는데 여백에 그대로 남아 있다」가
   * 구조적으로 불가능하다(여백은 독서가 아니라는 규칙을 두 번째로 어기지 않는다).
   */
  const leaveMargin = (next: TabKey) => {
    setMargin(null);
    setShop(null);
    setTab(next);
  };

  /**
   * 여백 탭바의 가운데 원 — 전체 화면으로 서는 두 여백 화면(사람축 상세 · 책축)이 같은 것을 쓴다.
   * 작성은 이제 그 위에 겹치는 시트라 자기 탭바를 갖지 않는다(2026-08-29) — 시트가 딤으로 탭바를
   * 덮으므로, 작성 중에는 이 원 자체가 눌리지 않는다.
   *
   * <p><b>먼저 나간다</b> — 측정이 여백 위에서 시작되는 순간을 만들지 않는다.
   */
  const startFromMargin = () => {
    leaveMargin('home');
    startTimer().catch(handleError);
  };

  /**
   * 여백을 여는 <b>유일한 문</b> — 측정 중이면 먼저 끝낸다(「여백은 독서가 아니다」, 사용자 결정 2026-08-22).
   *
   * <p>진입 경로가 셋(홈·서재 여백 문 · 홈 소식 피드 · 남의 책방)인데 전부 이 문을 지나므로, 규칙을
   * 지키는 코드가 한 자리다. 새 진입점이 `setMargin`을 직접 부르면 규칙이 <b>조용히</b> 새기 때문에,
   * `app.test.tsx`가 여는 호출(`setMargin({…})`)이 여기 말고 없는지를 소스로 계측한다.
   *
   * <p><b>종료가 끝난 뒤에 열다</b> — 실패하면 여백을 열지 않는다. 측정이 살아 있는데 여백에 들어가면
   * 「끝난 줄 알았는데 계속 돌고 있었다」가 되어, 고치려던 그 결함이 그대로 남는다.
   *
   * <p><b>태깅 시트·완독 축하는 저절로 안 뜨다</b> — 그것들은 탭바 원의 종료 경로(`MainTabs` 안)에만
   * 달려 있고 이 문의 종료는 별개의 왕복이다. 작성이 겹침이 되어 `MainTabs`가 뒤에 살아남은 뒤에도
   * 그대로다 — 억제 코드가 필요 없는 이유가 「언마운트」에서 「경로가 다름」으로 옮겨졌을 뿐이다.
   *
   * <p>⚠️ <b>공부 측정도 같은 규칙으로 끊는다</b> — 규칙의 이름은 「여백은 독서가 아니다」지만 내용은
   * 「여백은 <b>측정</b>이 아니다」이다. 안 끊으면 여백 화면에서 두 가지가 동시에 깨진다: ① 공부 시간이
   * 남의 글을 읽는 동안 계속 쌓이고 ② 여백 탭바({@link MarginShell})가 「여기선 측정이 꺼져 있다」를
   * <b>전제로</b> 상태 없이 그려져, 그 원이 「독서 측정 시작」인 채로 눌려 <b>두 세션이 동시에</b> 돈다
   * (그 상태는 화면 어디에도 안 보인 채 스윕이 6시간 뒤에 닫는다 — 목 모드 실측으로 드러난 자리다).
   */
  const openMargin = (next: MarginState) => {
    if (!dashboard.hasActiveSession && !study.hasActiveSession) {
      setMargin(next);
      return;
    }
    if (stoppingForMargin.current) return;
    stoppingForMargin.current = true;
    // 공부만 돌고 있으면 이쪽으로 — 독서와 같은 모양의 왕복이되 잔디·태깅이 없다(공부는 잔디 밖).
    if (!dashboard.hasActiveSession) {
      const studied =
        study.activeStartedAt === null ? 0 : elapsedSeconds(study.activeStartedAt, Date.now());
      stopStudy()
        .then((state) => {
          setStudy(state);
          trackEvent('study_session_completed', { duration_seconds: studied });
          setMargin({ ...next, timerStopped: true });
        })
        .catch(handleError)
        .finally(() => {
          stoppingForMargin.current = false;
        });
      return;
    }
    // 종료 직전 값으로 재는다 — 응답엔 세션 길이가 없고, 탭바 원의 종료와 같은 셜법을 쓴다.
    const duration =
      dashboard.activeStartedAt === null ? 0 : elapsedSeconds(dashboard.activeStartedAt, Date.now());
    stopSession()
      .then((result) => {
        applyTimer(result.timer);
        applyGraph(result.graph); // stop 응답에 잔디가 동봉돼 새로고침 없이 즉시 갱신된다.
        // 세션이 실제로 끝났으므로 지표에도 남긴다 — 빠지면 콘솔 대표 전환이 과소집계된다.
        trackEvent('reading_session_completed', { duration_seconds: duration });
        setMargin({ ...next, timerStopped: true });
      })
      .catch(handleError)
      .finally(() => {
        stoppingForMargin.current = false;
      });
  };

  /*
   * 여백은 탭 위에 전체 화면으로 선다 — 닫으면 열었던 탭이 그대로 남는다.
   *
   * 작성만 **겹침**이다(2026-08-29): 시트라서 밑 화면을 갈아치우지 않고 그 위에 얹힌다. 그래서
   * 화면 판정은 `composeBook`을 걷어낸 좌표로 한다 — 시트가 열려도 밑에 깔린 것은 그대로다.
   */
  const screen = margin === null ? null : underCompose(margin);

  const composer =
    margin === null || margin.composeBook === null ? null : (
      <StoryComposer
        book={margin.composeBook}
        // 남겼으면 밑 여백 상세를 다시 받아야 방금 쓴 글이 거기 보인다(취소는 그럴 것이 없다).
        onDone={() => {
          setComposeEpoch((n) => n + 1);
          setMargin(closeCompose(margin));
        }}
        onCancel={() => setMargin(closeCompose(margin))}
        onError={handleError}
        timerStopped={margin.timerStopped === true}
      />
    );

  /**
   * 밑 화면 위에 작성 시트를 얹는다 — 깔릴 수 있는 화면이 **전부** 이 문을 지나야, 어느 화면 위에서
   * 시트를 열든 시트가 산다. 하나만 빠지면 그 경로에서 작성이 조용히 사라진다(`app.test.tsx`가 센다).
   * 도달 가능한 밑 화면은 둘뿐이다 — 사람축 여백 상세와 탭 화면(그 근거도 같은 테스트에 적었다).
   *
   * ⚠️ **최상위 Fragment에 key를 달거나 `{composer}{under}` 순서를 뒤집지 마라.** 시트가 열려도 밑
   * 화면이 remount되지 않는 것은 React가 **key 없는 최상위 Fragment를 언랩**해(react-dom 18.3.1의
   * `isUnkeyedTopLevelFragment`) `under`가 같은 index에 남기 때문이다. 둘 중 하나만 어겨도 밑 화면이
   * **조용히** 새로 마운트되어 스크롤·탭 상태가 날아간다(겹침으로 얻은 것이 통째로 사라진다).
   */
  const withCompose = (under: ReactNode) =>
    composer === null ? (
      under
    ) : (
      <>
        {under}
        {composer}
      </>
    );

  // 작성도 아니고 깔린 화면도 없는 조합은 만들지 않는다 — 만약 생겨도 탭으로 떨어져 막다른 길이 안 된다.
  if (margin !== null && margin.loginId !== null && margin.bookId !== null && screen === 'person') {
    const under = margin.bookId;
    const who = margin.loginId;
    return withCompose(
      <MarginShell
        tab={shownTab}
        mode={mode}
        onGo={leaveMargin}
        onStart={startFromMargin}
      >
        <BookMargin
          // 글을 남기면 새로 마운트돼 목록을 다시 받는다 — 시트는 겹침이라 저절로는 안 일어난다.
          key={composeEpoch}
          loginId={who}
          bookId={under}
          // 시트가 떠 있는 동안은 이 화면의 배너를 접는다 — 딤 뒤에서 도는 노출은 무효 트래픽이다.
          adSuppressed={margin.composeBook !== null}
          timerStopped={margin.timerStopped === true}
          onBack={() => setMargin(null)}
          onCompose={(book) => openMargin({ ...margin, bookId: under, composeBook: book })}
          // 닫으면서 연다 — 뒤로 가면 출발한 탭으로 돌아간다(검색 시트와 같은 교체 경로, T-166).
          onOpenProfile={(picked) => {
            setMargin(null);
            setShop(picked);
          }}
          onError={handleError}
        />
      </MarginShell>,
    );
  }

  /*
   * 「이 책의 여백」 — 사람 좌표 없이 isbn13 하나로 서는 화면(검색 배지에서 들어온다).
   * 사람축보다 **뒤에** 판정한다: 둘 다 들고 있으면 사람축이 이긴다(「내 여백」 탭이 거기 산다).
   *
   * 시트를 얹지 않는다 — 이 화면에서는 작성을 열 길이 없다(`BookMarginAllView`에 `onCompose` 프롭
   * 자체가 없다). 도달 불가 분기를 감싸면 「이 조합이 가능하다」는 거짓말이 코드에 남는다.
   */
  if (margin !== null && margin.isbn13 !== null && screen === 'book') {
    return (
      <MarginShell tab={shownTab} mode={mode} onGo={leaveMargin} onStart={startFromMargin}>
        <BookMarginAll
          isbn13={margin.isbn13}
          timerStopped={margin.timerStopped === true}
          onBack={() => setMargin(null)}
          // 「내 여백」 탭 — 사람축 화면으로 갈아탄다(작성·삭제·토글이 사는 자리). 이미 여백 안이라
          // 측정은 진작 끝났으므로 문을 다시 지나도 아무 일이 없다(멱등).
          onOpenMine={(bookId) =>
            dashboard.loginId !== null &&
            openMargin({ ...margin, loginId: dashboard.loginId, bookId, composeBook: null })
          }
          // 닫으면서 연다 — 뒤로 가면 출발한 탭으로 돌아간다(사람축과 같은 교체 경로).
          onOpenProfile={(picked) => {
            setMargin(null);
            setShop(picked);
          }}
          onError={handleError}
        />
      </MarginShell>
    );
  }

  /*
   * 남의 책방 — 여백보다 **뒤에** 판정한다. 그래야 여기서 연 여백이 이 화면 위에 서고, 그 여백을 닫으면
   * 책방이 다시 나온다(2단 스택). 헤더도 카운트 핸들러도 주지 않는다 — 서버 follow-list는 본인 것만 준다.
   *
   * 여기도 시트를 얹지 않는다 — 책방은 `setMargin(null)`을 거쳐야 열려(`onOpenProfile`) 작성과 공존할
   * 수 없고, 여기서 여는 여백은 `composeBook: null`이다.
   */
  if (shop !== null) {
    return (
      <Profile
        loginId={shop}
        onBack={() => setShop(null)}
        onOpenMargin={(bookId) => openMargin({ loginId: shop, bookId, isbn13: null, composeBook: null })}
        onError={handleError}
      />
    );
  }

  return withCompose(
    <MainTabs
      // 여백 상세와 같은 이유로 다시 마운트한다 — 서재의 인라인 여백 박스(「여백 N」 + 최근 2장)도
      // 작성 화면이 탭을 언마운트해 주던 덕에 갱신되고 있었다(plan.md 「응답 캐시 ⏸ 보류」의 전제).
      // 탭·고른 책은 App이 들고 있어(위 `homeBookId`) 살아남지만, 축하 배너·태깅 시트 상태와 스크롤은
      // 버려진다 — 전체 화면 시절 저장 경로가 정확히 그랬으므로 회귀는 아니고, 취소 경로는 개선이다
      // (예전엔 취소해도 날아갔다). 잃는 것이 아까워지면 그때 재조회를 좁힌다(지금은 remount가 가장 싸다).
      key={composeEpoch}
      tab={shownTab}
      onTabChange={setTab}
      dashboard={dashboard}
      mode={mode}
      study={study}
      onStudyChange={setStudy}
      onChangeMode={changeMode}
      homeBookId={homeBookId}
      onSelectHomeBook={setHomeBookId}
      onOpenMargin={(loginId, bookId) => openMargin({ loginId, bookId, isbn13: null, composeBook: null })}
      // 책축 — 사람 좌표를 비운다. 내가 가진 책인지는 서버가 `myBookId`로 알려 주므로 클라가 안 따진다.
      onOpenBookMargin={(isbn13) => openMargin({ loginId: null, bookId: null, isbn13, composeBook: null })}
      onComposeMargin={(book) =>
        // 문은 핸들이 있을 때만 그려지므로 여기서 loginId는 언제나 있다(`marginDoorBook`이 앞에서 판정).
        // `bookId: null` — 작성만 띄운다. 닫으면 여백 상세를 거치지 않고 여기(홈·서재)로 곧장 돌아온다.
        dashboard.loginId !== null &&
        openMargin({ loginId: dashboard.loginId, bookId: null, isbn13: null, composeBook: book })
      }
      onTimerChange={applyTimer}
      onStartTimer={startTimer}
      onGraphChange={applyGraph}
      // 홈 손잡이(「변경 ›」·GoalHandle)는 코드 무변경 — 어느 목표 화면으로 가느냐만 모드가 고른다.
      onGoGoal={mode === 'study' ? goToStudyGoal : goToGoal}
      goalAdPending={goalAdPending}
      onGoSettings={() => setView('settings')}
      onError={handleError}
      onShelfChanged={() => silentRefresh(true)}
      onHandleCreated={() => silentRefresh(true)}
    />,
  );
}

/**
 * 여백 위에 탭바를 남기는 껍데기 — 여백은 탭 밖 전체 화면이라 {@link MainTabs}가 통째로
 * 언마운트되고, 그때 탭바도 함께 사라져 나갈 길이 뒤로가기 하나뿐이었다.
 *
 * <p><b>상태가 0이다</b> — 여백에 있다는 것은 곱 측정이 꺼져 있다는 뜻이라(여백 진입 게이트가
 * 먼저 끝낸다) 원은 언제나 「시작」이고 잠길 탭도 없다. `dashboard`를 들이지 않는 이유다.
 */
export function MarginShell({
  tab,
  mode = 'reading',
  onGo,
  onStart,
  children,
}: {
  /** 여백을 열었던 탭 — 그 칸이 선택 표시로 남아 「어디서 왜는지」를 잃지 않는다. */
  tab: TabKey;
  /**
   * 지금 재는 것 — 탭바가 그릴 목록을 고른다. 여백은 공부 모드에서도 도달 가능하므로
   * (진입 게이트는 측정만 끊는다) 이걸 안 받으면 <b>여백에서만</b> 탭바가 책방으로 되돌아간다.
   */
  mode?: TimerMode;
  /** 탭을 눌렀다 — 여백을 닫고 그 탭으로 나간다. */
  onGo: (tab: TabKey) => void;
  /** 가운데 원 — 홈으로 나가며 측정을 시작한다(여백에 남지 않는다). */
  onStart: () => void;
  children: ReactNode;
}) {
  return (
    <>
      <div style={{ paddingBottom: TAB_BAR_SPACE }}>{children}</div>
      <BottomTabBar tab={tab} onTabChange={onGo} action={{ active: false, busy: false, onPress: onStart, mode }} />
    </>
  );
}

/**
 * 메인 탭 셸 — 탭바와 본문을 같은 `TABS` 목록에서 그려 index와 화면이 어긋날 자리를 없앤다.
 * 탭바가 하단에 떠 있으므로 본문 아래에 그만큼 여백을 둔다.
 *
 * <p><b>측정 시작·종료도 여기가 든다</b>(홈에서 승격). 탭바를 그리는 바로 그 컴포넌트가 액션을 들어야
 * 어느 탭에서든 시작·종료할 수 있다 — 홈이 들고 있으면 다른 탭으로 가는 순간 언마운트돼 사라진다.
 * 덤으로 태깅 시트·축하 배너가 탭 전환에 살아남는다(종전보다 개선).
 */
export function MainTabs({
  tab,
  onTabChange,
  dashboard,
  mode,
  study,
  onStudyChange,
  onChangeMode,
  homeBookId,
  onSelectHomeBook,
  onOpenMargin,
  onComposeMargin,
  onOpenBookMargin,
  onTimerChange,
  onStartTimer,
  onGraphChange,
  onGoGoal,
  goalAdPending,
  onGoSettings,
  onError,
  onShelfChanged,
  onHandleCreated,
}: {
  tab: TabKey;
  onTabChange: (tab: TabKey) => void;
  dashboard: DashboardResponse;
  /** 지금 재는 것 — 독서냐 공부냐. 파생값이라 여기선 받기만 한다({@link effectiveMode}). */
  mode: TimerMode;
  /** 공부 원장 — 독서(`dashboard`)와 따로 온다. */
  study: StudyState;
  /** 공부 start/stop 응답 반영 — 대시보드를 건드리지 않는다(원장이 갈렸으니 갱신도 갈린다). */
  onStudyChange: (study: StudyState) => void;
  /** 모드 전환 — 저장은 App이 든다(홈 토글이 부른다). */
  onChangeMode: (mode: TimerMode) => void;
  /** 홈 캐러셀에서 고른 책 — 탭 밖 전체 화면이 홈을 언마운트해도 남도록 App이 든다(`undefined`=아직 안 고름). */
  homeBookId: number | null | undefined;
  onSelectHomeBook: (bookId: number | null) => void;
  /** 그 사람의 그 책 여백을 연다 — 홈 소식과 서재 문이 같은 자리로 온다(App이 전체 화면으로 든다). */
  onOpenMargin: (loginId: string, bookId: number) => void;
  /** 홈 여백 문 — 그 책의 작성 화면으로 직행한다. */
  onComposeMargin: (book: BookOption) => void;
  /** 검색 행의 「여백 N」 배지 — 사람 좌표 없이 책 하나(isbn13)로 서는 화면을 연다. */
  onOpenBookMargin: (isbn13: string) => void;
  onTimerChange: (timer: TimerState) => void;
  /** 측정 시작 — 구현은 App이 든다(여백 탭바와 같은 것을 쓴다). 실패는 여기서 스트립으로 말한다. */
  /** 측정 시작 — 응답 `TimerState`를 그대로 흘려준다(토스트가 그릴 책의 단일 출처). */
  onStartTimer: () => Promise<TimerState>;
  onGraphChange: (graph: DashboardResponse['graph']) => void;
  onGoGoal: () => void;
  /** 전면광고를 기다리는 중 — 목표 손잡이를 "준비 중"으로 바꿔 탭이 먹통으로 보이지 않게 한다. */
  goalAdPending: boolean;
  /** 홈 맨 위의 계정 진입 — 프로필·설정 화면(닉네임·@아이디·목표·로그아웃)으로 나간다. */
  onGoSettings: () => void;
  onError: (error: Error) => void;
  /** 서재에서 책이 바뀌면 홈이 보는 대시보드도 같이 갱신해야 한다 — 안 그러면 캐러셀이 옛 목록 그대로다. */
  onShelfChanged: () => void;
  /** 책방에서 핸들(@아이디)을 만들면 대시보드의 loginId가 바뀐다 — 다시 받아야 다른 화면도 같은 값을 본다. */
  onHandleCreated: () => void;
}) {
  /** 액션 처리 중 — 연타로 세션이 두 번 시작·종료되지 않게 원을 흐리고 핸들러를 잠근다. */
  const [busy, setBusy] = useState(false);
  /**
   * 지금 <b>무엇이든</b> 재고 있나 — 탭 잠금·안내 배너·토스트 정리가 전부 이 하나를 본다.
   *
   * <p>모드별로 갈라 물으면 「공부 재는 중인데 서재로 넘어가진다」 같은 빠뜨린 조합이 생긴다.
   * 원장은 갈렸지만 <b>「재는 중」이라는 사실은 하나</b>다.
   */
  const measuring = dashboard.hasActiveSession || study.hasActiveSession;
  /** 태깅 시트 — `null`이면 닫힘. 열림 여부와 대상 세션이 늘 같이 움직여 상태 하나로 족하다. */
  const [tagging, setTagging] = useState<Untagged | null>(null);
  /** 첫 완료 축하 — 홈에 prop으로 내린다. 다른 탭에서 끝냈어도 홈에 돌아오면 배너가 보인다. */
  const [celebrate, setCelebrate] = useState(false);
  /** 액션 실패 문구 — 다른 탭엔 홈의 ErrorMessage가 없으므로 탭바 위 스트립으로 띄운다. */
  const [actionError, setActionError] = useState<string | null>(null);
  /**
   * 지금 걷고 있는 걸음(`-1`=안 걷는 중) — 렌더 중에 읽는다(정적 렌더 하니스로도 계측된다).
   *
   * <p>⚠️ <b>마운트 때 시작하지 않는다</b>(심사 반려 대응 — {@link shouldShowGuideBanner}). 옛 배선은
   * 초기값을 `nextFlowStep()`으로 둬서 「안 본 걸음이 있다」가 곧 「지금 걷는다」였고, 그래서 진입 즉시 딤이 깔렸다.
   */
  const [flowIndex, setFlowIndex] = useState(-1);
  /** 걷기를 포기했나 — 포기 후에는 닫힘 알림이 와도 커서를 되살리지 않는다(순서와 무관하게). */
  const abandoned = useRef(false);
  /** 배너를 ✕로 닫았나 — 이 마운트 동안의 즉시 반영만 맡는다(다음 진입의 판정은 기기 기록이 맡는다). */
  const [guideClosed, setGuideClosed] = useState(false);

  /**
   * 어디서 안내가 닫혀도 커서가 따라온다 — <b>인라인 안내는 화면 안에서 닫히므로</b> 이 구독이
   * 없으면 흐름이 그 자리에서 멈춘다(그게 이 기능의 핵심 배선이다).
   *
   * <p>걷는 중이 아니면 아무것도 하지 않는다 — 안 그러면 어딘가의 닫힘 하나가 <b>시작하지도 않은 투어를</b>
   * 깨워 진입 직후 노출이 되살아난다(수동 시작의 유일한 문은 배너여야 한다).
   */
  useEffect(
    () =>
      onCoachmarkChange(() =>
        setFlowIndex((current) => (current < 0 ? -1 : abandoned.current ? -1 : nextFlowStep())),
      ),
    [],
  );

  /**
   * 인라인 안내의 잠금을 커서와 동기화한다 — 걷지 않을 때 그것들이 스스로 뜨면 「진입 직후 오버레이」가
   * 되어 심사 반려가 재발한다(실측). 언마운트(설정·여백 전체 화면)에서도 잠근다.
   */
  useEffect(() => {
    setCoachmarkWalking(flowIndex >= 0);
    return () => setCoachmarkWalking(false);
  }, [flowIndex]);

  /** 잠긴 탭을 눌렀다는 안내가 떠 있는지 — 잠깐 떴다 스스로 사라진다. */
  const [lockHint, setLockHint] = useState(false);
  const lockHintTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  /** 안내를 띄운다 — 연타해도 타이머가 겹치지 않게 앞의 것을 걷고 다시 잰다. */
  const showLockHint = () => {
    if (lockHintTimer.current !== null) clearTimeout(lockHintTimer.current);
    setLockHint(true);
    lockHintTimer.current = setTimeout(() => setLockHint(false), TAB_LOCK_HINT_MS);
  };

  /** 시작 토스트 — 다른 탭에서 시작했을 때 「무슨 책인가」를 말한다(홈은 「읽는 중」 카드가 이미 말한다). */
  const [startToast, setStartToast] = useState<StartToastState | null>(null);
  const startToastTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  /** 교체 시트가 열려 있는지 — 토스트의 [바꾸기]가 여는 유일한 문이다. */
  const [changing, setChanging] = useState(false);

  /** 토스트를 걷는다 — 타이머까지 함께 걷어야 나중에 빈 토스트가 되살아나지 않는다. */
  const clearStartToast = () => {
    if (startToastTimer.current !== null) clearTimeout(startToastTimer.current);
    setStartToast(null);
  };

  /** 토스트를 띄운다 — `showLockHint`와 같은 패턴(앞 타이머를 걷고 다시 잰다). */
  const showStartToast = (next: StartToastState) => {
    if (startToastTimer.current !== null) clearTimeout(startToastTimer.current);
    setStartToast(next);
    startToastTimer.current = setTimeout(() => setStartToast(null), START_TOAST_MS);
  };

  // 측정이 끝나면 잠금이 풀리므로 안내도 함께 걷는다(남은 타이머가 사라진 화면을 건드리지 않게 한다).
  // 시작 토스트·교체 시트도 같은 운명이다 — 세션이 다른 경로로 끝나면(여백 진입 자동 종료 등)
  // 「이 책으로 재는 중」이라 말하는 카드와 그 대상을 고르는 시트는 둘 다 거짓말이 된다.
  useEffect(() => {
    if (measuring) return;
    setLockHint(false);
    setStartToast(null);
    setChanging(false);
    return () => {
      if (lockHintTimer.current !== null) clearTimeout(lockHintTimer.current);
    };
  }, [measuring]);

  /**
   * 토스트 타이머는 <b>언마운트 때만</b> 걷는다.
   *
   * <p>⚠️ 위 effect의 cleanup에 얹으면 안 된다 — React는 cleanup을 <b>의존성이 바뀔 때마다</b> 돌리는데,
   * 측정을 시작하는 순간이 바로 `hasActiveSession`이 `false → true`로 뒤집히는 순간이다. 그러면 방금
   * 건 5초 타이머가 그 자리에서 지워져 <b>시작 토스트가 영영 안 사라진다</b>(목 모드 실측: 8초 뒤에도
   * 그대로였고, 같은 탭에서 잰 맨 `setTimeout(5000)`은 5186ms에 정상 발화해 스로틀이 아님을 갈랐다).
   * 교체 토스트는 그 사이 `hasActiveSession`이 안 변해 멀쩡했던 탓에 증상이 절반만 보였다.
   *
   * <p>세션이 끝날 때 남은 타이머를 따로 걷지 않아도 되는 이유: 위 effect가 이미 `setStartToast(null)`로
   * 상태를 비워, 뒤늦게 발화한 타이머는 같은 값을 한 번 더 쓰는 무해한 no-op이 된다.
   */
  useEffect(
    () => () => {
      if (startToastTimer.current !== null) clearTimeout(startToastTimer.current);
    },
    [],
  );

  /**
   * 독서등 — 측정 중 홈이 밤이 된다. 색은 css가 들고 여기선 스위치만 올린다.
   *
   * <p>`body`에 붙이는 이유는 <b>캔버스가 body의 배경</b>이기 때문이다(`html body { background }`).
   * 어두운 토큰 자체는 css가 `<main>` 안쪽에만 얹으므로, 탭바(화면 밖 형제)는 밝게 남는다 —
   * 측정을 끝내는 원이 어둠에 잠기면 끄는 법을 잃는다.
   *
   * <p>cleanup이 없으면 홈을 벗어나거나 언마운트될 때 클래스가 남아 앱 전체가 어두워진다.
   */
  useEffect(() => {
    document.body.classList.toggle(LAMP_CLASS, lampOn(tab, dashboard.hasActiveSession, mode));
    return () => document.body.classList.remove(LAMP_CLASS);
  }, [tab, dashboard.hasActiveSession, mode]);

  /**
   * 공부 모드 색 — 독서등과 같은 배선(스위치만 여기, 색은 css). 이쪽은 <b>탭과 무관</b>하다:
   * 모드는 앱 전체의 상태라 어느 탭에 있든 잉크가 같은 색이어야 한다.
   */
  useEffect(() => {
    document.body.classList.toggle(STUDY_CLASS, mode === 'study');
    return () => document.body.classList.remove(STUDY_CLASS);
  }, [mode]);

  const flowStep = flowIndex < 0 ? undefined : COACHMARK_FLOW[flowIndex];

  // 걸음이 설명할 화면으로 데려간다 — 딤 뒤에 그 화면이 보이는 상태로 읽게 하는 것이 이 흐름의 문법이다.
  useEffect(() => {
    const next = flowTabChange(flowStep, tab);
    if (next !== null) onTabChange(next);
  }, [flowStep, tab, onTabChange]);

  /** 한 걸음 봤다고 남긴다 — 커서는 위 구독이 옮긴다(진행 경로가 하나로 모인다). */
  const advanceFlow = () => {
    if (flowStep !== undefined) dismissCoachmark(flowStep.name);
  };

  /** 배너를 눌러 걷기를 시작한다 — 안내로 들어오는 <b>유일한 문</b>이다(#833 안내만 본 사람은 서재부터). */
  const startFlow = () => {
    abandoned.current = false;
    setFlowIndex(nextFlowStep());
  };

  /** 사용자가 스스로 탭을 옮기면 길 안내는 물러난다(인라인 걸음은 그 화면에서 다시 뜬다). */
  const abandonFlow = () => {
    abandoned.current = true;
    flowStepsOnAbandon().forEach(dismissCoachmark);
    setFlowIndex(-1);
  };

  // 안내가 가리키던 칸과 화면이 어긋난 채 남지 않게, 사용자 탭 이동은 걷기를 접는다.
  const changeTab = (next: TabKey) => {
    if (flowIndex >= 0) abandonFlow();
    onTabChange(next);
  };

  /**
   * 걷는 도중 모드가 study로 뒤집히면(원격 — 다른 기기에서 공부 시작) 그 자리에서 접는다.
   *
   * <p>안 접으면 말풍선이 <b>존재하지 않는 칸</b>(공부 탭바엔 책방이 없다)을 가리키는 프레임이 뜬다.
   * 위 `guide` 게이트는 <b>시작</b>을 막을 뿐이라, 이미 걷는 중인 흐름은 여기가 맡는다.
   */
  useEffect(() => {
    if (mode === 'study' && flowIndex >= 0) {
      abandoned.current = true;
      flowStepsOnAbandon().forEach(dismissCoachmark);
      setFlowIndex(-1);
    }
    // 모드 플립에만 반응한다 — flowIndex를 넣으면 걷는 매 걸음마다 다시 돈다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mode]);

  // 401은 App이 재로그인으로 처리하고, 그 외(409 중복 시작 등)만 화면에 남긴다.
  const fail = (e: Error) => (e.name === 'UnauthorizedError' ? onError(e) : setActionError(e.message));

  /**
   * 공부 측정 여닫기 — 독서 경로와 <b>갈라 둔다</b>. 태깅 시트·완독 축하·잔디 갱신이 여기 없는 것이
   * 곧 「공부는 잔디 밖」이라는 규칙의 구현이다(억제 코드가 0줄이다 — 배선 자체가 없다).
   */
  const studyAction = () => {
    if (study.hasActiveSession) {
      const duration =
        study.activeStartedAt === null ? 0 : elapsedSeconds(study.activeStartedAt, Date.now());
      stopStudy()
        .then((next) => {
          onStudyChange(next);
          trackEvent('study_session_completed', { duration_seconds: duration });
        })
        .catch(fail)
        .finally(() => setBusy(false));
      return;
    }
    startStudy()
      .then((next) => {
        onStudyChange(next);
        trackEvent('study_session_started');
        showStartToast({ book: null, changed: false, mode: 'study' });
      })
      .catch(fail)
      .finally(() => setBusy(false));
  };

  /** 탭바 가운데 원 — 측정 중이면 종료, 아니면 시작. 이 앱에서 세션을 여닫는 유일한 자리다. */
  const timerAction = () => {
    // 안내를 읽고 곧장 이 버튼을 누른 경우 — 덮개가 탭바 아래라 원이 그대로 눌린다. 그 걸음은 역할을 다했다.
    advanceFlow();
    if (busy) return;
    setBusy(true);
    setActionError(null);
    if (mode === 'study') {
      studyAction();
      return;
    }
    if (dashboard.hasActiveSession) {
      // 종료 시각 기준 경과 — 서버 응답엔 세션 길이가 없다. 홈의 매초 tick 없이 이 한 번의 계산으로 족하다.
      const duration =
        dashboard.activeStartedAt === null ? 0 : elapsedSeconds(dashboard.activeStartedAt, Date.now());
      stopSession()
        .then((result) => {
          onTimerChange(result.timer);
          onGraphChange(result.graph); // stop 응답에 잔디가 동봉돼 새로고침 없이 즉시 갱신된다.
          setCelebrate(result.firstCompletedSession);
          // 이 앱의 핵심 전환 — 콘솔 대표 전환이 이 이벤트라, 빠지면 지표 자체가 죽는다.
          trackEvent('reading_session_completed', { duration_seconds: duration });
          // 종료 직후 시트를 저절로 연다(태깅은 지금 기억이 가장 선명하다). 붙일 책이 0권이면 열지 않는다 —
          // 빈 시트는 닫는 것 말고 할 수 있는 게 없는 막다른 길이다.
          if (result.untagged && dashboard.readingBooks.length > 0) setTagging({ sessionId: result.sessionId });
        })
        .catch(fail)
        .finally(() => setBusy(false));
    } else {
      setCelebrate(false); // 지난 세션의 축하가 새 측정 화면에 남아 있으면 거짓말이 된다.
      onStartTimer()
        .then((timer) => {
          // 책은 서버가 확정한 값을 쓴다(`?? null`은 이 필드를 안 주는 옛 서버 방어 — api.ts의 기존 규약).
          // 「홈에선 안 띄운다」 판정은 여기가 아니라 렌더 게이트가 든다(토스트가 5초를 버티므로).
          showStartToast({ book: timer.activeBook ?? null, changed: false });
        })
        .catch(fail)
        .finally(() => setBusy(false));
    }
  };

  const tag = (book: BookOption) => {
    if (tagging === null) return;
    setBusy(true);
    tagBook(tagging.sessionId, book.id)
      .then(() => setTagging(null))
      .catch(fail)
      .finally(() => setBusy(false));
  };

  /**
   * 측정 대상 교체 — 세션은 멈추지 않는다. 응답 `TimerState`로 대시보드를 갱신해야 홈의 「읽는 중」
   * 카드가 어긋나지 않는다(재조회 없이 따라온다).
   */
  const changeBook = (book: BookOption | null) => {
    setBusy(true);
    setActionError(null);
    changeActiveBook(book === null ? null : book.id)
      .then((timer) => {
        onTimerChange(timer);
        setChanging(false);
        // 시작이 아니라 교체를 확인한다 — 같은 문구면 두 번 시작한 것처럼 읽힌다.
        showStartToast({ book: timer.activeBook ?? null, changed: true });
      })
      .catch((e) => {
        // ⚠️ 실패해도 시트를 닫는다. 에러 스트립은 탭바 층(z 100)인데 시트 패널은 z 201 **불투명**이라,
        //    시트를 연 채로 두면 메시지가 통째로 가려진다 — 사용자는 눌렀는데 아무 일도 안 일어나는
        //    화면을 보고 또 누른다(목 모드 실측: 스트립 좌표의 elementFromPoint가 시트의 책 행이었다).
        //    409(방금 끝난 세션)·네트워크 오류 둘 다 이 경로로 온다.
        setChanging(false);
        fail(e);
      })
      .finally(() => setBusy(false));
  };

  /** 교체 시트 닫기 — 안 바꾸고 닫으면 토스트도 되살리지 않는다(제 역할은 끝났다). */
  const closeChangeSheet = () => setChanging(false);

  /** 시트 닫기 — 태깅 시트를 닫는 건 곧 「건너뛰기」다(다시 들어갈 자리를 만들지 않는다). */
  const closeSheet = () => setTagging(null);

  // 안드로이드 뒤로가기는 시트만 닫는다 — 시트가 열린 채로 미니앱이 꺼지지 않게.
  // 둘은 동시에 열리지 않는다(태깅은 종료 후, 교체는 측정 중) — backStack이 스택이라 공존도 안전하다.
  useBackClose(tagging !== null, closeSheet);
  useBackClose(changing, closeChangeSheet);

  return (
    <>
      {/* 떠 있는 탭바 아래로 본문 끝이 숨는다 — 바 높이 + 띄운 높이 + 홈 인디케이터 + 숨 쉴 여백만큼 비운다. */}
      <div style={{ paddingBottom: TAB_BAR_SPACE }}>
        {tab === 'home' && (
          <Home
            dashboard={dashboard}
            mode={mode}
            study={study}
            onChangeMode={onChangeMode}
            // 측정 중엔 못 바꾼다 — 잠금 안내는 탭 잠금과 <b>같은 스트립</b>을 쓴다(새 층을 만들지 않는다).
            onBlockedModeChange={showLockHint}
            // 안내로 들어오는 문 — 홈은 자리만 정하고(히어로 카드 속), 만드는 쪽은 흐름을 든 여기다.
            guide={
              // 공부 모드에선 안내를 안 만든다 — 투어가 가리키는 책방 칸이 그 탭바엔 없고,
              // 문구도 서재·책방·여백 전제라 공부 화면에서 할 말이 아니다.
              mode === 'reading' && shouldShowGuideHero(flowIndex >= 0, guideClosed, measuring) ? (
                <GuideHero
                  onStart={startFlow}
                  onDismiss={() => {
                    abandonFlow(); // 길 안내만 접고 인라인 걸음은 남긴다(포기 규칙 그대로)
                    setGuideClosed(true);
                  }}
                />
              ) : null
            }
            selectedBookId={homeBookId}
            onSelectBook={onSelectHomeBook}
            onTimerChange={onTimerChange}
            celebrate={celebrate}
            onGoGoal={onGoGoal}
            goalAdPending={goalAdPending}
            onGoSettings={onGoSettings}
            onError={onError}
            onOpenMargin={onOpenMargin}
            onComposeMargin={onComposeMargin}
          />
        )}
        {tab === 'library' && (
          <Library
            myLoginId={dashboard.loginId}
            onError={onError}
            onShelfChanged={onShelfChanged}
            onOpenMargin={onOpenMargin}
            onComposeMargin={onComposeMargin}
            onOpenBookMargin={onOpenBookMargin}
          />
        )}
        {tab === 'bookshop' && (
          <Bookshop
            myLoginId={dashboard.loginId}
            onHandleCreated={onHandleCreated}
            onError={onError}
          />
        )}
        {/* 달력은 공부 탭바로만 도달한다 — 도달 경로가 곧 게이트라 여기서 모드를 다시 안 따진다. */}
        {tab === 'calendar' && <StudyCalendar onError={onError} />}
        {tab === 'history' && <History graph={dashboard.graph} />}
      </div>

      {/* 액션 실패는 탭바 바로 위 스트립으로 — 다른 탭엔 홈의 ErrorMessage 자리가 없다.
          다음 액션을 누르면 지워진다(자동 타이머는 두지 않는다). */}
      {actionError !== null && (
        <div
          style={{
            position: 'fixed',
            left: TAB_BAR_MARGIN,
            right: TAB_BAR_MARGIN,
            bottom: `calc(12px + env(safe-area-inset-bottom) + ${TAB_BAR_HEIGHT}px + 8px)`,
            zIndex: TAB_BAR_Z_INDEX,
            padding: '4px 14px',
            background: 'var(--adaptiveBackground, #FCFAF5)',
            borderRadius: 12,
            boxShadow: '0 4px 16px rgba(0, 0, 0, 0.12)',
          }}
        >
          <ErrorMessage message={actionError} />
        </div>
      )}

      {/* 잠긴 탭을 눌렀을 때의 안내 — 액션 실패 스트립과 같은 자리다(탭바 알약은 `overflow: hidden`이라
          그 안에 두면 잘린다). 색은 토큰이라 독서등(밤)에서도 함께 어두워진다. */}
      {lockHint && (
        <div
          role="status"
          style={{
            position: 'fixed',
            left: TAB_BAR_MARGIN,
            right: TAB_BAR_MARGIN,
            bottom: `calc(12px + env(safe-area-inset-bottom) + ${TAB_BAR_HEIGHT}px + 8px)`,
            zIndex: TAB_BAR_Z_INDEX,
            padding: '8px 14px',
            background: 'var(--adaptiveGrey100, #FCFAF5)',
            color: 'var(--adaptiveGrey600, #6F6A5E)',
            borderRadius: 12,
            fontSize: 14,
            textAlign: 'center',
            boxShadow: '0 4px 16px rgba(0, 0, 0, 0.12)',
          }}
        >
          {TAB_LOCK_HINT}
        </div>
      )}

      {/* 시작 토스트 — 위 두 스트립과 같은 좌표라 게이트가 한 장만 세운다(우선순위: 에러 > 잠금 > 토스트). */}
      {startToastVisible(startToast, actionError, lockHint, tab) && (
        <StartToast
          toast={startToast}
          onChange={() => {
            clearStartToast(); // 시트를 여는 순간 토스트는 제 역할을 다했다.
            setChanging(true);
          }}
        />
      )}

      {/* 코치마크는 탭바보다 먼저 그린다 — 덮개가 탭바 아래 층이라는 것을 코드 순서로도 읽히게 둔다.
          인라인 걸음(말풍선 없음)에는 아무것도 그리지 않는다 — 그 화면의 `Coachmark`가 맡는다. */}
      {flowStep?.bubble !== undefined && (
        <TabBarCoachmark bubble={flowStep.bubble} index={flowIndex} onNext={advanceFlow} />
      )}

      <BottomTabBar
        tab={tab}
        onTabChange={changeTab}
        locked={measuring}
        onBlocked={showLockHint}
        action={{ active: measuring, busy, onPress: timerAction, mode }}
      />

      {/* 시트는 측정 종료 후 태깅 자리 하나다 — 탭바(zIndex 100) 위에 떠 어느 탭에서 끝내도 보인다. */}
      {tagging !== null && (
        <BookSheet
          books={dashboard.readingBooks}
          disabled={busy}
          onPick={tag}
          onSkip={closeSheet}
          onClose={closeSheet}
        />
      )}

      {/* 교체 시트 — 측정 중에만 열리고, 토스트의 [바꾸기]가 여는 유일한 문이다. */}
      {changing && (
        <ChangeBookSheet
          books={dashboard.readingBooks}
          currentBookId={dashboard.activeBook?.id ?? null}
          disabled={busy}
          onPick={changeBook}
          onClose={closeChangeSheet}
        />
      )}
    </>
  );
}

/**
 * 투어 한 걸음 — 탭바의 그 칸을 말풍선으로 가리킨다.
 *
 * <p><b>스포트라이트를 마스크 없이 얻는다</b>: 덮개를 탭바(`TAB_BAR_Z_INDEX`)보다 한 층 <b>아래</b>
 * 깔면 불투명 배경의 탭바만 저절로 덮개 위에 밝게 남는다. SVG 마스크로 구멍을 뚫을 필요가 없고,
 * 덤으로 <b>가리킨 칸이 그대로 눌린다</b>(덮개를 위에 얹으면 가리키는 대상 자체를 막는 자기모순이
 * 된다 — 그 부등식이 테스트로 박혀 있다).
 *
 * <p>위치 계산도 없다(ref·`getBoundingClientRect`·리사이즈 관측 0): 가로는 {@link slotCenter}가
 * 순수 CSS로 주고, 세로는 탭바와 같은 식(띄운 높이 + 홈 인디케이터 + 바 높이)에 여백만 더한다.
 */
/**
 * 안내 카드 — 첫 사용 안내로 들어오는 문. <b>홈 히어로 카드 속을 통째로</b> 쓴다.
 *
 * <p>진입 직후 자동으로 뜨던 안내를 <b>사용자가 눌러서</b> 시작하도록 뒤집은 결과다(심사 반려 대응 —
 * {@link shouldShowGuideHero}). 이 카드는 <b>화면 안 요소</b>라 시트가 아니고 배경을 잠그지 않는다.
 * ✕가 있는 것이 요점이다 — 「들이대지 않는다」가 눈에 보여야 한다.
 *
 * <p>한때는 헤더 아래 <b>얇은 배너</b> 한 줄이었는데 <b>놓치기 쉬웠다</b>(M-1, 2026-08-23 실기기 제보).
 * 그래서 홈에서 가장 크고 시선이 먼저 닿는 것 — 히어로 카드 — 이 곧 안내의 문이 된다: 처음 온 사람에게
 * 그 박스는 타이머가 아니라 <b>안내 시작 버튼</b>이다. 껍데기(연필 테두리·독서등 표식)는 홈이 그대로 들고
 * 여기서는 <b>속만</b> 채우므로, 안내를 닫아도 카드가 제자리에서 타이머로 돌아올 뿐 화면이 밀리지 않는다.
 *
 * <p>⚠️ 첫 측정이 멀어지지 않는다 — 이 앱에서 측정을 여닫는 자리는 <b>탭바 가운데 원</b>이고 이 카드는
 * 원래부터 버튼이 아니었다. 카드 안 손잡이(ⓘ·「변경 ›」·「목표 정하기」)는 안내가 떠 있는 동안만 물러난다.
 *
 * <p>큰 버튼과 ✕는 <b>형제</b>다(중첩 아님) — 버튼 안의 버튼은 잘못된 DOM이라 React가 경고한다.
 * ✕가 뒤에 그려져 겹치는 자리에서 위에 얹히므로 z 층을 따로 쌓지 않는다.
 *
 * <p>닫기는 {@link MainTabs}의 포기 규칙을 그대로 쓴다: 길 안내만 본 것으로 접고 <b>인라인 걸음은 남긴다</b>
 * (서재·홈에서 그 대상 옆에 뜨는 안내는 시트로 읽힐 자리가 아니고, 그때가 제 차례다).
 */
export function GuideHero({ onStart, onDismiss }: { onStart: () => void; onDismiss: () => void }) {
  const sage = 'var(--adaptiveBlue700, #4F6B4C)';

  return (
    <div style={{ position: 'relative' }}>
      <button
        type="button"
        onClick={onStart}
        style={{
          display: 'block',
          width: '100%',
          border: 'none',
          background: 'transparent',
          padding: 0,
          textAlign: 'center',
          cursor: 'pointer',
        }}
      >
        {/* 오버라인 — 카드가 평소 「오늘 읽은 시간」을 놓는 자리다. 자간을 벌려 머리말로 읽히게 한다. */}
        <span style={{ display: 'block', fontSize: 12, letterSpacing: 3, color: sage }}>처음이신가요?</span>
        {/* 카드의 주인공 자리 — 세리프로 히어로의 수(오늘 읽은 시간)와 같은 무게를 받는다. */}
        <span style={{ display: 'block', marginTop: 6, fontSize: 26, ...SERIF_VALUE }}>앱 사용법 보기 ›</span>
        <span
          style={{ display: 'block', marginTop: 10, fontSize: 14, color: 'var(--adaptiveGrey600, #6F6A5E)' }}
        >
          서재·책방·여백까지 차례로 짚어드려요
        </span>
      </button>

      <button
        type="button"
        aria-label="안내 카드 닫기"
        onClick={onDismiss}
        style={{
          position: 'absolute',
          top: -12,
          right: -8,
          border: 'none',
          background: 'transparent',
          padding: 8,
          color: 'var(--adaptiveGrey600, #6F6A5E)',
          fontSize: 14,
          cursor: 'pointer',
        }}
      >
        ✕
      </button>
    </div>
  );
}

export function TabBarCoachmark({
  bubble,
  index,
  onNext,
}: {
  bubble: NonNullable<FlowStep['bubble']>;
  /** 흐름 안의 자리 — 진행 표시(점)의 위치이자, 마지막 걸음인지 판정하는 근거다. */
  index: number;
  onNext: () => void;
}) {
  const last = index === COACHMARK_FLOW.length - 1;

  return (
    <>
      {/* 덮개 자체가 진행 버튼이다 — 아무 데나 누르면 다음 걸음으로 간다(닫기 X를 따로 두지 않는다). */}
      <button
        type="button"
        aria-label={last ? '안내 닫기' : '다음 안내 보기'}
        onClick={onNext}
        style={{
          position: 'fixed',
          inset: 0,
          zIndex: TAB_BAR_Z_INDEX - 1,
          border: 'none',
          padding: 0,
          background: 'rgba(0, 0, 0, 0.55)',
          cursor: 'pointer',
        }}
      />

      <CoachmarkBubble
        title={bubble.title}
        detail={bubble.detail}
        tail="down"
        // 말풍선이 좌우 24px에 서므로, 화면 기준 칸 중앙에서 그만큼 뺀 자리가 말풍선 안의 꼬리 위치다.
        tailLeft={`calc(${slotCenter(bubble.slot)} - 24px)`}
        dots={{ index, total: COACHMARK_FLOW.length }}
        style={{
          position: 'fixed',
          zIndex: TAB_BAR_Z_INDEX + 1,
          left: 24,
          right: 24,
          bottom: `calc(12px + env(safe-area-inset-bottom) + ${TAB_BAR_HEIGHT}px + 12px)`,
        }}
      />
    </>
  );
}

/**
 * 토스트·시트가 함께 쓰는 미니 표지 — 책이 없으면 점선 빈 칸(「책 없이」의 시각 언어).
 *
 * <p>높이·radius를 프롭으로 받지 않고 {@link CoverInitial}과 <b>같은 식으로 파생</b>한다. 둘이 한 행에
 * 나란히 서므로 값이 갈리면 그 행만 어긋나는데, 프롭으로 받으면 width만 바꾼 다음 사람이 그걸 모른다.
 */
function MiniCover({ book, width }: { book: BookOption | null; width: number }) {
  if (book === null) {
    return (
      <span
        style={{
          flex: 'none',
          width,
          height: Math.round(width * 1.4),
          borderRadius: 4,
          border: '1.5px dashed #B8B29F',
          boxSizing: 'border-box',
        }}
      />
    );
  }
  return (
    <span style={{ flex: 'none' }}>
      <CoverInitial title={book.title} width={width} />
    </span>
  );
}

/**
 * 측정 시작 토스트 — 다른 탭에서 ▶를 눌렀을 때 <b>무슨 책으로 시작됐는지</b>를 말하고 그 자리에서
 * 바꾸게 한다. 대상 책은 홈 캐러셀 선택(`timerStartBookId`)으로 조용히 정해지는데, 정작 그 화면
 * 어디에도 표시되지 않던 것이 이 장치의 존재 이유다(UX 감사 3f).
 *
 * <p>자리는 `actionError`·`lockHint` 스트립과 <b>같은 좌표</b>다(탭바 위 8px) — 세 장이 겹치지 않게
 * {@link startToastVisible} 게이트가 한 장만 세운다.
 *
 * <p>⚠️ <b>애니메이션·트랜지션이 없다</b>(T-176). 이 카드는 표지를 품고 있어, 값이 변하는 그림자·필터를
 * 걸면 실기기에서 표지를 매 프레임 재래스터화한다 — 발광 `box-shadow`가 책방 격자를 무너뜨린 자리와
 * 같은 클래스다. 나타남·사라짐은 즉시 마운트/언마운트다.
 *
 * <p>화면에서 꺼내 둔 이유는 늘 같다: 하니스가 정적 렌더라 시작 성공 콜백에 도달할 수 없다(T-149).
 */
export function StartToast({ toast, onChange }: { toast: StartToastState; onChange: () => void }) {
  const message = startToastMessage(toast);
  const hasBookControls = toastHasBookControls(toast);
  // 제목만 세리프로 — 문구 안에서 「무슨 책인가」가 값이고 나머지는 서술이다.
  const quoted = toast.book === null ? null : `『${toast.book.title}』`;
  const rest = quoted === null ? message : message.slice(quoted.length);

  return (
    <div
      role="status"
      style={{
        position: 'fixed',
        left: TAB_BAR_MARGIN,
        right: TAB_BAR_MARGIN,
        bottom: `calc(12px + env(safe-area-inset-bottom) + ${TAB_BAR_HEIGHT}px + 8px)`,
        zIndex: TAB_BAR_Z_INDEX,
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        padding: '10px 12px',
        background: 'var(--adaptiveBackground, #FCFAF5)',
        borderRadius: 12,
        border: '1px solid transparent',
        borderImage: PENCIL_FRAME,
        boxShadow: '0 4px 16px rgba(0, 0, 0, 0.14)',
      }}
    >
      {/* 표지 자리와 [바꾸기]는 책이 있는 측정의 장치다 — 공부 토스트엔 둘 다 안 그린다
          ({@link toastHasBookControls}). 덤으로 아래 버튼 배경 리터럴이 공부 모드의 마지막 세이지
          누출이었는데, 그 조각이 아예 안 그려지면서 색 스코프도 함께 닫힌다. */}
      {hasBookControls && <MiniCover book={toast.book} width={26} />}
      <span style={{ flex: 1, minWidth: 0, fontSize: 14, lineHeight: 1.5, wordBreak: 'keep-all' }}>
        {quoted !== null && <span style={{ ...SERIF_VALUE, fontWeight: 700 }}>{quoted}</span>}
        {rest}
      </span>
      {hasBookControls && (
        <button
          type="button"
          onClick={onChange}
          style={{
            flex: 'none',
            padding: '6px 12px',
            border: 0,
            borderRadius: 8,
            background: 'rgba(110, 138, 106, 0.16)',
            color: 'var(--adaptiveBlue700, #4F6B4C)',
            fontSize: 13,
            fontWeight: 700,
            cursor: 'pointer',
          }}
        >
          바꾸기
        </button>
      )}
    </div>
  );
}

/**
 * 측정 대상 교체 시트 — 종료 후 태깅 시트(`BookSheet`)와 <b>다른 자리</b>다. 저쪽은 "끝난 세션에 무슨
 * 책이었는지 붙이기"고 이쪽은 "재는 도중 대상을 갈아 끼우기"라, 여기엔 저쪽에 없는 셋이 있다:
 * <b>지금 대상 표시</b>·<b>「책 없이」로 되돌리는 행</b>·<b>측정이 안 멈춘다는 부제</b>.
 *
 * <p>부제가 특히 필요하다 — 재는 도중에 뭔가를 누르라고 하면 사용자는 시간이 날아갈까 봐 안 누른다.
 *
 * <p>읽는 중인 책이 0권이어도 「책 없이」 행은 남는다(빈 시트는 닫는 것 말고 할 게 없다).
 */
export function ChangeBookSheet({
  books,
  currentBookId,
  disabled,
  onPick,
  onClose,
}: {
  books: BookOption[];
  /** 지금 재고 있는 책 — `null`이면 「책 없이」 행에 표시가 선다. */
  currentBookId: number | null;
  disabled: boolean;
  /** `null` = 「책 없이」 선택. */
  onPick: (book: BookOption | null) => void;
  onClose: () => void;
}) {
  const row = (book: BookOption | null) => {
    const current = (book === null ? null : book.id) === currentBookId;
    return (
      <button
        key={book === null ? 'none' : book.id}
        type="button"
        // 계측용 표지 — TDS emotion 클래스 사이에서 "어느 행인가"를 집을 손잡이가 없다(BookSheet 선례).
        data-book-title={book === null ? '' : book.title}
        aria-current={current ? 'true' : undefined}
        disabled={disabled}
        onClick={() => onPick(book)}
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          width: '100%',
          marginBottom: 8,
          padding: '10px 12px',
          border: 'none',
          borderRadius: 10,
          background: current ? 'rgba(110, 138, 106, 0.14)' : 'transparent',
          cursor: 'pointer',
        }}
      >
        <MiniCover book={book} width={30} />
        <span
          style={{
            flex: 1,
            minWidth: 0,
            textAlign: 'left',
            fontSize: 15,
            fontWeight: current ? 700 : undefined,
            color: book === null ? 'var(--adaptiveGrey600, #6F6A5E)' : undefined,
            wordBreak: 'keep-all',
          }}
        >
          {book === null ? '책 없이' : book.title}
        </span>
        {current && (
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" style={{ flex: 'none', stroke: 'var(--adaptiveBlue700, #4F6B4C)' }} strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round">
            <path d="M5 12.5 10 17.5 19 7" />
          </svg>
        )}
      </button>
    );
  };

  return (
    <Sheet title="무슨 책으로 잴까요?" onClose={onClose}>
      <div style={{ marginTop: -4, marginBottom: 10, fontSize: 13, color: 'var(--adaptiveGrey600, #6F6A5E)', wordBreak: 'keep-all' }}>
        측정은 멈추지 않아요 — 지금까지 잰 시간은 바꾼 책에 붙어요
      </div>
      {books.map((b) => row(b))}
      {row(null)}
    </Sheet>
  );
}

/**
 * 하단 탭바 — 아이콘+라벨 세로 스택을 **떠 있는 알약(pill)**에 담는다.
 *
 * <p>토스 미니앱 브랜딩 가이드가 요구하는 플로팅 형태다(전폭 부착형은 2026-08-12 심사 반려 사유 2).
 * 홈 인디케이터 회피는 `padding-bottom`이 아니라 **띄운 높이**(`bottom`)가 맡는다 — 둘 다 두면
 * safe-area가 두 번 더해져 바가 붕 뜬다.
 */
export function BottomTabBar({
  tab,
  onTabChange,
  action,
  locked = false,
  onBlocked,
}: {
  tab: TabKey;
  onTabChange: (tab: TabKey) => void;
  /** 가운데 측정 액션 — 탭이 아니라 동작이다(그래서 `TABS` 밖에 산다). `mode`는 라벨만 가른다. */
  action: { active: boolean; busy: boolean; onPress: () => void; mode?: TimerMode };
  /** 측정 중인가 — 홈을 뺀 탭이 잠긴다({@link tabLocked}). 가운데 액션은 절대 안 잠근다. */
  locked?: boolean;
  /** 잠긴 탭을 눌렀다 — 안내 문구는 `MainTabs`가 그린다(이 알약은 `overflow: hidden`이라 안에 두면 잘린다). */
  onBlocked?: () => void;
}) {
  // 모드가 목록을 고르고, 그리기·index 풀이가 **같은 배열**을 쓴다 — 여기가 갈리면 누른 칸과
  // 바뀌는 탭이 어긋난다. 액션이 모드를 이미 들고 있어(라벨 분기) 새 프롭이 필요 없다.
  const tabs = tabsFor(action.mode ?? 'reading');
  const change = tabChangeHandler(onTabChange, tabs);

  const cells = tabs.map(({ key, label, icon }, index) => {
    const selected = key === tab;
    const shut = tabLocked(key, locked);
    return (
      <button
        key={key}
        type="button"
        // ARIA tabs 패턴은 `role="tabpanel"`과 짝일 때만 성립하는데 이 앱엔 그 짝이 없다 —
        // 하단 내비게이션의 표준 마크업(APG)인 `aria-current`로 현재 위치를 말한다.
        aria-current={selected ? 'page' : undefined}
        // `disabled`가 아니라 `aria-disabled`다 — 진짜로 잠그면 클릭이 안 와서 이유를 말할 기회가 없다.
        aria-disabled={shut ? true : undefined}
        title={label}
        onClick={() => (shut ? onBlocked?.() : change(index))}
        style={{
          flex: 1,
          minHeight: TAB_BAR_HEIGHT,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 2,
          padding: 0,
          border: 'none',
          background: 'transparent',
          // 시안 4c — 고른 칸은 세이지 700이다(500은 옆 라벨 회색과 대비가 약했다).
          color: selected ? 'var(--adaptiveBlue700, #4F6B4C)' : 'var(--adaptiveGrey600, #6F6A5E)',
          // 잠긴 칸은 흐려진다 — 눌러 보기 전에 눈으로 먼저 알아야 한다.
          opacity: shut ? 0.35 : 1,
          cursor: 'pointer',
        }}
      >
        {/* 시안 4c — 고른 칸의 아이콘만 알약을 입는다. 색약에게도 「여기」가 형태로 보이게.
            안 고른 칸도 <b>같은 38×26 span</b>을 쓰고 배경만 비운다 — 알약이 있다가 없으면 칸마다
            아이콘 위치가 달라져 탭을 옮길 때 레이아웃이 튄다. 반투명 세이지를 쓰는 것은 의도다
            (불투명 토큰보다 밤 테마에서 잘 적응한다). */}
        <span
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            width: 38,
            height: 26,
            borderRadius: 13,
            // 토큰 경유 — 공부 모드에서 이 알약도 저절로 파랑이 된다(리터럴이면 세이지로 남는다).
            background: selected ? 'var(--accentPill, rgba(110,138,106,.18))' : 'transparent',
          }}
        >
          <svg
            width="22"
            height="22"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            // 22px 아이콘에서 0.2 차이가 실제로 보인다 — 굵기도 「고른 칸」을 함께 말한다.
            strokeWidth={selected ? 2 : 1.8}
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
          >
            <path d={icon} />
          </svg>
        </span>
        {/* 전부 700이면 굵기가 아무 말도 안 한다 — 고른 칸만 굵다(비선택 400은 body 상속과 같다). */}
        <span style={{ fontSize: 11, fontWeight: selected ? 700 : 400, lineHeight: 1.2 }}>{label}</span>
      </button>
    );
  });

  // 각 탭의 onClick이 map 시점의 TABS index를 그대로 닫아 두므로, 액션을 끼워도 index↔화면 대응은 흔들리지 않는다.
  cells.splice(TIMER_ACTION_SLOT, 0, <TimerActionButton key="timer-action" {...action} />);

  return (
    <nav
      aria-label="메인 탭"
      style={{
        position: 'fixed',
        left: TAB_BAR_MARGIN,
        right: TAB_BAR_MARGIN,
        bottom: 'calc(12px + env(safe-area-inset-bottom))',
        zIndex: TAB_BAR_Z_INDEX,
        display: 'flex',
        overflow: 'hidden', // 모서리 밖으로 새는 탭 눌림 효과를 알약 안에 가둔다
        background: 'var(--adaptiveBackground, #FCFAF5)',
        borderRadius: 28,
        boxShadow: '0 4px 16px rgba(0, 0, 0, 0.12)',
      }}
    >
      {cells}
    </nav>
  );
}

/**
 * 탭바 가운데의 측정 액션 — 채운 원이라 스트로크 아이콘들 사이에서 **동작**으로 읽힌다.
 *
 * <p>라벨 글자는 없다(원 44px과 11px 라벨은 56px 높이에 함께 못 선다) — 상태는 `aria-label`이 말한다.
 * 원이 셀(69×56) 안에 들어가므로 알약의 `overflow: hidden`에 잘리지 않는다(돌출형 아님) —
 * 46px + 링 3px = 52px라 시안 4c로 키운 뒤에도 세로·가로 모두 여유가 남는다.
 */
function TimerActionButton({
  active,
  busy,
  onPress,
  mode = 'reading',
}: {
  active: boolean;
  busy: boolean;
  onPress: () => void;
  mode?: TimerMode;
}) {
  const view = timerActionView(active, mode);

  return (
    <button
      type="button"
      aria-label={view.label}
      onClick={onPress}
      style={{
        flex: 1,
        minHeight: TAB_BAR_HEIGHT,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 0,
        border: 'none',
        background: 'transparent',
        cursor: 'pointer',
      }}
    >
      <span
        style={{
          width: 46,
          height: 46,
          borderRadius: '50%',
          background: view.background,
          // 시안 4c의 링 — **고정값 단층**이다. 애니메이션을 걸지 않는다: T-176이 발광
          // `box-shadow` 무한 애니메이션으로 표지를 초당 60번 재래스터화한 자리와 같은 종류이고,
          // 데스크톱·목 모드는 그 클래스를 원리상 못 잡는다. 값은 상태 전환 때만 바뀐다.
          boxShadow: view.ring,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          opacity: busy ? 0.6 : 1,
        }}
      >
        <svg width="22" height="22" viewBox="0 0 24 24" fill="#FFFFFF" aria-hidden="true">
          <path d={view.icon} />
        </svg>
      </span>
    </button>
  );
}
