import { TDSMobileProvider } from '@toss/tds-mobile';
import { readFileSync } from 'node:fs';
import { renderToStaticMarkup } from 'react-dom/server';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { startToastMessage, timerStartBookId } from './App';
import type { DashboardResponse, StudyBookRow, StudyState } from './api';
import { IDLE_STUDY } from './api';
import { mockRequest } from './dev-mock';
import { Home } from './screens/Home';
import { studyBookChips } from './screens/StudyLibrary';
import { graph, stubLocalStorage, userAgent } from './test-fixtures';

/**
 * 공부 타이머–책 연결(PR-2) — 시작할 때 책을 고르고, 홈이 그 책을 말하고, 서재 카드가 누적 시간을 말한다.
 *
 * <p>하니스가 정적 렌더라(effect·클릭 없음) 배선은 순수 함수와 <b>소스 문자열</b>로 잰다(T-149:
 * 못 도는 경로에 부정 단언을 두지 않는다).
 */

vi.mock('./toss', () => ({
  REWARD_AD_GROUP_ID: '',
  watchRewardAd: vi.fn(),
  GOAL_MET_TEMPLATE_CODE: 'test-template',
  notificationAgreementSupported: () => false,
  requestNotificationAgreement: vi.fn(),
  trackEvent: vi.fn(),
  openExternal: vi.fn(),
}));

beforeEach(() => {
  stubLocalStorage();
});

// ── 순수 함수 ───────────────────────────────────────────────────────────────

describe('startToastMessage — 모드는 명사만 바꾼다', () => {
  it('공부 시작에 고른 책이 실린다 — 이제 고를 수 있으니 무엇을 재는지 말해야 한다', () => {
    expect(
      startToastMessage({ book: { id: 101, title: '정보처리기사', coverUrl: null, author: null }, changed: false, mode: 'study' }),
    ).toBe('『정보처리기사』 공부 측정을 시작했어요');
  });

  it('책 없이 공부를 시작하면 「책 없이」라고 말한다 — 빠진 것이 아니라 고른 것이다', () => {
    expect(startToastMessage({ book: null, changed: false, mode: 'study' })).toBe('책 없이 공부 측정을 시작했어요');
  });

  it('공부 교체 문구도 같은 공식에서 나온다', () => {
    expect(
      startToastMessage({ book: { id: 102, title: '토익', coverUrl: null, author: null }, changed: true, mode: 'study' }),
    ).toBe('『토익』 공부 측정으로 바꿨어요');
  });

  it('독서 문구는 글자 그대로 남는다(회귀 가드) — 공식화가 독서 출력을 건드리지 않는다', () => {
    expect(startToastMessage({ book: null, changed: false })).toBe('책 없이 측정을 시작했어요');
    expect(startToastMessage({ book: { id: 1, title: '데미안', coverUrl: null, author: null }, changed: true })).toBe(
      '『데미안』 측정으로 바꿨어요',
    );
  });
});

describe('studyBookChips — 회독은 상태, 시간은 부재할 수 있다', () => {
  const book = (overrides: Partial<StudyBookRow> = {}): StudyBookRow => ({
    id: 101,
    title: '정보처리기사',
    author: null,
    coverUrl: null,
    isbn13: null,
    readCount: 3,
    purchaseLink: null,
    ...overrides,
  });

  it('0초면 시간 칩을 안 만든다 — 「0초 공부」는 정보가 아니라 소음이다', () => {
    const chips = studyBookChips(book({ totalSeconds: 0 }));
    expect(chips).toHaveLength(1);
    expect(chips[0].label).toBe('3독');
  });

  it('필드 자체가 없는 옛 서버도 시간 칩 없이 회독만 그린다', () => {
    expect(studyBookChips(book())).toHaveLength(1);
  });

  it('12000초면 「3시간 20분 공부」 칩이 붙고 값은 숫자 부분이다', () => {
    const chips = studyBookChips(book({ totalSeconds: 12_000 }));
    expect(chips).toHaveLength(2);
    expect(chips[1].label).toBe('3시간 20분 공부');
    expect(chips[1].value).toBe('3시간 20분');
  });

  it('1초여도 잰 시간은 잰 시간이다 — 0만 부재다', () => {
    expect(studyBookChips(book({ totalSeconds: 1 }))[1].label).toBe('1초 공부');
  });
});

