import { Button, Wheel } from '@toss/tds-mobile';
import { useState } from 'react';

import { setGoal } from '../api';
import { formatDuration } from '../format';
import { ErrorMessage, FilledButton, Screen, Text } from '../ui';

/** 휠 시간 열의 상한 — 하루 독서 목표로 12시간이면 넘치고, 더 길면 휠만 길어져 고르기 힘들다. */
const MAX_HOURS = 12;
const HOUR_OPTIONS = Array.from({ length: MAX_HOURS + 1 }, (_, i) => i);
const MINUTE_OPTIONS = Array.from({ length: 60 }, (_, i) => i);

/** 첫 실행에서 미리 골라 둘 값(10분) — 첫 세션 안에 「오늘 목표 달성」을 실제로 밟을 수 있는 크기다. */
export const FIRST_RUN_GOAL_SECONDS = 600;

/**
 * 초 → 휠 표시값(시/분).
 *
 * <p>휠은 분 단위라 자투리 초는 버리고, 휠에 없는 칸을 가리키지 않도록 상한(12시간)을 넘는 값은
 * 12시간 59분으로 붙인다. 음수 같은 이상값도 0으로 눌러 휠이 빈 칸을 가리키지 않게 한다.
 */
export function wheelIndices(seconds: number): { hours: number; minutes: number } {
  const totalMinutes = Math.max(0, Math.floor(seconds / 60));
  if (totalMinutes >= (MAX_HOURS + 1) * 60) {
    return { hours: MAX_HOURS, minutes: 59 };
  }
  return { hours: Math.floor(totalMinutes / 60), minutes: totalMinutes % 60 };
}

/** 휠 표시값(시/분) → 초. */
export function combineWheel(hours: number, minutes: number): number {
  return hours * 3600 + minutes * 60;
}

/**
 * 첫 화면에 미리 골라 둘 목표(초).
 *
 * <p>신규 계정의 서버 기본은 1시간(`UserRegistrationService.DEFAULT_DAILY_INCREMENT_SECONDS`)인데,
 * 첫 실행에서 그 값을 그대로 골라 두면 첫 세션에서 목표 달성(히어로 「🌿 오늘 목표 달성」 + lv4 잔디)을
 * 경험할 확률이 사실상 0이다(운영 실측 2026-08-13 — 신규 3명 전원 5분+ 독서 0건). 서버 기본값은 웹
 * 신규 가입과 공유하므로 건드리지 않고 <b>첫 화면의 초기 선택만</b> 내린다.
 *
 * <p>목표를 바꾸러 들어온 기존 사용자에겐 지금 값 그대로 — 남의 설정을 몰래 내리지 않는다.
 */
export function initialGoalSelection(firstRun: boolean, current: number): number {
  return firstRun ? FIRST_RUN_GOAL_SECONDS : current;
}

/**
 * 목표 설정 — 신규 계정 첫 실행 유도 + 이후 변경(같은 엔드포인트).
 *
 * <p>미니앱 온보딩은 공개 핸들(login_id)을 요구하지 않는다 — 평생 1번만 바꿀 수 있어 첫 진입에
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
  const [selected, setSelected] = useState(() => initialGoalSelection(firstRun, current));
  /** 휠은 비제어 컴포넌트라 시작 칸만 첫 렌더에서 한 번 읽는다 — 이후 값은 onChange가 selected로 되돌린다. */
  const [initialWheel] = useState(() => wheelIndices(initialGoalSelection(firstRun, current)));
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

      {/* 프리셋 칩 대신 휠 2열 — 초는 selected 하나가 단일 소스고, 시/분은 그때그때 풀었다 다시 합친다.
          높이는 컨테이너가 줘야 한다 — Wheel 루트가 height:100%라(항목 한 칸 = 그 16%) 높이 없는 부모에
          넣으면 컨테이너가 0이 되어 항목이 전부 한 줄에 겹친다(브라우저 실측 2026-08-13). */}
      <div
        className="goal-wheels"
        style={{ position: 'relative', display: 'flex', justifyContent: 'center', gap: 8, height: 180 }}
      >
        {/* 선택 밴드(시안 2e) — 가운데 칸이 「고른 것」임을 색으로 말한다. 정적이다(애니메이션 0):
            T-176이 발광 애니메이션으로 표지를 초당 60번 재래스터화했던 자리와 같은 종류다. */}
        <div
          aria-hidden="true"
          data-wheel-band=""
          style={{
            position: 'absolute',
            left: 0,
            right: 0,
            top: '50%',
            height: 44,
            transform: 'translateY(-50%)',
            background: 'var(--adaptiveBlue50, #E7EEE2)',
            borderRadius: 10,
          }}
        />
        <Wheel
          options={HOUR_OPTIONS}
          formatValue={(n) => `${n}시간`}
          initialIndex={initialWheel.hours}
          onChange={(hours) => setSelected((s) => combineWheel(hours, wheelIndices(s).minutes))}
          width={120}
          aria-label="시간 선택"
        />
        <Wheel
          options={MINUTE_OPTIONS}
          formatValue={(n) => `${n}분`}
          initialIndex={initialWheel.minutes}
          onChange={(minutes) => setSelected((s) => combineWheel(wheelIndices(s).hours, minutes))}
          width={120}
          aria-label="분 선택"
        />
      </div>

      {/* 미리 골라 둔 값이 왜 이렇게 작은지 한 줄로 — 없으면 "이 앱은 나를 얕본다"로 읽히고,
          있으면 첫날 달성을 밟게 하려는 배려로 읽힌다. 목표를 바꾸러 온 사람에겐 할 말이 아니다. */}
      {firstRun && (
        <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 12 }}>
          🌱 가볍게 시작 — {formatDuration(FIRST_RUN_GOAL_SECONDS)}이면 오늘 안에 한 번 달성할 수 있어요.
          언제든 늘릴 수 있어요.
        </Text>
      )}

      <ErrorMessage message={error} />

      {/* 0시간 0분 저장은 서버가 허용하는 「목표 없음」이지만, 휠을 끝까지 내린 실수일 가능성이 더 높다. */}
      {/* 이 화면의 주 동작 하나 — 채움이다(시안 2e). 홈은 탭바 원이 그 역할을 한다. */}
      <FilledButton
        display="block"
        style={{ marginTop: 28 }}
        loading={busy}
        disabled={selected === 0}
        onClick={save}
      >
        {firstRun ? '이걸로 시작하기' : '저장'}
      </FilledButton>
      <Button display="block" variant="weak" style={{ marginTop: 12 }} disabled={busy} onClick={onSkip}>
        {firstRun ? '나중에 정할래요' : '돌아가기'}
      </Button>
    </Screen>
  );
}
