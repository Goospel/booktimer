import { Button, ProgressBar, Text } from '@toss/tds-mobile';
import { useEffect, useState } from 'react';

import type { BookOption, DashboardResponse, QuoteDto, TimerState, WaiveResponse } from '../api';
import { ApiError, startSession, stopSession, tagBook, waiveDebt } from '../api';
import { elapsedSeconds, formatDuration } from '../format';
import {
  GOAL_MET_TEMPLATE_CODE,
  REWARD_AD_GROUP_ID,
  notificationAgreementSupported,
  requestNotificationAgreement,
  watchRewardAd,
} from '../toss';
import { CoverInitial, ErrorMessage, GrassGrid, Screen, sectionStyle } from '../ui';

/** 홈 잔디 미리보기 폭 — 최근 15주만 축약해 보여주고 전체는 기록 화면이 맡는다(카드 폭을 채우는 주 수). */
const PREVIEW_WEEKS = 15;

/** 알림 동의 결과 캐시 — 값은 토스가 준 결과 문자열 그대로. 정본은 토스이고 이건 카드 노출 스위치일 뿐이다. */
const AGREEMENT_KEY = 'booktimer.notificationAgreement';

/**
 * 처음 골라 둘 책 — 최근 읽은 책(=이어 읽기)이 읽는 중 목록에 있으면 그 책, 아니면 첫 책, 없으면 `null`.
 *
 * <p>웹 `BookPickForm`의 `defaultBook`과 같은 규칙이다. `recentBookId`가 목록 밖일 수 있는 건 그 책을
 * 다 읽었거나 뺐기 때문이다 — 그때 아무것도 안 고른 채로 두면 "측정 시작"이 죽은 버튼이 된다.
 */
export function defaultBookId(readingBooks: BookOption[], recentBookId: number | null): number | null {
  return readingBooks.find((b) => b.id === recentBookId)?.id ?? readingBooks[0]?.id ?? null;
}

/**
 * 리워드 광고 버튼을 노출할지 — 셋 다 참이어야 한다.
 *
 * <p>① 밀린 시간이 있다(=죄책감이 화면에 뜬 순간, 보상이 필요한 바로 그 지점) ② 서버가 오늘 지급
 * 가능하다고 했다 ③ 광고 그룹 ID가 설정됐다(config-gate). **부채가 없으면 광고의 존재 자체가 안 보인다** —
 * 입문자에게 "광고 보는 앱" 인상을 주지 않으려는 배치다(설계 §3).
 */
export function showWaiverButton(
  carriedDebtSeconds: number,
  debtWaiverAvailable: boolean,
  adGroupId: string,
): boolean {
  return carriedDebtSeconds > 0 && debtWaiverAvailable && adGroupId !== '';
}

/**
 * 광고 시청 → 지급. 끝까지 안 봤으면 **지급 API를 부르지 않고** `null`(조용히 원상태).
 *
 * <p>클릭 흐름을 화면에서 꺼내 둔 이유: 테스트 하니스가 정적 렌더라 클릭이 안 돌아,
 * "보상 없이 지급 요청을 보내지 않는다"는 이 기능의 신뢰 경계를 함수로만 계측할 수 있다.
 */
export async function claimDebtWaiver(adGroupId: string): Promise<WaiveResponse | null> {
  const rewarded = await watchRewardAd(adGroupId);
  return rewarded ? waiveDebt() : null;
}

/**
 * 알림 동의 카드를 띄울지 — 아직 한 번도 답하지 않았고(캐시 없음) 지원되는 토스앱(5.255.0+)일 때만.
 *
 * <p>거절(`agreementRejected`)도 캐시라 카드가 사라진다 — 거절한 사람을 다시 조르지 않는다.
 * 다른 기기에서 이미 동의했다면 캐시가 없어 카드가 한 번 더 보이지만, 누르면 `alreadyAgreed`가
 * 와서 캐시되고 사라진다(무해).
 */
