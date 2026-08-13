import { Button, ProgressBar, Text } from '@toss/tds-mobile';
import { useEffect, useState } from 'react';

import type { BookOption, DashboardResponse, QuoteDto, TimerState, WaiveResponse } from '../api';
import { ApiError, logout, startSession, stopSession, tagBook, waiveDebt } from '../api';
import { useBackClose } from '../back';
import { elapsedSeconds, formatClock, formatDuration } from '../format';
import {
  GOAL_MET_TEMPLATE_CODE,
  REWARD_AD_GROUP_ID,
  notificationAgreementSupported,
  requestNotificationAgreement,
  trackEvent,
  watchRewardAd,
} from '../toss';
import { CoverInitial, ErrorMessage, GrassGrid, Screen, sectionStyle } from '../ui';

/** 홈 잔디 미리보기 폭 — 최근 15주만 축약해 보여주고 전체는 기록 화면이 맡는다(카드 폭을 채우는 주 수). */
const PREVIEW_WEEKS = 15;

/** 알림 동의 결과 캐시 — 값은 토스가 준 결과 문자열 그대로. 정본은 토스이고 이건 카드 노출 스위치일 뿐이다. */
const AGREEMENT_KEY = 'booktimer.notificationAgreement';

/**
 * 진행바 색 — `global.css`가 TDS `--adaptiveBlue500`을 이 세이지로 재테마한다. TDS ProgressBar는
 * 색을 prop으로만 받아 CSS 변수가 안 닿으므로 값을 직접 준다(다른 초록을 쓰면 화면에 초록이 둘이 된다).
 */
const SAGE = '#6E8A6A';

/**
 * 측정 중 안심 문구 — 이 앱의 핵심 계약은 "화면을 꺼도 서버가 센다"인데 측정 중 화면에 그 말이 없었다.
 * 첫 세션에 한정하지 않는다: 짧고 무해하며, 잊어버리는 건 신규 유저만이 아니다.
 */
export const ACTIVE_SESSION_RELIEF = '화면을 꺼도 측정은 계속돼요. 책 읽고 오세요 🌿';

/** 축하 중 잔디 카드에 두르는 테두리 — 폴드 아래 카드로 시선을 끄는 유일한 표지다. */
export const HIGHLIGHT_BORDER = `2px solid ${SAGE}`;

/**
 * 첫 완료 축하 배너 — 서버 `firstCompletedSession`이 참인 그 한 번만. 잔디는 1초만 읽어도 lv1로
 * 점등되는데(`ContributionGraphBuilder.levelFor`) 미리보기가 폴드 아래라 첫 보상을 아무도 못 봤다.
 *
 * <p>화면에서 꺼내 둔 이유는 늘 같다 — 하니스가 정적 렌더라 「측정 끝내기」를 눌러 켜진 상태에
 * 도달할 수 없다(`BookSheet`와 같은 처지).
 */
export function FirstSessionBanner({ show }: { show: boolean }) {
  if (!show) return null;

  return (
    <div style={{ marginTop: 12, padding: 14, borderRadius: 12, background: '#EFF3EE', textAlign: 'center' }}>
      <Text typography="st11" style={{ display: 'block', wordBreak: 'keep-all' }}>
        🌱 첫 독서 기록이 심어졌어요! 아래 잔디에 첫 칸이 생겼어요.
      </Text>
    </div>
  );
}

/**
 * 처음 골라 둘 책 — 최근 읽은 책(=이어 읽기)이 읽는 중 목록에 있으면 그 책, 아니면 첫 책, 없으면 `null`.
 *
 * <p>웹 `BookPickForm`의 `defaultBook`과 같은 규칙이다. `recentBookId`가 목록 밖일 수 있는 건 그 책을
 * 다 읽었거나 뺐기 때문이다 — 그때 아무것도 안 고른 채로 두면 "측정 시작"이 죽은 버튼이 된다.
 */
export function defaultBookId(readingBooks: BookOption[], recentBookId: number | null): number | null {
  return readingBooks.find((b) => b.id === recentBookId)?.id ?? readingBooks[0]?.id ?? null;
}

