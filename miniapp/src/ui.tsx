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
 * 책 표지 썸네일 — 서재·검색·책방·여백이 같은 컴포넌트를 쓴다.
 *
 * <p>표지가 없는 책도 같은 자리를 차지해야 목록의 줄 높이가 책마다 들쭉날쭉해지지 않는다 — 그래서
 * `null`이면 아무것도 안 그리는 대신 같은 크기의 자리 채움 상자를 그린다. 원격 표지(알라딘 등)의
 * **로드 실패도 같은 상자로** 떨어뜨린다 — 안 그러면 브라우저의 깨진 이미지 아이콘이 그대로 노출된다.
 * `alt=""`는 의도적이다(제목이 바로 옆 줄에 있어 표지를 다시 읽어 주면 같은 말이 두 번 들린다).
 *
 * <p>`eager`는 접힌 위 첫 화면에 서는 표지(홈 캐러셀)만 켠다 — 홈은 탭을 오갈 때마다 재마운트되는데
 * lazy면 그때마다 표지가 한 박자 늦게 뜬다. 접힌 아래 목록(서재·프로필)은 그대로 lazy가 맞다.
 */
export function BookCover({ url, width = 40, eager = false }: { url: string | null; width?: number; eager?: boolean }) {
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
      loading={eager ? 'eager' : 'lazy'}
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
 * 바텀시트 공통 껍데기 — 딤 + 하단 패널 + 제목 줄(닫기 ✕).
 *
 * <p>TDS `BottomSheet`을 쓰지 않는다: 포털(`tds-mobile-portal-container`)로 그려져
 * `renderToStaticMarkup` 하니스에서 **마크업이 통째로 비어 나온다**(실측) — 이 저장소는 jsdom을 두지
 * 않기로 했으므로 시트 내용이 영영 계측 불가가 된다. 딤·safe-area·zIndex는 이 30줄로 충분하다.
 *
 * <p>홈의 태깅 시트와 서재의 「펼쳐보기」·「관리」가 같은 껍데기를 쓴다 — 셋이 각자 딤과 zIndex를
 * 들고 있으면 탭바(zIndex 100) 위를 덮는 규칙이 한 군데만 어긋나도 시트 아래로 탭바가 비친다.
 */
export function Sheet({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  return (
    <>
      {/* 딤 — 탭바(zIndex 100) 위를 덮어야 시트 아래로 탭바가 비치지 않는다. */}
      <div onClick={onClose} style={{ position: 'fixed', inset: 0, zIndex: 200, background: 'rgba(0, 0, 0, 0.45)' }} />
      <div
        role="dialog"
        aria-modal="true"
        aria-label={title}
        style={{
          position: 'fixed',
          left: 0,
          right: 0,
          bottom: 0,
          zIndex: 201,
          maxHeight: '78vh',
          overflowY: 'auto',
          // 홈 인디케이터 위로 마지막 줄이 올라오게 — 바닥 여백만 safe-area를 탄다.
          padding: '20px 20px calc(20px + env(safe-area-inset-bottom))',
          borderRadius: '16px 16px 0 0',
          background: '#FCFAF5',
          boxShadow: '0 -4px 20px rgba(0, 0, 0, 0.14)',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12 }}>
          <Text typography="t6" fontWeight="bold" style={{ flex: 1, minWidth: 0, wordBreak: 'keep-all' }}>
            {title}
          </Text>
          <button
            type="button"
            aria-label="닫기"
            onClick={onClose}
            style={{
              flex: '0 0 auto',
              width: 32,
              height: 32,
              padding: 0,
              border: 'none',
              borderRadius: 999,
              fontSize: 16,
              lineHeight: 1,
              background: 'transparent',
              color: 'var(--adaptiveGrey700, #57534A)',
              cursor: 'pointer',
            }}
          >
            ✕
          </button>
        </div>
        {children}
      </div>
    </>
  );
}

/**
 * 화면 공통 껍데기 — 제목 + 본문 여백. 미니앱은 화면이 다섯 뿐이라 레이아웃도 이 하나면 된다.
 *
 * <p>`onBack`을 주면 **제목 위 줄에 「‹ 돌아가기」 알약**을 세운다. 나갈 길이 화면 맨 아래에만 있으면
 * 목록이 긴 화면에서 나가려고 끝까지 스크롤해야 한다 — 그래서 위다.
 *
 * <p>글자를 붙인 것은 취향이 아니라 두 번의 제보다(2026-08-16): 배경 없는 `←` 글리프는 ① 버튼으로
 * 안 보이고 ② 직선 화살표가 「이전 화면」보다 「왼쪽 이동」으로 읽힌다. 아이콘을 아무리 다듬어도 뜻은
 * 읽는 사람의 추론에 맡겨지므로, 인식률을 아이콘 디자인에 걸지 않고 글자로 못 박는다.
 *
 * <p>제목은 **선택**이다. 홈처럼 첫 카드가 곧 히어로인 화면에서는 제목이 정보를 하나도 안 보태면서
 * 자리만 먹었다(「구스펠님의 오늘」은 바로 아래 「오늘 읽은 시간」의 중복이고, 그 화면 이름은 탭바가
 * 이미 말한다). 없으면 빈 행을 남기지 않고 **헤더를 통째로 생략**한다 — 자리만 비우면 지운 값이 없다.
 *
 * <p>제목 위아래로 슬롯이 하나씩 있다. `above`는 <b>화면 소속이 아니라 그 위에 얹히는 도구</b> 자리다
 * (책방의 검색 진입 아이콘) — 본문에 끼우면 「…님의 책방」 아래에 검색창이 오는 어색한 순서가
 * 된다. `subtitle`은 반대로 <b>제목에 딸린 식별자</b>(@핸들)라 제목과 떨어지면 다른 정보처럼 읽힌다 —
 * 그래서 있으면 제목 행의 아래 여백을 좁혀 밀착시킨다.
 */
export function Screen({
  title,
  onBack,
  right,
  above,
  subtitle,
  children,
}: {
  title?: string;
  onBack?: () => void;
  /** 제목 줄 오른쪽 끝 손잡이(서재의 「펼쳐보기」) — 제목이 없으면 그릴 줄 자체가 없다. */
  right?: ReactNode;
  /** 제목보다 **위**에 얹히는 도구 줄 — 제목이 없는 화면에서도 그린다. */
  above?: ReactNode;
  /** 제목 **바로 아래**에 밀착하는 부제(@핸들) — 있으면 제목 행 여백이 20 → 3으로 좁아진다. */
  subtitle?: ReactNode;
  children: ReactNode;
}) {
  return (
    <main style={{ padding: '24px 20px 40px', maxWidth: 480, margin: '0 auto' }}>
      {/* 나갈 길이 맨 위다 — 제목·도구줄보다 앞. 제목이 없는 화면에서도 그린다(출구는 제목과 무관). */}
      {onBack !== undefined && (
        <div style={{ display: 'flex', marginBottom: 14 }}>
          <button
            type="button"
            onClick={onBack}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 2,
              height: 34,
              padding: '0 14px 0 8px',
              border: 'none',
              borderRadius: 999,
              background: 'var(--adaptiveGrey100, #EDE7DA)',
              color: 'var(--adaptiveGrey700, #57534A)',
              fontSize: 14,
              fontWeight: 600,
              cursor: 'pointer',
            }}
          >
            {/* 꺾쇠다 — 직선 화살표는 「이전 화면」이 아니라 「왼쪽 이동」으로 읽힌다. */}
            <svg
              width="20"
              height="20"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth={2.2}
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              <path d="M14.5 5 8 12l6.5 7" />
            </svg>
            돌아가기
          </button>
        </div>
      )}
      {above}
      {title !== undefined && (
      <>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: subtitle === undefined ? 20 : 3 }}>
        {/* 제목만 세리프(고운바탕) — 웹 `.brand h1`과 같은 위계다. 본문은 전역 고운돋움 그대로. */}
        <Text
          typography="t3"
          fontWeight="bold"
          style={{ flex: 1, minWidth: 0, fontFamily: "'Gowun Batang', serif", wordBreak: 'keep-all' }}
        >
          {title}
        </Text>
        {right}
      </div>
      {subtitle}
      </>
      )}
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
