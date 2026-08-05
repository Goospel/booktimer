import { Button } from '@toss/tds-mobile';
import { useCallback, useEffect, useState } from 'react';

import type { DashboardResponse, TimerState } from './api';
import { fetchDashboard, token } from './api';
import { Goal } from './screens/Goal';
import { History } from './screens/History';
import { Home } from './screens/Home';
import { LinkAccount } from './screens/LinkAccount';
import { LoginBridge } from './screens/LoginBridge';
import { ErrorMessage, Loading, Screen } from './ui';

type View = 'auth' | 'link' | 'loading' | 'home' | 'history' | 'goal' | 'error';

/**
 * 화면 다섯 개짜리 미니앱이라 라우터를 두지 않는다 — 상태 하나로 어느 화면인지 정한다.
 *
 * <p>인증 실패(401)는 어디서 나든 토큰이 이미 폐기된 상태라 로그인 브릿지로 되돌린다.
 */
export function App() {
  const [view, setView] = useState<View>(() => (token.get() === null ? 'auth' : 'loading'));
  const [dashboard, setDashboard] = useState<DashboardResponse | null>(null);
  const [firstRun, setFirstRun] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const toLogin = useCallback(() => {
    token.clear();
    setDashboard(null);
    setView('auth');
  }, []);

  const load = useCallback(
    (next: View = 'home') => {
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
          onAuthenticated={() => load('home')}
          onNewAccount={() => {
            setFirstRun(true); // 신규 계정은 목표 설정을 먼저 유도한다(설계 §2.5-5).
            load('goal');
          }}
          onLinkAccount={() => setView('link')}
        />
      );

    case 'link':
      return <LinkAccount onLinked={() => load('home')} onBack={() => setView('auth')} />;

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

  switch (view) {
    case 'history':
      return <History graph={dashboard.graph} onBack={() => setView('home')} />;

    case 'goal':
      return (
        <Goal
          current={dashboard.todayGoalSeconds}
          firstRun={firstRun}
          onSaved={() => {
            setFirstRun(false);
            load('home');
          }}
          onSkip={() => {
            setFirstRun(false);
            setView('home');
          }}
        />
      );

    default:
      return (
        <Home
          dashboard={dashboard}
          onTimerChange={applyTimer}
          onGraphChange={applyGraph}
          onGoHistory={() => setView('history')}
          onGoGoal={() => setView('goal')}
          onError={handleError}
        />
      );
  }
}
