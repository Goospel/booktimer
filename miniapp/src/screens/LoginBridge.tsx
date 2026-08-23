import { Button } from '@toss/tds-mobile';
import { useState } from 'react';

import { login, register } from '../api';
import { ErrorMessage, Loading, Screen, Text } from '../ui';

type Phase = 'intro' | 'checking' | 'choice' | 'failed';

/** 인트로 소개문 — "무엇을 하는 앱인지"를 로그인 전에 읽힌다(심사 반려 1 대응). */
const PITCH = [
  '책 읽는 시간을 타이머로 기록하고, 매일의 독서를 잔디로 쌓아요.',
  '하루 목표를 정하면 달성 순간 토스 알림으로 알려드려요.',
  'PC 웹(booktimer.app)과 같은 계정으로 이어서 쓸 수 있어요.',
];

/**
 * 로그인 시작 — `appLogin()` → `/api/toss/login`의 결과를 다음 화면으로 옮긴다.
 *
 * <p>정적 렌더 하니스로는 클릭을 못 잡으므로 이 흐름만 따로 꺼내 단위로 계측한다
 * (관례: `claimDebtWaiver`·`tabChangeHandler`).
 */
export async function beginLogin(): Promise<'authenticated' | 'choice'> {
  const result = await login();
  return result.registered ? 'authenticated' : 'choice';
}

/**
 * 로그인 브릿지 — **서비스 소개(인트로)를 먼저 보여주고**, 「토스로 시작하기」를 누를 때 인가를 시작한다.
 * 진입 즉시 `appLogin()`을 부르던 것이 미니앱 심사 반려 사유였다(2026-08-12).
 *
 * <p>등록된 신원이면 바로 홈으로. 미등록이면 서버가 계정을 만들지 않고 `registered:false`를 주므로
 * 여기서 "새로 시작 / 기존 계정 연결"을 묻는다(설계 §2.2).
 */
export function LoginBridge({
  onAuthenticated,
  onNewAccount,
  onLinkAccount,
}: {
  onAuthenticated: () => void;
  onNewAccount: () => void;
  onLinkAccount: () => void;
}) {
  const [phase, setPhase] = useState<Phase>('intro');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const start = () => {
    setPhase('checking');
    beginLogin()
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
        {PITCH.map((line) => (
          <Text key={line} typography="st11" color="grey600" style={{ display: 'block', marginBottom: 12 }}>
            {line}
          </Text>
        ))}
        <Button display="block" style={{ marginTop: 24 }} onClick={start}>
          토스로 시작하기
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
