import { Button } from '@toss/tds-mobile';
import { useEffect, useState } from 'react';

import { login, register } from '../api';
import { trackEvent } from '../toss';
import { ErrorMessage, Loading, Screen } from '../ui';
import type { LoginSource } from './GuestHome';

type Phase = 'checking' | 'failed';

/**
 * 로그인 시작 — 인가 1회의 결과를 다음 화면으로 옮긴다.
 *
 * <p>손잡이 여섯은 처음부터 `register`(서버 find-or-create, 멱등)를 부른다 — 인가(appLogin) <b>1회</b>로
 * 계정까지 간다(2026-09-17 선택 화면 제거). 옛 흐름은 `login`(조회) → 미등록이면 선택 화면 → `register`로
 * 인가를 한 번 더 받았다. 예외는 `web_link` 하나다: 웹 계정 보유자가 여기서 계정을 만들면 토스 신원이 새
 * 계정에 once-set으로 박혀 웹 계정에 다시는 못 붙으므로, 조회만 하고 미등록이면 연결 화면으로 보낸다.
 *
 * <p>정적 렌더 하니스로는 클릭을 못 잡으므로 이 흐름만 따로 꺼내 단위로 계측한다
 * (관례: `claimDebtWaiver`·`tabChangeHandler`).
 */
export async function beginLogin(source: LoginSource): Promise<'authenticated' | 'created' | 'link'> {
  // 「토스로 시작하기」를 눌렀다 — `await` **앞**이라 인가가 취소·실패해도 남는다. 「눌렀는데 안 온
  // 사람」과 「아예 안 누른 사람」은 처방이 달라서(인가·약관 단계 문제 vs 소개문 문제) 이 한 점이 가른다.
  // `source`는 그 위에 한 층을 더 얹는다: 어느 자리가 사람을 데려오는지가 다음 손질의 좌표다.
  trackEvent('login_started', { source });

  if (source === 'web_link') {
    const result = await login();
    return result.registered ? 'authenticated' : 'link';
  }

  const result = await register();
  // 옛 서버는 `created`를 안 준다(undefined) — 기존 계정으로 읽힌다. 서버를 먼저 배포하는 이유다.
  if (result.created !== true) return 'authenticated';
  trackEvent('account_created', { source });
  return 'created';
}

/**
 * 로그인 진행 화면 — <b>인가 왕복과 그 결말만</b> 든다.
 *
 * <p>이 화면은 사용자가 게스트 홈의 손잡이를 <b>누른 뒤에만</b> 마운트되므로 마운트 즉시 인가로 들어간다 —
 * 「서비스 설명 없이 즉시 로그인 유도」(2026-08-12 심사 반려)와는 다른 자리다. 심사자가 보는 앱 <b>진입</b>
 * 첫 화면은 게스트 홈이고, 그 사실은 `guest-home.test.tsx`·`app.test.tsx`가 못 박는다.
 *
 * <p>결말은 셋이다: 기존 계정이면 홈, 방금 만들어졌으면 목표 설정, 「기존 계정 연결」에서 미등록이면 연결
 * 화면. 체험은 로그인 뒤 `App.load()`의 `flushTrial()`이 합류시킨다.
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

  /*
   * 마운트 1회 — 이 화면이 서는 것 자체가 「시작을 눌렀다」는 뜻이다. `source`는 마운트마다 고정이라
   * 의존성이 비어 있어도 낡은 값을 잡지 않는다(다른 손잡이를 누르면 App이 새로 마운트한다).
   */
  useEffect(() => {
    beginLogin(source)
      .then((next) => {
        if (next === 'created') onNewAccount();
        else if (next === 'link') onLinkAccount();
        else onAuthenticated();
      })
      .catch((e: Error) => {
        setError(e.message);
        setPhase('failed');
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (phase === 'checking') return <Loading message="토스로 로그인하는 중…" />;

  return (
    <Screen title="로그인하지 못했어요">
      <ErrorMessage message={error} />
      <Button display="block" style={{ marginTop: 24 }} onClick={() => window.location.reload()}>
        다시 시도
      </Button>
    </Screen>
  );
}