export function shouldShowNotificationCard(cached: string | null, supported: boolean): boolean {
  return supported && cached === null;
}

/**
 * 동의 화면을 띄우고 결과를 캐시한다 — 이 캐시가 카드를 끄는 유일한 스위치다.
 *
 * <p>미지원 기기(`null`)에서는 **캐시를 남기지 않는다** — 남기면 나중에 최신 토스앱에서 열어도
 * 영영 안 묻는다. 클릭 흐름을 화면 밖으로 꺼낸 이유는 광고 쪽과 같다(정적 렌더 하니스라 클릭이 안 돈다).
 */
export async function askNotificationAgreement(): Promise<string | null> {
  const result = await requestNotificationAgreement(GOAL_MET_TEMPLATE_CODE);
  if (result !== null) localStorage.setItem(AGREEMENT_KEY, result);
  return result;
}

/**
 * 실패 문구 — 서버가 준 평문(409 "오늘은 이미 사용했어요" 등)은 그대로 쓰고, SDK가 준 광고 에러는
 * 영문·기술 문구라 그대로 띄우면 안 되므로 안내로 바꾼다.
 */
export function waiverErrorMessage(error: Error): string {
  return error instanceof ApiError ? error.message : '광고를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.';
}

/**
 * 책 버튼 목록 — 「바꾸기」로 편 고르기 목록과 종료 후 태깅 목록이 같은 렌더를 쓴다.
 *
 * <p>`selectedId`가 있으면 그 책만 채움(fill)으로 구분한다 — variant를 가릴 class·속성이 없어
 * 이 채움색이 선택의 유일한 표지다. 태깅엔 "고른 책"이 없어 `null`(전부 weak)로 쓴다.
 *
 * <p>화면에서 꺼내 둔 이유는 늘 같다: 하니스가 정적 렌더라 「바꾸기」를 눌러 편 상태에 도달할 수 없어,
 * 목록 자체는 여기서 직접 렌더해야 계측된다.
 */
export function BookList({
  books,
  selectedId = null,
  disabled,
  onPick,
}: {
  books: BookOption[];
  selectedId?: number | null;
  disabled: boolean;
  onPick: (book: BookOption) => void;
}) {
  return (
    <>
      {books.map((book) => (
        <Button
          key={book.id}
          display="block"
          variant={book.id === selectedId ? 'fill' : 'weak'}
          size="medium"
          style={{ marginBottom: 8 }}
          disabled={disabled}
          onClick={() => onPick(book)}
        >
          {book.title}
        </Button>
      ))}
    </>
  );
}

/** 종료 직후 태깅 대상 — 책 없이 측정한 세션에 나중에 책을 붙인다. */
interface Untagged {
  sessionId: number;
}

/**
 * 타이머 홈 — `/api/dashboard` 렌더(오늘 진행률 · 시작/정지 · 읽는 중 책 · 잔디 미리보기 · 격언).
 * 서재 관리·검색·정원은 웹이 본진이라 미니앱에 두지 않는다(설계 §2.5).
 */
