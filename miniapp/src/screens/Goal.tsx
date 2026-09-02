import { Button, Wheel } from '@toss/tds-mobile';
import { useState } from 'react';

import { setGoal, setStudyGoal } from '../api';
import type { TimerMode } from '../App';
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
 * 고른 값의 일주일 환산 한 줄 — 하루치 숫자 하나로는 크기가 안 잡힌다("10분"은 하찮게 읽히지만
 * "1시간 10분"은 쌓인 것처럼 읽힌다).
 *
 * <p>0 이하면 문장 자체가 없다 — 「0초씩 쌓여요」는 목표를 지운 사람에게 할 말이 아니다.
 */
export function weeklyLine(seconds: number, variant: TimerMode = 'reading'): string | null {
  if (seconds <= 0) return null;
  const week = formatDuration(seconds * 7);
  // 「쌓여요」는 이월 어휘다 — 공부는 못 채운 시간이 다음 날로 넘어가지 않아 그 말이 거짓이 된다.
  return variant === 'study' ? `일주일이면 ${week}을 공부하는 셈이에요` : `일주일이면 ${week}씩 쌓여요`;
}

/**
 * 고른 목표를 <b>어느 문으로</b> 보내는가 — 두 목표는 서버 원장이 갈려 있어 문도 다르다.
 *
 * <p>화면 밖으로 꺼낸 이유는 늘 같다: 하니스가 정적 렌더라 「저장」 클릭이 안 돌아(T-149), 이 분기가
 * 컴포넌트 클로저 안에 있으면 <b>계측할 방법이 소스 grep밖에 없다</b>. 실패해도 조용한 자리다 —
 * 잘못 보내면 공부 목표가 독서 목표를 덮어쓰고 `ReadingGoalChange` 원장까지 오염시킨다(서버는 200을 준다).
 */
export function saveGoal(variant: TimerMode, seconds: number): Promise<void> {
  return variant === 'study' ? setStudyGoal(seconds).then(() => {}) : setGoal(seconds);
}

/**
 * 「목표 없이 지내기」가 서는 조건 — 공부이고 <b>지울 목표가 있을 때만</b>.
 *
 * <p>독서엔 이 문이 없다: 독서의 0은 「목표 없음」이 아니라 이월·부채 원장이 깨지는 값이다.
 *
 * <p>`selected`가 아니라 `current`를 본다 — 휠을 돌리는 동안 버튼이 나타났다 사라지면 안 된다.
 * 화면 밖으로 꺼낸 이유는 `saveGoal`과 같다: 하니스가 정적 렌더라 이 조건을 클로저 안에 두면
 * 계측할 방법이 소스 grep밖에 없다(T-149).
 */
