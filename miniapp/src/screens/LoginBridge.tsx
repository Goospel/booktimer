import { Button } from '@toss/tds-mobile';
import { useEffect, useState } from 'react';

import { login, register } from '../api';
import { elapsedSeconds, formatClock, formatDuration } from '../format';
import { trackEvent } from '../toss';
import { ErrorMessage, Loading, PENCIL_FRAME, SERIF_VALUE, Screen, Text } from '../ui';
import type { Trial } from '../trial';
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

type Phase = 'intro' | 'running' | 'done' | 'checking' | 'choice' | 'failed';

/** 인트로 소개문 — "무엇을 하는 앱인지"를 로그인 전에 읽힌다(심사 필수 항목). */
const PITCH = '책 읽는 시간을 타이머로 기록하고, 매일의 독서를 잔디로 쌓아요.';

/** 로그인을 청하는 자리에서만 하는 말 — 「왜 계정이 필요한가」의 답이다. */
const WHY_LOGIN = [
  '하루 목표를 정하면 달성 순간 토스 알림으로 알려드려요.',
  'PC 웹(booktimer.app)과 같은 계정으로 이어서 쓸 수 있어요.',
];

/** 어느 화면에서 눌렀는가 — 처방이 다른 두 층을 가른다(§6 계측). */
export type LoginSource = 'intro' | 'trial';

/**
 * 로그인 시작 — `appLogin()` → `/api/toss/login`의 결과를 다음 화면으로 옮긴다.
 *
 * <p>정적 렌더 하니스로는 클릭을 못 잡으므로 이 흐름만 따로 꺼내 단위로 계측한다
 * (관례: `claimDebtWaiver`·`tabChangeHandler`).
 */
export async function beginLogin(source: LoginSource): Promise<'authenticated' | 'choice'> {
  // 「토스로 시작하기」를 눌렀다 — `await` **앞**이라 인가가 취소·실패해도 남는다. 「눌렀는데 안 온
  // 사람」과 「아예 안 누른 사람」은 처방이 달라서(인가·약관 단계 문제 vs 소개문 문제) 이 한 점이 가른다.
  // `source`는 그 위에 한 층을 더 얹는다: 체험을 마치고 누른 사람이 거절하면 그때가 「개인정보
  // 경각심」 가설을 다시 볼 시점이고, 인트로에서 아예 안 눌렸으면 그건 화면 문제다.
  trackEvent('login_started', { source });
  const result = await login();
  return result.registered ? 'authenticated' : 'choice';
}

/** 저장된 체험을 첫 화면 상태로 — 상한을 넘겨 돌아왔으면 서버와 같은 규칙으로 접는다. */
function restoreTrial(saved: Trial | null): Trial | null {
  if (saved === null || saved.endedAt !== null) return saved;
  return elapsedSeconds(saved.startedAt, Date.now()) >= TRIAL_CAP_SECONDS
    ? stopTrial(saved, Date.now())
    : saved;
}

/** 체험 카드 — 홈 히어로와 같은 문법(연필 테두리 + 세리프 수)이되, 대시보드를 안 끌고 온다. */
function TimerCard({ seconds }: { seconds: number }) {
  return (
    <div
      style={{
        padding: '28px 20px',
        borderRadius: 16,
        background: '#FCFAF5',
        border: '1px solid transparent',
        borderImage: PENCIL_FRAME,
        textAlign: 'center',
      }}
    >
      <Text typography="t2" fontWeight="bold" style={{ ...SERIF_VALUE }}>
        {formatClock(seconds)}
      </Text>
    </div>
  );
}

/**
 * 로그인 브릿지 — **먼저 재게 하고, 기록을 남길 때 로그인을 청한다**(2026-09-11).
 *
 * <p>소개 화면 21명 중 버튼을 누른 사람은 4명이었다. 이탈은 토스 동의창 <b>이전</b>에 났으므로 첫 탭의
 * 대가를 0으로 만드는 것만이 그 단계를 직접 건드린다. 진입 즉시 `appLogin()`을 부르던 것이 심사
 * 반려 사유였고(2026-08-12), 지금도 첫 화면은 <b>오버레이가 아닌 제품 화면</b>이다 — 세 상태(체험 전·
 * 재는 중·끝남)가 전부 `Screen` 안의 분기라 덮는 것이 하나도 없다.
 *
 * <p>등록된 신원이면 바로 홈으로. 미등록이면 서버가 계정을 만들지 않고 `registered:false`를 주므로
 * 여기서 "새로 시작 / 기존 계정 연결"을 묻는다. 체험은 로그인 뒤 `App.load()`의 `flushTrial()`이 합류시킨다.
 */
