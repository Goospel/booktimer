import { useEffect, useState } from 'react';

import type { StudyCalendarDay } from '../api';
import { fetchStudyCalendar, setStudyCheck } from '../api';
import { ErrorMessage, SERIF_VALUE, Screen, Text } from '../ui';

/**
 * 공부 일정 달력 — <b>지킨 날을 사용자가 직접 표시하는</b> 화면.
 *
 * <p>기록 탭의 잔디와 <b>네 축이 전부 다르다</b>: 형태(숫자 달력 vs 무숫자 격자) · 마크(원 vs 사각) ·
 * 색(파랑 vs 세이지) · 이름(일정 vs 기록). 두 화면이 같은 것을 말한다고 오해되면 「측정하면 저절로
 * 지킨 날이 된다」는 거짓 기대가 생기는데, 이 화면의 요점은 정확히 그 반대다.
 *
 * <p>서버가 자동으로 채워 주는 것은 <b>「그날 측정이 있었나」(점)</b>까지다. 목표 대비 달성 배지를
 * 그리지 않는 이유는 공부 목표에 변경 이력이 없어서다 — 과거를 현재 목표로 판정하면 목표를 올린
 * 날 과거 달성일이 소급 취소된다.
 */

/** 무기록 → 지킴 → 못 지킴 → 무기록. 탭 한 번에 한 칸이고, 세 번이면 제자리다(되돌릴 길이 늘 있다). */
export function cycleCheck(kept: boolean | null): boolean | null {
  if (kept === null) return true;
  return kept ? false : null;
}

/** `2026-09` → `2026년 9월`. 0 채움 없이 읽는 말로 쓴다. */
export function monthTitle(year: number, month: number): string {
  return `${year}년 ${month}월`;
}

/** `YYYY-MM-DD` — 로컬 달력 좌표라 UTC 변환을 태우지 않는다(태우면 자정 근처에서 하루가 밀린다). */
function iso(year: number, month: number, day: number): string {
  return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
}

/**
 * 그 달의 격자 — 앞쪽 요일 오프셋만큼 {@code null}을 채우고 그 뒤로 날짜를 잇는다.
 *
 * <p>말일은 <b>다음 달 0일</b>로 구한다(하드코딩한 28·30·31이면 윤년 2월에서 하루가 사라진다).
 */
export function calendarCells(year: number, month: number): (string | null)[] {
  const offset = new Date(year, month - 1, 1).getDay();
  const lastDay = new Date(year, month, 0).getDate();
  const cells: (string | null)[] = Array.from({ length: offset }, () => null);
  for (let day = 1; day <= lastDay; day++) cells.push(iso(year, month, day));
  return cells;
}

/** 만질 수 있는 날인가 — 오늘까지다. 과거엔 하한이 없다(지난달 정리는 정당한 사용). */
export function tappable(dateIso: string, todayIso: string): boolean {
  return dateIso <= todayIso;
}

/** 기기 기준 오늘 — 서버가 유저 타임존으로 다시 판정하므로(400) 어긋나도 거짓 저장은 없다. */
function todayIso(): string {
  const now = new Date();
  return iso(now.getFullYear(), now.getMonth() + 1, now.getDate());
}

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

/** 셀 안의 원 지름 — 44px 히트영역 안에 여백을 남기고 앉는다. */
const MARK = 34;

type CellState = 'kept' | 'missed' | 'none';

function stateOf(kept: boolean | null): CellState {
  if (kept === null) return 'none';
  return kept ? 'kept' : 'missed';
}

const STATE_LABEL: Record<CellState, string> = { kept: ', 지킴', missed: ', 못 지킴', none: '' };

/**
 * 한 달 격자 — <b>데이터를 받아 그리기만</b> 한다(조회·저장은 위가 든다).
 *
 * <p>화면에서 떼어 둔 이유는 늘 같다: 하니스가 정적 렌더라 effect를 못 돌린다(T-149). 그려진 꼴은
 * 여기서 재고, 탭 왕복은 목 모드 실브라우저가 잰다.
 */