/**
 * 히어로 파생값 — 웹 `frontend/src/dashboard/timerProgress.ts`의 `computeProgress`를 옮겼다.
 *
 * <p>웹은 UX 리뷰로 "오늘 남은 시간" 카운트다운을 **"오늘 읽은 시간" 카운트업**으로 뒤집었다(성취를
 * 세지, 빚을 세지 않는다). 남은 시간은 보조 메타로 강등된다. 미니앱도 같은 프레이밍을 쓴다.
 *
 * <p>서버는 스냅샷만 주므로 측정 중 라이브 값은 `remainingSeconds - elapsed`로 만든다 — 기존 elapsed
 * 인터벌이 그대로 카운트업의 동력이 되어 tick을 따로 두지 않는다. `carryover`면 밀린 시간은 오늘 몫이
 * 아니라 바닥(floor)이라 빼고 센다(그래야 어제 빚이 오늘 성취를 갉아먹지 않는다).
 *
 * <p>목표 미설정(0)이면 나눌 게 없어 `progress`는 `null`(게이지를 안 그린다)이고 달성이라 우기지도
 * 않는다 — 웹은 이 경우를 100% 달성으로 치지만, 미니앱은 목표 설정으로 유도하는 자리라 그대로 둔다.
 */
export function todayProgress(
  timer: Pick<TimerState, 'remainingSeconds' | 'carriedDebtSeconds' | 'todayGoalSeconds' | 'carryover'>,
  elapsed: number,
): { todayRead: number; remainingToGoal: number; overflow: number; progress: number | null; achieved: boolean } {
  const { carriedDebtSeconds: floor, todayGoalSeconds: goal, carryover } = timer;
  const remainingNow = timer.remainingSeconds - elapsed;
  const todayDebt = carryover ? remainingNow - floor : remainingNow;
  // 스냅샷이 어긋나도 음수 시간이 화면에 뜨지 않게 바닥을 친다(표시용 값이라 여기서 자른다).
  const todayRead = Math.max(0, goal - todayDebt);
  return {
    todayRead,
    remainingToGoal: Math.max(0, todayDebt),
    overflow: Math.max(0, todayRead - goal),
    progress: goal > 0 ? Math.min(1, todayRead / goal) : null,
    achieved: goal > 0 && todayDebt <= 0,
  };
}

/**
 * 용서 문구가 말하는 밀린 분 — 웹 `TimerCard.forgiveMinutes`와 같은 규칙(항상 분, 최소 1분).
 *
 * <p>`formatDuration`을 쓰면 1분 미만 부채가 "45초"로 나와 뒤에 붙는 조사가 "45초은"으로 깨진다.
 * 분으로 고정하면 조사가 언제나 "분은"이라 문장이 성립한다.
 */
export function forgiveMinutes(debtSeconds: number): number {
  return Math.max(1, Math.round(debtSeconds / 60));
}

/**
 * 리워드 광고 버튼을 노출할지 — 셋 다 참이어야 한다.
 *
 * <p>① 밀린 시간이 있다(=죄책감이 화면에 뜬 순간, 보상이 필요한 바로 그 지점) ② 서버가 오늘 지급
 * 가능하다고 했다 ③ 광고 그룹 ID가 설정됐다(config-gate). **부채가 없으면 광고의 존재 자체가 안 보인다** —
 * 입문자에게 "광고 보는 앱" 인상을 주지 않으려는 배치다(설계 §3).
 */