describe('timerStartBookId — 공부 목록에도 같은 규칙이 선다', () => {
  const books: StudyBookRow[] = [
    { id: 101, title: '정보처리기사', author: null, coverUrl: null, isbn13: null, readCount: 3, purchaseLink: null },
    { id: 102, title: '토익', author: null, coverUrl: null, isbn13: null, readCount: 1, purchaseLink: null },
  ];

  it('아직 안 골랐으면 최근 공부한 책으로 떨어진다', () => {
    expect(timerStartBookId(books, 102, undefined)).toBe(102);
  });

  it('서재에서 빠진 id(stale)는 「책 없이」로 강등된다 — 죽은 버튼이 되지 않는다', () => {
    expect(timerStartBookId(books, null, 999)).toBeNull();
  });

  it('「책 없이」를 고른 것은 그대로 존중한다', () => {
    expect(timerStartBookId(books, 101, null)).toBeNull();
  });
});

// ── 홈 렌더 ─────────────────────────────────────────────────────────────────

function dashboard(study: StudyState): DashboardResponse {
  return {
    nickname: '공부하는사람',
    loginId: 'studyid',
    previousLoginId: null,
    profileCharacterCode: null,
    remainingSeconds: 900,
    todayReadSeconds: 2700,
    carriedDebtSeconds: 0,
    todayGoalSeconds: 3600,
    carryover: true,
    hasActiveSession: false,
    activeStartedAt: null,
    activeBookTitle: null,
    activeBookTotalSeconds: 0,
    activeBook: null,
    readingBooks: [{ id: 1, title: '데미안', coverUrl: null, author: null }],
    finishedBooks: [],
    wantToReadBooks: [],
    recentBookId: 1,
    graph,
    emailVerified: true,
    debtWaiverAvailable: false,
    study,
  };
}

function renderHome(mode: 'reading' | 'study', study: StudyState) {
  return renderToStaticMarkup(
    <TDSMobileProvider userAgent={userAgent}>
      <Home
        dashboard={dashboard(study)}
        mode={mode}
        study={study}
        onChangeMode={() => {}}
        onBlockedModeChange={() => {}}
        selectedBookId={undefined}
        onSelectBook={() => {}}
        selectedStudyBookId={undefined}
        onSelectStudyBook={() => {}}
        onTimerChange={() => {}}
        celebrate={false}
        onGoGoal={() => {}}
        goalAdPending={false}
        onGoSettings={() => {}}
        onError={() => {}}
        onOpenMargin={() => {}}
        onComposeMargin={() => {}}
      />
    </TDSMobileProvider>,
  );
}

const studyBooks: StudyBookRow[] = [
  { id: 101, title: '정보처리기사 필기', author: null, coverUrl: null, isbn13: null, readCount: 3, purchaseLink: null, totalSeconds: 12_000 },
  { id: 102, title: '토익 실전 1000제', author: null, coverUrl: null, isbn13: null, readCount: 1, purchaseLink: null },
];