export function CalendarGrid({
  year,
  month,
  days,
  todayIso: today,
  busyDate,
  onPick,
}: {
  year: number;
  month: number;
  /** 서버가 준 <b>데이터 있는 날만</b>(희소). 나머지 날은 무기록·측정 0으로 선다. */
  days: StudyCalendarDay[];
  todayIso: string;
  /** 왕복 중인 날 — 그 칸만 잠근다(연타 방지). 화면 전체를 잠그지 않는다. */
  busyDate: string | null;
  onPick: (date: string, kept: boolean | null) => void;
}) {
  const byDate = new Map(days.map((d) => [d.date, d]));

  return (
    <div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', marginTop: 8 }}>
        {WEEKDAYS.map((label) => (
          <Text key={label} typography="st12" color="grey600" style={{ display: 'block', textAlign: 'center' }}>
            {label}
          </Text>
        ))}
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)' }}>
        {calendarCells(year, month).map((date, index) => {
          if (date === null) return <span key={`pad-${index}`} aria-hidden="true" />;

          const day = Number(date.slice(8));
          const entry = byDate.get(date);
          const kept = entry?.kept ?? null;
          const state = stateOf(kept);
          const studied = (entry?.studiedSeconds ?? 0) > 0;
          const open = tappable(date, today);
          const busy = busyDate === date;

          return (
            <button
              key={date}
              type="button"
              // 계측용 표지 — TDS emotion 클래스 사이에서 「어느 날, 무슨 상태」를 집을 손잡이가 없다.
              data-cal-day={date}
              data-cal-state={state}
              aria-label={`${day}일${STATE_LABEL[state]}`}
              // `disabled`가 아니라 `aria-disabled`다 — 탭바 잠금과 같은 이유(진짜로 잠그면 이유를
              // 말할 기회가 없다). 미래 칸은 아래 onClick이 조용히 흘린다.
              aria-disabled={open ? undefined : true}
              onClick={() => open && !busy && onPick(date, cycleCheck(kept))}
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                gap: 2,
                minHeight: 44,
                padding: 0,
                border: 'none',
                background: 'transparent',
                opacity: open ? (busy ? 0.5 : 1) : 0.35,
                cursor: open ? 'pointer' : 'default',
              }}
            >
              <span
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  width: MARK,
                  height: MARK,
                  borderRadius: '50%',
                  boxSizing: 'border-box',
                  // 채운 원 = 지킴 · 테두리 원 = 못 지킴 · 맨 숫자 = 무기록. 세 꼴이 형태로 갈린다.
                  background: state === 'kept' ? 'var(--adaptiveBlue500, #6E8A6A)' : 'transparent',
                  border: state === 'missed' ? '1.5px solid var(--adaptiveBlue300, #B6C9AE)' : 'none',
                  color: state === 'kept' ? '#FFFFFF' : 'var(--adaptiveGrey700, #57534A)',
                  ...SERIF_VALUE,
                  fontSize: 15,
                  // 세리프 기본 굵기를 덮는다 — 지킨 날만 굵어야 「채운 원」이 형태로 한 번 더 말한다.
                  fontWeight: state === 'kept' ? 700 : 400,
                }}
              >
                {day}
              </span>
              {/* 측정 있음 — <b>자동 정보</b>지 판정이 아니다(그래서 원과 다른 꼴로 쓴다). */}
              <span
                data-cal-dot={studied ? '' : undefined}
                aria-hidden="true"
                style={{
                  width: 3,
                  height: 3,
                  borderRadius: '50%',
                  background: studied ? 'var(--adaptiveBlue300, #B6C9AE)' : 'transparent',
                }}
              />
            </button>
          );
        })}
      </div>
    </div>
  );
}

/** 세 표식의 뜻 — 기본 이모지가 아니라 같은 도형을 작게 그려 말한다. */
export function CalendarLegend() {
  const item = (mark: React.ReactNode, label: string) => (
    <span key={label} style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
      {mark}
      <Text typography="st12" color="grey600">
        {label}
      </Text>
    </span>
  );

  return (
    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12, marginTop: 12 }}>
      {item(
        <span
          aria-hidden="true"
          style={{ width: 10, height: 10, borderRadius: '50%', background: 'var(--adaptiveBlue500, #6E8A6A)' }}
        />,
        '지킨 날',
      )}
      {item(
        <span
          aria-hidden="true"
          style={{
            width: 10,
            height: 10,
            borderRadius: '50%',
            boxSizing: 'border-box',
            border: '1.5px solid var(--adaptiveBlue300, #B6C9AE)',
          }}
        />,
        '못 지킨 날',
      )}
      {item(
        <span
          aria-hidden="true"
          style={{ width: 3, height: 3, borderRadius: '50%', background: 'var(--adaptiveBlue300, #B6C9AE)' }}
        />,
        '측정 기록',
      )}
    </div>
  );
}

