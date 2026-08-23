import { TDSMobileProvider } from '@toss/tds-mobile';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';

// 렌더로 관측 불가능한 배선은 소스로 잰다 — `env-production.test.ts`·`home.test.tsx`와 같은 방식.
import appSource from './App.tsx?raw';

import type { BookOption } from './api';
import { ChangeBookSheet, START_TOAST_MS, StartToast, startToastMessage, startToastVisible } from './App';
import { userAgent } from './test-fixtures';

/**
 * 측정 시작 토스트 + 교체 시트 — 다른 탭에서 ▶를 누르면 <b>무슨 책으로 시작됐는지</b>를 말하고
 * 그 자리에서 바꾸게 한다(UX 감사 3f → 시안 4b·4c).
 *
 * <p>옛 배치는 `timerStartBookId`(홈 캐러셀 선택)로 조용히 시작해 놓고 <b>그 화면 어디에도 대상 책을
 * 표시하지 않았다</b>. 홈에서는 안 띄운다 — 홈의 「읽는 중」 카드가 이미 그 책을 말한다.
 *
 * <p>하니스가 정적 렌더라 클릭·effect가 안 돈다(T-149). 그래서 로직은 순수 함수로 꺼내 계측하고,
 * 컴포넌트는 직접 렌더하며, 「홈이 그 둘을 잇는가」는 소스로 잰다.
 */

const book = (id: number, title: string): BookOption => ({ id, title, coverUrl: null, author: null });

function render(node: React.ReactNode): string {
  return renderToStaticMarkup(<TDSMobileProvider userAgent={userAgent}>{node}</TDSMobileProvider>);
}

/**
 * ⚠️ 문구는 <b>조사 변형이 안 생기는 꼴</b>로 고정한다. 「『용기』로」 같은 자리에 받침 판정이 끼면
 * 「용기로 / 용기으로」가 갈리는데, 제목은 사용자 데이터라 그 판정을 이길 수 없다. 그래서 조사는
 * 제목이 아니라 <b>「측정」</b>이 받는다 — 『제목』 측정을 / 『제목』 측정으로.
 */
describe('토스트 문구 (startToastMessage)', () => {
  it('책이 있으면 제목을 『』로 감싸 말한다', () => {
    expect(startToastMessage({ book: book(1, '미움받을 용기'), changed: false })).toBe(
      '『미움받을 용기』 측정을 시작했어요',
    );
  });

  it('책 없이 시작하면 그렇게 말한다 — 빈 제목을 지어내지 않는다', () => {
    expect(startToastMessage({ book: null, changed: false })).toBe('책 없이 측정을 시작했어요');
  });

  it('바꾼 뒤에는 시작이 아니라 교체를 확인한다 — 같은 문구면 두 번 시작한 것처럼 읽힌다', () => {
    expect(startToastMessage({ book: book(1, '사피엔스'), changed: true })).toBe('『사피엔스』 측정으로 바꿨어요');
    expect(startToastMessage({ book: null, changed: true })).toBe('책 없이 측정으로 바꿨어요');
  });

  it('조사가 제목 뒤에 붙지 않는다 — 받침이 갈려도 문구가 안 깨진다', () => {
    // 받침 있음(용기)·없음(사피엔스) 둘 다 제목 바로 뒤는 공백 + 「측정」이다.
    for (const title of ['미움받을 용기', '사피엔스']) {
      expect(startToastMessage({ book: book(1, title), changed: false })).toContain(title + '』 측정을');
      expect(startToastMessage({ book: book(1, title), changed: true })).toContain(title + '』 측정으로');
    }
  });
});

/**
 * 토스트·액션 실패·잠금 안내 <b>셋이 같은 fixed 좌표</b>를 쓴다(탭바 위 8px). 상태 간 clear 배선을
 * 만드는 대신 렌더 게이트 하나로 한 장만 세운다 — 배선은 늘어날수록 빠뜨린 조합이 생긴다.
 */
