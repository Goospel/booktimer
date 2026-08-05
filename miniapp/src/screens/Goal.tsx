import { Button, Text } from '@toss/tds-mobile';
import { useState } from 'react';

import { setGoal } from '../api';
import { formatDuration } from '../format';
import { ErrorMessage, Screen } from '../ui';

/** 하루 목표 후보 — 미니앱은 설정 화면이 없으니 흔한 값만 골라 마찰을 없앤다. */
const PRESETS = [600, 1200, 1800, 3600, 5400, 7200];

/**
 * 목표 설정 — 신규 계정 첫 실행 유도 + 이후 변경(같은 엔드포인트).
 *
 * <p>미니앱 온보딩은 공개 핸들(login_id)을 요구하지 않는다 — 한 번 정하면 불변이라 첫 진입에
 * 강요하지 않는다(설계 §2.4).
 */
export function Goal({
  current,
  firstRun,
  onSaved,
  onSkip,
}: {
  current: number;
  firstRun: boolean;
  onSaved: () => void;
  onSkip: () => void;
}) {
  const [selected, setSelected] = useState(current);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const save = () => {
    setBusy(true);
    setError(null);
    setGoal(selected)
      .then(onSaved)
      .catch((e: Error) => setError(e.message))
      .finally(() => setBusy(false));
  };

  return (
    <Screen title={firstRun ? '하루에 얼마나 읽을까요?' : '하루 목표 바꾸기'}>
      <Text typography="st11" color="grey600" style={{ display: 'block', marginBottom: 20 }}>
        매일 이만큼씩 쌓여요. 못 채운 시간은 다음 날로 넘어가니 부담 없는 값으로 시작해 보세요.
      </Text>

      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
        {PRESETS.map((seconds) => (
          <Button
            key={seconds}
            size="medium"
            variant={selected === seconds ? 'fill' : 'weak'}
            onClick={() => setSelected(seconds)}
          >
            {formatDuration(seconds)}
          </Button>
        ))}
      </div>

      <ErrorMessage message={error} />

      <Button display="block" style={{ marginTop: 28 }} loading={busy} onClick={save}>
        {firstRun ? '이걸로 시작하기' : '저장'}
      </Button>
      <Button display="block" variant="weak" style={{ marginTop: 12 }} disabled={busy} onClick={onSkip}>
        {firstRun ? '나중에 정할래요' : '돌아가기'}
      </Button>
    </Screen>
  );
}
