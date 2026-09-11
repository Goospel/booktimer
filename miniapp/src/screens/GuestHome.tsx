import { Button } from '@toss/tds-mobile';
import { useEffect, useRef, useState } from 'react';

import {
  BottomTabBar,
  TABS,
  TAB_BAR_HEIGHT,
  TAB_BAR_MARGIN,
  TAB_BAR_SPACE,
  TAB_BAR_Z_INDEX,
  TAB_LOCK_HINT_MS,
} from '../App';
import type { TabKey } from '../App';
import { elapsedSeconds, formatClock, formatDuration, hasFinalConsonant } from '../format';
import { trackEvent } from '../toss';
import type { Trial, TrialPhase, TrialSource } from '../trial';
import {
  TRIAL_CAP_SECONDS,
  beginTrial,
  completeTrial,
  readTrial,
  stopTrial,
  trialDurationSeconds,
  trialPhase,
  writeTrial,
} from '../trial';
import { PENCIL_FRAME, SERIF_VALUE, Screen, Text, coverColor } from '../ui';
import { ACTIVE_SESSION_RELIEF, HERO_CARD_BG_VAR, heroOverline } from './Home';

/**
 * 게스트 홈 — <b>로그인 전에도 홈을 보여 주고</b> 서재·책방·기록만 잠근다(2026-09-11).
 *
 * <p>PR-2는 로그인 전 화면을 「타이머 카드 + 버튼 둘」의 단독 화면으로 뒀다. 그 화면은 체험을 열었지만
 * <b>이 앱이 무엇인지</b>는 여전히 안 보여 줬다 — 탭바도 없어 「나중에 무엇이 열리는가」가 화면 어디에도
 * 없었다. 여기서는 홈의 모양(헤더 · 히어로 · 탭바)을 그대로 세우고, 계정이 있어야 여는 칸만 잠근다.
 *
 * <p><b>서버를 한 번도 부르지 않는다.</b> 로그인 홈(`Home`)을 재사용하지 않은 첫째 이유가 이것이다 —
 * 그 화면은 마운트 즉시 `/api/home-feed`를 받고 401이면 `toLogin()`으로 떨어져, 게스트에 두면 진입
 * 즉시 재렌더 루프가 된다. 잠긴 탭 셋도 같은 사정이라 화면을 마운트하지 않고 {@link LockedScreen}이
 * 그 자리에 선다(각 화면 무변경).
 *
 * <p>덮는 것이 하나도 없다 — 전부 `Screen` 안의 정적 요소이고 `position: fixed`는 탭바 하나뿐이다
 * (T-183: 진입 직후 시트·딤·모달·툴팁 금지).
 */

/** 어느 손잡이에서 로그인을 시작했나 — 진입점 여섯을 갈라 찍는다(PR-2의 `'trial'`은 전후 비교용으로 유지). */
export type LoginSource =
  | 'header'
  | 'book_card'
  | 'trial'
  | 'locked_library'
  | 'locked_bookshop'
  | 'locked_history';

/** 인트로 소개문 — "무엇을 하는 앱인지"를 로그인 전에 읽힌다(심사 필수 항목). */
const PITCH = '책 읽는 시간을 타이머로 기록하고, 매일의 독서를 잔디로 쌓아요.';

/** 로그인을 청하는 자리에서만 하는 말 — 「왜 계정이 필요한가」의 답이다. */
const WHY_LOGIN = [
  '하루 목표를 정하면 달성 순간 토스 알림으로 알려드려요.',
  'PC 웹(booktimer.app)과 같은 계정으로 이어서 쓸 수 있어요.',
];

/** 끝난 체험에서 ▶를 눌렀을 때의 안내 — 체험은 한 건이라 새로 재지 않는다(조용한 유실 금지). */
export const TRIAL_DONE_HINT = '기록을 남길지 먼저 정해 주세요';

/** 잠긴 탭 — 홈은 게스트도 열리고, 일정(공부)은 게스트 탭바에 아예 없다. */
type LockedTab = Exclude<TabKey, 'home' | 'calendar'>;

