import { Button } from '@toss/tds-mobile';
import { useCallback, useEffect, useRef, useState } from 'react';

import type { DashboardResponse, TimerState } from './api';
import { fetchDashboard, token } from './api';
import { useBackClose } from './back';
import type { MarginTarget } from './screens/Bookshop';
import { Bookshop } from './screens/Bookshop';
import { Goal } from './screens/Goal';
import { History } from './screens/History';
import { Home } from './screens/Home';
import { Library } from './screens/Library';
import { LinkAccount } from './screens/LinkAccount';
import { LoginBridge } from './screens/LoginBridge';
import { Settings } from './screens/Settings';
import { showInterstitialAd } from './toss';
import { ErrorMessage, Loading, Screen } from './ui';

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

export type TabKey = (typeof TABS)[number]['key'];

/**
 * 탭바가 주는 index를 탭 키로 옮기는 핸들러 — `TABS` 순서가 곧 탭바 순서라는 계약이 여기 한 줄에 모인다.
 * 정적 렌더 하니스로는 클릭을 못 잡으므로, 이 변환만 따로 꺼내 단위로 계측한다.
 */
export const tabChangeHandler =
  (onTabChange: (tab: TabKey) => void) =>
  (index: number): void =>
    onTabChange(TABS[index].key);

/** 탭 밖 전역 상태 — 인증·연결·목표·에러는 탭바 없이 화면 전체를 차지한다. */
type View = 'auth' | 'link' | 'loading' | 'main' | 'goal' | 'settings' | 'error';

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
 * 라우터 없이 상태 두 개로 화면을 정한다 — `view`(탭 밖 오케스트레이션) × `tab`(메인 탭).
 *
 * <p>인증 실패(401)는 어디서 나든 토큰이 이미 폐기된 상태라 로그인 브릿지로 되돌린다.
 */
export function App() {
  const [view, setView] = useState<View>(() => (token.get() === null ? 'auth' : 'loading'));
  const [tab, setTab] = useState<TabKey>('home');
  const [dashboard, setDashboard] = useState<DashboardResponse | null>(null);
  const [firstRun, setFirstRun] = useState(false);
  const [error, setError] = useState<string | null>(null);
  /** 목표 바꾸기 전면광고를 기다리는 중 — 버튼을 "준비 중"으로 바꾸고 중복 진입을 막는다. */
  const [goalAdPending, setGoalAdPending] = useState(false);
  /**
   * 홈 소식에서 누른 여백 — 탭 점프의 전달 수단이다. 탭이 바뀌면 책방이 새로 마운트되므로 이 값이
   * 그 화면의 <b>초기 상태</b>가 되고, 소비되는 즉시 null로 비운다(다음 수동 탭 진입에 또 열리지 않게).
   */
  const [marginTarget, setMarginTarget] = useState<MarginTarget | null>(null);

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
        .then(setDashboard)
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
  useBackClose(view === 'settings', () => setView('main'));

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

  return (
    <MainTabs
      tab={tab}
      onTabChange={setTab}
      dashboard={dashboard}
      marginTarget={marginTarget}
      onMarginConsumed={() => setMarginTarget(null)}
      onOpenMargin={(loginId, bookId) => {
        setMarginTarget({ loginId, bookId });
        setTab('bookshop');
      }}
      onTimerChange={applyTimer}
      onGraphChange={applyGraph}
      onGoGoal={goToGoal}
      goalAdPending={goalAdPending}
      onGoSettings={() => setView('settings')}
      onError={handleError}
      onShelfChanged={() => silentRefresh(true)}
      onHandleCreated={() => silentRefresh(true)}
    />
  );
}

/**
 * 메인 탭 셸 — 탭바와 본문을 같은 `TABS` 목록에서 그려 index와 화면이 어긋날 자리를 없앤다.
 * 탭바가 하단에 떠 있으므로 본문 아래에 그만큼 여백을 둔다.
 */
export function MainTabs({
  tab,
  onTabChange,
  dashboard,
  marginTarget,
  onMarginConsumed,
  onOpenMargin,
  onTimerChange,
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
  /** 홈 소식에서 넘어온 여백 점프 대상 — 책방 탭이 마운트될 때 초기 상태로 소비된다. */
  marginTarget: MarginTarget | null;
  onMarginConsumed: () => void;
  /** 소식의 여백 줄을 눌렀다 — 책방 탭으로 옮기고 그 책의 여백을 연다. */
  onOpenMargin: (loginId: string, bookId: number) => void;
  onTimerChange: (timer: TimerState) => void;
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
  return (
    <>
      {/* 떠 있는 탭바 아래로 본문 끝이 숨는다 — 바 높이 + 띄운 높이 + 홈 인디케이터 + 숨 쉴 여백만큼 비운다. */}
      <div style={{ paddingBottom: `calc(${TAB_BAR_HEIGHT}px + 12px + env(safe-area-inset-bottom) + 16px)` }}>
        {tab === 'home' && (
          <Home
            dashboard={dashboard}
            onTimerChange={onTimerChange}
            onGraphChange={onGraphChange}
            onGoGoal={onGoGoal}
            goalAdPending={goalAdPending}
            onGoSettings={onGoSettings}
            onError={onError}
            onOpenMargin={onOpenMargin}
          />
        )}
        {tab === 'library' && <Library onError={onError} onShelfChanged={onShelfChanged} />}
        {tab === 'bookshop' && (
          <Bookshop
            myLoginId={dashboard.loginId}
            initialMargin={marginTarget}
            onMarginConsumed={onMarginConsumed}
            onHandleCreated={onHandleCreated}
            onError={onError}
          />
        )}
        {tab === 'history' && <History graph={dashboard.graph} />}
      </div>

      <BottomTabBar tab={tab} onTabChange={onTabChange} />
    </>
  );
}

/**
 * 하단 탭바 — 아이콘+라벨 세로 스택을 **떠 있는 알약(pill)**에 담는다.
 *
 * <p>토스 미니앱 브랜딩 가이드가 요구하는 플로팅 형태다(전폭 부착형은 2026-08-12 심사 반려 사유 2).
 * 홈 인디케이터 회피는 `padding-bottom`이 아니라 **띄운 높이**(`bottom`)가 맡는다 — 둘 다 두면
 * safe-area가 두 번 더해져 바가 붕 뜬다.
 */
export function BottomTabBar({ tab, onTabChange }: { tab: TabKey; onTabChange: (tab: TabKey) => void }) {
  const change = tabChangeHandler(onTabChange);

  return (
    <nav
      role="tablist"
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
      {TABS.map(({ key, label, icon }, index) => {
        const selected = key === tab;
        return (
          <button
            key={key}
            type="button"
            role="tab"
            aria-selected={selected}
            title={label}
            onClick={() => change(index)}
            style={{
              flex: 1,
              minHeight: TAB_BAR_HEIGHT,
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              gap: 3,
              padding: 0,
              border: 'none',
              background: 'transparent',
              color: selected ? 'var(--adaptiveBlue500, #6E8A6A)' : 'var(--adaptiveGrey600, #6F6A5E)',
              cursor: 'pointer',
            }}
          >
            <svg
              width="24"
              height="24"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth={1.8}
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              <path d={icon} />
            </svg>
            <span style={{ fontSize: 11, fontWeight: selected ? 600 : 400, lineHeight: 1.2 }}>{label}</span>
          </button>
        );
      })}
    </nav>
  );
}
