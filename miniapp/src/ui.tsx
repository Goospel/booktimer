import { Text } from '@toss/tds-mobile';
import { useState } from 'react';
import type { ReactNode } from 'react';

import type { ContributionDay } from './api';

/** 잔디 색 농도 0~4 — 웹 app.css `--grass-0..4`와 같은 값(서버가 level을 계산해 준다). */
const LEVEL_COLORS = ['#EAE4D7', '#C3D9B0', '#94BE7F', '#5E9250', '#35662F'];

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
                outline: day.manual ? '1px solid #9A9486' : undefined, // 웹 --neutral-3
              }}
            />
          ))}
        </div>
      ))}
    </div>
  );
}

/**
 * 그릴 표지 주소 — 없거나(`null`) **그 주소가 방금 로드에 실패했으면** 자리 채움으로 떨어뜨린다.
 *
 * <p>실패를 boolean으로 들면 목록 재사용(같은 자리에 다른 책이 오는 경우)에서 앞 책의 실패가 뒤 책으로
 * 번져 멀쩡한 표지가 사라진다 — 그래서 "실패한 주소"를 들고 지금 주소와 대조한다.
 */
export function coverSource(url: string | null, failedUrl: string | null): string | null {
  return url === null || url === failedUrl ? null : url;
}

/**
 * 책 표지 썸네일 — 서재·검색·책방·스토리가 같은 컴포넌트를 쓴다.
 *
 * <p>표지가 없는 책도 같은 자리를 차지해야 목록의 줄 높이가 책마다 들쭉날쭉해지지 않는다 — 그래서
 * `null`이면 아무것도 안 그리는 대신 같은 크기의 자리 채움 상자를 그린다. 원격 표지(알라딘 등)의
 * **로드 실패도 같은 상자로** 떨어뜨린다 — 안 그러면 브라우저의 깨진 이미지 아이콘이 그대로 노출된다.
 * `alt=""`는 의도적이다(제목이 바로 옆 줄에 있어 표지를 다시 읽어 주면 같은 말이 두 번 들린다).
 */
export function BookCover({ url, width = 40 }: { url: string | null; width?: number }) {
  const [failedUrl, setFailedUrl] = useState<string | null>(null);
  const box = { width, height: Math.round(width * 1.4), borderRadius: 4, flex: '0 0 auto' } as const;
  const src = coverSource(url, failedUrl);

  if (src === null) {
    return (
      <div
        aria-hidden="true"
        style={{
          ...box,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontSize: Math.round(width * 0.45),
          background: 'var(--adaptiveGrey200, #E4DDD0)',
        }}
      >
        📚
      </div>
    );
  }
  return (
    <img
      src={src}
      alt=""
      loading="lazy"
      onError={() => setFailedUrl(src)}
      style={{ ...box, objectFit: 'cover' }}
    />
  );
}

/**
 * 섹션 블록 — 구분 없이 나열되던 목록에 카드 경계를 준다(홈·소셜의 카드 위계).
 * 크림 캔버스(--bg) 위 카드지(--card-bg)는 명도차가 작아 배경만으로는 경계가 안 보인다 → 보더를 함께 쓰는
 * 웹 카드 문법을 그대로 옮겼다.
 */
export const sectionStyle = {
  marginTop: 20,
  padding: 16,
  borderRadius: 12,
  background: '#FCFAF5',
  border: '1px solid #E4DDD0',
} as const;

/** 화면 공통 껍데기 — 제목 + 본문 여백. 미니앱은 화면이 다섯 뿐이라 레이아웃도 이 하나면 된다. */
export function Screen({ title, children }: { title: string; children: ReactNode }) {
  return (
    <main style={{ padding: '24px 20px 40px', maxWidth: 480, margin: '0 auto' }}>
      {/* 제목만 세리프(고운바탕) — 웹 `.brand h1`과 같은 위계다. 본문은 전역 고운돋움 그대로. */}
      <Text typography="t3" fontWeight="bold" style={{ marginBottom: 20, fontFamily: "'Gowun Batang', serif" }}>
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
