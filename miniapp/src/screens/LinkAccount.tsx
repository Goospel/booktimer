import { Button, TextField } from '@toss/tds-mobile';
import { useState } from 'react';

import { linkAccount } from '../api';
import { ErrorMessage, Screen, Text } from '../ui';

/**
 * 계정 연결 — 웹 설정에서 발급받은 일회용 코드가 "이 계정의 주인" 증명이다(설계 §2.2 ⓐ).
 *
 * <p>만료·이미 사용·없는 코드는 서버가 400으로 한데 묶어 거부하고(구분은 추측 단서가 된다),
 * 이미 연결된 계정은 409다 — 둘 다 서버 메시지를 그대로 보여준다.
 */
export function LinkAccount({ onLinked, onBack }: { onLinked: () => void; onBack: () => void }) {
  const [code, setCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = () => {
    setBusy(true);
    setError(null);
    linkAccount(code.trim())
      .then(onLinked)
      .catch((e: Error) => setError(e.message))
      .finally(() => setBusy(false));
  };

  return (
    <Screen title="기존 계정 연결" onBack={onBack} backDisabled={busy}>
      <Text typography="st11" color="grey600" style={{ display: 'block', marginBottom: 20 }}>
        PC 웹에서 booktimer.app에 로그인한 뒤 <b>설정 → 토스 앱 연결</b>에서 연결 코드를 받아 입력해 주세요.
        코드는 5분 뒤 만료돼요.
      </Text>
      {/* 입력 하나짜리 form이라 엔터(키보드 「완료」)가 곧 제출이다 — 버튼은 밖에 둔다(Social과 같은 배선). */}
      <form
        onSubmit={(e) => {
          e.preventDefault(); // 막지 않으면 페이지가 새로고침돼 로그인 화면으로 되돌아간다
          if (!busy && code.trim() !== '') submit();
        }}
      >
        <TextField
          variant="box"
          label="연결 코드"
          placeholder="예: A1B2C3"
          value={code}
          disabled={busy}
          onChange={(e) => setCode(e.target.value)}
        />
      </form>
      <ErrorMessage message={error} />
      <Button
        display="block"
        style={{ marginTop: 24 }}
        loading={busy}
        disabled={code.trim() === ''}
        onClick={submit}
      >
        연결하기
      </Button>
    </Screen>
  );
}
