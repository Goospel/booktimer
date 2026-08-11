import { Button, ProgressBar, Text } from '@toss/tds-mobile';
import { useEffect, useState } from 'react';

import type { BookOption, DashboardResponse, QuoteDto, TimerState, WaiveResponse } from '../api';
import { ApiError, startSession, stopSession, tagBook, waiveDebt } from '../api';
import { elapsedSeconds, formatDuration } from '../format';
import { REWARD_AD_GROUP_ID, watchRewardAd } from '../toss';
import { ErrorMessage, GrassGrid, Screen, sectionStyle } from '../ui';

/** 홈 잔디 미리보기 폭 — 최근 5주만 축약해 보여주고 전체는 기록 화면이 맡는다. */
const PREVIEW_WEEKS = 5;

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
 * 실패 문구 — 서버가 준 평문(409 "오늘은 이미 사용했어요" 등)은 그대로 쓰고, SDK가 준 광고 에러는
 * 영문·기술 문구라 그대로 띄우면 안 되므로 안내로 바꾼다.
 */
export function waiverErrorMessage(error: Error): string {
  return error instanceof ApiError ? error.message : '광고를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.';
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
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [now, setNow] = useState(() => Date.now());
  /** 방금 지운 부채(초) — 성공 직후 한 줄 안내용. */
  const [waived, setWaived] = useState<number | null>(null);

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

  const remaining = dashboard.remainingSeconds;
  const goal = dashboard.todayGoalSeconds;
  // 목표 미설정(0)이면 나눌 게 없다 — 게이지를 아예 안 그리고 목표 설정으로 유도한다.
  const progress = goal > 0 ? Math.min(1, Math.max(0, (goal - remaining) / goal)) : null;
  const elapsed =
    dashboard.hasActiveSession && dashboard.activeStartedAt !== null
      ? elapsedSeconds(dashboard.activeStartedAt, now)
      : 0;
  const quotes = dashboard.quotes ?? [];

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

      {!dashboard.hasActiveSession && dashboard.readingBooks.length > 0 && (
        <section style={sectionStyle}>
          <Text typography="st11" color="grey600" style={{ display: 'block', marginBottom: 10 }}>
            읽는 중인 책 — 탭하면 바로 측정을 시작해요
          </Text>
          {dashboard.readingBooks.map((book) => (
            <Button
              key={book.id}
              display="block"
              variant="weak"
              size="medium"
              style={{ marginBottom: 8 }}
              disabled={busy}
              onClick={() => start(book.id)}
            >
              {book.title}
            </Button>
          ))}
        </section>
      )}

      {untagged !== null && (
        <section style={sectionStyle}>
          <Text typography="st11" style={{ display: 'block', marginBottom: 10 }}>
            방금 측정, 무슨 책이었나요?
          </Text>
          {dashboard.readingBooks.map((book) => (
            <Button
              key={book.id}
              display="block"
              variant="weak"
              size="medium"
              style={{ marginBottom: 8 }}
              disabled={busy}
              onClick={() => tag(book)}
            >
              {book.title}
            </Button>
          ))}
          <Button display="block" variant="weak" size="medium" onClick={() => setUntagged(null)}>
            나중에
          </Button>
        </section>
      )}

      <ErrorMessage message={error} />

      <Button
        display="block"
        color={dashboard.hasActiveSession ? 'danger' : 'primary'}
        style={{ marginTop: 24 }}
        loading={busy}
        onClick={dashboard.hasActiveSession ? stop : () => start(null)}
      >
        {dashboard.hasActiveSession
          ? '측정 끝내기'
          : dashboard.readingBooks.length > 0
            ? '책 없이 측정 시작'
            : '측정 시작'}
      </Button>

      <GrassPreview graph={dashboard.graph} onGoHistory={onGoHistory} />

      {quotes.length > 0 && <QuoteCard quotes={quotes} />}

      <Button display="block" variant="weak" size="medium" style={{ marginTop: 12 }} onClick={onGoGoal}>
        목표 바꾸기
      </Button>
    </Screen>
  );
}

/** 잔디 미리보기 — 최근 5주만. 카드 전체가 기록 화면 진입점이다. */
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
      <GrassGrid weeks={graph.weeks.slice(-PREVIEW_WEEKS)} cellSize={14} />
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
