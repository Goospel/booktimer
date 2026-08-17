import { renderToStaticMarkup } from 'react-dom/server';
import { beforeEach, describe, expect, it } from 'vitest';

import {
  Coachmark,
  INLINE_DIM_Z_INDEX,
  coachmarkSeen,
  dismissCoachmark,
  onCoachmarkChange,
  resetCoachmarks,
} from './coachmark';
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

/**
 * 닫힘 알림 — 인라인 안내는 <b>화면 안에</b> 살아서, 닫혀도 흐름을 이끄는 쪽(`MainTabs`)이 알 길이 없다.
 * 이 구독이 그 통로다(prop을 Home·Library까지 내리지 않는 대신).
 */
describe('코치마크 닫힘 알림', () => {
  it('어느 안내를 닫아도 구독자에게 알린다 — 화면 밖의 흐름이 다음 걸음으로 넘어갈 수 있다', () => {
    const calls: string[] = [];
    const stop = onCoachmarkChange(() => calls.push('changed'));

    dismissCoachmark('library');
    dismissCoachmark('add-book');
    stop();

    expect(calls).toHaveLength(2);
  });

  it('해지하면 더 오지 않는다 — 언마운트된 화면이 계속 깨어나지 않는다', () => {
    const calls: string[] = [];
    onCoachmarkChange(() => calls.push('a'))();

    dismissCoachmark('margin');

    expect(calls).toEqual([]);
  });
});

/**
 * 안내 다시 보기 — 기록이 <b>기기</b>에 붙어 있어 로그아웃·탈퇴로도 안 지워진다. 미니앱엔 devtools가
 * 없으니 앱 안에 지우는 손잡이가 없으면 <b>기기에서 두 번 볼 방법이 영영 없다</b>(실측 — 검증하려던 기기가 막혔다).
 */
describe('안내 다시 보기 — resetCoachmarks', () => {
  it('본 기록을 전부 지운다 — 여러 개가 남아 있어도 하나도 안 남는다', () => {
    dismissCoachmark('timer');
    dismissCoachmark('library');
    dismissCoachmark('margin');

    resetCoachmarks();

    // 셋을 넣고 지우는 이유: 훑는 도중 지우면 인덱스가 밀려 하나씩 살아남는다(그 실수를 잡는다).
    expect(coachmarkSeen('timer')).toBe(false);
    expect(coachmarkSeen('library')).toBe(false);
    expect(coachmarkSeen('margin')).toBe(false);
  });

  it('코치마크 기록만 지운다 — 토큰까지 지우면 「안내 다시 보기」가 강제 로그아웃이 된다', () => {
    localStorage.setItem('booktimer.token', 'tok');
    localStorage.setItem('booktimer.notificationAgreement', 'newAgreement');
    dismissCoachmark('timer');

    resetCoachmarks();

    expect(localStorage.getItem('booktimer.token')).toBe('tok');
    expect(localStorage.getItem('booktimer.notificationAgreement')).toBe('newAgreement');
  });

  it('본 게 없어도 조용히 끝난다 — 갓 깐 기기에서 눌러도 아무 일이 없다', () => {
    expect(() => resetCoachmarks()).not.toThrow();
  });
});
