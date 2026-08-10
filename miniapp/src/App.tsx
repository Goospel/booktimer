import { Button, Tab } from '@toss/tds-mobile';
import { useCallback, useEffect, useState } from 'react';

import type { DashboardResponse, TimerState } from './api';
import { fetchDashboard, token } from './api';
import { Goal } from './screens/Goal';
import { History } from './screens/History';
import { Home } from './screens/Home';
import { Library } from './screens/Library';
import { LinkAccount } from './screens/LinkAccount';
import { LoginBridge } from './screens/LoginBridge';
import { Social } from './screens/Social';
import { ErrorMessage, Loading, Screen } from './ui';

/** 메인 탭 — 이 순서가 곧 탭바 순서다(index↔화면 대응의 단일 출처). */
export const TABS = [
  { key: 'home', label: '홈' },
  { key: 'library', label: '서재' },
  { key: 'social', label: '소셜' },
  { key: 'history', label: '기록' },
] as const;

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

  const load = useCallback(
    (next: View = 'main') => {
      setView('loading');
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
 * 탭바는 하단 고정이라 본문 아래에 그만큼 여백을 둔다.
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
      <div style={{ paddingBottom: 72 }}>
        {tab === 'home' && (
          <Home
            dashboard={dashboard}
            onTimerChange={onTimerChange}
            onGraphChange={onGraphChange}
            onGoHistory={() => onTabChange('history')}
            onGoGoal={onGoGoal}
            onError={onError}
          />
        )}
        {tab === 'library' && <Library onError={onError} />}
        {tab === 'social' && <Social myLoginId={dashboard.loginId} onError={onError} />}
        {tab === 'history' && <History graph={dashboard.graph} />}
      </div>

      <div
        style={{
          position: 'fixed',
          left: 0,
          right: 0,
          bottom: 0,
          background: 'var(--adaptiveBackground, #ffffff)',
          borderTop: '1px solid var(--adaptiveGrey200, #e5e8eb)',
        }}
      >
        <Tab size="small" onChange={tabChangeHandler(onTabChange)}>
          {TABS.map(({ key, label }) => (
            <Tab.Item key={key} selected={key === tab}>
              {label}
            </Tab.Item>
          ))}
        </Tab>
      </div>
    </>
  );
}