export function showWaiverButton(
  carriedDebtSeconds: number,
  debtWaiverAvailable: boolean,
  adGroupId: string,
): boolean {
  return carriedDebtSeconds > 0 && debtWaiverAvailable && adGroupId !== '';
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
export async function askNotificationAgreement(): Promise<string | null> {
  const result = await requestNotificationAgreement(GOAL_MET_TEMPLATE_CODE);
  if (result !== null) localStorage.setItem(AGREEMENT_KEY, result);
  return result;
}

/**
 * 실패 문구 — 서버가 준 평문(409 "오늘은 이미 사용했어요" 등)은 그대로 쓰고, SDK가 준 광고 에러는
 * 영문·기술 문구라 그대로 띄우면 안 되므로 안내로 바꾼다.
 */
export function waiverErrorMessage(error: Error): string {
  return error instanceof ApiError ? error.message : '광고를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.';
}

/**
 * 시트가 서 있는 자리 — 웹 `BookPickSheet`와 같은 겸용 구조다. 하나의 시트가 두 자리를 맡는다:
 * `start`(측정 전 무슨 책을 읽을지) / `tag`(측정 종료 후 무슨 책이었는지).
 */
export type BookSheetMode = 'start' | 'tag';

/** 모드별 문구·보조 CTA — 겸용 시트에서 두 자리가 갈리는 지점은 결국 이 두 줄이 전부다. */
export const SHEET_COPY: Record<BookSheetMode, { title: string; cta: string }> = {
  start: { title: '어떤 책을 읽을까요?', cta: '책 없이 시작' },
  tag: { title: '무슨 책을 읽으셨나요?', cta: '건너뛰기' },
};

/**
 * 시트를 열까 — 고를 책이 0권이면 열지 않는다(`null`).
 *
 * <p>빈 시트는 막다른 길이라 그 자리는 홈의 「첫 책 추가하기」 빈 상태가 계속 맡는다. 태깅도 같다 —
 * 붙일 책이 없는데 "무슨 책이었나요?"를 띄우면 닫는 것 말고 할 수 있는 게 없다.
 */
export function openSheetMode(mode: BookSheetMode, books: BookOption[]): BookSheetMode | null {
  return books.length > 0 ? mode : null;
}

/**
 * 시트에서 고른 책의 행선지 — `start`면 칩을 갈아끼우고, `tag`면 방금 세션에 붙인다.
 *
 * <p>같은 목록이 두 일을 하므로 여기가 어긋나면 조용히 틀린다: 태깅 자리에서 칩만 갈아끼우면 기록은
 * 영영 책 없이 남고, 시작 자리에서 태깅 API를 부르면 없는 세션에 붙이려 든다.
 */
export function pickHandler(
  mode: BookSheetMode,
  handlers: { select: (book: BookOption) => void; tag: (book: BookOption) => void },
): (book: BookOption) => void {
  return mode === 'start' ? handlers.select : handlers.tag;
}

/**
 * 책 고르기 바텀시트 — 딤 + 하단 패널. 행은 표지 자리 + 제목이라 버튼 나열에 없던 시각 위계가 생긴다.
 *
 * <p>TDS `BottomSheet`을 쓰지 않은 이유: 그건 포털(`tds-mobile-portal-container`)로 그려져
 * `renderToStaticMarkup` 하니스에서 **마크업이 통째로 비어 나온다**(실측) — 이 저장소는 jsdom을 두지
 * 않기로 했으므로 시트 내용이 영영 계측 불가가 된다. 딤·safe-area·zIndex는 이 30줄로 충분하다.
 *
 * <p>화면에서 꺼내 둔 이유는 늘 같다: 하니스가 정적 렌더라 「바꾸기」를 눌러 열린 상태에 도달할 수 없어,
 * 시트 자체는 여기서 직접 렌더해야 계측된다.
 */
export function BookSheet({
  mode,
  books,
  selectedId = null,
  disabled,
  onPick,
  onSkip,
  onClose,
}: {
  mode: BookSheetMode;
  books: BookOption[];
  selectedId?: number | null;
  disabled: boolean;
  onPick: (book: BookOption) => void;
  onSkip: () => void;
  onClose: () => void;
}) {
  const { title, cta } = SHEET_COPY[mode];

  return (
    <>
      {/* 딤 — 탭바(zIndex 100) 위를 덮어야 시트 아래로 탭바가 비치지 않는다. */}
      <div
        onClick={onClose}
        style={{ position: 'fixed', inset: 0, zIndex: 200, background: 'rgba(0, 0, 0, 0.45)' }}
      />
      <div
        role="dialog"
        aria-modal="true"
        aria-label={title}
        style={{
          position: 'fixed',
          left: 0,
          right: 0,
          bottom: 0,
          zIndex: 201,
          maxHeight: '72vh',
          overflowY: 'auto',
          // 홈 인디케이터 위로 CTA가 올라오게 — 바닥 여백만 safe-area를 탄다.
          padding: '20px 20px calc(20px + env(safe-area-inset-bottom))',
          borderRadius: '16px 16px 0 0',
          background: '#FCFAF5',
          boxShadow: '0 -4px 20px rgba(0, 0, 0, 0.14)',
        }}
      >
        <Text typography="t6" fontWeight="bold" style={{ display: 'block', marginBottom: 12 }}>
          {title}
        </Text>
        {books.map((book) => (
          <button
            key={book.id}
            type="button"
            // 고른 책의 표지는 이 한 속성 — 배경 틴트만으로는 마크업에서 선택을 가릴 수 없다.
            aria-current={book.id === selectedId ? 'true' : undefined}
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
              background: book.id === selectedId ? '#EFEADD' : 'transparent',
              cursor: 'pointer',
            }}
          >
            <CoverInitial title={book.title} width={28} />
            {/* 한글 제목이 flex 자식이라 minWidth:0이 없으면 줄바꿈 대신 행을 밀어낸다. */}
            <Text typography="st11" style={{ flex: 1, minWidth: 0, textAlign: 'left', wordBreak: 'keep-all' }}>
              {book.title}
            </Text>
          </button>
        ))}
        <Button display="block" variant="weak" size="medium" style={{ marginTop: 8 }} disabled={disabled} onClick={onSkip}>
          {cta}
        </Button>
      </div>
    </>
  );
}