describe('겹침 게이트 (startToastVisible)', () => {
  const toast = { book: null, changed: false };

  it('토스트만 있으면 선다', () => {
    expect(startToastVisible(toast, null, false, 'library')).toBe(true);
  });

  it('토스트가 없으면 안 선다', () => {
    expect(startToastVisible(null, null, false, 'library')).toBe(false);
  });

  it('액션 실패가 이긴다 — 실패는 토스트보다 새 사건이고 더 급하다', () => {
    expect(startToastVisible(toast, '이미 진행 중인 측정이 있습니다', false, 'library')).toBe(false);
  });

  it('잠금 안내가 이긴다 — 방금 누른 탭에 대한 답이 먼저다', () => {
    expect(startToastVisible(toast, null, true, 'library')).toBe(false);
  });

  /**
   * 홈 규칙이 <b>시작 시점이 아니라 매 렌더</b>에 있어야 하는 이유 — 토스트는 5초를 버티므로,
   * 시작 시점 판정만으로는 그 사이 홈으로 건너간 사용자에게 중복이 그대로 따라온다.
   * (목 모드 실측으로 드러난 자리: 서재에서 시작 → 홈 이동 → 토스트가 「읽는 중」 카드와 같은 말을 했다.)
   */
  it('홈에선 안 선다 — 「읽는 중」 카드가 이미 그 책을 말한다', () => {
    expect(startToastVisible(toast, null, false, 'home')).toBe(false);
    expect(startToastVisible(toast, null, false, 'library')).toBe(true);
  });
});

describe('토스트 렌더 (StartToast)', () => {
  const toast = (b: BookOption | null, changed = false) => render(<StartToast toast={{ book: b, changed }} onChange={() => {}} />);

  it('문구와 [바꾸기]를 함께 낸다 — 말만 하고 손잡이가 없으면 반쪽이다', () => {
    const markup = toast(book(1, '데미안'));

    expect(markup).toContain('데미안');
    expect(markup).toContain('측정을 시작했어요');
    expect(markup).toContain('바꾸기');
  });

  it('책 없이도 [바꾸기]가 선다 — 그 상태에서 고르는 게 바로 이 손잡이의 쓸모다', () => {
    const markup = toast(null, true);

    expect(markup).toContain('책 없이 측정으로 바꿨어요');
    expect(markup).toContain('바꾸기');
  });

  /**
   * ⚠️ T-176 — 값이 변하는 그림자·필터 애니메이션이 실기기 페인트를 무너뜨린다. 이 토스트는 <b>표지를
   * 품고 있어</b> 정확히 그 클래스다(발광 `box-shadow`가 표지를 초당 60번 재래스터화하던 자리와 같다).
   * 나타남·사라짐은 즉시 마운트/언마운트여야 한다.
   */
  it('애니메이션·트랜지션이 없다 — 표지를 품은 카드라 T-176의 자리다', () => {
    const markup = toast(book(1, '데미안'));

    expect(markup).not.toContain('animation');
    expect(markup).not.toContain('transition');
  });

  it('스크린리더에 알린다 — 5초 뒤 사라지는 안내라 놓치면 되돌릴 길이 없다', () => {
    expect(toast(book(1, '데미안'))).toContain('role="status"');
  });
});

/**
 * 교체 시트 — 종료 후 태깅 시트(`BookSheet`)와 <b>다른 자리</b>다: 지금 대상이 무엇인지 표시하고,
 * 「책 없이」로 되돌리는 행이 있고, 측정이 안 멈춘다는 사실을 부제가 말한다.
 */
