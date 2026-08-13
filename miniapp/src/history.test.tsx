import { TDSMobileProvider } from '@toss/tds-mobile';
import { readFileSync } from 'node:fs';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';

import type { MonthlySection } from './api';
import { History, MonthlyRecords, formatMonthTitle, formatRecordDate } from './screens/History';
import { graph, userAgent } from './test-fixtures';
import { monthLabelPositions } from './ui';

/**
 * 월 라벨 배치 — 서버가 준 `monthLabels`(주 인덱스 + "M월")를 잔디 격자 위 픽셀 자리로 옮긴다.
 * 웹 `ContributionGraph.vue`는 CSS 그리드의 `gridColumnStart`가 열을 맞춰 주지만, 미니앱 격자는
 * flex + 고정 칸이라 열 자리를 직접 계산해야 한다 — 그 계산만 꺼내 계측한다.
 */
describe('월 라벨 배치', () => {
  const at = (...weekIndexes: number[]) => weekIndexes.map((weekIndex) => ({ weekIndex, label: `${weekIndex}월` }));

  it('라벨이 없으면 아무것도 안 그린다 — 빈 그래프(가입 직후)가 여기로 온다', () => {
    expect(monthLabelPositions([], 11)).toEqual([]);
  });

  it('주 인덱스를 칸 폭 + 간격으로 환산한다 — 격자와 같은 산수를 써야 라벨이 그 열 위에 선다', () => {
    // 칸 11 + gap 3 = 주당 14px. 0주는 왼쪽 끝, 4주는 56px.
    expect(monthLabelPositions(at(0, 4), 11)).toEqual([
      { label: '0월', left: 0 },
      { label: '4월', left: 56 },
    ]);
  });

  it('직전 라벨과 너무 가까우면 버린다 — 그래프가 월말에서 시작하면 첫 두 라벨이 한 칸 차라 겹쳐 읽힌다', () => {
    expect(monthLabelPositions(at(0, 1), 11)).toEqual([{ label: '0월', left: 0 }]);
  });

  it('간격은 버린 라벨이 아니라 **남긴 라벨** 기준으로 잰다 — 입력 기준으로 재면 촘촘한 구간이 통째로 사라진다', () => {
    // left = 0·14·28·42·56. 남긴 것 기준이면 0·28·56이 살아남는다(버린 것 기준이면 0 하나만 남는다).
    expect(monthLabelPositions(at(0, 1, 2, 3, 4), 11).map((m) => m.left)).toEqual([0, 28, 56]);
  });
});

/** 기록 화면 — 잔디 위 월 라벨과 아래 범례가 실제로 그려지는지(계산만 맞고 배선이 없으면 화면은 그대로다). */
describe('기록 화면', () => {
  const markup = renderToStaticMarkup(
    <TDSMobileProvider userAgent={userAgent}>
      <History graph={graph} />
    </TDSMobileProvider>,
  );

  it('격자 위에 월 라벨을 그린다 — 서버가 실어 주는데 안 그려서 잔디가 언제인지 알 수 없었다', () => {
    expect(markup).toContain('8월');
  });

  it('격자 아래에 색 범례를 둔다 — 색 농도가 무슨 뜻인지 화면 어디에도 없었다', () => {
    expect(markup).toContain('적게');
    expect(markup).toContain('많이');
  });

  it('수동 기록(테두리 칸) 설명도 함께 둔다 — 범례 없이는 테두리가 오류처럼 보인다', () => {
    expect(markup).toContain('직접 채움');
  });

  it('가로 스크롤을 손대지 않는다 — weeks[0]이 최신이라 초기 위치(왼쪽 끝)가 이미 오늘이다', () => {
    // 마운트 이펙트는 정적 렌더에 안 잡히니 소스로 계측한다. 오른쪽 끝으로 밀면 1년 전 빈 잔디가 뜬다.
    expect(readFileSync(new URL('./screens/History.tsx', import.meta.url), 'utf8')).not.toContain('scrollLeft');
  });
});

/**
 * 날짜·월 머리글 포맷 — 표기 로직만 꺼내 계측한다(요일은 `Date`가 계산하므로 경계에서 틀리기 쉽다).
 * 웹 `MonthlyRecords.vue`와 같은 말을 쓰되 폭이 좁아 연도는 일자에서 뺀다.
 */
describe('기록 목록 포맷', () => {
  it('일자는 "MM-DD (요일)" — 연도는 월 머리글이 이미 말해 준다', () => {
    expect(formatRecordDate('2026-08-14')).toBe('08-14 (금)');
  });

  it('한 자리 월·일도 0을 유지한다 — 자리수가 흔들리면 오른쪽 시간 열이 들쭉날쭉해진다', () => {
    expect(formatRecordDate('2026-01-05')).toBe('01-05 (월)');
  });

  it('월 머리글은 "YYYY년 M월" — 월의 앞 0은 뗀다(사람이 읽는 자리)', () => {
    expect(formatMonthTitle('2026-08')).toBe('2026년 8월');
    expect(formatMonthTitle('2026-01')).toBe('2026년 1월');
  });
});

/** 잔디 아래 월별 기록 — 화면 조립부. 마운트 이펙트가 안 도는 하니스라 순수 컴포넌트로 꺼내 렌더한다. */
describe('월별 기록 목록', () => {
  const months: MonthlySection[] = [
    {
      month: '2026-08',
      totalSeconds: 45_000,
      days: [
        { date: '2026-08-14', totalSeconds: 5_400, bookTitles: ['미움받을 용기', '사피엔스'], manuallyFilled: false },
        { date: '2026-08-09', totalSeconds: 1_200, bookTitles: [], manuallyFilled: true },
      ],
    },
    { month: '2026-07', totalSeconds: 3_600, days: [] },
  ];

  const markup = renderToStaticMarkup(
    <TDSMobileProvider userAgent={userAgent}>
      <MonthlyRecords months={months} />
    </TDSMobileProvider>,
  );

  it('월 머리글에 그 달 총 독서 시간을 함께 둔다 — 잔디만으론 "그 달에 얼마나"를 못 읽는다', () => {
    expect(markup).toContain('2026년 8월');
    expect(markup).toContain('12시간 30분');
  });

  it('일자마다 날짜·읽은 시간을 그린다 — 사용자가 요청한 "언제 얼마나"', () => {
    expect(markup).toContain('08-14 (금)');
    expect(markup).toContain('1시간 30분');
  });

  it('그날 읽은 책 제목을 쉼표로 잇는다 — "무슨 책"이 이 화면의 나머지 절반이다', () => {
    expect(markup).toContain('미움받을 용기, 사피엔스');
  });

  it('책 미지정 세션만 있는 날도 행을 남긴다 — 읽은 시간은 있는데 행이 사라지면 합계가 안 맞아 보인다', () => {
    expect(markup).toContain('08-09 (일)');
    expect(markup).toContain('20분');
  });

  it('여러 달을 한 화면에 이어 붙인다 — 월 넘기기 버튼 없이 스크롤로 과거를 본다(A안)', () => {
    expect(markup).toContain('2026년 7월');
  });

  it('기록이 없으면 안내를 대신 둔다 — 가입 직후 잔디 아래가 통째로 비어 고장처럼 보였다', () => {
    const empty = renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <MonthlyRecords months={[]} />
      </TDSMobileProvider>,
    );
    expect(empty).toContain('아직 독서 기록이 없어요');
  });
});
