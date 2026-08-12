import { Button, Text } from '@toss/tds-mobile';
import { useState } from 'react';
import type { ReactNode } from 'react';

import type { ContributionDay } from './api';

/** 잔디 색 농도 0~4 — 웹 app.css `--grass-0..4`와 같은 값(서버가 level을 계산해 준다). */
export const LEVEL_COLORS = ['#EAE4D7', '#C3D9B0', '#94BE7F', '#5E9250', '#35662F'];

/** 수동 기록 칸의 테두리 — 웹 `--neutral-3`. 격자와 범례가 같은 값을 봐야 범례가 거짓말을 안 한다. */
export const MANUAL_OUTLINE = '1px solid #9A9486';

/** 주 컬럼 사이 간격 — 격자와 월 라벨 배치가 이 값을 공유해야 라벨이 그 열 위에 선다. */
export const GRASS_GAP = 3;

/**
 * 서버 `monthLabels`(주 인덱스 + "M월")를 격자 위 픽셀 자리로 옮긴다. 웹은 CSS 그리드의
 * `gridColumnStart`가 열을 맞춰 주지만 미니앱 격자는 flex + 고정 칸이라 자리를 직접 센다.
 *
 * <p>직전에 **남긴** 라벨과 `minGapPx`보다 가까운 라벨은 버린다 — 그래프가 월말에서 시작하면 0주·1주에
 * 라벨이 연달아 붙어 글자가 겹쳐 읽힌다. 버린 라벨이 아니라 남긴 라벨을 기준으로 재야, 촘촘한 구간에서
 * 기준점이 끌려가며 뒤 라벨까지 줄줄이 사라지지 않는다.
 */
export function monthLabelPositions(
  labels: { weekIndex: number; label: string }[],
  cellSize: number,
  minGapPx = 28,
): { label: string; left: number }[] {
  const kept: { label: string; left: number }[] = [];
  for (const { weekIndex, label } of labels) {
    const left = weekIndex * (cellSize + GRASS_GAP);
    if (kept.length > 0 && left - kept[kept.length - 1].left < minGapPx) continue;
    kept.push({ label, left });
  }
  return kept;
}

/**
 * 잔디 그리드 — 기록 화면(전체)과 홈 미리보기(최근 몇 주)가 같은 렌더를 쓴다.
 *
 * <p>`fill`이면 칸 크기를 컨테이너가 정한다 — 주 컬럼이 폭을 나눠 갖고(`flex:1`) 칸은 정사각 비율로
 * 따라온다. 홈 카드처럼 **폭이 정해진 자리**용이다. 기록 화면은 가로 스크롤이 전제라 고정 px가 맞다
 * (폭을 나눠 가지면 주 수가 늘수록 칸이 무한히 작아진다).
 */
export function GrassGrid({
  weeks,
  cellSize = 11,
  fill = false,
}: {
  weeks: ContributionDay[][];
  cellSize?: number;
  fill?: boolean;
}) {
  return (
    <div style={{ display: 'flex', gap: GRASS_GAP, width: fill ? '100%' : undefined }}>
      {weeks.map((week, weekIndex) => (
        <div
          key={weekIndex}
          style={{ display: 'flex', flexDirection: 'column', gap: GRASS_GAP, flex: fill ? 1 : undefined }}
        >
          {week.map((day, dayIndex) => (
            <div
              key={dayIndex}
              title={day.date ?? ''}
              style={{
                width: fill ? '100%' : cellSize,
                height: fill ? undefined : cellSize,
                aspectRatio: fill ? '1 / 1' : undefined,
                borderRadius: 2,
                // 날짜 없는 칸은 그리드 가장자리 placeholder라 빈 칸으로 둔다.
                background: day.date === null ? 'transparent' : LEVEL_COLORS[day.level],
                outline: day.manual ? MANUAL_OUTLINE : undefined,
              }}
            />
          ))}
        </div>
      ))}
    </div>
  );
}

/**
 * 무표지 자리 표지의 배경색 팔레트 — 웹 `books/pure.ts` `COVER_PALETTE`와 같은 12색(종이톤).
 * 웹은 `{bg, fg}`를 돌려주지만 fg가 상수라 미니앱은 색 문자열 하나로 줄였다.
 */
export const COVER_PALETTE = [
  '#B8C2A6', '#D6C3B0', '#C7B89B', '#A9B9A0', '#B0B7A8', '#CBB9A3',
  '#C3CBC0', '#BFC8B4', '#CFC0AE', '#D9C8A9', '#C2BBA8', '#B5A98F',
] as const;