/**
 * 로그아웃 → 로그인 화면. **무슨 일이 있어도 화면을 넘긴다.**
 *
 * <p>`api.logout()`이 이미 실패를 삼키고 `finally`로 토큰을 버리므로, 여기서 넘기지 않으면 토큰 없는
 * 홈에 남아 무엇을 눌러도 401만 나는 막다른 길이 된다. 그래서 `finally`로 이동을 못 박는다.
 */
export async function logoutAndLeave(onDone: () => void): Promise<void> {
  try {
    await logout();
  } catch {
    // 폐기 실패는 삼킨다 — 되던지면 호출부의 `void`가 unhandled rejection이 된다. 토큰은 이미 버려졌다.
  }
  onDone();
}

/**
 * 계정 진입점 — 홈 맨 아래 muted 한 줄 + 로그아웃.
 *
 * <p>미니앱엔 설정 화면이 없어 `api.logout()`은 구현돼 있는데 부르는 UI가 없었다. 계정 관리·상세 설정은
 * 웹이 본진이라(설계 §2.5) 여기서 흉내내지 않고 어디로 가면 되는지만 말한다.
 *
 * <p>확인 단계를 밖에서 받는 이유는 늘 같다 — 정적 렌더 하니스가 클릭을 못 잡아, 프롭이 아니면
 * 「정말 로그아웃」 가지에 영영 닿지 못한다(서재 `confirmDelete`·책방 `confirmBlock`과 같다).
 */
export function AccountSection({
  confirm,
  onConfirm,
  onLogout,
}: {
  confirm: boolean;
  onConfirm: (confirm: boolean) => void;
  onLogout: () => void;
}) {
  return (
    <div style={{ marginTop: 32, textAlign: 'center' }}>
      <Text typography="st12" color="grey600" style={{ display: 'block', wordBreak: 'keep-all' }}>
        계정 관리·상세 설정은 booktimer.app에서 할 수 있어요
      </Text>
      <div style={{ display: 'flex', justifyContent: 'center', gap: 8, marginTop: 10 }}>
        {confirm ? (
          <>
            <Button size="small" color="danger" onClick={onLogout}>
              정말 로그아웃
            </Button>
            <Button size="small" variant="weak" onClick={() => onConfirm(false)}>
              취소
            </Button>
          </>
        ) : (
          <Button size="small" variant="weak" onClick={() => onConfirm(true)}>
            로그아웃
          </Button>
        )}
      </div>
    </div>
  );
}

/** 종료 직후 태깅 대상 — 책 없이 측정한 세션에 나중에 책을 붙인다. */
interface Untagged {
  sessionId: number;
}