describe('교체 시트 (ChangeBookSheet)', () => {
  const books = [book(1, '데미안'), book(2, '사피엔스')];

  const sheet = (currentBookId: number | null) =>
    render(
      <ChangeBookSheet
        books={books}
        currentBookId={currentBookId}
        disabled={false}
        onPick={() => {}}
        onClose={() => {}}
      />,
    );

  /** 그 행의 여는 태그만 — 표시(`aria-current`)가 **어느 행에** 붙었는지는 이 방법으로만 잰다. */
  const rowTag = (markup: string, title: string) => {
    const at = markup.indexOf('data-book-title="' + title + '"');
    return at < 0 ? '' : markup.slice(markup.lastIndexOf('<button', at), markup.indexOf('>', at) + 1);
  };

  it('읽는 중인 책을 전부 행으로 내고 맨 아래에 「책 없이」를 둔다', () => {
    const markup = sheet(1);

    expect(markup).toContain('데미안');
    expect(markup).toContain('사피엔스');
    expect(markup).toContain('책 없이');
    expect(markup.indexOf('사피엔스')).toBeLessThan(markup.indexOf('책 없이')); // 되돌리기는 맨 아래
  });

  it('측정이 안 멈춘다고 부제가 말한다 — 이걸 모르면 무서워서 눌러 볼 수가 없다', () => {
    expect(sheet(1)).toContain('측정은 멈추지 않아요');
  });

  /**
   * 현재 대상 표시가 이 시트의 존재 이유 절반이다 — 「무슨 책으로 재고 있나」에 답하지 못하면
   * 그냥 또 하나의 고르기 목록이다.
   */
  it('지금 재고 있는 책 행에만 표시가 선다', () => {
    const markup = sheet(2);

    expect(rowTag(markup, '사피엔스')).toContain('aria-current="true"');
    expect(rowTag(markup, '데미안')).not.toContain('aria-current="true"');
  });

  it('책 없이 재고 있으면 그 행에 표시가 선다 — 「아무것도 안 골랐다」도 하나의 상태다', () => {
    const markup = sheet(null);

    expect(rowTag(markup, '')).toContain('aria-current="true"');
    expect(rowTag(markup, '데미안')).not.toContain('aria-current="true"');
  });

  /**
   * 교체는 네트워크를 탄다 — 응답 전에 다른 행을 또 누르면 요청이 겹쳐, 나중에 도착한 응답이 이기는
   * 경합이 된다(사용자가 마지막에 고른 책과 다를 수 있다). 그 창을 막는 게 `disabled` 배선이다.
   */
  it('진행 중(disabled)이면 행이 잠긴다 — 연타하면 어느 책으로 바뀔지 사용자가 못 정한다', () => {
    const markup = render(
      <ChangeBookSheet books={books} currentBookId={1} disabled onPick={() => {}} onClose={() => {}} />,
    );

    for (const title of ['데미안', '사피엔스', '']) {
      expect(rowTag(markup, title)).toContain('disabled=""');
    }
  });

  it('읽는 중인 책이 없어도 「책 없이」 행은 남는다 — 빈 시트는 닫는 것 말고 할 게 없다', () => {
    const markup = render(
      <ChangeBookSheet books={[]} currentBookId={null} disabled={false} onPick={() => {}} onClose={() => {}} />,
    );

    expect(markup).toContain('책 없이');
  });
});

/**
 * 배선 — 순수 함수와 컴포넌트가 각각 초록이어도 <b>`MainTabs`가 그 둘을 잇지 않으면</b> 화면엔 아무 일도
 * 안 일어난다. 토스트는 시작 성공 콜백에서만 뜨고 시트는 그 토스트에서만 열려, 정적 렌더로는 <b>관측
 * 자체가 불가능</b>하다(T-149) — 그래서 소스를 읽는다.
 *
 * <p>주석을 먼저 걷는 이유는 T-205다 — 안 걷으면 블록을 <b>주석 처리해서 죽여도</b> 문자열이 남아 통과한다.
 */