export function Home({
  dashboard,
  onTimerChange,
  onGraphChange,
  onGoHistory,
  onGoGoal,
  onError,
}: {
  dashboard: DashboardResponse;
  onTimerChange: (timer: TimerState) => void;
  onGraphChange: (graph: DashboardResponse['graph']) => void;
  onGoHistory: () => void;
  onGoGoal: () => void;
  onError: (error: Error) => void;
}) {
  const [untagged, setUntagged] = useState<Untagged | null>(null);
  /** 측정할 책 — 칩에 뜨는 그 책이고, 시작은 아래 주 버튼이 맡는다(여러 책을 번갈아 읽는 사람). */
  const [selectedBookId, setSelectedBookId] = useState(() =>
    defaultBookId(dashboard.readingBooks, dashboard.recentBookId),
  );
  /** 「바꾸기」로 목록을 펼쳤는지 — 시트를 따로 띄우지 않고 칩 아래에 인라인으로 편다(화면 다섯 개짜리 앱). */
  const [picking, setPicking] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [now, setNow] = useState(() => Date.now());
  /** 방금 지운 부채(초) — 성공 직후 한 줄 안내용. */
  const [waived, setWaived] = useState<number | null>(null);
  /** 알림 동의 캐시·지원 여부 — 렌더마다 다시 묻지 않게 초기값으로 한 번만 읽는다. */
  const [agreement, setAgreement] = useState(() => localStorage.getItem(AGREEMENT_KEY));
  const [agreementSupported] = useState(notificationAgreementSupported);

  useEffect(() => {
    if (!dashboard.hasActiveSession) return;
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, [dashboard.hasActiveSession]);

  const fail = (e: Error) => {
    // 401은 App이 재로그인으로 처리하고, 그 외(409 중복 시작 등)만 화면에 남긴다.
    if (e.name === 'UnauthorizedError') onError(e);
    else setError(e.message);
  };

  const run = (action: Promise<void>) => {
    setBusy(true);
    setError(null);
    action.catch(fail).finally(() => setBusy(false));
  };

  const start = (bookId: number | null) => run(startSession(bookId).then(onTimerChange));

  const stop = () =>
    run(
      stopSession().then((result) => {
        onTimerChange(result.timer);
        onGraphChange(result.graph); // stop 응답에 잔디가 동봉돼 새로고침 없이 즉시 갱신된다.
        if (result.untagged) setUntagged({ sessionId: result.sessionId });
      }),
    );

  const tag = (book: BookOption) => {
    if (untagged === null) return;
    run(tagBook(untagged.sessionId, book.id).then(() => setUntagged(null)));
  };

  /** 광고 보고 밀린 하루 지우기 — 중간 이탈(null)이면 아무 일도 없었던 것처럼 둔다. */
  const claimWaiver = () => {
    setBusy(true);
    setError(null);
    claimDebtWaiver(REWARD_AD_GROUP_ID)
      .then((result) => {
        if (result === null) return;
        onTimerChange(result.timer); // 부채·버튼 노출이 재조회 없이 갱신된다
        setWaived(result.waivedSeconds);
      })
      .catch((e: Error) => setError(waiverErrorMessage(e)))
      .finally(() => setBusy(false));
  };

  /** 알림 동의 요청 — 결과(동의·이미동의·거절)가 캐시되면 카드가 사라진다. 미지원(null)이면 그대로 둔다. */
  const askNotification = () => {
    setBusy(true);
    setError(null);
    askNotificationAgreement()
      .then(setAgreement)
      .catch(() => setError('알림 동의를 요청하지 못했어요. 잠시 후 다시 시도해 주세요.'))
      .finally(() => setBusy(false));
  };

  const remaining = dashboard.remainingSeconds;
  const goal = dashboard.todayGoalSeconds;
  // 목표 미설정(0)이면 나눌 게 없다 — 게이지를 아예 안 그리고 목표 설정으로 유도한다.
  const progress = goal > 0 ? Math.min(1, Math.max(0, (goal - remaining) / goal)) : null;
  const elapsed =
    dashboard.hasActiveSession && dashboard.activeStartedAt !== null
      ? elapsedSeconds(dashboard.activeStartedAt, now)
      : 0;
  const quotes = dashboard.quotes ?? [];
  // 칩에 띄울 책 — 시작 버튼도 이 값을 그대로 쓴다(칩과 시작 대상이 어긋날 자리를 없앤다).
  const selectedBook = dashboard.readingBooks.find((b) => b.id === selectedBookId) ?? null;

  return (
    <Screen title={`${dashboard.nickname}님의 오늘`}>
      <div
        style={{
          padding: '28px 20px',
          borderRadius: 16,
          background: 'var(--adaptiveGrey100, #FCFAF5)',
          textAlign: 'center',
        }}
      >
        {/* 라벨과 값은 각자 블록이어야 세로로 쌓인다 — 같은 줄에 붙으면 "오늘 남은 시간15분"으로 읽힌다. */}
        <div>
          <Text typography="st11" color="grey600">
            {remaining > 0 ? '오늘 남은 시간' : '오늘 목표를 채웠어요'}
          </Text>
        </div>
        <div style={{ marginTop: 6 }}>
          <Text typography="t2" fontWeight="bold">
            {remaining > 0 ? formatDuration(remaining) : `+${formatDuration(-remaining)}`}
          </Text>
        </div>
        {progress !== null && (
          <div style={{ marginTop: 16 }}>
            <ProgressBar progress={progress} size="normal" color="#2F8F6B" />
            <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 8 }}>
              오늘 목표 {formatDuration(goal)} 중 {Math.round(progress * 100)}%
            </Text>
          </div>
        )}
        {dashboard.carriedDebtSeconds > 0 && (
          <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 8 }}>
            어제까지 밀린 시간 {formatDuration(dashboard.carriedDebtSeconds)} 포함
          </Text>
        )}
        {/* 광고는 죄책감이 뜬 이 자리에만 나타난다. 문구에 "광고"를 명시해 광고 위장 금지 조항을 지킨다. */}
        {showWaiverButton(dashboard.carriedDebtSeconds, dashboard.debtWaiverAvailable, REWARD_AD_GROUP_ID) && (
          <Button
            variant="weak"
            size="small"
            style={{ marginTop: 10 }}
            disabled={busy}
            onClick={claimWaiver}
          >
            광고 보고 밀린 하루 지우기
          </Button>
        )}
        {waived !== null && (
          <Text typography="st12" color="blue500" style={{ display: 'block', marginTop: 8 }}>
            밀린 {formatDuration(waived)}을 지웠어요. 잔디는 그대로예요.
          </Text>
        )}
        {dashboard.hasActiveSession && (
          <Text typography="t5" color="blue500" style={{ display: 'block', marginTop: 16 }}>
            측정 중 {formatDuration(elapsed)}
            {dashboard.activeBookTitle !== null && ` · ${dashboard.activeBookTitle}`}
          </Text>
        )}
      </div>

      {/* 알림 동의 — 발송은 동의한 유저에게만 가능하고, 동의를 받는 주체는 미니앱이다(콘솔 심사 조건). */}
      {shouldShowNotificationCard(agreement, agreementSupported) && (
        <section style={sectionStyle}>
          <Text typography="st11" color="grey600" style={{ display: 'block', marginBottom: 10 }}>
            목표 달성과 완독 소식을 토스 알림으로 받아보세요
          </Text>
          <Button display="block" variant="weak" size="medium" disabled={busy} onClick={askNotification}>
            알림 받기
          </Button>
        </section>
      )}

      {!dashboard.hasActiveSession && selectedBook !== null && (
        <section style={sectionStyle}>
          <Text typography="st11" color="grey600" style={{ display: 'block', marginBottom: 10 }}>
            이 책으로 측정할까요?
          </Text>
          {/* 칩 = 지금 고른 책 하나. 목록을 상시 펼쳐 두면 책이 늘수록 홈이 목록 화면이 된다(웹과 같은 접근). */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <CoverInitial title={selectedBook.title} />
            <Text typography="st11" style={{ flex: 1, textAlign: 'left' }}>
              {selectedBook.title}
            </Text>
            <Button variant="weak" size="small" disabled={busy} onClick={() => setPicking((p) => !p)}>
              바꾸기
            </Button>
          </div>
          {picking && (
            <div style={{ marginTop: 10 }}>
              <BookList
                books={dashboard.readingBooks}
                selectedId={selectedBook.id}
                disabled={busy}
                onPick={(book) => {
                  setSelectedBookId(book.id);
                  setPicking(false); // 고르면 곧바로 칩으로 되돌아간다(같은 책을 다시 골라도 접힌다).
                }}
              />
            </div>
          )}
        </section>
      )}

      {untagged !== null && (
        <section style={sectionStyle}>
          <Text typography="st11" style={{ display: 'block', marginBottom: 10 }}>
            방금 측정, 무슨 책이었나요?
          </Text>
          {/* 태깅은 "고른 책"이 없다 — selectedId를 안 주면 전부 weak로 나열된다. */}
          <BookList books={dashboard.readingBooks} disabled={busy} onPick={tag} />
          <Button display="block" variant="weak" size="medium" onClick={() => setUntagged(null)}>
            나중에
          </Button>
        </section>
      )}

      <ErrorMessage message={error} />

      {/* 주 버튼은 "칩에 뜬 책으로 시작" 하나 — 칩이 없으면(책 0권) 그대로 책 없이 시작이다. */}
      <Button
        display="block"
        color={dashboard.hasActiveSession ? 'danger' : 'primary'}
        style={{ marginTop: 24 }}
        loading={busy}
        onClick={dashboard.hasActiveSession ? stop : () => start(selectedBook?.id ?? null)}
      >
        {dashboard.hasActiveSession ? '측정 끝내기' : '측정 시작'}
      </Button>

      {/* 고른 책이 있을 때만 탈출구를 둔다 — 고를 책이 없는데 "책 없이"를 되묻는 건 군더더기다. */}
      {!dashboard.hasActiveSession && selectedBook !== null && (
        <Button
          display="block"
          variant="weak"
          size="medium"
          style={{ marginTop: 8 }}
          disabled={busy}
          onClick={() => start(null)}
        >
          책 없이 시작
        </Button>
      )}

      <GrassPreview graph={dashboard.graph} onGoHistory={onGoHistory} />

      {quotes.length > 0 && <QuoteCard quotes={quotes} />}

      <Button display="block" variant="weak" size="medium" style={{ marginTop: 12 }} onClick={onGoGoal}>
        목표 바꾸기
      </Button>
    </Screen>
  );
}

