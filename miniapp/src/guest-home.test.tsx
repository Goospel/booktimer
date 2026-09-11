import { readFileSync } from 'node:fs';

import { TDSMobileProvider } from '@toss/tds-mobile';
import { renderToStaticMarkup } from 'react-dom/server';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { TabKey } from './App';
import type { LoginSource } from './screens/GuestHome';
import { GuestShell, guestAction, lockedCopy, startLogin } from './screens/GuestHome';
import { stubLocalStorage, userAgent } from './test-fixtures';
import type { Trial } from './trial';
import { TRIAL_CAP_SECONDS, beginTrial, flushTrial, readTrial, trialDurationSeconds, writeTrial } from './trial';

/**
 * 게스트 홈 — 로그인 전에도 <b>홈의 모양</b>을 보여 주고 서재·책방·기록만 잠근다(2026-09-11).
 *
 * <p>PR-2의 단독 체험 화면(타이머 카드 + 버튼 둘)이 여기로 옮겨 왔다. 그 화면의 계측기도 함께 온다 —
 * 「첫 렌더가 체험 화면인가」(자동 로그인 부활 감지)와 「상한을 넘겨 돌아온 체험이 storage에 박히는가」.
 *
 * <p>하니스가 `renderToStaticMarkup`이라 effect도 클릭도 안 돈다(T-149). 그래서 체험 세 상태는
 * `trial` 주입으로 각각 그려 보고, 클릭 배선은 순수 판정({@link guestAction})으로 따로 잰다.
 */

vi.mock('./toss', () => ({
  trackEvent: vi.fn(),
  trackScreen: vi.fn(),
  tossLogin: vi.fn(),
}));

beforeEach(stubLocalStorage);

const shell = (trial: Trial | null, tab: TabKey = 'home') =>
  renderToStaticMarkup(
    <TDSMobileProvider userAgent={userAgent}>
      <GuestShell tab={tab} onTabChange={() => {}} onLogin={() => {}} trial={trial} />
    </TDSMobileProvider>,
  );

/** 탭바(`<nav>`) 조각 — 화면 안의 잠금과 탭바의 잠금을 가르는 유일한 손잡이다. */
const navOf = (markup: string) => markup.slice(markup.indexOf('<nav'));

const running = { startedAt: new Date(Date.now() - 90_000).toISOString(), endedAt: null };
const done = { startedAt: '2026-09-11T01:00:00.000Z', endedAt: '2026-09-11T01:07:00.000Z' };

describe('탭바 가운데 원이 무엇을 하나 (guestAction)', () => {
  it('체험 전엔 시작 버튼이다', () => {
    expect(guestAction('none')).toEqual({ active: false, kind: 'start' });
  });

  it('재는 중엔 끝내는 버튼이다 — 어느 탭에서든 여기로 끝낸다', () => {
    expect(guestAction('running')).toEqual({ active: true, kind: 'stop' });
  });

  /** 체험은 한 건이다 — 끝난 뒤 ▶를 누르면 새로 재지 않고 「먼저 정하라」고 답한다(조용한 유실 금지). */
  it('끝난 뒤엔 안내만 한다 — 새로 재면 앞의 기록이 조용히 사라진다', () => {
    expect(guestAction('done')).toEqual({ active: false, kind: 'hint' });
  });
});

describe('잠긴 탭 문구 (lockedCopy)', () => {
  it('탭마다 다른 말을 한다 — 셋이 같으면 어느 칸을 눌렀는지 화면이 답하지 않는다', () => {
    const titles = (['library', 'bookshop', 'history'] as const).map((t) => lockedCopy(t).title);

    expect(new Set(titles).size).toBe(3);
  });

  it('셋 다 이유를 같은 말로 댄다 — 잠긴 이유가 「계정」임이 어디서든 같아야 한다', () => {
    for (const tab of ['library', 'bookshop', 'history'] as const) {
      expect(lockedCopy(tab).title).toContain('계정이 있어야');
      expect(lockedCopy(tab).detail.length).toBeGreaterThan(0);
    }
  });
});