/** 24 격자 단색 스트로크 path — 탭 아이콘(`TABS.icon`)과 같은 문법이다(라이브러리 0·이모지 0). */
const PERSON_ICON = 'M12 11.6a3.6 3.6 0 1 0 0-7.2 3.6 3.6 0 0 0 0 7.2ZM4.8 20c0-3.6 3.2-5.9 7.2-5.9s7.2 2.3 7.2 5.9';
const LOCK_ICON = 'M7.6 10.4V7.9a4.4 4.4 0 0 1 8.8 0v2.5M6.2 10.4h11.6v9.1H6.2z';

/**
 * 탭바 가운데 원이 체험 상태에 따라 무엇을 하나 — 순수 판정이라 정적 하니스가 잴 수 있다.
 *
 * <p>`done`이 <b>시작이 아니라 안내</b>인 것이 요점이다: 체험은 한 건이라 다시 재면 방금 잰 것이
 * 조용히 사라진다. 「남길지 먼저 정하라」고 답하는 편이 정직하다.
 */
export function guestAction(phase: TrialPhase): { active: boolean; kind: 'start' | 'stop' | 'hint' } {
  if (phase === 'running') return { active: true, kind: 'stop' };
  return { active: false, kind: phase === 'done' ? 'hint' : 'start' };
}

/**
 * 잠긴 탭이 하는 말 — 탭마다 다르다. 셋이 같으면 「어느 칸을 눌렀는가」에 화면이 답하지 않는다.
 *
 * <p>이유는 셋 다 같은 말(「계정이 있어야」)로 댄다 — 잠긴 이유가 자리마다 다르게 읽히면 로그인이
 * 무엇을 여는지가 흐려진다.
 */
export function lockedCopy(tab: LockedTab): { title: string; detail: string } {
  switch (tab) {
    case 'library':
      return { title: '서재는 계정이 있어야 열려요', detail: '읽는 책을 올려 두고, 다 읽은 책을 모아요.' };
    case 'bookshop':
      return { title: '책방은 계정이 있어야 열려요', detail: '내 책방을 꾸미고, 다른 독서가의 책방을 구경해요.' };
    default:
      return { title: '기록은 계정이 있어야 열려요', detail: '잔디와 달력으로 매일의 독서를 돌아봐요.' };
  }
}

/** 저장된 체험을 첫 화면 상태로 — 상한을 넘겨 돌아왔으면 서버와 같은 규칙으로 접는다. */
function restoreTrial(saved: Trial | null): Trial | null {
  if (saved === null || saved.endedAt !== null) return saved;
  if (elapsedSeconds(saved.startedAt, Date.now()) < TRIAL_CAP_SECONDS) return saved;
  const folded = stopTrial(saved, Date.now());
  // 화면만 접고 storage를 두면 `endedAt:null`이 남아 로그인 뒤 flushTrial이 「올릴 것 없음」으로
  // 지나간다 — 기록을 남기려면 계정이 필요하다고 청해 놓고 아무것도 안 남는다. 접는 쪽(여기)에서
  // 박는다: flushTrial은 「끝난 것만 올린다」는 한 가지 규칙만 알면 된다.
  writeTrial(folded);
  return folded;
}

/** 회색 뼈대 한 줄 — 「여기에 무언가 들어온다」만 말하는 정적 상자다(실데이터 미리보기는 비목표). */
function SkeletonLine({ width }: { width: string }) {
  return (
    <div
      style={{
        height: 12,
        width,
        marginTop: 10,
        borderRadius: 6,
        background: 'var(--adaptiveGrey200, #E4DDD0)',
        opacity: 0.5,
      }}
    />
  );
}

/**
 * 잠긴 탭 화면 — 탭은 <b>열리고</b> 안에서 이유를 말한다.
 *
 * <p>누르면 아무 일도 안 일어나는 칸은 고장으로 읽힌다. 반대로 화면을 열어 주면 「무엇이 잠겼는지」와
 * 「어떻게 여는지」를 한 자리에서 말할 수 있고, 그 자리가 곧 로그인 손잡이가 된다.
 */
