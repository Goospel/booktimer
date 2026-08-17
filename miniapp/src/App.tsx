import { Button } from '@toss/tds-mobile';
import { useCallback, useEffect, useRef, useState } from 'react';

import type { BookOption, DashboardResponse, MarginBook, TimerState } from './api';
import { fetchDashboard, startSession, stopSession, tagBook, token } from './api';
import { useBackClose } from './back';
import { elapsedSeconds } from './format';
import { Bookshop } from './screens/Bookshop';
import { Goal } from './screens/Goal';
import { History } from './screens/History';
import { BookSheet, Home, defaultBookId } from './screens/Home';
import { Library } from './screens/Library';
import { LinkAccount } from './screens/LinkAccount';
import { LoginBridge } from './screens/LoginBridge';
import { Settings } from './screens/Settings';
import { BookMargin, StoryComposer } from './screens/Story';
import { showInterstitialAd, trackEvent } from './toss';
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
export function timerActionView(active: boolean): { label: string; background: string; icon: string } {
  return active
    ? { label: '측정 끝내기', background: 'var(--adaptiveRed500, #F04452)', icon: STOP_ICON }
    : { label: '측정 시작', background: 'var(--adaptiveBlue500, #6E8A6A)', icon: PLAY_ICON };
}

/**
 * 첫 진입 코치마크를 봤다는 기록 — 기기 로컬로 족하다(서버 컬럼·마이그레이션·API 왕복이 통째로 없다).
 *
 * <p>기기를 바꾸면 한 번 더 뜨는 게 유일한 대가인데, 1회성 안내에 그 정도는 손해가 아니다.
 * 키가 <b>없으면</b> 뜨므로 이미 쓰던 사람도 1회 보게 된다 — 「측정 시작」이 홈에서 이 자리로
 * 옮겨간 변경(#832)을 모른 채 홈에서 버튼을 찾는 일이 실제 위험이라 그쪽이 오히려 요점이다.
 */
export const COACHMARK_KEY = 'booktimer.coachmark.timer';

/** 코치마크를 봤다고 못 박는다 — 덮개 탭·측정 버튼 누름이 모두 이 한 자리로 온다. */
export function dismissCoachmark(): void {
  localStorage.setItem(COACHMARK_KEY, 'seen');
}

/** 종료 직후 태깅 대상 — 책 없이 측정한 세션에 나중에 책을 붙인다. */
interface Untagged {
  sessionId: number;
}

/** 탭 밖 전역 상태 — 인증·연결·목표·에러는 탭바 없이 화면 전체를 차지한다. */
type View = 'auth' | 'link' | 'loading' | 'main' | 'goal' | 'settings' | 'error';

/**
 * 열린 여백 — 「누구의 + 어느 책」 두 축이 곧 서버 계약이고, `composeBook`이 있으면 그 책의 **작성
 * 화면**이 선다(홈·서재 문은 이 값을 채워 1탭에 작성으로 직행한다).
 *
 * <p>여백을 <b>탭 밖 전역 뷰</b>로 든 이유는 뒤로가기다: 예전엔 책방 탭으로 점프해 열었더니 뒤로 가면
 * 책방이 나왔다 — 홈에서 들어온 사람에게 그건 남의 전시장으로 밀려나는 것이라 문의 취지가 무너진다.
 * 여기서 들면 열었던 탭이 그대로 뒤에 남는다(`goal`·`settings`와 같은 자리).
 */
export interface MarginState {
  loginId: string;
  /**
   * 작성 화면 **아래에 깔린** 여백 화면 — `null`이면 깔린 것이 없어, 작성을 닫으면 열었던 탭으로 곧장 돌아간다.
   *
   * <p>예전엔 작성 직행(홈·서재의 「여백에 글쓰기」)도 여백 상세를 깔고 그 위에 섰다. 그래서 쓰고 나오면
   * 출발한 탭이 아니라 <b>한 번도 요청한 적 없는 여백 상세</b>에 떨어졌다.
   */
  bookId: number | null;
  composeBook: MarginBook | null;
}