describe('게스트 홈 — 첫 화면', () => {
  it('무엇을 하는 앱인지 한 줄로 읽힌다 — 인트로 서비스 설명은 심사 필수 항목이다', () => {
    expect(shell(null)).toContain('책 읽는 시간을 타이머로 기록');
  });

  it('첫 화면이 곧 타이머다 — 소개문만 있던 화면에서 17/21이 떠났다', () => {
    const markup = shell(null);

    expect(markup).toContain('둘러보는 중');
    expect(markup).toContain('00:00');
    expect(markup).toContain('읽기 시작');
    expect(markup).toContain('오늘 읽은 시간');
  });

  it('로그인은 누를 때만 시작한다 — 진입 즉시 인가 화면이 뜨면 반려 사유 그대로다', () => {
    // 로그인 진행 화면(`LoginBridge`)이 첫 렌더에 서면 이 문구가 뜬다 — 자동 로그인 부활의 계측기다.
    expect(shell(null)).not.toContain('토스로 로그인하는 중');
  });

  /**
   * 「진입 직후 덮는 것 0」의 정적 절반 — 떠 있는 것은 탭바 하나뿐이다(T-183: 시트·딤·모달·툴팁 전부 금지).
   * 동적 절반(첫 진입 실측)은 목 모드 원장 U-3이 잰다.
   */
  it('떠 있는 것은 탭바 하나뿐이다 — 진입 직후 화면을 덮는 것이 없다', () => {
    const markup = shell(null);
    const fixed = markup.match(/position:fixed/g) ?? [];

    expect(fixed).toHaveLength(1);
    expect(markup.slice(markup.indexOf('position:fixed') - 400, markup.indexOf('position:fixed'))).toContain('<nav');
  });
});

describe('게스트 홈 — 탭바', () => {
  it('탭 넷이 다 서 있다 — 잠겼어도 보여야 「나중에 열린다」가 읽힌다', () => {
    const markup = shell(null);

    for (const label of ['홈', '서재', '책방', '기록']) expect(markup).toContain(`title="${label}"`);
  });

  it('홈 말고 셋이 잠긴다 — 잠긴 칸 수가 곧 게스트가 못 보는 화면 수다', () => {
    // 탭바 안에서만 센다 — 화면 안에도 잠긴 것이 있다(피드 탭 머리). 전체를 세면 이 단언이
    // 「탭바가 몇 칸 잠겼나」가 아니라 「화면에 잠긴 것이 몇 개나」가 돼 뜻을 잃는다.
    const bar = navOf(shell(null));

    expect(bar.match(/aria-disabled="true"/g)).toHaveLength(3);
    expect(bar.match(/aria-current="page"/g)).toHaveLength(1);
  });
});

/**
 * 「무엇으로 측정할까요?」 — 로그인 홈과 <b>같은 자리·같은 카드</b>다. 다른 점은 고를 수 있는 칸이
 * 「책 없이」 하나라는 것뿐이고, 옆 두 칸이 잠긴 표지로 서서 「로그인하면 여기 내 책이 온다」를
 * 글자가 아니라 그림으로 말한다.
 */
describe('게스트 홈 — 무엇으로 측정할까요?', () => {
  it('로그인 홈과 같은 카드가 서고, 고를 수 있는 것은 「책 없이」다', () => {
    const markup = shell(null);

    expect(markup).toContain('무엇으로 측정할까요?');
    expect(markup).toContain('책 없이');
    expect(markup).toContain('로그인하면 서재의 책을 골라 잴 수 있어요');
  });

  it('잠긴 표지 두 칸이 옆에 선다 — 로그인이 무엇을 여는지를 그림으로 말한다', () => {
    const markup = shell(null);

    expect(markup.match(/data-book-slot="free"/g)).toHaveLength(1);
    expect(markup.match(/data-book-slot="locked"/g)).toHaveLength(2);
  });

  it('책을 고르는 문이 그 카드 안에 있다 — 잰 뒤가 아니라 고를 때 청한다', () => {
    expect(shell(null)).toContain('토스로 시작하고 책 고르기');
  });

  // 정적 하니스는 클릭을 못 돌려(T-149) 이 버튼이 어느 source를 싣는지 행동으로는 못 잰다 — 리뷰어가
  // `'header'`로 바꿔도 전 건 초록임을 실측했다. 그래서 App.tsx의 「소스로 잠근다」와 같은 방식으로 한 줄을 박는다.
  it('그 문은 login_started에 book_card를 싣는다 — 다른 값이면 「어느 자리가 로그인을 부르는가」의 한 층이 사라진다', () => {
    const src = readFileSync(new URL('./screens/GuestHome.tsx', import.meta.url), 'utf8').replace(/\s+/g, ' ');
    expect(src).toContain("onClick={() => onLogin('book_card')}");
  });

  it('재는 중에도 끝난 뒤에도 그대로 선다 — 상태마다 갈아끼우면 화면이 세로로 들썩인다', () => {
    expect(shell(running)).toContain('무엇으로 측정할까요?');
    expect(shell(done)).toContain('무엇으로 측정할까요?');
  });
});