export function LockedScreen({ tab, onLogin }: { tab: LockedTab; onLogin: (source: LoginSource) => void }) {
  const copy = lockedCopy(tab);
  const label = TABS.find((t) => t.key === tab)?.label ?? '';

  return (
    <Screen
      title={label}
      subtitle={
        <Text typography="st12" color="grey600" style={{ display: 'block', marginBottom: 20 }}>
          둘러보는 중
        </Text>
      }
    >
      <div
        style={{
          padding: '24px 20px',
          borderRadius: 16,
          background: `var(${HERO_CARD_BG_VAR}, #FCFAF5)`,
          border: '1px solid transparent',
          borderImage: PENCIL_FRAME,
          textAlign: 'center',
        }}
      >
        <svg
          width="28"
          height="28"
          viewBox="0 0 24 24"
          fill="none"
          stroke="var(--adaptiveGrey600, #6F6A5E)"
          strokeWidth="1.8"
          strokeLinecap="round"
          strokeLinejoin="round"
          aria-hidden="true"
        >
          <path d={LOCK_ICON} />
        </svg>
        <Text typography="st10" fontWeight="bold" style={{ display: 'block', marginTop: 10, wordBreak: 'keep-all' }}>
          {copy.title}
        </Text>
        <Text typography="st11" color="grey600" style={{ display: 'block', marginTop: 6, wordBreak: 'keep-all' }}>
          {copy.detail}
        </Text>
        <Button display="block" style={{ marginTop: 20 }} onClick={() => onLogin(`locked_${tab}` as LoginSource)}>
          토스로 시작하기
        </Button>
      </div>

      {/* 뼈대 — 「여기에 목록이 들어온다」는 말이다. 실데이터는 공개 API가 필요해 비목표(사용자 결정). */}
      <div style={{ marginTop: 20, opacity: 0.6 }} aria-hidden="true">
        <SkeletonLine width="72%" />
        <SkeletonLine width="88%" />
        <SkeletonLine width="56%" />
      </div>
    </Screen>
  );
}

/** 체험 결과 카드의 3칸 — 계정을 만들면 이 값들이 무엇이 되는지를 방금 잰 값으로 보여 준다. */
function TrialStat({ label, value }: { label: string; value: string }) {
  return (
    <div style={{ flex: 1, minWidth: 0 }}>
      <div style={{ fontSize: 12, color: 'var(--adaptiveGrey600, #6F6A5E)' }}>{label}</div>
      <div style={{ ...SERIF_VALUE, fontSize: 19, marginTop: 2 }}>{value}</div>
    </div>
  );
}

/**
 * 게스트 홈 본문 — 헤더 · 히어로(체험 전 / 재는 중 / 끝남). 탭바는 {@link GuestShell}이 든다.
 *
 * <p>로그인 홈과 <b>같은 부품</b>을 쓴다(연필 테두리 · 히어로 배경 토큰 · 오버라인 · 세리프 시계) —
 * 로그인하면 같은 자리에 같은 모양의 카드가 서고, 값만 진짜가 된다.
 */
