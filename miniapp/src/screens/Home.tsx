import { Button, Text } from '@toss/tds-mobile';
import { useEffect, useState } from 'react';

import type { BookOption, DashboardResponse, TimerState } from '../api';
import { startSession, stopSession, tagBook } from '../api';
import { elapsedSeconds, formatDuration } from '../format';
import { ErrorMessage, Screen } from '../ui';

/** 종료 직후 태깅 대상 — 책 없이 측정한 세션에 나중에 책을 붙인다. */
interface Untagged {
  sessionId: number;
}

/**
 * 타이머 홈 — `/api/dashboard` 축약 렌더(오늘 남은 시간 · 시작/정지 · 읽는 책 선택 · 미태깅 세션 태깅).
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
  const [bookId, setBookId] = useState<number | null>(dashboard.recentBookId);
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

  const start = () =>
    run(startSession(bookId).then(onTimerChange));

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
  const elapsed =
    dashboard.hasActiveSession && dashboard.activeStartedAt !== null
      ? elapsedSeconds(dashboard.activeStartedAt, now)
      : 0;

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
            무슨 책을 읽나요?
          </Text>
          <BookPicker
            books={dashboard.readingBooks}
            selected={bookId}
            onSelect={(id) => setBookId(id === bookId ? null : id)}
          />
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
        onClick={dashboard.hasActiveSession ? stop : start}
      >
        {dashboard.hasActiveSession ? '측정 끝내기' : '측정 시작'}
      </Button>

      <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>
        <Button display="block" variant="weak" size="medium" onClick={onGoHistory}>
          기록 보기
        </Button>
        <Button display="block" variant="weak" size="medium" onClick={onGoGoal}>
          목표 바꾸기
        </Button>
      </div>
    </Screen>
  );
}

function BookPicker({
  books,
  selected,
  onSelect,
}: {
  books: BookOption[];
  selected: number | null;
  onSelect: (id: number) => void;
}) {
  return (
    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
      {books.map((book) => (
        <Button
          key={book.id}
          size="small"
          variant={selected === book.id ? 'fill' : 'weak'}
          onClick={() => onSelect(book.id)}
        >
          {book.title}
        </Button>
      ))}
    </div>
  );
}
