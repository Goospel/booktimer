import { Button, ProgressBar, Text } from '@toss/tds-mobile';
import { useEffect, useState } from 'react';

import type { BookOption, DashboardResponse, QuoteDto, TimerState } from '../api';
import { startSession, stopSession, tagBook } from '../api';
import { elapsedSeconds, formatDuration } from '../format';
import { ErrorMessage, GrassGrid, Screen } from '../ui';

/** 홈 잔디 미리보기 폭 — 최근 5주만 축약해 보여주고 전체는 기록 화면이 맡는다. */
const PREVIEW_WEEKS = 5;

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
          background: 'var(--adaptiveGrey100, #f2f4f6)',
          textAlign: 'center',
        }}
      >
        <Text typography="st11" color="grey600" style={{ display: 'block' }}>
          {remaining > 0 ? '오늘 남은 시간' : '오늘 목표를 채웠어요'}
        </Text>
        <Text typography="t2" fontWeight="bold" style={{ display: 'block', marginTop: 6 }}>
          {remaining > 0 ? formatDuration(remaining) : `+${formatDuration(-remaining)}`}
        </Text>
        {progress !== null && (
          <div style={{ marginTop: 16 }}>
            <ProgressBar progress={progress} size="normal" color="#4caf50" />
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
        {dashboard.hasActiveSession && (
          <Text typography="t5" color="blue500" style={{ display: 'block', marginTop: 16 }}>
            측정 중 {formatDuration(elapsed)}
            {dashboard.activeBookTitle !== null && ` · ${dashboard.activeBookTitle}`}
          </Text>
        )}
      </div>

      {!dashboard.hasActiveSession && dashboard.readingBooks.length > 0 && (
        <section style={{ marginTop: 24 }}>
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
        <section style={{ marginTop: 24 }}>
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
  background: 'var(--adaptiveGrey100, #f2f4f6)',
  textAlign: 'left',
  cursor: 'pointer',
} as const;
