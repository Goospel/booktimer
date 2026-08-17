import { renderToStaticMarkup } from 'react-dom/server';
import { beforeEach, describe, expect, it } from 'vitest';

import { Coachmark, INLINE_DIM_Z_INDEX, coachmarkSeen, dismissCoachmark } from './coachmark';
import { stubLocalStorage } from './test-fixtures';

beforeEach(stubLocalStorage); // 코치마크는 렌더 중에 「봤는가」를 기기에서 읽는다

/**
 * 인라인 코치마크 — 콘텐츠 안의 버튼을 가리킨다. 탭바 코치마크(App)와 뿌리가 같다:
 * 마스크로 구멍을 뚫지 않고 **대상이 스스로 딤 위로 올라온다**. 그래서 좌표 계산이 0이고
 * (말풍선은 대상 바로 아래 흐름에 놓인 형제다), 가리킨 버튼이 그대로 눌린다.
 */
describe('인라인 코치마크', () => {
  const GUIDE = '여기서 찾아 담아요';

  function render(props: { name?: string; after?: string } = {}) {
    return renderToStaticMarkup(
      <Coachmark name={props.name ?? 'add-book'} after={props.after} title={GUIDE} detail="검색하면 담겨요">
        <button type="button">책 추가하기</button>
      </Coachmark>,
    );
  }

  it('아직 안 본 안내는 대상과 함께 뜬다', () => {
    const markup = render();

    expect(markup).toContain(GUIDE);
    expect(markup).toContain('책 추가하기'); // 안내가 대상을 대체하지 않는다
  });

  it('한 번 본 안내는 사라지고 대상만 남는다 — 감싼 층도 걷힌다', () => {
    dismissCoachmark('add-book');

    const markup = render();

    expect(markup).not.toContain(GUIDE);
    expect(markup).toContain('책 추가하기');
    expect(markup).not.toContain(`z-index:${INLINE_DIM_Z_INDEX}`); // 딤도 없다
  });

  it('대상은 딤 위로 올라온다 — 마스크 없이 그 버튼만 밝게 남고 그대로 눌린다', () => {
    const markup = render();

    expect(markup).toContain(`z-index:${INLINE_DIM_Z_INDEX}`); // 딤
    expect(markup).toContain(`z-index:${INLINE_DIM_Z_INDEX + 1}`); // 대상과 말풍선
  });

  it('딤은 탭바를 덮는다 — 인라인 안내에서 밝은 것은 가리킨 대상 하나뿐이다', () => {
    expect(INLINE_DIM_Z_INDEX).toBeGreaterThan(100); // App의 TAB_BAR_Z_INDEX
  });

  it('꼬리는 위를 가리킨다 — 말풍선이 대상 아래에 서기 때문이다', () => {
    expect(render()).toContain('border-bottom:9px solid');
  });

  it('앞선 안내(투어)를 아직 안 봤으면 뜨지 않는다 — 딤 두 장이 겹치지 않는다', () => {
    const markup = render({ after: 'bookshop' });

    expect(markup).not.toContain(GUIDE);
    expect(markup).toContain('책 추가하기'); // 대상은 평소처럼 그려진다
  });

  it('앞선 안내를 본 뒤에는 뜬다 — 투어가 끝나고서 차례가 온다', () => {
    dismissCoachmark('bookshop');

    expect(render({ after: 'bookshop' })).toContain(GUIDE);
  });
});

describe('코치마크 기록', () => {
  it('스텝마다 자기 키에 남는다 — 이름이 곧 키다', () => {
    dismissCoachmark('library');

    expect(localStorage.getItem('booktimer.coachmark.library')).toBe('seen');
    expect(coachmarkSeen('library')).toBe(true);
  });

  it('안 본 스텝은 안 봤다고 답한다 — 키가 없으면 뜬다(이미 쓰던 사람도 1회 본다)', () => {
    expect(coachmarkSeen('bookshop')).toBe(false);
  });

  it('#833에 나간 측정 안내 키를 그대로 쓴다 — 그 스텝을 본 사람은 다시 보지 않는다', () => {
    dismissCoachmark('timer');

    expect(localStorage.getItem('booktimer.coachmark.timer')).toBe('seen');
  });
});
