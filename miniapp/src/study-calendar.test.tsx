import { TDSMobileProvider } from '@toss/tds-mobile';
import { readFileSync } from 'node:fs';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';

import type { StudyCalendarDay } from './api';
import {
  CalendarGrid,
  CalendarLegend,
  calendarCells,
  cycleCheck,
  monthTitle,
  tappable,
} from './screens/StudyCalendar';
import { userAgent } from './test-fixtures';

/**
 * 공부 일정 달력 — 순환·격자·탭 가능 판정은 <b>순수 함수</b>로 꺼내 계측한다.
 *
 * <p>하니스가 정적 렌더라 클릭·effect가 안 돈다(T-149) — 그래서 「탭하면 어떻게 되나」는 함수로,
 * 「그 상태가 어떻게 보이나」는 마크업으로 나눠 잰다. 실제 탭 왕복은 목 모드 실브라우저가 게이트다.
 */

describe('cycleCheck — 탭 한 번에 한 칸씩 도는 3상태', () => {
  it('무기록 → 지킴 → 못 지킴 → 무기록으로 제자리에 돌아온다', () => {
    expect(cycleCheck(null)).toBe(true);
    expect(cycleCheck(true)).toBe(false);
    expect(cycleCheck(false)).toBeNull();
  });

  it('세 번 돌면 처음 값이다 — 되돌릴 길이 없는 상태가 없다', () => {
    expect(cycleCheck(cycleCheck(cycleCheck(null)))).toBeNull();
  });
});

describe('calendarCells — 요일에 맞춘 격자', () => {
  it('1일의 요일만큼 앞을 비운다(2026-09-01은 화요일 → 앞 두 칸)', () => {
    const cells = calendarCells(2026, 9);

    expect(cells.slice(0, 2)).toEqual([null, null]);
    expect(cells[2]).toBe('2026-09-01');
    expect(cells).toHaveLength(2 + 30);
    expect(cells.at(-1)).toBe('2026-09-30');
  });

  it('31일 달도 마지막 날까지 낸다(2026-08은 토요일 시작 → 앞 여섯 칸)', () => {
    const cells = calendarCells(2026, 8);

    expect(cells.slice(0, 6)).toEqual([null, null, null, null, null, null]);
    expect(cells[6]).toBe('2026-08-01');
    expect(cells.at(-1)).toBe('2026-08-31');
  });

  it('윤년 2월은 29일까지다 — 하드코딩한 28이면 마지막 하루가 사라진다', () => {
    const cells = calendarCells(2028, 2);

    expect(cells.at(-1)).toBe('2028-02-29');
    expect(cells.filter((c) => c !== null)).toHaveLength(29);
  });

  it('1일이 일요일이면 앞을 안 비운다 — 오프셋이 항상 붙지 않는다', () => {
    expect(calendarCells(2026, 3)[0]).toBe('2026-03-01');
  });
});

describe('tappable — 오늘까지만 만질 수 있다', () => {
  it('오늘은 탭할 수 있다 — 경계는 「오늘 초과」다', () => {
    expect(tappable('2026-09-01', '2026-09-01')).toBe(true);
  });

  it('내일은 못 만진다(서버도 400으로 막는 이중 방어)', () => {
    expect(tappable('2026-09-02', '2026-09-01')).toBe(false);
  });

  it('과거엔 하한이 없다 — 지난달을 나중에 정리하는 건 정당한 사용이다', () => {
    expect(tappable('2025-01-01', '2026-09-01')).toBe(true);
  });
});

describe('monthTitle', () => {
  it('연·월을 한국어로 읽는다(0 채움 없이)', () => {
    expect(monthTitle(2026, 9)).toBe('2026년 9월');
    expect(monthTitle(2026, 12)).toBe('2026년 12월');
  });
});

/**
 * 셀 마크업 — 세 상태가 <b>서로 다른 표식</b>을 달아야 화면이 무엇을 말하는지 계측할 수 있다.
 * TDS emotion 클래스 사이에서 셀을 집을 손잡이가 없어 `data-cal-*`를 쓴다(`data-book-title` 선례).
 */