export const COVER_FG = 'rgba(44,42,36,0.5)';

/** 표지 대신 세울 첫 글자 — 앞뒤 공백은 버리고, 빈 제목은 `?`로(빈 상자가 되면 표지 자리가 무너진다). */
export function initialOf(title: string): string {
  const t = (title ?? '').trim();
  return t === '' ? '?' : t.charAt(0);
}

/**
 * 제목에서 결정적으로 고른 배경색 — **같은 책은 언제 그려도 같은 색**이어야 다시 그릴 때 색이 튀지 않는다.
 * 웹과 같은 해시(부호 없는 32bit `h*31 + charCode`)·같은 팔레트라 웹/미니앱의 같은 책이 같은 색이 된다.
 */
export function coverColor(seed: string): string {
  const key = seed ?? '';
  let h = 0;
  for (let i = 0; i < key.length; i++) h = (h * 31 + key.charCodeAt(i)) >>> 0;
  return COVER_PALETTE[h % COVER_PALETTE.length];
}

/** 무표지 책의 자리 표지 — `BookOption`엔 표지 주소가 없어 첫 글자 + 제목색 상자로 대신한다. */
export function CoverInitial({ title, width = 32 }: { title: string; width?: number }) {
  return (
    <div
      aria-hidden="true"
      style={{
        width,
        height: Math.round(width * 1.4),
        borderRadius: 4,
        flex: '0 0 auto',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        fontSize: Math.round(width * 0.5),
        background: coverColor(title),
        color: COVER_FG,
      }}
    >
      {initialOf(title)}
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

/**
 * 화면 공통 껍데기 — 제목 + 본문 여백. 미니앱은 화면이 다섯 뿐이라 레이아웃도 이 하나면 된다.
 *
 * <p>`onBack`을 주면 제목 왼쪽에 ← 를 세운다. 나갈 길이 화면 맨 아래에만 있으면 목록이 긴 화면에서
 * 나가려고 끝까지 스크롤해야 한다 — 헤더가 제목을 그리는 자리라 뒤로가기도 여기가 맡는다.
 */
export function Screen({ title, onBack, children }: { title: string; onBack?: () => void; children: ReactNode }) {
  return (
    <main style={{ padding: '24px 20px 40px', maxWidth: 480, margin: '0 auto' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 20 }}>
        {onBack !== undefined && (
          <button
            type="button"
            aria-label="뒤로"
            onClick={onBack}
            style={{
              flex: '0 0 auto',
              width: 32,
              height: 32,
              padding: 0,
              border: 'none',
              borderRadius: 999,
              fontSize: 20,
              lineHeight: 1,
              background: 'transparent',
              color: 'var(--adaptiveGrey700, #57534A)',
              cursor: 'pointer',
            }}
          >
            ←
          </button>
        )}
        {/* 제목만 세리프(고운바탕) — 웹 `.brand h1`과 같은 위계다. 본문은 전역 고운돋움 그대로. */}
        {/* 한글 제목이 flex 자식이라 minWidth:0이 없으면 줄바꿈 대신 ← 를 밀어낸다. */}
        <Text
          typography="t3"
          fontWeight="bold"
          style={{ flex: 1, minWidth: 0, fontFamily: "'Gowun Batang', serif", wordBreak: 'keep-all' }}
        >
          {title}
        </Text>
      </div>
      {children}
    </main>
  );
}

/**
 * 서버가 준 실패 메시지를 그대로 보여준다(연결 코드 오류·409 등은 문구 자체가 안내다).
 *
 * <p>`onRetry`를 주면 그 자리에서 다시 받을 수 있다 — 초기 로드가 실패하면 빨간 글자만 남아
 * 미니앱을 껐다 켜야 하는 막다른 길이었다. 되돌릴 게 없는 실패(액션 거절 등)엔 주지 않는다.
 */
export function ErrorMessage({ message, onRetry }: { message: string | null; onRetry?: () => void }) {
  if (message === null) return null;
  return (
    <>
      <Text typography="st11" color="red500" style={{ display: 'block', marginTop: 12 }}>
        {message}
      </Text>
      {onRetry !== undefined && (
        <Button size="small" variant="weak" style={{ marginTop: 12 }} onClick={onRetry}>
          다시 시도
        </Button>
      )}
    </>
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