describe('홈 공부 히어로 — 대기 중엔 고르고, 재는 중엔 무엇을 재는지 말한다', () => {
  it('대기 중이면 「무엇을 공부할까요?」 캐러셀이 공부 서재를 세운다', () => {
    const markup = renderHome('study', { ...IDLE_STUDY, books: studyBooks, recentBookId: 101 });

    expect(markup).toContain('무엇을 공부할까요?');
    expect(markup).toContain('data-lead-card');
    expect(markup).toContain('정보처리기사 필기');
    expect(markup).toContain('토익 실전 1000제');
  });

  /**
   * 기본 선택 규칙이 <b>화면에 닿는지</b>를 잰다 — 순수 함수(`defaultBookId`) 테스트만으론
   * 홈이 그 함수를 부르는지 알 수 없다(리뷰어 실측: 호출을 `null`로 바꿔도 전건 초록이었다).
   * `data-selected-book`은 정적 렌더에 실리는 캐러셀 손잡이다(`home.test`의 독서판 선례).
   */
  it('아직 안 골랐으면 최근 공부한 책이 가운데 온다 — 규칙이 화면까지 닿는다', () => {
    const markup = renderHome('study', { ...IDLE_STUDY, books: studyBooks, recentBookId: 102 });

    expect(markup).toContain('data-selected-book="토익 실전 1000제"');
  });

  it('측정 중이면 캐러셀 대신 「측정 중 · 제목」이다 — 고를 자리가 사라진다', () => {
    const markup = renderHome('study', {
      ...IDLE_STUDY,
      hasActiveSession: true,
      activeStartedAt: new Date().toISOString(),
      books: studyBooks,
      activeBook: studyBooks[0],
    });

    expect(markup).toContain('· 정보처리기사 필기');
    expect(markup).not.toContain('무엇을 공부할까요?');
  });

  it('책 없이 측정 중이면 제목 없이 「측정 중」만 — 없는 책을 지어내지 않는다', () => {
    const markup = renderHome('study', {
      ...IDLE_STUDY,
      hasActiveSession: true,
      activeStartedAt: new Date().toISOString(),
      books: studyBooks,
      activeBook: null,
    });

    expect(markup).toContain('측정 중');
    expect(markup).not.toContain('정보처리기사 필기');
  });

  it('독서 홈은 그대로다 — 공부 헤더가 새지 않고 독서 헤더가 산다(짝 단언)', () => {
    const markup = renderHome('reading', IDLE_STUDY);

    expect(markup).toContain('무엇으로 측정할까요?');
    expect(markup).not.toContain('무엇을 공부할까요?');
  });
});

// ── 배선(소스 단언) ────────────────────────────────────────────────────────

describe('배선 — 정적 렌더가 못 도는 경로는 소스로 잠근다', () => {
  const app = readFileSync(new URL('./App.tsx', import.meta.url), 'utf8');
  const home = readFileSync(new URL('./screens/Home.tsx', import.meta.url), 'utf8');
  const shelf = readFileSync(new URL('./screens/StudyLibrary.tsx', import.meta.url), 'utf8');
  const api = readFileSync(new URL('./api.ts', import.meta.url), 'utf8');

  /**
   * ⚠️ <b>인자열 전체</b>를 잰다. 앞 판은 `startStudy(timerStartBookId(`까지만 봐서, 셋째 인자를
   * `homeBookId`로 바꾼 <b>슬롯 혼용</b>(설계 §6 「그럴듯한 사고 ②」)이 `tsc`도 이 단언도 통과했다 —
   * 두 서재의 id 공간이 섞이는 바로 그 사고가 계측 밖에 서 있었다(리뷰어 돌연변이 실측).
   */
  it('공부 시작이 고른 책 id를 실어 보낸다 — 무인자 회귀도 슬롯 혼용도 여기서 걸린다', () => {
    expect(app).toContain(
      'startStudy(timerStartBookId(study.books ?? [], study.recentBookId ?? null, studyBookId))',
    );
  });

  /**
   * 와이어 키는 <b>클라이언트가 보내는 문자열</b>이라 목 왕복으로는 안 잡힌다 — 그 테스트는
   * `mockRequest`를 직접 부르므로 `startStudy`의 body를 <b>한 번도 안 지난다</b>(리뷰어 실측:
   * `{ book: bookId }`로 바꿔도 전건 초록이었다).
   */
  it('시작 요청의 필드명이 서버 계약(bookId)과 같다', () => {
    expect(api).toContain("request('/api/study/start', { body: { bookId } })");
  });

  it('시작 토스트가 서버 확정값을 말한다 — 클라가 고른 값을 되뇌지 않는다', () => {
    expect(app).toContain("showStartToast({ book: next.activeBook ?? null, changed: false, mode: 'study' })");
  });

  /**
   * ⚠️ `setStudyBookId`를 <b>개수로</b> 잰다. 앞 판의 `toContain('setStudyBookId')`는 `useState`
   * <b>선언문</b>에 걸려 항상 통과했고, 그래서 App→MainTabs 프롭 전달을 통째로 지워도 초록이었다
   * (리뷰어 실측). 2건 = 선언 1 + `MainTabs`로 전달 1이다.
   */
  it('공부 캐러셀 선택은 App이 든 별개 슬롯이고, 그 슬롯이 실제로 아래까지 내려간다', () => {
    expect(app.match(/setStudyBookId/g) ?? []).toHaveLength(2);
    expect(app).toContain('studyBookId={studyBookId}');
    expect(app).toContain('selectedStudyBookId={studyBookId}');
    expect(home).toContain('onSelect={onSelectStudyBook}');
  });

  it('공부 서재의 담기·삭제가 홈 캐러셀 갱신을 부른다 — 안 부르면 캐러셀이 옛 목록 그대로다', () => {
    // 두 성공 경로 각각에 있어야 한다(하나만 있어도 문자열 1건은 잡히므로 개수로 잰다).
    expect(shelf.match(/onShelfChanged\(\)/g) ?? []).toHaveLength(2);
    expect(shelf).toContain('deleteStudyBook(book.id).then(() => onShelfChanged())');
  });

  it('서재 카드 칩이 순수 함수를 탄다 — 시간 칩 규칙의 계측기가 화면에 실제로 닿는다', () => {
    expect(shelf).toContain('chipsOf={studyBookChips}');
  });
});