/** `‹`·`›` 화살표 — 글자가 아니라 획이라 서체에 흔들리지 않는다. */
function Chevron({ direction }: { direction: 'prev' | 'next' }) {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d={direction === 'prev' ? 'M15 5 8 12l7 7' : 'M9 5l7 7-7 7'}
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

/**
 * 공부 모드 탭바의 「일정」 — 월 달력 하나가 화면 전부다.
 *
 * <p>도달 경로가 곧 게이트다(공부 탭바로만 온다) — 그래서 여기서 모드를 다시 따지지 않는다.
 *
 * <p>낙관 갱신을 하지 않는다: 체크는 드문 동작이라 왕복 대기가 「눌렀는데 되돌아가는」 위험보다 싸다.
 * 대신 그 칸만 잠가(`busyDate`) 연타를 막는다.
 */
export function StudyCalendar({ onError }: { onError: (error: Error) => void }) {
  const today = todayIso();
  const [year, setYear] = useState(() => Number(today.slice(0, 4)));
  const [month, setMonth] = useState(() => Number(today.slice(5, 7)));
  const [days, setDays] = useState<StudyCalendarDay[] | null>(null);
  const [busyDate, setBusyDate] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const monthParam = `${year}-${String(month).padStart(2, '0')}`;
  /** 다음 달이 미래면 › 를 잠근다 — 아직 오지 않은 달엔 볼 것도 만질 것도 없다. */
  const atCurrentMonth = monthParam >= today.slice(0, 7);

  useEffect(() => {
    let alive = true;
    setDays(null);
    fetchStudyCalendar(monthParam)
      .then((r) => alive && setDays(r.days))
      .catch((e: Error) => {
        if (e.name === 'UnauthorizedError') onError(e);
        else if (alive) setError(e.message);
      });
    return () => {
      alive = false;
    };
  }, [monthParam, onError]);

  /** 그 달의 앞뒤로 한 칸 — 12월↔1월을 넘을 때 연도가 함께 움직인다. */
  const shift = (step: number) => {
    const next = new Date(year, month - 1 + step, 1);
    setYear(next.getFullYear());
    setMonth(next.getMonth() + 1);
  };

  const pick = (date: string, kept: boolean | null) => {
    setBusyDate(date);
    setError(null);
    setStudyCheck(date, kept)
      .then(() =>
        setDays((prev) => {
          if (prev === null) return prev;
          const rest = prev.filter((d) => d.date !== date);
          if (kept === null) {
            // 무기록으로 되돌렸어도 그날 측정이 있었으면 점은 남아야 한다(자동 정보는 판정과 별개다).
            const studied = prev.find((d) => d.date === date)?.studiedSeconds ?? 0;
            return studied > 0 ? [...rest, { date, studiedSeconds: studied, kept: null }] : rest;
          }
          const studied = prev.find((d) => d.date === date)?.studiedSeconds ?? 0;
          return [...rest, { date, studiedSeconds: studied, kept }];
        }),
      )
      .catch((e: Error) => {
        if (e.name === 'UnauthorizedError') onError(e);
        else setError(e.message);
      })
      .finally(() => setBusyDate(null));
  };

  const navButton = (direction: 'prev' | 'next', disabled: boolean) => (
    <button
      type="button"
      aria-label={direction === 'prev' ? '지난달 보기' : '다음달 보기'}
      aria-disabled={disabled ? true : undefined}
      onClick={() => !disabled && shift(direction === 'prev' ? -1 : 1)}
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        width: 44,
        height: 44,
        border: 'none',
        background: 'transparent',
        color: 'var(--adaptiveGrey700, #57534A)',
        opacity: disabled ? 0.3 : 1,
        cursor: disabled ? 'default' : 'pointer',
      }}
    >
      <Chevron direction={direction} />
    </button>
  );

  return (
    <Screen title="공부 일정">
      <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 4, wordBreak: 'keep-all' }}>
        지킨 날을 눌러 표시해요. 한 번 더 누르면 못 지킨 날, 또 누르면 표시가 지워져요.
      </Text>

      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, marginTop: 12 }}>
        {navButton('prev', false)}
        <Text typography="st10" fontWeight="bold" style={{ ...SERIF_VALUE, fontSize: 19, minWidth: 120, textAlign: 'center' }}>
          {monthTitle(year, month)}
        </Text>
        {navButton('next', atCurrentMonth)}
      </div>

      <CalendarGrid
        year={year}
        month={month}
        days={days ?? []}
        todayIso={today}
        busyDate={busyDate}
        onPick={pick}
      />

      <CalendarLegend />
      <ErrorMessage message={error} />
    </Screen>
  );
}
