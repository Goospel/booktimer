import { Button } from '@toss/tds-mobile';
import { useCallback, useEffect, useRef, useState } from 'react';

import type { DashboardResponse, TimerState } from './api';
import { fetchDashboard, token } from './api';
import { useBackClose } from './back';
import { Goal } from './screens/Goal';
import { History } from './screens/History';
import { Home } from './screens/Home';
import { Library } from './screens/Library';
import { LinkAccount } from './screens/LinkAccount';
import { LoginBridge } from './screens/LoginBridge';
import { Social } from './screens/Social';
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
    key: 'social',
    label: '소셜',
    icon: 'M9.5 11.5a3.6 3.6 0 1 0 0-7.2 3.6 3.6 0 0 0 0 7.2ZM3 20.2c0-3.1 2.9-5.3 6.5-5.3s6.5 2.2 6.5 5.3M17 5a3.4 3.4 0 0 1 0 6.6M18.6 15.4c1.7.7 2.9 2 2.9 4.1',
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
type View = 'auth' | 'link' | 'loading' | 'main' | 'goal' | 'error';

/** 포커스 복귀 재조회의 최소 간격 — 미니앱은 앱 전환이 잦아 복귀마다 받으면 서버를 두들긴다. */
export const REFRESH_THROTTLE_MS = 60_000;

/** 지금 다시 받을 때인가. 판정만 순수하게 빼 계측한다(배선은 effect라 하니스가 못 잡는다). */
export function shouldRefresh(lastAt: number, now: number): boolean {
  return now - lastAt >= REFRESH_THROTTLE_MS;
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
   * 앱으로 돌아오면 조용히 다시 받는다 — 웹·다른 기기에서 바꾼 값(목표·책장·측정)이 재진입 전까지
   * 안 보이던 문제. 화면은 그대로 두고 **성공했을 때만** 갈아끼운다: 실패로 멀쩡한 화면을 에러로
   * 바꾸지 않는다. 측정 중이어도 서버 응답이 진실이라 그대로 덮는다.
   */
  useEffect(() => {
    const refresh = () => {
      if (document.visibilityState !== 'visible' || token.get() === null) return;
      if (!shouldRefresh(lastFetchedAt.current, Date.now())) return;
      lastFetchedAt.current = Date.now(); // 응답 전에 찍는다 — 연속 복귀가 요청을 겹쳐 쌓지 않게
      fetchDashboard()
        .then(setDashboard)
        .catch((e: Error) => {
          if (e.name === 'UnauthorizedError') toLogin(); // 토큰이 폐기됐으면 조용히 넘어갈 수 없다
        });
    };
    document.addEventListener('visibilitychange', refresh);
    return () => document.removeEventListener('visibilitychange', refresh);
  }, [toLogin]);

  // 탭 밖 전체 화면도 뒤로가기로 나갈 수 있다 — 각 화면의 「돌아가기」와 같은 자리로 돌려보낸다.
  useBackClose(view === 'link', () => setView('auth'));
  useBackClose(view === 'goal', () => {
    setFirstRun(false);
    setView('main');
  });

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

  return (
    <MainTabs
      tab={tab}
      onTabChange={setTab}
      dashboard={dashboard}
      onTimerChange={applyTimer}
      onGraphChange={applyGraph}
      onGoGoal={() => setView('goal')}
      onError={handleError}
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
  onTimerChange,
  onGraphChange,
  onGoGoal,
  onError,
}: {
  tab: TabKey;
  onTabChange: (tab: TabKey) => void;
  dashboard: DashboardResponse;
  onTimerChange: (timer: TimerState) => void;
  onGraphChange: (graph: DashboardResponse['graph']) => void;
  onGoGoal: () => void;
  onError: (error: Error) => void;
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
            onGoHistory={() => onTabChange('history')}
            onGoLibrary={() => onTabChange('library')}
            onGoGoal={onGoGoal}
            onError={onError}
          />
        )}
        {tab === 'library' && <Library onError={onError} />}
        {tab === 'social' && <Social myLoginId={dashboard.loginId} onError={onError} />}
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