/**
 * 잠긴 피드 — 머리(소식·여백·책 뉴스)는 <b>남겨</b> 무엇이 잠겼는지 보여 주고, 본문 자리에서 이유를
 * 말한다. 실데이터 미리보기는 공개 API가 필요해 비목표라 뼈대만 깐다(사용자 결정).
 */
describe('게스트 홈 — 잠긴 피드', () => {
  it('무엇이 잠겼는지 머리로 보여 주고 안에서 이유를 말한다', () => {
    const markup = shell(null);

    expect(markup).toContain('책 뉴스');
    expect(markup).toContain('소식·여백·책 뉴스는 계정이 있어야 보여요');
    expect(markup).toContain('다른 독서가들이 무엇을 읽고 무슨 글을 남겼는지');
  });
});

describe('게스트 홈 — 재는 중', () => {
  it('그만둘 손잡이 하나뿐이고, 꺼도 계속 센다고 말한다', () => {
    const markup = shell(running);

    expect(markup).toContain('그만 읽기');
    expect(markup).toContain('화면을 꺼도 측정은 계속돼요');
  });

  it('탭바 원이 끝내는 버튼으로 바뀐다 — 다른 탭에 가 있어도 끝낼 수 있다', () => {
    expect(shell(running)).toContain('aria-label="측정 끝내기"');
  });
});

describe('게스트 홈 — 다 재고 나서', () => {
  it('그때서야 로그인을 청한다 — 잃을 것이 생긴 뒤다', () => {
    const markup = shell(done);

    expect(markup).toContain('7분');
    expect(markup).toContain('기록을 남기려면');
    expect(markup).toContain('토스로 시작하기');
  });

  it('거절할 길을 같은 화면에 둔다 — 나갈 길이 없으면 그게 덮는 것이다', () => {
    expect(shell(done)).toContain('기록 없이 둘게요');
  });

  it('로그인의 이유(알림·PC 연동)는 로그인을 청하는 자리에서 말한다', () => {
    const markup = shell(done);

    expect(markup).toContain('토스 알림');
    expect(markup).toContain('booktimer.app');
  });

  /** 계정을 만들면 무엇이 되는지를 <b>방금 잰 값으로</b> 보여 준다 — 「연속 1일」은 그 투영이다. */
  it('방금 잰 것이 무엇이 되는지 같은 카드에서 보여 준다', () => {
    const markup = shell(done);

    expect(markup).toContain('연속');
    expect(markup).toContain('잔디 첫 칸');
  });

  /**
   * 길이가 <b>사용자 데이터</b>라 조사를 고정할 수 없다 — 「7분」은 받침이 있고 「12초」·「2시간」은 없다.
   * 목 모드 실측에서 「12초이 잔디 첫 칸이 돼요」가 나왔다(2026-09-11). 판정은 `hasFinalConsonant`
   * 하나로 하고(그 함수 주석이 「두 벌로 두면 한쪽만 고쳐진다」고 적어 둔 자리다), 세 꼴을 다 잰다.
   */
  it('「돼요」 앞 조사가 길이를 따라간다 — 초·분·시간이 다 지나가는 자리다', () => {
    const at = (from: string, to: string) => ({ startedAt: from, endedAt: to });

    expect(shell(at('2026-09-11T01:00:00.000Z', '2026-09-11T01:00:12.000Z'))).toContain('12초가 잔디 첫 칸');
    expect(shell(at('2026-09-11T01:00:00.000Z', '2026-09-11T01:07:00.000Z'))).toContain('7분이 잔디 첫 칸');
    expect(shell(at('2026-09-11T01:00:00.000Z', '2026-09-11T03:00:00.000Z'))).toContain('2시간이 잔디 첫 칸');
  });
});

