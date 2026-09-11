import { Button } from '@toss/tds-mobile';
import { useEffect, useState } from 'react';

import { login, register } from '../api';
import { trackEvent } from '../toss';
import { ErrorMessage, Loading, Screen, Text } from '../ui';
import type { LoginSource } from './GuestHome';

type Phase = 'checking' | 'choice' | 'failed';

/**
 * 로그인 시작 — `appLogin()` → `/api/toss/login`의 결과를 다음 화면으로 옮긴다.
 *
 * <p>정적 렌더 하니스로는 클릭을 못 잡으므로 이 흐름만 따로 꺼내 단위로 계측한다
 * (관례: `claimDebtWaiver`·`tabChangeHandler`).
 */
export async function beginLogin(source: LoginSource): Promise<'authenticated' | 'choice'> {
  // 「토스로 시작하기」를 눌렀다 — `await` **앞**이라 인가가 취소·실패해도 남는다. 「눌렀는데 안 온
  // 사람」과 「아예 안 누른 사람」은 처방이 달라서(인가·약관 단계 문제 vs 소개문 문제) 이 한 점이 가른다.
  // `source`는 그 위에 한 층을 더 얹는다: 게스트 홈엔 손잡이가 여섯이라(헤더·책 카드·체험 결과·잠긴 탭 셋)
  // 어느 자리가 사람을 데려오는지가 다음 손질의 좌표다.
  trackEvent('login_started', { source });
  const result = await login();
  return result.registered ? 'authenticated' : 'choice';
}

/**
 * 로그인 진행 화면 — <b>인가 왕복과 그 결말만</b> 든다(2026-09-11 게스트 홈 도입으로 축소).
 *
 * <p>체험(인트로·재는 중·끝남)은 `GuestHome`으로 옮겼다. 이 화면은 사용자가 게스트 홈의 손잡이를
 * <b>누른 뒤에만</b> 마운트되므로 마운트 즉시 인가로 들어간다 — 「서비스 설명 없이 즉시 로그인 유도」
 * (2026-08-12 심사 반려)와는 다른 자리다. 심사자가 보는 앱 <b>진입</b> 첫 화면은 게스트 홈이고,
 * 그 사실은 `guest-home.test.tsx`·`app.test.tsx`가 못 박는다.
 *
 * <p>등록된 신원이면 바로 홈으로. 미등록이면 서버가 계정을 만들지 않고 `registered:false`를 주므로
 * 여기서 "새로 시작 / 기존 계정 연결"을 묻는다. 체험은 로그인 뒤 `App.load()`의 `flushTrial()`이 합류시킨다.
 */
export function LoginBridge({
  source,
  onAuthenticated,
  onNewAccount,
  onLinkAccount,
}: {
  /** 어느 손잡이에서 왔나 — 그대로 `login_started`에 실린다. */
  source: LoginSource;
  onAuthenticated: () => void;
  onNewAccount: () => void;
  onLinkAccount: () => void;
}) {
  const [phase, setPhase] = useState<Phase>('checking');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  /*
   * 마운트 1회 — 이 화면이 서는 것 자체가 「시작을 눌렀다」는 뜻이다. `source`는 마운트마다 고정이라
   * 의존성이 비어 있어도 낡은 값을 잡지 않는다(다른 손잡이를 누르면 App이 새로 마운트한다).
   */
  useEffect(() => {
    beginLogin(source)
      .then((next) => {
        if (next === 'authenticated') onAuthenticated();
        else setPhase('choice');
      })
      .catch((e: Error) => {
        setError(e.message);
        setPhase('failed');
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

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