export function LoginBridge({
  onAuthenticated,
  onNewAccount,
  onLinkAccount,
  trial: injected,
}: {
  onAuthenticated: () => void;
  onNewAccount: () => void;
  onLinkAccount: () => void;
  /** 테스트 주입 — 정적 렌더가 세 상태를 다 그려 보는 유일한 길(관례: `Home`의 `celebrate`). */
  trial?: Trial | null;
}) {
  const [trial, setTrial] = useState<Trial | null>(() =>
    restoreTrial(injected !== undefined ? injected : readTrial()),
  );
  const [phase, setPhase] = useState<Phase>(() => {
    const restored = restoreTrial(injected !== undefined ? injected : readTrial());
    const started = trialPhase(restored);
    return started === 'none' ? 'intro' : started;
  });
  const [now, setNow] = useState(() => Date.now());
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // 초 자리가 움직여야 재는 중으로 보인다(홈 히어로와 같은 간격).
  useEffect(() => {
    if (phase !== 'running') return;
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, [phase]);

  const start = (source: LoginSource) => () => {
    setPhase('checking');
    beginLogin(source)
      .then((next) => {
        if (next === 'authenticated') onAuthenticated();
        else setPhase('choice');
      })
      .catch((e: Error) => {
        setError(e.message);
        setPhase('failed');
      });
  };

  if (phase === 'intro') {
    return (
      <Screen title="북타이머">
        <TimerCard seconds={0} />
        <Text typography="st11" color="grey600" style={{ display: 'block', marginTop: 16 }}>
          {PITCH}
        </Text>
        <Button
          display="block"
          style={{ marginTop: 24 }}
          onClick={() => {
            setTrial(beginTrial());
            setNow(Date.now());
            setPhase('running');
          }}
        >
          읽기 시작
        </Button>
        <Button display="block" variant="weak" style={{ marginTop: 12 }} onClick={start('intro')}>
          이미 쓰고 있어요 · 토스로 로그인
        </Button>
      </Screen>
    );
  }

  if (phase === 'running' && trial !== null) {
    return (
      <Screen title="읽는 중">
        <TimerCard seconds={Math.min(elapsedSeconds(trial.startedAt, now), TRIAL_CAP_SECONDS)} />
        <Text typography="st11" color="grey600" style={{ display: 'block', marginTop: 16 }}>
          화면을 꺼도 계속 재고 있어요.
        </Text>
        <Button
          display="block"
          style={{ marginTop: 24 }}
          onClick={() => {
            setTrial(completeTrial(trial));
            setPhase('done');
          }}
        >
          그만 읽기
        </Button>
      </Screen>
    );
  }

  if (phase === 'done' && trial !== null) {
    return (
      <Screen title="읽었어요">
        <TimerCard seconds={trialDurationSeconds(trial)} />
        <Text typography="st11" style={{ display: 'block', marginTop: 16 }}>
          {formatDuration(trialDurationSeconds(trial))} 읽었어요. 기록을 남기려면 계정이 필요해요.
        </Text>
        {WHY_LOGIN.map((line) => (
          <Text key={line} typography="st11" color="grey600" style={{ display: 'block', marginTop: 8 }}>
            {line}
          </Text>
        ))}
        <Button display="block" style={{ marginTop: 24 }} onClick={start('trial')}>
          토스로 시작하기
        </Button>
        <Button
          display="block"
          variant="weak"
          style={{ marginTop: 12 }}
          onClick={() => {
            writeTrial(null);
            setTrial(null);
            setPhase('intro');
          }}
        >
          기록 없이 둘게요
        </Button>
      </Screen>
    );
  }

  if (phase === 'checking') return <Loading message="토스로 로그인하는 중…" />;

  if (phase === 'failed') {
    return (
      <Screen title="로그인하지 못했어요">
        <ErrorMessage message={error} />
        <Button display="block" style={{ marginTop: 24 }} onClick={() => window.location.reload()}>
          다시 시도
        </Button>
      </Screen>
    );
  }

  const startFresh = () => {
    setBusy(true);
    setError(null);
    // 인가코드는 일회성이라 register가 appLogin을 새로 부른다 — 사용자는 한 번 더 확인만 하면 된다.
    register()
      .then(onNewAccount)
      .catch((e: Error) => setError(e.message))
      .finally(() => setBusy(false));
  };

  return (
    <Screen title="북타이머 시작하기">
      <Text typography="st11" color="grey600" style={{ display: 'block', marginBottom: 24 }}>
        처음이신가요? 새로 시작할 수 있어요. 이미 booktimer.app 계정이 있다면 연결하면 PC에서 쌓은 기록을
        그대로 이어서 볼 수 있어요.
      </Text>
      <Button display="block" loading={busy} onClick={startFresh}>
        새로 시작
      </Button>
      <Button
        display="block"
        variant="weak"
        style={{ marginTop: 12 }}
        disabled={busy}
        onClick={onLinkAccount}
      >
        기존 booktimer 계정 연결
      </Button>
      <ErrorMessage message={error} />
    </Screen>
  );
}