/**
 * 작성 화면을 닫으면 남을 상태 — 출발지로 돌려보낸다. 배선은 클릭·뒤로가기라 판정만 순수하게 계측한다.
 */
export function closeCompose(margin: MarginState): MarginState | null {
  return margin.bookId === null ? null : { ...margin, composeBook: null };
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
  /** 열린 여백 — 탭 위에 전체 화면으로 선다(홈 문·서재 문·홈 소식이 모두 이 한 자리로 온다). */
  const [margin, setMargin] = useState<MarginState | null>(null);
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
  // 작성 화면이 위면 그것만 닫는다 — 단 밑에 깔린 여백 화면이 없으면 통째로 닫아 출발한 탭으로 돌아간다.
  useBackClose(margin?.composeBook != null, () => setMargin((m) => (m === null ? null : closeCompose(m))));
  useBackClose(margin !== null && margin.composeBook === null, () => setMargin(null));

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

  /*
   * 여백은 탭 위에 전체 화면으로 선다 — 닫으면 열었던 탭이 그대로 남는다.
   * 작성 화면을 닫으면 `composeBook`만 비워 그 아래 여백 화면이 **새로 마운트**되고, 그 재조회가
   * 곧 "방금 남긴 글이 보인다"이다(에포크 같은 별도 갱신 표식이 필요 없다).
   */
  if (margin !== null && margin.composeBook !== null) {
    const close = () => setMargin(closeCompose(margin));
    return <StoryComposer book={margin.composeBook} onDone={close} onCancel={close} onError={handleError} />;
  }

  // 작성도 아니고 깔린 화면도 없는 조합은 만들지 않는다 — 만약 생겨도 탭으로 떨어져 막다른 길이 안 된다.
  if (margin !== null && margin.bookId !== null) {
    const under = margin.bookId;
    return (
      <BookMargin
        loginId={margin.loginId}
        bookId={under}
        onBack={() => setMargin(null)}
        onCompose={(book) => setMargin({ ...margin, bookId: under, composeBook: book })}
        onError={handleError}
      />
    );
  }

  return (
    <MainTabs
      tab={tab}
      onTabChange={setTab}
      dashboard={dashboard}
      homeBookId={homeBookId}
      onSelectHomeBook={setHomeBookId}
      onOpenMargin={(loginId, bookId) => setMargin({ loginId, bookId, composeBook: null })}
      onComposeMargin={(book) =>
        // 문은 핸들이 있을 때만 그려지므로 여기서 loginId는 언제나 있다(`marginDoorBook`이 앞에서 판정).
        // `bookId: null` — 작성만 띄운다. 닫으면 여백 상세를 거치지 않고 여기(홈·서재)로 곧장 돌아온다.
        dashboard.loginId !== null && setMargin({ loginId: dashboard.loginId, bookId: null, composeBook: book })
      }
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
 *
 * <p><b>측정 시작·종료도 여기가 든다</b>(홈에서 승격). 탭바를 그리는 바로 그 컴포넌트가 액션을 들어야
 * 어느 탭에서든 시작·종료할 수 있다 — 홈이 들고 있으면 다른 탭으로 가는 순간 언마운트돼 사라진다.
 * 덤으로 태깅 시트·축하 배너가 탭 전환에 살아남는다(종전보다 개선).
 */
export function MainTabs({
  tab,
  onTabChange,
  dashboard,
  homeBookId,
  onSelectHomeBook,
  onOpenMargin,
  onComposeMargin,
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
  /** 홈 캐러셀에서 고른 책 — 탭 밖 전체 화면이 홈을 언마운트해도 남도록 App이 든다(`undefined`=아직 안 고름). */
  homeBookId: number | null | undefined;
  onSelectHomeBook: (bookId: number | null) => void;
  /** 그 사람의 그 책 여백을 연다 — 홈 소식과 서재 문이 같은 자리로 온다(App이 전체 화면으로 든다). */
  onOpenMargin: (loginId: string, bookId: number) => void;
  /** 홈 여백 문 — 그 책의 작성 화면으로 직행한다. */
  onComposeMargin: (book: BookOption) => void;
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
  /** 액션 처리 중 — 연타로 세션이 두 번 시작·종료되지 않게 원을 흐리고 핸들러를 잠근다. */
  const [busy, setBusy] = useState(false);
  /** 태깅 시트 — `null`이면 닫힘. 열림 여부와 대상 세션이 늘 같이 움직여 상태 하나로 족하다. */
  const [tagging, setTagging] = useState<Untagged | null>(null);
  /** 첫 완료 축하 — 홈에 prop으로 내린다. 다른 탭에서 끝냈어도 홈에 돌아오면 배너가 보인다. */
  const [celebrate, setCelebrate] = useState(false);
  /** 액션 실패 문구 — 다른 탭엔 홈의 ErrorMessage가 없으므로 탭바 위 스트립으로 띄운다. */
  const [actionError, setActionError] = useState<string | null>(null);
  /** 첫 진입 코치마크 — 렌더 중에 읽는다(홈의 알림 동의 캐시와 같은 방식이라 정적 렌더로도 계측된다). */
  const [coachmark, setCoachmark] = useState(() => localStorage.getItem(COACHMARK_KEY) === null);

  const closeCoachmark = () => {
    dismissCoachmark();
    setCoachmark(false);
  };

  // 401은 App이 재로그인으로 처리하고, 그 외(409 중복 시작 등)만 화면에 남긴다.
  const fail = (e: Error) => (e.name === 'UnauthorizedError' ? onError(e) : setActionError(e.message));

  /** 탭바 가운데 원 — 측정 중이면 종료, 아니면 시작. 이 앱에서 세션을 여닫는 유일한 자리다. */
  const timerAction = () => {
    // 안내를 읽고 곧장 이 버튼을 누른 경우 — 덮개가 탭바 아래라 원이 그대로 눌린다. 안내는 역할을 다했다.
    if (coachmark) closeCoachmark();
    if (busy) return;
    setBusy(true);
    setActionError(null);
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
      startSession(timerStartBookId(dashboard.readingBooks, dashboard.recentBookId, homeBookId))
        .then((timer) => {
          onTimerChange(timer);
          trackEvent('reading_session_started');
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

  /** 시트 닫기 — 태깅 시트를 닫는 건 곧 「건너뛰기」다(다시 들어갈 자리를 만들지 않는다). */
  const closeSheet = () => setTagging(null);

  // 안드로이드 뒤로가기는 시트만 닫는다 — 시트가 열린 채로 미니앱이 꺼지지 않게.
  useBackClose(tagging !== null, closeSheet);

  return (
    <>
      {/* 떠 있는 탭바 아래로 본문 끝이 숨는다 — 바 높이 + 띄운 높이 + 홈 인디케이터 + 숨 쉴 여백만큼 비운다. */}
      <div style={{ paddingBottom: `calc(${TAB_BAR_HEIGHT}px + 12px + env(safe-area-inset-bottom) + 16px)` }}>
        {tab === 'home' && (
          <Home
            dashboard={dashboard}
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
          />
        )}
        {tab === 'bookshop' && (
          <Bookshop
            myLoginId={dashboard.loginId}
            onHandleCreated={onHandleCreated}
            onError={onError}
          />
        )}
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

      {/* 코치마크는 탭바보다 먼저 그린다 — 덮개가 탭바 아래 층이라는 것을 코드 순서로도 읽히게 둔다. */}
      {coachmark && <TimerCoachmark onClose={closeCoachmark} />}

      <BottomTabBar
        tab={tab}
        onTabChange={onTabChange}
        action={{ active: dashboard.hasActiveSession, busy, onPress: timerAction }}
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
    </>
  );
}

/**
 * 첫 진입 코치마크 — 탭바 가운데 측정 원을 말풍선으로 가리킨다.
 *
 * <p><b>스포트라이트를 마스크 없이 얻는다</b>: 덮개를 탭바(`TAB_BAR_Z_INDEX`)보다 한 층 <b>아래</b>
 * 깔면 불투명 배경의 탭바만 저절로 덮개 위에 밝게 남는다. SVG 마스크로 구멍을 뚫을 필요가 없고,
 * 덤으로 원이 그대로 눌려 <b>안내를 보고 곧장 측정을 시작</b>할 수 있다(덮개를 위에 얹으면 가리키는
 * 대상 자체를 막는 자기모순이 된다 — 그 부등식이 테스트로 박혀 있다).
 *
 * <p>위치 계산도 없다(ref·`getBoundingClientRect`·리사이즈 관측 0): 탭바는 좌우 같은 여백의
 * `fixed`라 <b>다섯 칸 중 가운데 = 화면 가로 정중앙</b>이고, 세로는 탭바와 같은 식에 여백만 더한다.
 *
 * <p>애니메이션은 두지 않는다 — 등장 효과 하나가 표지를 재래스터화하던 실측 사고 클래스다(T-176).
 */
export function TimerCoachmark({ onClose }: { onClose: () => void }) {
  return (
    <>
      {/* 덮개 자체가 닫기 버튼이다 — 아무 데나 누르면 닫힌다(요소 하나로 접근성 이름까지 얻는다). */}
      <button
        type="button"
        aria-label="안내 닫기"
        onClick={onClose}
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

      <div
        style={{
          position: 'fixed',
          zIndex: TAB_BAR_Z_INDEX + 1,
          left: 24,
          right: 24,
          // 탭바와 같은 식(띄운 높이 + 홈 인디케이터 + 바 높이)에 말풍선 숨 쉴 여백만 더한다.
          bottom: `calc(12px + env(safe-area-inset-bottom) + ${TAB_BAR_HEIGHT}px + 12px)`,
          padding: '14px 16px',
          borderRadius: 14,
          background: 'var(--adaptiveBlue500, #6E8A6A)',
          boxShadow: '0 6px 20px rgba(0, 0, 0, 0.25)',
          textAlign: 'center',
          pointerEvents: 'none', // 말풍선은 읽는 것뿐 — 눌린 곳은 덮개(닫기)나 탭바(측정)여야 한다.
        }}
      >
        <div style={{ color: '#FFFFFF', fontSize: 15, fontWeight: 600, lineHeight: 1.45 }}>
          여기를 눌러 독서 시간을 재요
        </div>
        <div style={{ color: '#FFFFFF', fontSize: 13, lineHeight: 1.45, opacity: 0.85 }}>
          읽던 책은 홈에서 고를 수 있어요
        </div>

        {/* 꼬리 — 말풍선(fixed)이 담는 블록이라 가운데 정렬만으로 원을 가리킨다. */}
        <div
          style={{
            position: 'absolute',
            left: '50%',
            bottom: -9,
            marginLeft: -9,
            width: 0,
            height: 0,
            borderLeft: '9px solid transparent',
            borderRight: '9px solid transparent',
            borderTop: '9px solid var(--adaptiveBlue500, #6E8A6A)',
          }}
        />
      </div>
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
export function BottomTabBar({
  tab,
  onTabChange,
  action,
}: {
  tab: TabKey;
  onTabChange: (tab: TabKey) => void;
  /** 가운데 측정 액션 — 탭이 아니라 동작이다(그래서 `TABS` 밖에 산다). */
  action: { active: boolean; busy: boolean; onPress: () => void };
}) {
  const change = tabChangeHandler(onTabChange);

  const cells = TABS.map(({ key, label, icon }, index) => {
    const selected = key === tab;
    return (
      <button
        key={key}
        type="button"
        // ARIA tabs 패턴은 `role="tabpanel"`과 짝일 때만 성립하는데 이 앱엔 그 짝이 없다 —
        // 하단 내비게이션의 표준 마크업(APG)인 `aria-current`로 현재 위치를 말한다.
        aria-current={selected ? 'page' : undefined}
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
 * 원이 셀(69×56) 안에 들어가므로 알약의 `overflow: hidden`에 잘리지 않는다(돌출형 아님).
 */
function TimerActionButton({ active, busy, onPress }: { active: boolean; busy: boolean; onPress: () => void }) {
  const view = timerActionView(active);

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
          width: 44,
          height: 44,
          borderRadius: '50%',
          background: view.background,
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