describe('달력 격자 렌더', () => {
  const days: StudyCalendarDay[] = [
    { date: '2026-09-01', studiedSeconds: 3600, kept: true },
    { date: '2026-09-02', studiedSeconds: 0, kept: false },
    { date: '2026-09-03', studiedSeconds: 1200, kept: null },
  ];

  const grid = (todayIso = '2026-09-10') =>
    renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <CalendarGrid year={2026} month={9} days={days} todayIso={todayIso} busyDate={null} onPick={() => {}} />
      </TDSMobileProvider>,
    );

  const cell = (markup: string, date: string) => {
    const at = markup.indexOf(`data-cal-day="${date}"`);
    expect(at).toBeGreaterThan(-1);
    const from = markup.lastIndexOf('<button', at);
    return markup.slice(from, markup.indexOf('</button>', at));
  };

  it('지킴·못 지킴·무기록이 서로 다른 상태로 그려진다', () => {
    const markup = grid();

    expect(cell(markup, '2026-09-01')).toContain('data-cal-state="kept"');
    expect(cell(markup, '2026-09-02')).toContain('data-cal-state="missed"');
    expect(cell(markup, '2026-09-03')).toContain('data-cal-state="none"');
  });

  it('데이터가 없는 날도 무기록으로 선다 — 달력은 빈 칸 없이 한 달을 다 그린다', () => {
    const markup = grid();

    expect(cell(markup, '2026-09-20')).toContain('data-cal-state="none"');
    expect(markup.match(/data-cal-day=/g)).toHaveLength(30);
  });

  it('상태를 소리로도 말한다 — 색만으로는 색약에게 안 읽힌다', () => {
    const markup = grid();

    expect(cell(markup, '2026-09-01')).toContain('1일, 지킴');
    expect(cell(markup, '2026-09-02')).toContain('2일, 못 지킴');
    expect(cell(markup, '2026-09-03')).toContain('aria-label="3일"');
  });

  /** 점은 <b>자동 정보</b>다(그날 측정이 있었나) — 판정(원)과 다른 것을 말한다. */
  it('측정이 있었던 날에만 점이 붙는다', () => {
    const markup = grid();

    expect(cell(markup, '2026-09-01')).toContain('data-cal-dot');
    expect(cell(markup, '2026-09-03')).toContain('data-cal-dot');
    // 판정만 있고 측정이 없는 날엔 점이 없다 — 원과 점이 같은 것을 말하지 않는다는 증거다.
    expect(cell(markup, '2026-09-02')).not.toContain('data-cal-dot');
  });

  it('미래 날짜는 흐리고 못 눌린다 — 서버 400 앞에서 화면이 먼저 막는다', () => {
    const markup = grid('2026-09-10');

    expect(cell(markup, '2026-09-11')).toContain('aria-disabled="true"');
    // 짝 단언 — 오늘까지는 열려 있다.
    expect(cell(markup, '2026-09-10')).not.toContain('aria-disabled="true"');
  });

  it('셀 터치 영역이 손가락 최소치(44px)를 넘는다', () => {
    expect(cell(grid(), '2026-09-01')).toContain('min-height:44px');
  });

  /**
   * <b>달을 옮기면 앞 달의 실패 문구를 걷는가</b>(독립 리뷰 W-4).
   *
   * <p>안 걷으면 8월 조회가 실패한 뒤 9월이 멀쩡히 떠도 그 에러가 화면에 남아, 방금 성공한 달을
   * 실패한 것처럼 말한다. 에러 상태는 화면 안에 있고 하니스는 effect를 못 돌리므로(T-149) 소스로 잰다 —
   * 조회 effect가 `setError(null)`을 <b>실제로 부르는지</b>가 유일하게 관측 가능한 형태다.
   */
  it('달을 옮기는 조회 effect가 앞 달의 에러를 걷는다', () => {
    const src = readFileSync(new URL('./screens/StudyCalendar.tsx', import.meta.url), 'utf8')
      .replace(/\/\*[\s\S]*?\*\//g, '')
      .replace(/\/\/.*/g, '');
    // 그 effect만 본다 — 통짜 검색이면 탭 저장 경로(`pick`)의 setError(null)에 걸려 늘 통과한다.
    const from = src.indexOf('useEffect(() => {');
    const to = src.indexOf('[monthParam, onError]);');
    expect(from).toBeGreaterThan(-1);
    expect(to).toBeGreaterThan(from);

    expect(src.slice(from, to)).toContain('setError(null)');
  });

  it('범례가 세 표식의 뜻을 말한다 — 기본 이모지가 아니라 도형으로', () => {
    const markup = renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <CalendarLegend />
      </TDSMobileProvider>,
    );

    for (const word of ['지킨 날', '못 지킨 날', '측정 기록']) expect(markup).toContain(word);
  });
});
