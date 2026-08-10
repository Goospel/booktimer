import { Text } from '@toss/tds-mobile';
import type { ReactNode } from 'react';

import type { ContributionDay } from './api';

/** 잔디 색 농도 0~4 — 웹 잔디와 같은 단계 체계(서버가 level을 계산해 준다). */
const LEVEL_COLORS = ['#ebedf0', '#c6e6c8', '#8fd694', '#4caf50', '#2e7d32'];

/** 잔디 그리드 — 기록 화면(전체)과 홈 미리보기(최근 몇 주)가 같은 렌더를 쓴다. */
export function GrassGrid({ weeks, cellSize = 11 }: { weeks: ContributionDay[][]; cellSize?: number }) {
  return (
    <div style={{ display: 'flex', gap: 3 }}>
      {weeks.map((week, weekIndex) => (
        <div key={weekIndex} style={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
          {week.map((day, dayIndex) => (
            <div
              key={dayIndex}
              title={day.date ?? ''}
              style={{
                width: cellSize,
                height: cellSize,
                borderRadius: 2,
                // 날짜 없는 칸은 그리드 가장자리 placeholder라 빈 칸으로 둔다.
                background: day.date === null ? 'transparent' : LEVEL_COLORS[day.level],
                outline: day.manual ? '1px solid #9e9e9e' : undefined,
              }}
            />
          ))}
        </div>
      ))}
    </div>
  );
}

/**
 * 책 표지 썸네일 — 서재·검색·책방이 같은 크기를 쓴다.
 *
 * <p>표지가 없는 책도 같은 자리를 차지해야 목록의 줄 높이가 책마다 들쭉날쭉해지지 않는다 — 그래서
 * `null`이면 아무것도 안 그리는 대신 같은 크기의 자리 채움 상자를 그린다. `alt=""`는 의도적이다
 * (제목이 바로 옆 줄에 있어 표지를 다시 읽어 주면 같은 말이 두 번 들린다).
 */
export function BookCover({ url, width = 40 }: { url: string | null; width?: number }) {
  const box = { width, height: Math.round(width * 1.4), borderRadius: 4, flex: '0 0 auto' } as const;

  if (url === null) {
    return (
      <div
        aria-hidden="true"
        style={{
          ...box,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontSize: Math.round(width * 0.45),
          background: 'var(--adaptiveGrey200, #e5e8eb)',
        }}
      >
        📚
      </div>
    );
  }
  return <img src={url} alt="" loading="lazy" style={{ ...box, objectFit: 'cover' }} />;
}

/** 섹션 블록 — 구분 없이 나열되던 목록에 옅은 배경으로 경계를 준다(홈·소셜의 카드 위계). */
export const sectionStyle = { marginTop: 20, padding: 16, borderRadius: 12, background: '#f9fafb' } as const;

/** 화면 공통 껍데기 — 제목 + 본문 여백. 미니앱은 화면이 다섯 뿐이라 레이아웃도 이 하나면 된다. */
export function Screen({ title, children }: { title: string; children: ReactNode }) {
  return (
    <main style={{ padding: '24px 20px 40px', maxWidth: 480, margin: '0 auto' }}>
      <Text typography="t3" fontWeight="bold" style={{ marginBottom: 20 }}>
        {title}
      </Text>
      {children}
    </main>
  );
}

/** 서버가 준 실패 메시지를 그대로 보여준다(연결 코드 오류·409 등은 문구 자체가 안내다). */
export function ErrorMessage({ message }: { message: string | null }) {
  if (message === null) return null;
  return (
    <Text typography="st11" color="red500" style={{ display: 'block', marginTop: 12 }}>
      {message}
    </Text>
  );
}

export function Loading({ message = '불러오는 중…' }: { message?: string }) {
  return (
    <main style={{ padding: 40, textAlign: 'center' }}>
      <Text typography="st11" color="grey600">
        {message}
      </Text>
    </main>
  );
}
