import { Button, Wheel } from '@toss/tds-mobile';
import { useState } from 'react';

import type { StudyBookRow } from '../api';
import { DEFAULT_SESSION_GOAL, SESSION_GOAL_MAX_HOURS, sessionGoalWheelState } from '../sessionGoal';
import { ErrorMessage, FilledButton, Sheet, Text } from '../ui';
import { wheelIndices } from '../wheelTime';

const HOUR_OPTIONS = Array.from({ length: SESSION_GOAL_MAX_HOURS + 1 }, (_, i) => i);
const MINUTE_OPTIONS = Array.from({ length: 60 }, (_, i) => i);

/**
 * 공부 책의 「회당 시간」 고르기 — 사용자가 손잡이를 눌러야만 열린다(진입 직후 화면을 덮지 않는다, 심사 규칙).
 *
 * <p>휠·밴드·높이는 독서 목표 화면(`Goal.tsx`)과 같은 부품이다 — 키보드·IME가 없어 빈칸·소수·전각 숫자가
 * 원리상 없고, 남는 경계(10분 미만·6시간 초과)는 {@link sessionGoalWheelState}가 판정해 저장을 잠근다.
 * 회당 시간 설정에는 광고를 붙이지 않는다(사용자 결정 Q1).
 */
export function SessionGoalSheet({
  book,
  busy = false,
  error = null,
  onPick,
  onClose,
}: {
  book: StudyBookRow;
  /** 저장 요청 중 — 두 버튼을 잠근다(연타로 두 번 보내지 않게). */
  busy?: boolean;
  /**
   * 저장 실패 문구 — 시트 <b>안</b>에서 말한다. 액션 스트립은 탭바 층(z 100)이라 이 패널(z 201)에 가려 안 보인다.
   */
  error?: string | null;
  /** 고른 초 또는 `null`(해제). 저장·닫기는 부르는 쪽이 응답을 보고 한다. */
  onPick: (seconds: number | null) => void;
  onClose: () => void;
}) {
  const current = book.sessionGoalSeconds ?? null;
  /** 휠은 비제어라 시작 칸만 첫 렌더에서 읽는다 — 이후 값은 onChange가 되돌린다. */
  const [picked, setPicked] = useState(() =>
    wheelIndices(current ?? DEFAULT_SESSION_GOAL, SESSION_GOAL_MAX_HOURS),
  );
  const [initial] = useState(picked);
  const wheel = sessionGoalWheelState(picked.hours, picked.minutes);

  return (
    <Sheet title="이 책 회당 시간" onClose={onClose}>
      <Text typography="st11" color="grey600" style={{ display: 'block', marginBottom: 16 }}>
        {book.title}
      </Text>
      {/* 높이는 컨테이너가 준다 — Wheel 루트가 height:100%라 높이 없는 부모에선 항목이 한 줄에 겹친다(Goal.tsx 실측).
          `goal-wheels`는 안개 색·선택 행 조판을 거는 css 훅이다. */}
      <div
        className="goal-wheels"
        style={{ position: 'relative', display: 'flex', justifyContent: 'center', gap: 8, height: 180 }}
      >
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
          initialIndex={initial.hours}
          onChange={(hours) => setPicked((p) => ({ ...p, hours }))}
          width={120}
          aria-label="시간 선택"
        />
        <Wheel
          options={MINUTE_OPTIONS}
          formatValue={(n) => `${n}분`}
          initialIndex={initial.minutes}
          onChange={(minutes) => setPicked((p) => ({ ...p, minutes }))}
          width={120}
          aria-label="분 선택"
        />
      </div>
      {/* 판정 줄 — 저장이 왜 잠겼는지 한 줄로. 가운데 정렬은 바깥 div가 한다(TDS Text는 textAlign을 걸러낸다). */}
      {wheel.note !== null && (
        <div style={{ textAlign: 'center', marginTop: 12 }}>
          <Text typography="st12" color="grey600">
            {wheel.note}
          </Text>
        </div>
      )}
      <ErrorMessage message={error} />
      <FilledButton
        display="block"
        style={{ marginTop: 20 }}
        loading={busy}
        disabled={!wheel.valid}
        onClick={() => onPick(wheel.seconds)}
      >
        저장
      </FilledButton>
      {current !== null && (
        <Button display="block" variant="weak" style={{ marginTop: 12 }} disabled={busy} onClick={() => onPick(null)}>
          회당 시간 없이
        </Button>
      )}
    </Sheet>
  );
}