export function GuestHome({
  trial,
  phase,
  now,
  onStart,
  onStop,
  onDiscard,
  onLogin,
}: {
  trial: Trial | null;
  phase: TrialPhase;
  /** 재는 중 시계가 보는 시각 — 매초 갱신은 셸이 든다(멈춰 있으면 재는 중으로 안 읽힌다). */
  now: number;
  onStart: () => void;
  onStop: () => void;
  onDiscard: () => void;
  onLogin: (source: LoginSource) => void;
}) {
  const duration = trial === null ? 0 : trialDurationSeconds(trial);
  const seconds =
    phase === 'running' && trial !== null
      ? Math.min(elapsedSeconds(trial.startedAt, now), TRIAL_CAP_SECONDS)
      : duration;

  return (
    <Screen>
      {/* 헤더 — 로그인 홈의 `AccountSection`과 같은 자리·같은 조판이다. 이름 자리에 앱 이름이 서고,
          아바타 자리에 사람 아이콘이 서서 <b>그 아이콘이 곧 로그인 문</b>이 된다(로그아웃한 기존 사용자의 길). */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 12, padding: '2px 2px 0' }}>
        <span style={{ flex: 1, minWidth: 0 }}>
          <span style={{ display: 'block', ...SERIF_VALUE, fontSize: 26, color: 'var(--adaptiveGrey900, #3A362E)' }}>
            북타이머
          </span>
          <span style={{ display: 'block', marginTop: 1, fontSize: 13, color: 'var(--adaptiveGrey600, #6F6A5E)' }}>
            둘러보는 중
          </span>
        </span>
        <button
          type="button"
          aria-label="토스로 시작하기"
          onClick={() => onLogin('header')}
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flex: 'none',
            width: 38,
            height: 38,
            padding: 0,
            border: 'none',
            borderRadius: '50%',
            background: coverColor('북타이머'),
            cursor: 'pointer',
          }}
        >
          <svg
            width="22"
            height="22"
            viewBox="0 0 24 24"
            fill="none"
            stroke="rgba(44,42,36,0.5)"
            strokeWidth="1.8"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
          >
            <path d={PERSON_ICON} />
          </svg>
        </button>
      </div>

      <div
        style={{
          padding: '28px 20px',
          borderRadius: 16,
          background: `var(${HERO_CARD_BG_VAR}, #FCFAF5)`,
          border: '1px solid transparent',
          borderImage: PENCIL_FRAME,
          textAlign: 'center',
        }}
      >
        {/* 오버라인 — 로그인 홈과 <b>같은 글자</b>다(`heroOverline`). 로그인하면 이 줄만 그대로 남는다. */}
        <div>
          <span style={{ fontSize: 12, letterSpacing: 3, color: 'var(--adaptiveBlue700, #4F6B4C)' }}>
            {heroOverline('reading', false)}
          </span>
        </div>
        <div style={{ marginTop: 6 }}>
          <Text typography="t2" fontWeight="bold" style={{ ...SERIF_VALUE }}>
            {formatClock(seconds)}
          </Text>
        </div>

        {phase === 'none' && (
          <>
            <Text typography="st11" color="grey600" style={{ display: 'block', marginTop: 16, wordBreak: 'keep-all' }}>
              {PITCH}
            </Text>
            <Button display="block" style={{ marginTop: 24 }} onClick={onStart}>
              읽기 시작
            </Button>
          </>
        )}

        {phase === 'running' && (
          <>
            <Text typography="st11" color="grey600" style={{ display: 'block', marginTop: 16, wordBreak: 'keep-all' }}>
              {ACTIVE_SESSION_RELIEF}
            </Text>
            <Button display="block" style={{ marginTop: 24 }} onClick={onStop}>
              그만 읽기
            </Button>
          </>
        )}

        {phase === 'done' && (
          <>
            <Text typography="st11" style={{ display: 'block', marginTop: 16, wordBreak: 'keep-all' }}>
              {formatDuration(duration)} 읽었어요. 기록을 남기려면 계정이 필요해요.
            </Text>

            {/* 계정을 만들면 무엇이 되는가 — 방금 잰 값으로 보여 준다. 「연속 1일」은 <b>투영</b>이라
                아래 한 줄이 「시작하면 …이 돼요」로 그 사실을 말한다(없는 기록을 있다고 하지 않는다). */}
            <div style={{ display: 'flex', marginTop: 20 }}>
              <TrialStat label="오늘" value={formatDuration(duration)} />
              <div style={{ width: 1, background: 'rgba(44, 42, 36, 0.12)' }} />
              <TrialStat label="연속" value="1일" />
              <div style={{ width: 1, background: 'rgba(44, 42, 36, 0.12)' }} />
              <TrialStat label="목표" value="—" />
            </div>
            <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 12, wordBreak: 'keep-all' }}>
              {/* 길이가 사용자 데이터라 조사를 고정할 수 없다 — 「7분이」/「12초가」/「2시간이」가 다 지나간다. */}
              시작하면 방금 {formatDuration(duration)}
              {hasFinalConsonant(formatDuration(duration)) ? '이' : '가'} 잔디 첫 칸이 돼요.
            </Text>

            {WHY_LOGIN.map((line) => (
              <Text key={line} typography="st12" color="grey600" style={{ display: 'block', marginTop: 8, wordBreak: 'keep-all' }}>
                {line}
              </Text>
            ))}
            <Button display="block" style={{ marginTop: 24 }} onClick={() => onLogin('trial')}>
              토스로 시작하기
            </Button>
            <Button display="block" variant="weak" style={{ marginTop: 12 }} onClick={onDiscard}>
              기록 없이 둘게요
            </Button>
          </>
        )}
      </div>
    </Screen>
  );
}