/**
 * 타이머 홈 — `/api/dashboard` 렌더(오늘 진행률 · 시작/정지 · 읽는 중 책 · 잔디 미리보기 · 격언).
 * 서재 관리·검색·정원은 웹이 본진이라 미니앱에 두지 않는다(설계 §2.5).
 */
export function Home({
  dashboard,
  onTimerChange,
  onGraphChange,
  onGoHistory,
  onGoLibrary,
  onGoGoal,
  onLogout,
  onError,
}: {
  dashboard: DashboardResponse;
  onTimerChange: (timer: TimerState) => void;
  onGraphChange: (graph: DashboardResponse['graph']) => void;
  onGoHistory: () => void;
  onGoLibrary: () => void;
  onGoGoal: () => void;
  onLogout: () => void;
  onError: (error: Error) => void;
}) {
  const [untagged, setUntagged] = useState<Untagged | null>(null);
  /** 로그아웃 확인 단계 — 실수 한 탭에 세션이 날아가지 않게 한 번 더 받는다. */
  const [confirmLogout, setConfirmLogout] = useState(false);
  /** 측정할 책 — 칩에 뜨는 그 책이고, 시작은 아래 주 버튼이 맡는다(여러 책을 번갈아 읽는 사람). */
  const [selectedBookId, setSelectedBookId] = useState(() =>
    defaultBookId(dashboard.readingBooks, dashboard.recentBookId),
  );
  /** 열린 시트의 모드 — `null`이면 닫힘. 고르기와 태깅이 같은 시트를 쓴다(웹 `BookPickSheet`와 같다). */
  const [sheet, setSheet] = useState<BookSheetMode | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [now, setNow] = useState(() => Date.now());
  /** 방금 지운 부채(초) — 성공 직후 한 줄 안내용. */
  const [waived, setWaived] = useState<number | null>(null);
  /**
   * 첫 완료 축하가 떠 있는지 — 메모리에만 둔다. 새로고침·재조회로 사라지는 게 맞다(축하는 그 순간 1회로 족하고,
   * 서버는 두 번째 종료부터 `firstCompletedSession=false`를 주므로 다시 켜질 일도 없다).
   */
  const [celebrate, setCelebrate] = useState(false);
  /** 알림 동의 캐시·지원 여부 — 렌더마다 다시 묻지 않게 초기값으로 한 번만 읽는다. */
  const [agreement, setAgreement] = useState(() => localStorage.getItem(AGREEMENT_KEY));
  const [agreementSupported] = useState(notificationAgreementSupported);

  useEffect(() => {
    if (!dashboard.hasActiveSession) return;
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, [dashboard.hasActiveSession]);

  const fail = (e: Error) => {
    // 401은 App이 재로그인으로 처리하고, 그 외(409 중복 시작 등)만 화면에 남긴다.
    if (e.name === 'UnauthorizedError') onError(e);
    else setError(e.message);
  };

  const run = (action: Promise<void>) => {
    setBusy(true);
    setError(null);
    action.catch(fail).finally(() => setBusy(false));
  };

  // 다음 측정을 시작하면 축하는 접는다 — 지난 세션의 축하가 새 측정 화면에 남아 있으면 거짓말이 된다.
  const start = (bookId: number | null) => {
    setCelebrate(false);
    return run(
      startSession(bookId).then((timer) => {
        onTimerChange(timer);
        trackEvent('reading_session_started');
      }),
    );
  };

  const stop = () =>
    run(
      stopSession().then((result) => {
        onTimerChange(result.timer);
        onGraphChange(result.graph); // stop 응답에 잔디가 동봉돼 새로고침 없이 즉시 갱신된다.
        setCelebrate(result.firstCompletedSession); // 첫 기록이면 배너 + 잔디 하이라이트로 시선을 아래로 보낸다.
        // 이 앱의 핵심 전환 — 콘솔 대표 전환을 「토스로그인 완료」에서 여기로 갈아끼우기 위한 신호다.
        // 서버 응답엔 세션 길이가 없어 화면이 세던 elapsed를 그대로 쓴다(시작 시각도 서버가 준 값이다).
        trackEvent('reading_session_completed', { duration_seconds: elapsed });
        // 웹처럼 종료 직후 시트를 저절로 연다 — 태깅은 지금 기억이 가장 선명하다.
        const mode = openSheetMode('tag', dashboard.readingBooks);
        if (result.untagged && mode !== null) {
          setUntagged({ sessionId: result.sessionId });
          setSheet(mode);
        }
      }),
    );

  const tag = (book: BookOption) => {
    if (untagged === null) return;
    run(
      tagBook(untagged.sessionId, book.id).then(() => {
        setUntagged(null);
        setSheet(null);
      }),
    );
  };

  /** 시트 닫기 — 태깅 시트를 닫는 건 곧 「건너뛰기」다(다시 들어갈 자리를 만들지 않는다). */
  const closeSheet = () => {
    if (sheet === 'tag') setUntagged(null);
    setSheet(null);
  };

  // 안드로이드 뒤로가기는 시트만 닫는다 — 시트가 열린 채로 미니앱이 꺼지지 않게.
  useBackClose(sheet !== null, closeSheet);

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

  /** 알림 동의 요청 — 결과(동의·이미동의·거절)가 캐시되면 카드가 사라진다. 미지원(null)이면 그대로 둔다. */
  const askNotification = () => {
    setBusy(true);
    setError(null);
    askNotificationAgreement()
      .then(setAgreement)
      .catch(() => setError('알림 동의를 요청하지 못했어요. 잠시 후 다시 시도해 주세요.'))
      .finally(() => setBusy(false));
  };

  const goal = dashboard.todayGoalSeconds;
  const elapsed =
    dashboard.hasActiveSession && dashboard.activeStartedAt !== null
      ? elapsedSeconds(dashboard.activeStartedAt, now)
      : 0;
  // 측정 중이면 elapsed가 매초 늘어 todayRead도 매초 늘어난다 — 카운트업의 동력이 이 한 줄이다.
  const { todayRead, remainingToGoal, overflow, progress, achieved } = todayProgress(dashboard, elapsed);
  const quotes = dashboard.quotes ?? [];
  // 칩에 띄울 책 — 시작 버튼도 이 값을 그대로 쓴다(칩과 시작 대상이 어긋날 자리를 없앤다).
  const selectedBook = dashboard.readingBooks.find((b) => b.id === selectedBookId) ?? null;

  return (
    <Screen title={`${dashboard.nickname}님의 오늘`}>
      <div
        style={{
          padding: '28px 20px',
          borderRadius: 16,
          background: 'var(--adaptiveGrey100, #FCFAF5)',
          textAlign: 'center',
        }}
      >
        {/* 라벨과 값은 각자 블록이어야 세로로 쌓인다 — 같은 줄에 붙으면 "오늘 읽은 시간45:00"으로 읽힌다. */}
        <div>
          <Text typography="st11" color="grey600">
            {achieved ? '🌿 오늘 목표 달성' : '오늘 읽은 시간'}
          </Text>
        </div>
        <div style={{ marginTop: 6 }}>
          <Text typography="t2" fontWeight="bold">
            {formatClock(todayRead)}
          </Text>
        </div>
        {progress !== null && (
          <div style={{ marginTop: 16 }}>
            <ProgressBar progress={progress} size="normal" color={SAGE} />
            <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 8 }}>
              오늘 목표 {formatDuration(goal)} ·{' '}
              {achieved ? `달성${overflow > 0 ? ` +${formatDuration(overflow)}` : ''}` : `목표까지 ${formatClock(remainingToGoal)}`}
            </Text>
          </div>
        )}
        {/* 빚을 위협이 아니라 "괜찮다"로 말한다 — 웹 대시보드 TimerCard의 용서 문구와 같은 말. */}
        {dashboard.carriedDebtSeconds > 0 && (
          <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 8 }}>
            밀린 {forgiveMinutes(dashboard.carriedDebtSeconds)}분은 최근 7일이 지나면 자동으로 사라져요 — 뒤처져도 괜찮아요.
          </Text>
        )}
        {/* 광고는 죄책감이 뜬 이 자리에만 나타난다. 문구에 "광고"를 명시해 광고 위장 금지 조항을 지킨다. */}
        {showWaiverButton(dashboard.carriedDebtSeconds, dashboard.debtWaiverAvailable, REWARD_AD_GROUP_ID) && (
          <Button
            variant="weak"
            size="small"
            style={{ marginTop: 10 }}
            disabled={busy}
            onClick={claimWaiver}
          >
            광고 보고 밀린 하루 지우기
          </Button>
        )}
        {waived !== null && (
          <Text typography="st12" color="blue500" style={{ display: 'block', marginTop: 8 }}>
            밀린 {formatDuration(waived)}을 지웠어요. 잔디는 그대로예요.
          </Text>
        )}
        {dashboard.hasActiveSession && (
          <>
            <Text typography="t5" color="blue500" style={{ display: 'block', marginTop: 16 }}>
              측정 중 {formatDuration(elapsed)}
              {dashboard.activeBookTitle !== null && ` · ${dashboard.activeBookTitle}`}
            </Text>
            {/* 이 앱의 핵심 계약(측정은 서버 권위)을 측정 중 화면에서 말하지 않으면, 사용자는 화면을 켜 둬야
                하는 줄 알고 몇 초 만에 끈다 — 운영 실측에서 완료 세션 대부분이 1분 미만이었다. */}
            <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 6 }}>
              {ACTIVE_SESSION_RELIEF}
            </Text>
          </>
        )}
      </div>

      <FirstSessionBanner show={celebrate} />

      {/* 알림 동의 — 발송은 동의한 유저에게만 가능하고, 동의를 받는 주체는 미니앱이다(콘솔 심사 조건). */}
      {shouldShowNotificationCard(agreement, agreementSupported) && (
        <section style={sectionStyle}>
          <Text typography="st11" color="grey600" style={{ display: 'block', marginBottom: 10 }}>
            목표 달성과 완독 소식을 토스 알림으로 받아보세요
          </Text>
          <Button display="block" variant="weak" size="medium" disabled={busy} onClick={askNotification}>
            알림 받기
          </Button>
        </section>
      )}

      {!dashboard.hasActiveSession && selectedBook !== null && (
        <section style={sectionStyle}>
          <Text typography="st11" color="grey600" style={{ display: 'block', marginBottom: 10 }}>
            이 책으로 측정할까요?
          </Text>
          {/* 칩 = 지금 고른 책 하나. 목록을 상시 펼쳐 두면 책이 늘수록 홈이 목록 화면이 된다(웹과 같은 접근). */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <CoverInitial title={selectedBook.title} />
            <Text typography="st11" style={{ flex: 1, textAlign: 'left' }}>
              {selectedBook.title}
            </Text>
            <Button
              variant="weak"
              size="small"
              disabled={busy}
              onClick={() => setSheet(openSheetMode('start', dashboard.readingBooks))}
            >
              바꾸기
            </Button>
          </div>
        </section>
      )}

      {/* 책이 없으면 칩 자리가 통째로 비어 막다른 길이었다 — 여기서 서재로 건네준다(측정 자체는 책 없이도 된다). */}
      {!dashboard.hasActiveSession && selectedBook === null && (
        <section style={sectionStyle}>
          <Text typography="st11" color="grey600" style={{ display: 'block', marginBottom: 10 }}>
            아직 책이 없어요. 읽고 있는 책을 추가하면 측정할 때 고를 수 있어요.
          </Text>
          <Button display="block" variant="weak" size="medium" disabled={busy} onClick={onGoLibrary}>
            첫 책 추가하기
          </Button>
        </section>
      )}

      <ErrorMessage message={error} />

      {/* 주 버튼은 "칩에 뜬 책으로 시작" 하나 — 칩이 없으면(책 0권) 그대로 책 없이 시작이다. */}
      <Button
        display="block"
        color={dashboard.hasActiveSession ? 'danger' : 'primary'}
        style={{ marginTop: 24 }}
        loading={busy}
        onClick={dashboard.hasActiveSession ? stop : () => start(selectedBook?.id ?? null)}
      >
        {dashboard.hasActiveSession ? '측정 끝내기' : '측정 시작'}
      </Button>

      {/* 고른 책이 있을 때만 탈출구를 둔다 — 고를 책이 없는데 "책 없이"를 되묻는 건 군더더기다. */}
      {!dashboard.hasActiveSession && selectedBook !== null && (
        <Button
          display="block"
          variant="weak"
          size="medium"
          style={{ marginTop: 8 }}
          disabled={busy}
          onClick={() => start(null)}
        >
          책 없이 시작
        </Button>
      )}

      <GrassPreview graph={dashboard.graph} highlight={celebrate} onGoHistory={onGoHistory} />

      {quotes.length > 0 && <QuoteCard quotes={quotes} />}

      <Button display="block" variant="weak" size="medium" style={{ marginTop: 12 }} onClick={onGoGoal}>
        목표 바꾸기
      </Button>

      <AccountSection
        confirm={confirmLogout}
        onConfirm={setConfirmLogout}
        onLogout={() => void logoutAndLeave(onLogout)}
      />

      {/* 태깅 시트는 "고른 책"이 없다 — selectedId를 안 주면 아무 행도 강조되지 않는다. */}
      {sheet !== null && (
        <BookSheet
          mode={sheet}
          books={dashboard.readingBooks}
          selectedId={sheet === 'start' ? selectedBookId : null}
          disabled={busy}
          onPick={pickHandler(sheet, {
            select: (book) => {
              setSelectedBookId(book.id);
              setSheet(null); // 고르면 곧바로 칩으로 되돌아간다(같은 책을 다시 골라도 닫힌다).
            },
            tag,
          })}
          onSkip={sheet === 'start' ? () => { setSheet(null); start(null); } : closeSheet}
          onClose={closeSheet}
        />
      )}
    </Screen>
  );
}

