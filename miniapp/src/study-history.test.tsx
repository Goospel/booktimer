import { TDSMobileProvider } from '@toss/tds-mobile';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';

import type { StudyHistoryResponse, StudyMonth } from './api';
import { Legend, StatStrip } from './screens/History';
import { StudyHistoryView, StudyMonthlyRecords } from './screens/StudyHistory';
import { graph, userAgent } from './test-fixtures';

/**
 * 공부 기록 화면 — <b>측정 사실만</b>. 판정(지킴/못 지킴)은 「일정」 탭의 몫이고, 책이 없으니
 * 표지 열·펼침도 없다. 하니스가 정적 렌더라(effect·클릭이 안 돈다) 데이터를 받아 그리기만 하는
 * `StudyHistoryView`를 잰다(T-149).
 */

const months: StudyMonth[] = [
  {
    month: '2026-09',
    totalSeconds: 5_400,
    days: [
      { date: '2026-09-02', totalSeconds: 3_600 },
      { date: '2026-09-01', totalSeconds: 1_800 },
    ],
  },
  { month: '2026-08', totalSeconds: 1_200, days: [{ date: '2026-08-31', totalSeconds: 1_200 }] },
];

const render = (node: React.ReactNode) =>
  renderToStaticMarkup(<TDSMobileProvider userAgent={userAgent}>{node}</TDSMobileProvider>);

describe('공부 기록 화면', () => {
  const data: StudyHistoryResponse = { graph, months };
  const markup = render(<StudyHistoryView data={data} />);

  it('스탯·잔디·범례·날짜 목록을 한 화면에 세운다 — 독서 기록과 같은 조각을 재사용한다', () => {
    expect(markup).toContain('>공부한 날<');
    expect(markup).toContain('>연속<');
    expect(markup).toContain('>총 시간<');
    expect(markup).toContain('적게');
    expect(markup).toContain('많이');
    expect(markup).toContain('8월'); // 잔디 위 월 라벨(픽스처 monthLabels)
    expect(markup).toContain('2026년 9월');
    expect(markup).toContain('09-02');
  });

  /**
   * 독서 전제인 네 가지가 <b>마크업에 없어야</b> 한다. 부정 단언이지만 effect·핸들러가 아니라
   * 「그려진 것」을 재므로 항상 통과가 아니다 — `History`를 그대로 재사용하면 즉시 붉어진다(T-149 밖).
   */
  it('독서 전제는 한 조각도 안 그린다 — 읽은 날·직접 채움·책 안 고른 기록·펼침 손잡이', () => {
    expect(markup).not.toContain('>읽은 날<');
    expect(markup).not.toContain('직접 채움');
    expect(markup).not.toContain('책 안 고른 기록');
    expect(markup).not.toContain('data-day-toggle');
  });

  it('기록이 없으면 안내를 대신 둔다 — 가입 직후 아래가 통째로 비어 고장처럼 보인다', () => {
    expect(render(<StudyMonthlyRecords months={[]} />)).toContain('아직 공부 기록이 없어요');
  });

  /**
   * 색 — 공부 화면의 잔디·범례·막대가 <b>토큰 경유</b>라야 `body.study-mode`가 파랑으로 갈아 끼운다.
   *
   * <p>존재가 아니라 <b>건수와 전체 인자열</b>로 잰다(T-218): 「어딘가에 토큰이 있다」는 한 자리만
   * 고쳐도 통과하지만, 세 자리가 각각 몇 개인지는 한 곳만 리터럴로 남아도 붉어진다.
   */
  it('맨 세이지 리터럴이 한 칸도 없다 — 하나라도 남으면 그 자리만 공부 모드에서 초록으로 남는다', () => {
    expect(markup).not.toMatch(/background:#(C3D9B0|94BE7F|5E9250|35662F)/);
  });

  it('하루 막대 3줄이 전부 토큰을 탄다 — 픽스처의 날 수만큼', () => {
    const bars = markup.match(/height:6px;border-radius:3px;background:var\(--grass2, #94BE7F\)/g);
    expect(bars).toHaveLength(3);
  });

  it('범례 스와치 5개가 전부 토큰을 탄다 — 농도 0~4가 한 칸도 빠짐없이', () => {
    const swatches = markup.match(/width:10px;height:10px;border-radius:2px;flex:0 0 auto;background:var\(--grass/g);
    expect(swatches).toHaveLength(5);
  });

  it('월 머리글에 그 달 합계를, 하루 줄에 그날 시간을 적는다', () => {
    // ⚠️ `>…<`로 겨눈다 — 맨 문자열이면 월 합계 「1시간 30분」이 하루의 「1시간」·「30분」을 품어
    //    두 단언이 통째로 공허해진다(history.test의 같은 함정과 한 뿌리).
    expect(markup).toContain('>1시간 30분<'); // 2026-09 월 합계
    expect(markup).toContain('>1시간<'); // 09-02
    expect(markup).toContain('>30분<'); // 09-01
    expect(markup).toContain('>수요일<'); // 09-02의 요일
  });
});

/**
 * 공유 조각의 <b>기본값 가드</b> — 독서 화면이 쓰는 인자 없는 호출이 옛 문구 그대로여야 한다.
 * (독서 렌더 불변의 나머지 절반은 `history.test.tsx` 무수정 green이 맡는다.)
 */
describe('공유 조각 기본값', () => {
  it('StatStrip은 라벨을 안 주면 「읽은 날」이다', () => {
    expect(render(<StatStrip graph={graph} />)).toContain('>읽은 날<');
  });

  it('Legend는 기본이 「직접 채움」 포함, manual=false면 뺀다', () => {
    expect(render(<Legend />)).toContain('직접 채움');
    expect(render(<Legend manual={false} />)).not.toContain('직접 채움');
  });
});