/**
 * 로그인 전 셸 — 체험 상태와 탭바를 든다. 탭은 `App`이 드는데(`currentScreen`이 봐야 한다) 셸은
 * 그 값을 받아 그리기만 한다.
 *
 * <p>탭바는 `MarginShell`의 선례 그대로 {@link BottomTabBar}를 직접 그린다 — `MainTabs`는
 * `dashboard`가 non-null인 전제로 15곳을 읽어, 게스트에 끌어오면 회귀 표면이 가장 넓어진다.
 * 잠금 집합(`locked`)이 <b>측정 중 잠금과 정확히 같아</b>(홈만 열림) 새 규칙도 필요 없다.
 */
export function GuestShell({
  tab,
  onTabChange,
  onLogin,
  trial: injected,
}: {
  tab: TabKey;
  onTabChange: (tab: TabKey) => void;
  onLogin: (source: LoginSource) => void;
  /** 테스트 주입 — 정적 렌더가 세 상태를 다 그려 보는 유일한 길(관례: `Home`의 `celebrate`). */
  trial?: Trial | null;
}) {
  const [trial, setTrial] = useState<Trial | null>(() =>
    restoreTrial(injected !== undefined ? injected : readTrial()),
  );
  const [now, setNow] = useState(() => Date.now());
  /** 끝난 체험에서 ▶를 눌렀다는 안내 — 잠깐 떴다 스스로 사라진다(`MainTabs`의 잠금 안내와 같은 자리). */
  const [hint, setHint] = useState(false);
  const hintTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const phase = trialPhase(trial);

  /**
   * 게스트 진입 1건 — 판정식(`login_started / guest_entered`)의 <b>분모</b>다.
   *
   * <p>`screen_guest_*`를 분모로 못 쓰는 이유: 게스트가 홈↔잠긴 탭을 오갈 때마다 찍혀 진입 1회가
   * 여러 건으로 부푼다. 마운트 1회짜리 이벤트가 따로 있어야 비율이 뜻을 가진다.
   */
  useEffect(() => {
    trackEvent('guest_entered');
  }, []);

  // 초 자리가 움직여야 재는 중으로 보인다(홈 히어로와 같은 간격).
  useEffect(() => {
    if (phase !== 'running') return;
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, [phase]);

  useEffect(
    () => () => {
      if (hintTimer.current !== null) clearTimeout(hintTimer.current);
    },
    [],
  );

  const start = (source: TrialSource) => {
    setTrial(beginTrial(Date.now(), source));
    setNow(Date.now());
  };

  const stop = () => {
    if (trial === null) return;
    setTrial(completeTrial(trial));
    onTabChange('home'); // 결과 카드는 홈에 선다 — 다른 탭에서 끝냈으면 그리로 데려간다
  };

  const showHint = () => {
    if (hintTimer.current !== null) clearTimeout(hintTimer.current);
    setHint(true);
    hintTimer.current = setTimeout(() => setHint(false), TAB_LOCK_HINT_MS);
  };

  const action = guestAction(phase);
  const press = () => {
    if (action.kind === 'stop') stop();
    else if (action.kind === 'hint') showHint();
    else start('play');
  };

  const locked = tab === 'library' || tab === 'bookshop' || tab === 'history';

  return (
    <>
      <div style={{ paddingBottom: TAB_BAR_SPACE }}>
        {locked ? (
          <LockedScreen tab={tab} onLogin={onLogin} />
        ) : (
          <GuestHome
            trial={trial}
            phase={phase}
            now={now}
            onStart={() => start('hero')}
            onStop={stop}
            onDiscard={() => {
              writeTrial(null);
              setTrial(null);
            }}
            onLogin={onLogin}
          />
        )}
      </div>

      {/* 끝난 체험에서 ▶를 눌렀다는 안내 — 탭바 알약은 `overflow: hidden`이라 안에 두면 잘린다. */}
      {hint && (
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
          {TRIAL_DONE_HINT}
        </div>
      )}

      {/* 잠긴 칸을 눌러도 <b>열린다</b> — `onBlocked`가 칸 키를 그대로 탭 전환으로 흘린다. */}
      <BottomTabBar
        tab={tab}
        onTabChange={onTabChange}
        locked
        onBlocked={onTabChange}
        action={{ active: action.active, busy: false, onPress: press }}
      />
    </>
  );
}