/**
 * 잔디 미리보기 — 최근 15주만, 카드 폭을 꽉 채워서. 카드 전체가 기록 화면 진입점이다.
 * `highlight`면 테두리를 둘러 첫 완료 축하가 가리키는 곳을 눈에 띄게 한다.
 */
export function GrassPreview({
  graph,
  highlight = false,
  onGoHistory,
}: {
  graph: DashboardResponse['graph'];
  highlight?: boolean;
  onGoHistory: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onGoHistory}
      style={highlight ? { ...cardStyle, border: HIGHLIGHT_BORDER } : cardStyle}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
        <Text typography="st11" color="grey600">
          {graph.growthStageEmoji} 연속 {graph.currentStreak}일
        </Text>
        <Text typography="st12" color="grey600">
          기록 보기 ›
        </Text>
      </div>
      {/* 앞에서 자른다 — 서버가 weeks[0]을 최신 주로 뒤집어 준다(api.ts `weeks` 주석). */}
      <GrassGrid weeks={graph.weeks.slice(0, PREVIEW_WEEKS)} fill />
    </button>
  );
}

/** 작가 격언 — 서버가 셔플해 준 목록에서 하나. 탭하면 다음 격언으로 넘어간다. */
function QuoteCard({ quotes }: { quotes: QuoteDto[] }) {
  const [index, setIndex] = useState(() => Math.floor(Math.random() * quotes.length));
  const quote = quotes[index % quotes.length];

  return (
    <button type="button" onClick={() => setIndex((i) => (i + 1) % quotes.length)} style={cardStyle}>
      <Text typography="st11" style={{ display: 'block', textAlign: 'left' }}>
        “{quote.text}”
      </Text>
      <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 8, textAlign: 'left' }}>
        — {quote.author}
      </Text>
    </button>
  );
}

/** 탭 가능한 카드 공통 — button 기본 스타일을 지워 div처럼 보이게 한다(접근성은 button이 맡는다). */
const cardStyle = {
  display: 'block',
  width: '100%',
  marginTop: 12,
  padding: 16,
  border: 'none',
  borderRadius: 12,
  background: 'var(--adaptiveGrey100, #FCFAF5)',
  textAlign: 'left',
  cursor: 'pointer',
} as const;