/** 잔디 미리보기 — 최근 15주만, 카드 폭을 꽉 채워서. 카드 전체가 기록 화면 진입점이다. */
function GrassPreview({
  graph,
  onGoHistory,
}: {
  graph: DashboardResponse['graph'];
  onGoHistory: () => void;
}) {
  return (
    <button type="button" onClick={onGoHistory} style={cardStyle}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
        <Text typography="st11" color="grey600">
          {graph.growthStageEmoji} 연속 {graph.currentStreak}일
        </Text>
        <Text typography="st12" color="grey600">
          기록 보기 ›
        </Text>
      </div>
      <GrassGrid weeks={graph.weeks.slice(-PREVIEW_WEEKS)} fill />
    </button>
  );
}

/** 작가 격언 — 서버가 셔플해 준 목록에서 하나. 탭하면 다음 격언으로 넘어간다. */
function QuoteCard({ quotes }: { quotes: QuoteDto[] }) {
  const [index, setIndex] = useState(() => Math.floor(Math.random() * quotes.length));
  const quote = quotes[index % quotes.length];

  return (
    <button type="button" onClick={() => setIndex((i) => (i + 1) % quotes.length)} style={cardStyle}>
      <Text typography="st11" style={{ display: 'block', textAlign: 'left' }}>
        “{quote.text}”
      </Text>
      <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 8, textAlign: 'left' }}>
        — {quote.author}
      </Text>
    </button>
  );
}

/** 탭 가능한 카드 공통 — button 기본 스타일을 지워 div처럼 보이게 한다(접근성은 button이 맡는다). */
const cardStyle = {
  display: 'block',
  width: '100%',
  marginTop: 12,
  padding: 16,
  border: 'none',
  borderRadius: 12,
  background: 'var(--adaptiveGrey100, #FCFAF5)',
  textAlign: 'left',
  cursor: 'pointer',
} as const;