describe('게스트 홈 — 잠긴 탭', () => {
  it('탭을 누르면 열리고, 안에서 잠긴 이유를 말한다', () => {
    expect(shell(null, 'library')).toContain('서재는 계정이 있어야 열려요');
    expect(shell(null, 'bookshop')).toContain('책방은 계정이 있어야 열려요');
    expect(shell(null, 'history')).toContain('기록은 계정이 있어야 열려요');
  });

  it('내가 선 칸은 흐리지 않다 — 잠금 표시가 「여기 있다」를 덮으면 길을 잃는다', () => {
    const markup = shell(null, 'library');
    const at = markup.indexOf('title="서재"');
    const cell = markup.slice(markup.lastIndexOf('<button', at), markup.indexOf('</button>', at));

    expect(cell).toContain('aria-current="page"');
    expect(cell).toContain('aria-disabled="true"');
    expect(cell).toContain('opacity:1');
  });

  it('잠긴 화면에도 「둘러보는 중」이 남는다 — 로그인 홈과 헷갈리지 않게', () => {
    expect(shell(null, 'history')).toContain('둘러보는 중');
  });
});

/**
 * 며칠 뒤 재진입 — 화면은 상한으로 접어 「끝남」을 그리는데 storage에 `endedAt:null`이 남으면,
 * 로그인 뒤 `flushTrial`이 「올릴 것 없음」으로 지나친다. 「기록을 남기려면 계정이 필요해요」라고
 * 청해 놓고 아무것도 안 남는 자리라, 접은 값이 storage에 박히는지를 합류 결과로 잰다.
 */
describe('상한을 넘겨 돌아온 체험', () => {
  it('접은 값을 storage에도 박는다 — 로그인 뒤 그대로 합류한다', async () => {
    writeTrial({ startedAt: new Date(Date.now() - 7 * 3600_000).toISOString(), endedAt: null });

    const markup = renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <GuestShell tab="home" onTabChange={() => {}} onLogin={() => {}} />
      </TDSMobileProvider>,
    );
    const sent: Trial[] = [];
    const result = await flushTrial(async (t) => void sent.push(t));

    expect(markup).toContain('기록을 남기려면');
    expect(result).toBe('imported');
    expect(sent).toHaveLength(1);
    expect(trialDurationSeconds(sent[0])).toBe(TRIAL_CAP_SECONDS);
  });
});

/**
 * 재는 중 <b>히어로가 아닌</b> 손잡이로 로그인한다 — 헤더 사람 아이콘(로그아웃한 기존 사용자의 길)과
 * 잠긴 탭의 「토스로 시작하기」 둘이다. 둘 다 「그만 읽기」를 안 거치므로 `endedAt:null`인 채 넘어간다.
 *
 * <p>접지 않고 넘기면 손해가 둘이다: ① `flushTrial`이 「끝난 것만 올린다」는 규칙대로 `'none'`으로
 * 지나쳐 방금 잰 시간이 조용히 사라지고 ② 고아 체험이 storage에 남아, 나중에 로그아웃하고 돌아오면
 * {@link restoreTrial}이 상한으로 접어 <b>가짜 6시간</b>이 올라간다.
 */
describe('재는 중 다른 손잡이로 로그인 (startLogin)', () => {
  it('체험을 접어 storage에 박고 넘긴다 — 잰 시간이 그대로 합류한다', async () => {
    const started = beginTrial(Date.now() - 90_000);
    const seen: LoginSource[] = [];

    startLogin(started, (source) => seen.push(source), 'header');

    expect(seen).toEqual(['header']); // 어느 손잡이였는지는 그대로 흘러간다(`login_started{source}`)
    expect(readTrial()?.endedAt).not.toBeNull();

    const sent: Trial[] = [];
    const result = await flushTrial(async (t) => void sent.push(t));

    expect(result).toBe('imported');
    expect(trialDurationSeconds(sent[0])).toBeGreaterThanOrEqual(90);
  });

  it('끝난 체험은 다시 접지 않는다 — 두 번 접으면 끝 시각이 「로그인을 누른 때」로 늘어난다', () => {
    writeTrial(done);

    startLogin(done, () => {}, 'trial');

    expect(readTrial()).toEqual(done);
  });
});
