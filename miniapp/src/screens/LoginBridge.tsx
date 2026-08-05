import { Button, Text } from '@toss/tds-mobile';
import { useEffect, useRef, useState } from 'react';

import { login, register } from '../api';
import { ErrorMessage, Loading, Screen } from '../ui';

type Phase = 'checking' | 'choice' | 'failed';

/**
 * 로그인 브릿지 — 진입 즉시 `appLogin()` → `/api/toss/login`.
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
  const [phase, setPhase] = useState<Phase>('checking');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const started = useRef(false);

  useEffect(() => {
    // StrictMode의 이중 실행을 막는다 — appLogin을 두 번 부르면 사용자가 인증 화면을 두 번 본다.
    if (started.current) return;
    started.current = true;

    login()
      .then((result) => {
        if (result.registered) onAuthenticated();
        else setPhase('choice');
      })
      .catch((e: Error) => {
        setError(e.message);
        setPhase('failed');
      });
  }, [onAuthenticated]);

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