export function showClearGoal(variant: TimerMode, current: number): boolean {
  return variant === 'study' && current > 0;
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
  variant = 'reading',
  onSaved,
  onSkip,
}: {
  current: number;
  firstRun: boolean;
  /**
   * 어느 목표를 정하는가 — 휠·밴드·레이아웃·버튼은 <b>전부 공유</b>하고 문구와 저장 함수만 갈린다.
   * 파랑은 공짜다: 밴드(`--adaptiveBlue50`)·주간 줄(`blue700`)이 토큰이라 `body.study-mode`가 칠한다.
   */
  variant?: TimerMode;
  onSaved: () => void;
  onSkip: () => void;
}) {
  const study = variant === 'study';
  const [selected, setSelected] = useState(() => initialGoalSelection(firstRun, current));
  /** 휠은 비제어 컴포넌트라 시작 칸만 첫 렌더에서 한 번 읽는다 — 이후 값은 onChange가 selected로 되돌린다. */
  const [initialWheel] = useState(() => wheelIndices(initialGoalSelection(firstRun, current)));
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  /** 값을 받는다 — 휠 값과 0(해제) 두 문이 한 몸을 쓴다. */
  const save = (seconds: number) => {
    setBusy(true);
    setError(null);
    saveGoal(variant, seconds)
      .then(onSaved)
      .catch((e: Error) => setError(e.message))
      .finally(() => setBusy(false));
  };

  // 공부엔 온보딩이 없어 `firstRun` 분기가 오지 않는다(진입은 홈 손잡이·설정뿐).
  return (
    <Screen title={study ? '공부 하루 목표' : firstRun ? '하루에 얼마나 읽을까요?' : '하루 목표 바꾸기'}>
      {/* 화면 한 장을 세로로 다 쓴다 — 휠은 가운데, 버튼은 바닥. 예전엔 전부 위에 몰려 「돌아가기」가
          화면 중턱(390×844에서 y=477)에 떠 있었고 아래 43%가 빈 채였다.
          120 = Screen 상단 패딩 24 + 제목 줄 ≈56 + 하단 패딩 40. 제목 줄까지 빼는 이유: 덜 빼면
          그만큼 화면 밖으로 밀려 버튼이 스크롤해야 보인다(넘게 빼면 버튼이 조금 위에 설 뿐 무해하다).
          dvh 미지원 브라우저는 calc가 통째로 무효라 minHeight가 사라지고 예전 상단 몰림으로 강등된다. */}
      <div style={{ display: 'flex', flexDirection: 'column', minHeight: 'calc(100dvh - 120px)' }}>
      {/* 주의: 공부에 독서 문구(「다음 날로 넘어가요」)를 그대로 쓰면 <b>거짓말</b>이다 — 공부엔 이월이 없다. */}
      <Text typography="st11" color="grey600" style={{ display: 'block', marginBottom: 20 }}>
        {study
          ? '매일 이만큼 공부하는 걸 목표로 해요. 못 채워도 다음 날로 넘어가지 않아요.'
          : firstRun
            ? '매일 이만큼씩 쌓여요. 못 채운 시간은 다음 날로 넘어가니 부담 없는 값으로 시작해 보세요.'
            : '매일 이만큼씩 쌓여요. 못 채운 시간은 다음 날로 넘어가요.'}
      </Text>

      {/* 고르는 자리는 세로 가운데 — 위아래 남는 공간을 `auto`가 반씩 먹는다. */}
      <div style={{ margin: 'auto 0' }}>
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
            // ⚠️ 시안·설계는 `rgba(110,138,106,.12)`인데 **토큰으로 바꿨다**: 종이색 위 합성값이
            // ≈`#EAECE4`로 이 토큰(`#E7EEE2`)과 사실상 같고, 토큰이라야 밤 테마를 함께 탄다.
            // (이탈이라는 사실이 어디에도 없어 처음부터 그랬던 것처럼 읽혔다 — 독립 리뷰 적발.)
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

      {/* 고른 값의 일주일 환산 — 휠이 돌면 함께 바뀐다(selected가 단일 소스).
          주의: 가운데 정렬은 **바깥 div**가 한다 — TDS `Text`는 넘긴 style에서 `textAlign`을 걸러내고
          `display`도 자기 값(`inline-block`)으로 덮어, 인라인 스타일엔 `margin-top`만 남는다
          (목 모드 실측 2026-08-29: computed `text-align: start`로 왼쪽에 붙어 있었다). */}
      {weeklyLine(selected, variant) !== null && (
        <div style={{ textAlign: 'center', marginTop: 12 }}>
          <Text typography="st12" color="blue700">
            {weeklyLine(selected, variant)}
          </Text>
        </div>
      )}

      {/* 미리 골라 둔 값이 왜 이렇게 작은지 한 줄로 — 없으면 "이 앱은 나를 얕본다"로 읽히고,
          있으면 첫날 달성을 밟게 하려는 배려로 읽힌다. 목표를 바꾸러 온 사람에겐 할 말이 아니다. */}
      {firstRun && (
        <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 12 }}>
          가볍게 시작 — {formatDuration(FIRST_RUN_GOAL_SECONDS)}이면 오늘 안에 한 번 달성할 수 있어요.
          언제든 늘릴 수 있어요.
        </Text>
      )}
      </div>

      {/* 바닥에 붙는 손잡이 — 위 가운데 블록과의 간격은 남는 공간이 알아서 벌린다(marginTop은 최소값). */}
      <div>
        <ErrorMessage message={error} />

        {/* 0시간 0분 저장은 서버가 허용하는 「목표 없음」이지만, 휠을 끝까지 내린 실수일 가능성이 더 높다. */}
        {/* 이 화면의 주 동작 하나 — 채움이다(시안 2e). 홈은 탭바 원이 그 역할을 한다. */}
        <FilledButton
          display="block"
          style={{ marginTop: 20 }}
          loading={busy}
          disabled={selected === 0}
          onClick={() => save(selected)}
        >
          {firstRun ? '이걸로 시작하기' : '저장'}
        </FilledButton>
        {/* 휠 0 가드는 그대로다 — 「목표 없음」은 실수로 못 밟게 별도 문으로 낸다. 저장이 끝나면
            onSaved가 대시보드를 다시 읽어 홈이 「목표 정하기」로 돌아간다(배선 추가 없음). */}
        {showClearGoal(variant, current) && (
          <Button display="block" variant="weak" style={{ marginTop: 12 }} disabled={busy} onClick={() => save(0)}>
            목표 없이 지내기
          </Button>
        )}
        {/* firstRun에만 남는다 — 「나중에 정할래요」는 건너뛰기라는 선택이고(첫 실행엔 돌아갈 화면이
            아직 없다), 비-firstRun의 「돌아가기」는 네이티브 뒤로가기와 중복이라 걷었다(T-220). */}
        {firstRun && (
          <Button display="block" variant="weak" style={{ marginTop: 12 }} disabled={busy} onClick={onSkip}>
            나중에 정할래요
          </Button>
        )}
      </div>
      </div>
    </Screen>
  );
}