describe('배선 (MainTabs)', () => {
  const code = appSource.replace(/\{?\/\*[\s\S]*?\*\/\}?/g, '').replace(/^\s*\/\/.*$/gm, '');

  it('토스트가 겹침 게이트를 거쳐 그려진다 — 게이트를 우회하면 세 장이 한 자리에 포개진다', () => {
    expect(code).toMatch(/\{\s*startToastVisible\([\s\S]{0,120}?\)\s*&&\s*\(?\s*<StartToast/);
  });

  it('홈이 아닐 때만 띄운다 — 홈은 「읽는 중」 카드가 이미 그 책을 말한다', () => {
    expect(code).toMatch(/tab\s*!==\s*'home'/);
  });

  it('교체가 서버를 부른다 — 클라만 기억하면 새로고침 한 번에 되돌아간다', () => {
    expect(code).toContain('changeActiveBook(');
  });

  it('교체 응답으로 화면을 갱신한다 — 재조회 없이 대시보드가 따라와야 「읽는 중」 카드가 안 어긋난다', () => {
    expect(code).toMatch(/changeActiveBook\([\s\S]{0,200}?onTimerChange\(/);
  });

  /**
   * 교체 성공은 `changed: true`로 말해야 한다 — `false`면 「측정을 시작했어요」가 되어, 바꿨을 뿐인데
   * <b>두 번 시작한 것처럼</b> 읽힌다. 문구 함수는 두 변형을 다 계측하지만 <b>호출부가 어느 쪽을 쓰는지</b>는
   * 렌더로 관측할 수 없다(교체 성공 콜백에 정적 하니스가 못 닿는다).
   */
  it('교체 성공은 changed: true로 말한다 — false면 두 번 시작한 것처럼 읽힌다', () => {
    expect(code).toMatch(/changeActiveBook\([\s\S]{0,320}?showStartToast\(\{[^}]*changed:\s*true/);
  });

  /**
   * ⚠️ 교체 <b>실패</b>는 시트를 닫아야 보인다 — 에러 스트립은 탭바 층(z 100)인데 시트 패널은
   * <b>z 201 불투명</b>이라, 열린 채로 두면 메시지가 통째로 가려지고 사용자는 무반응 화면을 보고 또 누른다.
   * 409(방금 끝난 세션)·네트워크 오류 둘 다 이 경로다.
   */
  it('교체 실패가 시트를 닫는다 — 안 닫으면 에러가 불투명 패널 뒤에 숨는다', () => {
    const at = code.indexOf('changeActiveBook(');
    const body = code.slice(at, code.indexOf('.finally(', at));

    expect(body).toMatch(/\.catch\([\s\S]{0,200}?setChanging\(false\)/);
  });

  /**
   * 스스로 사라지는 배선 — 손잡이가 화면에 상주하면 안내가 아니라 장식이고, 탭바 위 한 칸을 영구히
   * 먹는다. 상수만 계측하면 `setTimeout`을 통째로 지워도 초록이라 <b>둘을 잇는 자리</b>를 잰다.
   */
  it('5초 뒤 스스로 사라진다 — 타이머에 그 상수가 실제로 물려 있다', () => {
    expect(START_TOAST_MS).toBe(5000);
    expect(code).toMatch(/setTimeout\([^,]+,\s*START_TOAST_MS\)/);
  });

  /**
   * 세션이 <b>다른 경로로</b> 끝나는 문이 있다(여백 진입 자동 종료 등). 그때 토스트·시트를 안 걷으면
   * 「이 책으로 재는 중」이라 말하는 카드와 그 대상을 고르는 시트가 <b>끝난 세션 위에</b> 남는다.
   */
  it('측정이 끝나면 토스트와 시트를 함께 걷는다 — 끝난 세션 위에 남으면 거짓말이다', () => {
    const at = code.indexOf('if (dashboard.hasActiveSession) return;');
    const body = code.slice(at, code.indexOf('}, [dashboard.hasActiveSession]);', at));

    expect(at).toBeGreaterThan(-1);
    expect(body).toContain('setStartToast(null)');
    expect(body).toContain('setChanging(false)');
  });

  /**
   * ⚠️ <b>토스트 타이머를 위 effect의 cleanup에 얹지 않는다.</b> React는 cleanup을 <b>의존성이 바뀔
   * 때마다</b> 돌리는데, 측정을 시작하는 순간이 바로 `hasActiveSession`이 `false → true`로 뒤집히는
   * 순간이다 — 방금 건 5초 타이머가 그 자리에서 지워져 <b>시작 토스트가 영영 안 사라진다</b>.
   *
   * <p>목 모드 실측으로만 드러났다(8초 뒤에도 그대로). 하니스는 effect를 안 돌려 이 경합을 재현할 수
   * 없으므로, 재발은 <b>그 코드가 다시 섞이는지</b>를 소스로 본다. 같은 탭에서 잰 맨 `setTimeout(5000)`이
   * 5186ms에 정상 발화한 것이 「스로틀이 아니라 앱 결함」을 가른 근거다(관측 경로부터 의심하는 규율).
   */
  it('토스트 타이머를 hasActiveSession effect의 cleanup에 얹지 않는다 — 시작하는 순간 제 타이머를 지운다', () => {
    const at = code.indexOf('if (dashboard.hasActiveSession) return;');
    const body = code.slice(at, code.indexOf('}, [dashboard.hasActiveSession]);', at));

    expect(body).not.toContain('startToastTimer');
    // 대신 언마운트 전용 effect가 걷는다 — 의존성이 빈 배열이라 시작·종료에 발화하지 않는다.
    expect(code).toMatch(/\(\)\s*=>\s*\(\)\s*=>\s*\{[\s\S]{0,200}?startToastTimer[\s\S]{0,200}?\},\s*\[\],/);
  });
});