// ── dev-mock 왕복 ──────────────────────────────────────────────────────────

describe('dev-mock — 고르고 재고 쌓인다', () => {
  // 목은 모듈 메모리라 한 건이 세션을 열어 둔 채 죽으면 다음 건이 전부 409로 연쇄한다 —
  // 진짜 원인 하나가 실패 다섯 줄에 묻힌다. 매 건 앞에서 열린 세션만 걷는다(없으면 409를 삼킨다).
  beforeEach(async () => {
    await mockRequest('/api/study/stop', { body: {} }).catch(() => {});
  });

  it('책을 걸고 시작하면 activeBook이 서고, 종료분이 그 책에 쌓인다', async () => {
    const started = await mockRequest<StudyState>('/api/study/start', { body: { bookId: 101 } });
    expect(started.activeBook?.id).toBe(101);
    expect(started.recentBookId).toBe(101);

    const stopped = await mockRequest<StudyState>('/api/study/stop', { body: {} });
    expect(stopped.activeBook).toBeNull();
    // 픽스처 12000초에 이번 측정분이 얹힌다 — 줄어들 길이 없다.
    expect(stopped.books?.find((b) => b.id === 101)?.totalSeconds).toBeGreaterThanOrEqual(12_000);
    // 안 고른 책엔 안 쌓인다 — 합산이 책별인지가 여기서 갈린다.
    expect(stopped.books?.find((b) => b.id === 102)?.totalSeconds).toBe(0);
  });

  it('없는 책 id로 시작하면 404다 — 서버처럼 존재를 비노출한다', async () => {
    await expect(mockRequest('/api/study/start', { body: { bookId: 999 } })).rejects.toMatchObject({ status: 404 });
  });

  it('책 없이도 시작된다 — 고르지 않는 것이 정당한 사용이다', async () => {
    const started = await mockRequest<StudyState>('/api/study/start', { body: { bookId: null } });
    expect(started.hasActiveSession).toBe(true);
    expect(started.activeBook).toBeNull();
    await mockRequest('/api/study/stop', { body: {} });
  });

  it('책을 지우면 그 책으로는 못 잰다 — 서재 목록에서도 시간에서도 사라진다', async () => {
    await mockRequest('/api/study/books/103/delete', { body: {} });
    await expect(mockRequest('/api/study/start', { body: { bookId: 103 } })).rejects.toMatchObject({ status: 404 });
  });

  it('서재 목록이 누적 시간을 실어 온다 — 칩의 재료가 목에도 있다', async () => {
    const shelf = await mockRequest<{ books: StudyBookRow[] }>('/api/study/books');
    // 앞 테스트가 이미 이 책에 얹었을 수 있어 하한으로 잰다 — 재는 것은 「픽스처가 실려 온다」다.
    expect(shelf.books.find((b) => b.id === 101)?.totalSeconds).toBeGreaterThanOrEqual(12_000);
  });
});
