import { Button, Loader, Text as TdsText, TextField } from '@toss/tds-mobile';
import { useState } from 'react';
import type { ComponentProps, CSSProperties, ReactNode } from 'react';

import type { ContributionDay, UserRow } from './api';

/**
 * 글자색 토큰 이름 → CSS 색 — TDS `Text`의 `color`는 <b>CSS 색 문자열</b>이지 토큰 이름이 아니다.
 *
 * <p>TDS는 이 prop을 인라인 `--tds-paragraph-color`에 그대로 박고 `color: var(--tds-paragraph-color,
 * var(--adaptiveGrey900))`로 소비한다. `grey600` 같은 이름은 무효 색이라 선언이 통째로 버려지고
 * <b>색이 상속으로 떨어진다</b> — 곧 본문과 완전히 같은 잉크다. 그렇게 죽어 있던 호출부가 87곳이었다
 * (목 모드 실측 2026-08-23: `color="grey600"`인 Text와 prop 없는 Text가 똑같이 `rgb(33,37,41)`).
 *
 * <p>값의 출처는 `global.css`의 `html:root` 팔레트다. <b>fallback을 함께 주는 이유</b>: `--adaptiveRed500`
 * 처럼 이 앱이 아예 정의하지 않는 토큰이 실제로 있고, 미정의 `var()`는 다시 상속으로 풀려 고친 자리가
 * 조용히 원위치한다(그게 위 Grey900 폴백이 아무 일도 못 한 이유이기도 하다).
 */
const INK: Record<string, string> = {
  grey600: 'var(--adaptiveGrey600, #5B5A4D)',
  grey700: 'var(--adaptiveGrey700, #43423A)',
  grey800: 'var(--adaptiveGrey800, #2A2921)',
  blue500: 'var(--adaptiveBlue500, #5B7F55)',
  blue700: 'var(--adaptiveBlue700, #3F5A3C)',
  red500: 'var(--adaptiveRed500, #F04452)',
};

/**
 * TDS `Text` — 색 토큰 이름을 값으로 옮기는 통로. 화면은 <b>반드시</b> 이걸 쓴다(TDS에서 직접 import 하면
 * 그 화면만 다시 토큰 이름이 무효가 된다 — `typography.test.tsx`가 import 경로를 가드한다).
 *
 * <p>이미 CSS 색인 값(`#4F6B4C`·`var(…)`)은 그대로 흘려보낸다. 표에 없는 <b>토큰처럼 생긴</b> 값은
 * dev에서 시끄럽게 짚는다 — 조용히 상속으로 떨어지는 게 이 버그의 정체였으니, 다음 색은 소리를 내야 한다.
 */
export function Text({ color, ...rest }: ComponentProps<typeof TdsText>) {
  const resolved = color == null ? color : (INK[color] ?? color);
  if (import.meta.env.DEV && resolved === color && typeof color === 'string' && /^[a-z]+\d{2,3}$/.test(color)) {
    console.warn(`[Text] color="${color}"는 CSS 색이 아니다 — ui.tsx의 INK 표에 없으면 상속으로 떨어진다.`);
  }
  return <TdsText color={resolved} {...rest} />;
}

/**
 * 잔디 색 농도 0~4 — 웹 app.css `--grass-0..4`와 같은 값(서버가 level을 계산해 준다).
 *
 * <p>리터럴이 아니라 <b>토큰 경유</b>인 이유는 공부 모드다: `body.study-mode`가 이 토큰을 파랑
 * 사다리로 갈아 끼워, 잔디·범례·하루 막대가 컴포넌트 한 줄 없이 따라온다(`--accentPill`과 같은 수법).
 * 리터럴은 fallback으로 남아 독서 렌더는 픽셀 하나 안 바뀐다.
 */
export const LEVEL_COLORS = [
  'var(--grass0, #E7E2D5)',
  'var(--grass1, #CFD9C0)',
  'var(--grass2, #A9BD99)',
  'var(--grass3, #6E9565)',
  'var(--grass4, #3F5A3C)',
];

/** 수동 기록 칸의 테두리 — 웹 `--neutral-3`. 격자와 범례가 같은 값을 봐야 범례가 거짓말을 안 한다. */
export const MANUAL_OUTLINE = '1px solid #9A9486';

/** 잔디 오늘 칸 링 — 범례 「오늘」 스와치도 같은 값을 둘러야 범례가 거짓말을 안 한다. */
export const TODAY_RING = '0 0 0 2px var(--adaptiveGrey100, #FBF9F4), 0 0 0 4.5px var(--adaptiveBlue700, #3F5A3C)';

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
  today,
}: {
  weeks: ContributionDay[][];
  cellSize?: number;
  fill?: boolean;
  /**
   * 오늘(ISO) — 그 날짜 칸 하나에 두 겹 링을 두른다(시안 Soft-History). 안쪽 한 겹은 카드 면색이라 링이 칸에서
   * 떨어져 보인다. 둘 다 blur 0이라 칸 수만큼 반복돼도 재래스터가 없다(§6). 0단 칸 전체에 두르는 옅은 링(§2-4)은
   * 없다. 오늘 링은 0단이어도 선다 — 아직 안 읽은 오늘이 가장 「오늘이 어디인가」를 물을 때다.
   */
  today?: string;
}) {
  return (
    <div style={{ display: 'flex', gap: GRASS_GAP, width: fill ? '100%' : undefined }}>
      {weeks.map((week, weekIndex) => (
        <div
          key={weekIndex}
          style={{ display: 'flex', flexDirection: 'column', gap: GRASS_GAP, flex: fill ? 1 : undefined }}
        >
          {week.map((day, dayIndex) => {
            const isToday = today !== undefined && day.date === today;
            return (
              <div
                key={dayIndex}
                title={day.date ?? ''}
                style={{
                  width: fill ? '100%' : cellSize,
                  height: fill ? undefined : cellSize,
                  aspectRatio: fill ? '1 / 1' : undefined,
                  // 11px 칸에 시안의 7px은 동그라미가 된다 — 판단값 3.
                  borderRadius: 3,
                  // 날짜 없는 칸은 그리드 가장자리 placeholder라 빈 칸으로 둔다.
                  background: day.date === null ? 'transparent' : LEVEL_COLORS[day.level],
                  outline: day.manual ? MANUAL_OUTLINE : undefined,
                  boxShadow: isToday ? TODAY_RING : undefined,
                  // 링 바깥 4.5px이 칸 간격 3px을 넘어 이웃 칸에 걸친다 — 층을 한 칸 올려야 문서 순서상 뒤 칸
                  // 밑에 깔리지 않고 사방이 고르게 보인다. 1이면 충분하다(99 이상은 덮개 판정식에 걸린다, T-183).
                  position: isToday ? 'relative' : undefined,
                  zIndex: isToday ? 1 : undefined,
                }}
              />
            );
          })}
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

/**
 * 사람 아바타 — 무표지 책과 같은 이니셜 원(<b>같은 사람은 언제 그려도 같은 색</b>).
 *
 * <p>책방 프로필과 홈 헤더가 같은 것을 쓴다 — 화면마다 색이 다르면 같은 사람이 다른 사람으로 읽힌다.
 * 링은 두르지 않는다: 발광은 책 격자가 지는 신호라 신원 아바타까지 두르면 뜻이 흐려진다.
 */
export function Avatar({ nickname, size = 72 }: { nickname: string; size?: number }) {
  return (
    <div
      aria-hidden="true"
      style={{
        flex: '0 0 auto',
        width: size,
        height: size,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        borderRadius: '50%',
        fontSize: Math.round(size * 0.375),
        background: coverColor(nickname),
        color: COVER_FG,
        ...SERIF_VALUE, // 이니셜은 값·인용 축(세리프)이다 — 손글씨는 톤 조율 A에서 걷었다
      }}
    >
      {initialOf(nickname)}
    </div>
  );
}

/** 무표지 책의 자리 표지 — `BookOption`엔 표지 주소가 없어 첫 글자 + 제목색 상자로 대신한다. */
export function CoverInitial({ title, width = 32, radius = 4 }: { title: string; width?: number; radius?: number }) {
  return (
    <div
      aria-hidden="true"
      style={{
        width,
        height: Math.round(width * 1.4),
        borderRadius: radius,
        flex: '0 0 auto',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        fontSize: Math.round(width * 0.5),
        background: coverColor(title),
        color: COVER_FG,
        ...SERIF_VALUE, // 표지 이니셜 — 아바타 이니셜과 한 몸(세리프)
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
 * `null`이면 아무것도 안 그리는 대신 같은 크기의 자리 채움을 그린다. 원격 표지(알라딘 등)의
 * **로드 실패도 같은 자리 채움으로** 떨어뜨린다 — 안 그러면 브라우저의 깨진 이미지 아이콘이 그대로 노출된다.
 * `alt=""`는 의도적이다(제목이 바로 옆 줄에 있어 표지를 다시 읽어 주면 같은 말이 두 번 들린다).
 *
 * <p><b>`title`을 주면 자리 채움이 {@link CoverInitial}(첫 글자 + 제목색)이 된다</b>(2026-08-18).
 * 전에는 여기서 `📚` 이모지를 그렸는데, 흔한 기본 이모지는 「AI가 만든 화면」이라는 인상을 준다. 게다가
 * 무표지 책이 여럿이면 죄다 같은 회색 상자라 서로 구분되지도 않았다 — 이 레포엔 이미 그 문제를 푼
 * `CoverInitial`이 있었고 <b>여기만 안 닿았을 뿐</b>이라, 없애는 김에 그 패턴으로 합류시킨다.
 *
 * <p>그래서 호출부의 `coverUrl !== null ? <BookCover> : <CoverInitial>` 삼항 세 개가 사라졌다
 * (홈 캐러셀·서재 상세·여백 머리) — 같은 분기를 세 곳이 손으로 하던 것을 이 컴포넌트가 흡수한 것이다.
 * `title`이 없는 자리(제목을 모르는 문맥)는 예전처럼 무채색 상자로 떨어진다.
 *
 * <p>`eager`는 접힌 위 첫 화면에 서는 표지(홈 캐러셀)만 켠다 — 홈은 탭을 오갈 때마다 재마운트되는데
 * lazy면 그때마다 표지가 한 박자 늦게 뜬다. 접힌 아래 목록(서재·프로필)은 그대로 lazy가 맞다.
 */
export function BookCover({
  url,
  title,
  width = 40,
  radius = 4,
  eager = false,
}: {
  url: string | null;
  /** 있으면 자리 채움이 첫 글자 + 제목색이 된다. 없으면 무채색 상자. */
  title?: string;
  width?: number;
  /** 모서리 — 기본 4. 홈·서재 캐러셀만 8(고른 칸의 후광과 동심이 되게). */
  radius?: number;
  eager?: boolean;
}) {
  const [failedUrl, setFailedUrl] = useState<string | null>(null);
  const box = { width, height: Math.round(width * 1.4), borderRadius: radius, flex: '0 0 auto' } as const;
  const src = coverSource(url, failedUrl);

  if (src === null) {
    if (title !== undefined) return <CoverInitial title={title} width={width} radius={radius} />;
    return <div aria-hidden="true" style={{ ...box, background: 'var(--adaptiveGrey200, #E4DDD0)' }} />;
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
 * Soft 부푼 면 — 카드의 기본 표면. 선이 아니라 면의 부풂(밝은 쪽 하이라이트 + 어두운 쪽 그림자)으로
 * 바탕과 갈린다.
 *
 * <p>⚠️ 그림자 값은 **CSS 변수 경유**다(`global.css`의 `--puffShadow`). 리터럴로 적으면 밤(독서등)·공부
 * 모드가 인라인을 이기려고 자리마다 `!important` 규칙을 둬야 한다 — 연필선이 겪은 고질의 재판이고,
 * 낮의 흰 하이라이트가 밤 카드에 그대로 새어 어두운 화면에 흰 테가 뜬다. 변수면 `body` 클래스 한 벌이
 * 값만 갈아 끼운다(`LEVEL_COLORS`·`--accentPill`과 같은 수법). 폴백은 낮 값이다.
 *
 * <p>인라인 상수인 이유: 하니스가 `renderToStaticMarkup`이라 CSS 클래스는 계측이 안 된다.
 * ⚠️ 이 그림자를 애니메이션하지 않는다 — 값이 변하면 프레임마다 재페인트다(T-176).
 */
export const PUFF = {
  background: 'var(--adaptiveGrey100, #FBF9F4)',
  boxShadow: 'var(--puffShadow, 10px 10px 24px rgba(112,96,64,.15), -8px -8px 20px rgba(255,255,255,.9))',
  borderRadius: 26,
} as const;

/** Soft 눌린 면 — 트랙·입력·눌린 묶음. 그림자가 안쪽(inset)이라 면이 바탕 아래로 파인다. */
export const DENT = {
  background: 'var(--softDent, #EAE5D9)',
  boxShadow: 'var(--dentShadow, inset 3px 3px 7px rgba(112,96,64,.16), inset -3px -3px 7px rgba(255,255,255,.85))',
  borderRadius: 20,
} as const;

/** 보조 손잡이 — 규칙 1 「보조 버튼은 1.5px 테두리」. 카드 위에 선다. */
export const SOFT_OUTLINE = {
  border: '1.5px solid var(--adaptiveBlue700, #3F5A3C)',
  borderRadius: 18,
  background: 'var(--adaptiveGrey100, #FBF9F4)',
  color: 'var(--adaptiveBlue700, #3F5A3C)',
} as const;

/** 시트 안 행 버튼 — 시트 바닥과 같은 색이라 선이 있어야 누르는 줄로 읽힌다. */
export const SOFT_ROW = {
  border: '1.5px solid var(--adaptiveGrey200, #DED8CA)',
  borderRadius: 14,
  background: 'var(--adaptiveGrey100, #FBF9F4)',
} as const;

/**
 * 새싹 세 획(줄기 · 왼 잎 · 오른 잎) — 24 격자. 홈의 「오늘 목표 달성」 표식과 메달 한가운데가 <b>같은 새싹</b>이라
 * 모양을 한 곳에 둔다(표식은 획만, 메달은 잎을 채워 그린다).
 */
export const SPROUT_PATHS = [
  'M12 20v-6',
  'M12 14c0-3.5-2.5-6-6-6 0 3.5 2.5 6 6 6z',
  'M12 14c0-3.5 2.5-6 6-6 0 3.5-2.5 6-6 6z',
] as const;

/**
 * 목표 달성 메달 — 코인 + 리본 둘 + 새싹(시안 Soft-Goal). 이 앱의 「부푼 입체 오브젝트」는 한 화면에 하나라
 * 달성 순간엔 이게 그 하나다.
 *
 * <p>튀는 움직임은 `global.css`의 `.medal-pop`(transform·opacity만, 1회). SVG 안에 글자·이미지가 없어 튀는
 * 동안 다시 그릴 콘텐츠가 없다(T-176). 색은 그림의 색이라 리터럴이다 — 메달은 독서 모드·측정 밖에서만 선다.
 */
export function GoalMedal({ size = 132 }: { size?: number }) {
  const id = `medal-coin-${size}`;
  return (
    <svg
      className="medal-pop"
      data-medal=""
      aria-hidden="true"
      width={size}
      height={Math.round((size * 130) / 120)}
      viewBox="0 0 120 130"
      style={{ display: 'block', flex: 'none' }}
    >
      <defs>
        <radialGradient id={id} cx="0.38" cy="0.32" r="0.75">
          <stop offset="0" stopColor="#FFF8DF" />
          <stop offset="0.7" stopColor="#EEDB97" />
          <stop offset="1" stopColor="#D9BF6A" />
        </radialGradient>
      </defs>
      <ellipse cx="60" cy="124" rx="34" ry="5" fill="rgba(112,96,64,0.2)" />
      <path d="M40 86 L30 122 L44 114 L50 126 L58 92 Z" fill="#6E9565" />
      <path d="M80 86 L90 122 L76 114 L70 126 L62 92 Z" fill="#3F5A3C" />
      <circle cx="60" cy="56" r="46" fill={`url(#${id})`} />
      <circle cx="60" cy="56" r="36" fill="none" stroke="#FFFFFF" strokeWidth="3" opacity="0.7" />
      <ellipse cx="44" cy="30" rx="14" ry="6" fill="#FFFFFF" opacity="0.55" transform="rotate(-24 44 30)" />
      {/* 새싹 — 24 격자의 (12,14)를 코인 중심(60,56)에 맞춰 3배로 키운다. */}
      <g transform="translate(24 14) scale(3)" stroke="#3F5A3C" strokeWidth="1.1" strokeLinecap="round" strokeLinejoin="round">
        <path d={SPROUT_PATHS[0]} fill="none" />
        <path d={SPROUT_PATHS[1]} fill="#8FA986" />
        <path d={SPROUT_PATHS[2]} fill="#B5C6A5" />
      </g>
    </svg>
  );
}

/**
 * 섹션 카드 — 부푼 면 그대로. 배경이 리터럴이 아니라 토큰이라야 독서등이 이 카드도 함께 밤으로 데려간다.
 */
export const sectionStyle = { marginTop: 20, padding: 18, ...PUFF } as const;

/**
 * 섹션 머리 아래 실선 — 시안(턴2)이 카드 안 머리에 그은 그 선이다. 그은 자리는 <b>넷</b>이다:
 * 2a 캐러셀 머리 · 2b 탭 머리 · 2c 여백 헤더 · 2d 월 헤더. 머리와 그 아래 목록이 한 덩어리로 뭉치는
 * 것을 막는다. 홈 「읽는 중」은 <b>시안에 없고</b> 캐러셀과 같은 슬롯이라 더한 다섯 번째다.
 *
 * <p>⚠️ 선은 <b>제목이 아니라 줄</b>에 건다. 위 넷 중 셋은 제목 옆에 카운트·손잡이·합계가 서므로,
 * {@link SectionTitle}에 걸면 선이 줄 한가운데서 끊긴다 — 그래서 공용 컴포넌트가 아니라 자리마다 준다.
 *
 * <p>색은 시안의 생 `rgba(44,42,36,.12)`가 아니라 토큰이다: 그 값은 <b>잉크를 12%로 깐 것</b>이라
 * 밤(독서등)에 카드지와 함께 어두워져 선이 통째로 사라진다. 낮 계산값(≈`#E3E1DC`)이 이 토큰과 한 톤
 * 안이라 <b>낮의 그림은 그대로 두면서 밤만 산다</b> — 「새 색을 만들지 않는다」는 이 파일의 원칙과 같은 방향.
 */
export const SECTION_RULE = '1px solid var(--adaptiveGrey200, #DED8CA)';

/**
 * 값(수)·성취 이름을 세리프로 — 웹이 이미 쓰는 축을 미니앱에도 놓는다.
 *
 * <p>본문이 개구(손글씨)이던 시절엔 그게 이미 700이라 <b>굵기로는 더 강조할 수 없었다</b>(700 위가
 * 없다) — 이 상수가 태어난 이유다. 축이 뒤집혀 본문이 400이 된 지금은 굵기도 다시 쓸 수 있지만,
 * <b>값은 여전히 세리프가 맡는다</b>: 굵기는 「중요하다」를 말하고 세리프는 「이건 수다」를 말한다.
 * 크기만 키우면
 * 카드가 세로로 커지므로, 남은 축이 색과 <b>서체</b>다. 웹 app.css는 제목·숫자·강조를 고운바탕으로
 * 바꾸는 축을 39곳에서 쓰는데(`.bd-accum-value`·`.shop-count strong`·`.record-time` …) 미니앱은 그
 * 폰트를 불러만 놓고 화면 제목 한 곳에서만 썼다. 손글씨 옆의 세리프는 「적어 둔 값」으로 읽혀,
 * 크기를 덜 키우고도 눈에 먼저 든다.
 *
 * <p>클래스가 아니라 <b>인라인 스타일 상수</b>인 이유는 {@link PUFF}·{@link sectionStyle}과 같다 —
 * 이 저장소의 테스트 하니스는 `renderToStaticMarkup` 정적 렌더라 <b>css를 적용하지 않는다</b>.
 * 인라인이라야 「이 값이 세리프로 오는가」를 마크업에서 계측할 수 있다.
 *
 * <p>`tabular-nums`가 한 쌍인 이유: 이 상수가 붙는 자리는 대부분 <b>변하는 수</b>(측정 중 시계·누적
 * 시간·팔로워 수)라, 폭이 들쭉날쭉하면 값이 바뀔 때마다 글자가 좌우로 흔들린다.
 */
export const SERIF_VALUE = {
  fontFamily: "'Gowun Batang', serif",
  /*
   * 굵기를 상수가 든다 — 값은 화면에서 **읽히는 수**라 항상 700이다. 사용처 8곳 중 7곳이 이미 인라인
   * 700을 적고 있었고 한 곳(`Profile`의 통계 수치)만 빠져 있었는데, 오늘은 그게 안 보인다:
   * `@import`가 `Gowun+Batang:wght@700` 단일이라 400 요청도 700 face로 매칭되기 때문이다.
   * 즉 **잠재 취약점**이다 — @import에 웨이트 축이 붙거나 폴백으로 떨어지는 순간 그 숫자만 얇아진다.
   * 여기 두면 그 자리가 상수를 쓰는 것만으로 닫힌다.
   */
  fontWeight: 700,
  fontVariantNumeric: 'tabular-nums',
} as const;

/**
 * 채움 주 버튼 — <b>한 화면의 주 동작 하나</b>에만 쓴다(서재=여백에 글쓰기 · 목표=저장).
 * 홈은 탭바 가운데 원이 그 역할이라 여기 해당하지 않는다.
 *
 * <p>위계가 3단이 된다: <b>채움</b>(화면의 주 동작) &gt; primary(연한 세이지 — 시트·확인 흐름의 긍정
 * 동작) &gt; weak(연필선 — 보조). 채움이 화면마다 여럿이면 그 축이 곧 무너지므로 개수를 소스로 센다.
 *
 * <p><b>마커 커스텀 프로퍼티를 선택자 키로 쓴다</b>(`global.css`의 대응 규칙 참고). TDS Button은
 * variant를 가려낼 표지가 class에도 data 속성에도 없어 인라인 값 자체를 키로 쓸 수밖에 없는데,
 * hex를 키로 쓰는 기존 방식과 달리 <b>이름만</b> 보므로 콜론 뒤 공백 직렬화 차이에 안 걸리고
 * danger red 팔레트를 css에 적을 일도 생기지 않는다.
 *
 * <p>TDS `Button`을 그대로 감싸는 이유: 목표 「저장」의 `loading`·`disabled`가 공짜로 따라온다.
 */
export function FilledButton({ style, ...props }: ComponentProps<typeof Button>) {
  return <Button {...props} style={{ ...style, ['--btn-filled' as string]: '1' }} />;
}

/**
 * 섹션 제목 — 카드·목록 덩어리의 머리.
 *
 * <p>전에는 자리마다 `<Text typography="st11" color="grey600">`을 손으로 적었다. 그건 <b>본문과 같은
 * 크기에 더 흐린</b> 글자라, 제목이 자기가 이끄는 본문보다 약했다 — 훑는 사람에겐 덩어리의 시작이
 * 안 보인다. 크기를 한 단 올리고(st10) 잉크색으로 되돌린다.
 *
 * <p>역할에 이름을 붙여 컴포넌트로 꺼낸 이유: 같은 `st11 · grey600` 조합이 <b>제목이 아닌 자리</b>에도
 * 스무 곳 넘게 쓰인다(빈 목록 안내·시트 설명문·보조 문구). 그것들은 흐린 게 맞으므로 일괄 치환이
 * 불가능하고, 「여기는 제목이다」라는 판단이 코드에 남아야 다음 화면에서도 같은 결정을 반복할 수 있다.
 */
export function SectionTitle({ children, style }: { children: ReactNode; style?: CSSProperties }) {
  return (
    <Text typography="st10" fontWeight="bold" style={{ display: 'block', wordBreak: 'keep-all', ...style }}>
      {children}
    </Text>
  );
}

/**
 * 바텀시트 공통 껍데기 — 딤 + 하단 패널 + 제목 줄(닫기 ✕).
 *
 * <p>TDS `BottomSheet`을 쓰지 않는다: 포털(`tds-mobile-portal-container`)로 그려져
 * `renderToStaticMarkup` 하니스에서 **마크업이 통째로 비어 나온다**(실측) — 이 저장소는 jsdom을 두지
 * 않기로 했으므로 시트 내용이 영영 계측 불가가 된다. 딤·safe-area·zIndex는 이 30줄로 충분하다.
 *
 * <p>홈의 태깅 시트와 서재의 「펼쳐보기」·「관리」, 여백 쓰기가 같은 껍데기를 쓴다 — 넷이 각자 딤과
 * zIndex를 들고 있으면 탭바(zIndex 100) 위를 덮는 규칙이 한 군데만 어긋나도 시트 아래로 탭바가 비친다.
 *
 * <p>올라오는 움직임은 `global.css`의 `.sheet-dim`·`.sheet-panel`이 든다. 인라인 style이 아닌 이유는
 * `prefers-reduced-motion`이 인라인 선언을 이길 수 없어서다(그 파일의 주석 참고).
 *
 * <p><b>`onDimClose`는 딤 탭만 따로 받는 문</b>이다(기본값 = `onClose`라 기존 세 시트는 그대로). 딤은
 * 스치기만 해도 눌리는 <b>우발적</b> 출구여서, 되돌릴 수 없는 것을 든 시트는 여기만 막고 ✕는 열어 둘
 * 필요가 있다 — 여백 쓰기가 그 자리다(`StoryComposer`의 `dimClosable`). ✕와 한 핸들러로 묶으면
 * 딤을 막는 순간 ✕까지 잠겨 시트에 갇힌다.
 */
export function Sheet({
  title,
  onClose,
  onDimClose = onClose,
  children,
}: {
  title: string;
  onClose: () => void;
  /** 딤 탭 전용 — 생략하면 `onClose`와 같다. 원고를 든 시트만 여기를 좁힌다. */
  onDimClose?: () => void;
  children: ReactNode;
}) {
  return (
    <>
      {/* 딤 — 탭바(zIndex 100) 위를 덮어야 시트 아래로 탭바가 비치지 않는다. */}
      <div
        className="sheet-dim"
        onClick={onDimClose}
        // 딤은 순검정이 아니라 잉크(#1E1E18)를 깐다 — 따뜻한 바탕 위에 순검정을 덮으면 화면이 잿빛으로 식는다(톤 조율 A).
        style={{ position: 'fixed', inset: 0, zIndex: 200, background: 'rgba(30, 30, 24, 0.45)' }}
      />
      <div
        className="sheet-panel"
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
          borderRadius: '26px 26px 0 0',
          background: 'var(--adaptiveGrey100, #FBF9F4)',
          boxShadow: '0 -8px 24px rgba(112, 96, 64, 0.15)',
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
 * <p>나가는 길은 **토스 네이티브 내비게이션 바의 뒤로가기**다(2026-09-02 심사 체크리스트 필수 항목 —
 * 자체 뒤로가기와 동시 노출 금지). 08-16의 「‹ 돌아가기」 알약(#830·#831)은 그래서 걷었다.
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
  right,
  above,
  subtitle,
  children,
}: {
  title?: string;
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

/**
 * 검색 입력칸 — 제출 손잡이가 <b>칸 안</b>에 있다(2026-08-21). 「책 추가」와 「친구 찾기」가 함께 쓴다.
 *
 * <p>예전엔 칸 아래 전폭 「검색」 버튼이었다. 그런데 입력 하나짜리 form은 브라우저가 엔터를 곧 제출로
 * 치므로 엔터는 이미 됐고, 사람은 제목을 치고 엔터를 친다 — 버튼은 자리만 먹었다(사용자 지적).
 * 그 자리가 비면서 「책 추가」 화면은 추천 카드에 쓸 세로 73px을 얻는다.
 *
 * <p>⚠️ <b>`enterKeyHint`가 이 변경의 절반이다.</b> 엔터가 <b>먹는데도</b> 키캡엔 「완료」라고 적혀
 * 있었다(레포 전체에 `enterkeyhint` 0건이었다). 버튼만 걷고 이걸 안 넣으면, 눌러도 되는지 모르는
 * 사람에게는 제출 수단이 통째로 사라진 화면이 된다. 둘은 반드시 한 쌍이다.
 *
 * <p>손잡이를 절대위치로 얹지 않는다 — TDS `TextField`가 `right` 슬롯을 이미 준다.
 *
 * <p>⚠️ <b>TDS의 `label`을 쓰지 않는다</b>(2026-08-21). 제목과 칸 사이가 63px이나 벌어져 있었는데
 * 그중 27px이 라벨 자리였고, TDS는 <b>값이 있을 때만</b> 라벨을 띄우므로 빈 칸에서는
 * `visibility: hidden`으로 <b>자리만 잡고 아무것도 안 그렸다</b> — 사용자가 처음 보는 그 상태에서
 * 27px이 통째로 헛것이었다는 뜻이다(사용자 지적: 「공백이 너무 넓어」). 값이 생겨 라벨이 떠도
 * 화면 제목이 이미 「책 추가」·「친구 찾기」라 보탤 말이 없다.
 *
 * <p>대신 이름은 `aria-label`로 옮긴다 — <b>안 그리는 것과 이름이 없는 것은 다르다</b>. 그림뿐인
 * 칸은 읽어 줄 이름이 사라지므로, 라벨을 걷을 때 이 한 줄을 같이 안 옮기면 접근성만 조용히 깎인다.
 *
 * <p>`paddingTop={0}`도 같은 이유다 — TDS 기본값 16px은 라벨을 제목에서 떼려던 여백인데 라벨이
 * 없으면 그냥 빈 자리다. 아래 16px은 남긴다(칸과 다음 카드가 붙으면 한 덩어리로 읽힌다).
 */
export function SearchField({
  label,
  placeholder,
  value,
  disabled = false,
  busy = false,
  onChange,
  onSubmit,
}: {
  /** 칸의 <b>이름</b>(`aria-label`) — 그려지지는 않는다. 위 주석의 라벨 제거 참조. */
  label: string;
  placeholder: string;
  value: string;
  disabled?: boolean;
  /** 요청이 도는 중 — 손잡이 자리가 로더로 바뀐다(옛 버튼의 `loading`이 하던 일). */
  busy?: boolean;
  onChange: (value: string) => void;
  onSubmit: () => void;
}) {
  const empty = value.trim() === '';
  return (
    <form
      className="search-field"
      onSubmit={(e) => {
        e.preventDefault(); // 막지 않으면 페이지가 새로고침돼 미니앱이 처음으로 돌아간다
        if (!disabled && !busy && !empty) onSubmit();
      }}
    >
      <TextField
        variant="box"
        aria-label={label}
        paddingTop={0}
        placeholder={placeholder}
        value={value}
        disabled={disabled || busy}
        enterKeyHint="search"
        onChange={(e) => onChange(e.target.value)}
        right={
          busy ? (
            <Loader size="small" />
          ) : (
            // ⚠️ `onClick`을 달지 않는다 — 이 버튼은 form 안의 `type="submit"`이라 탭하면 click과
            // submit이 **둘 다** 돈다(실측: click 1 + submit 1). 핸들러까지 달면 한 탭에 검색이 두 번
            // 나간다(GET이라 화면은 멀쩡해 보이고 알라딘 호출만 두 배가 된다 — 조용한 낭비).
            <button type="submit" aria-label="검색" disabled={disabled || empty} style={searchHandleStyle}>
              <SearchGlass dim={empty} />
            </button>
          )
        }
      />
    </form>
  );
}

/** 손잡이는 그림만 있어 몸집이 작다 — 44×44는 손가락이 닿는 최소치라 그림과 따로 잡는다. */
const searchHandleStyle = {
  width: 44,
  height: 44,
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  border: 0,
  padding: 0,
  background: 'transparent',
  cursor: 'pointer',
} as const;

/** 돋보기 — 기본 이모지를 쓰지 않기로 해서(2026-08-18) 선으로 그린다({@link OwnedCheck}과 같은 방식). */
function SearchGlass({ dim }: { dim: boolean }) {
  // 검색어가 없으면 흐린다 — 옛 버튼의 `disabled`가 하던 「아직 누를 때가 아니다」를 색이 잇는다.
  // 색은 토큰(`style.color` → `currentColor` — SVG 표현 속성은 CSS 변수를 못 푼다)이라 공부 모드가 데려간다.
  // 흐림은 옛 35% 알파 세이지와 같은 정도를 불투명도로 준다(정적 값 — 애니메이션 없음).
  return (
    <svg
      width="23"
      height="23"
      viewBox="0 0 24 24"
      fill="none"
      aria-hidden="true"
      style={{ color: 'var(--adaptiveBlue700, #3F5A3C)', opacity: dim ? 0.35 : 1 }}
    >
      <circle cx="10.5" cy="10.5" r="6.6" stroke="currentColor" strokeWidth="1.9" />
      <path d="M15.6 15.6 L20 20" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" />
    </svg>
  );
}

/**
 * 미읽음 수 알약 — 대화함 진입(책방 헤더)과 대화함 행이 같은 모양을 쓴다. 0이면 아무것도 안 그린다.
 * `unit`이 세는 것을 말한다: 헤더는 미읽음 **방** 수(`unreadRooms`)라 「대화」, 대화함 행은 그 방의 메시지 수라 「메시지」.
 */
export function UnreadBadge({ count, unit }: { count: number; unit: '대화' | '메시지' }) {
  if (count <= 0) return null;
  return (
    <span
      aria-label={`읽지 않은 ${unit} ${count}개`}
      style={{
        minWidth: 18,
        padding: '1px 6px',
        borderRadius: 999,
        background: 'var(--adaptiveBlue500, #5B7F55)',
        color: '#FFFDF8',
        fontSize: 13,
        lineHeight: '16px',
        textAlign: 'center',
      }}
    >
      {count}
    </span>
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

/**
 * 사용자 목록 — 검색 결과·팔로우 목록·좋아요 명단이 같은 줄 모양을 쓴다(서버도 같은 행 DTO를 준다).
 *
 * <p>책방 화면에 있던 것을 여기로 옮겼다(2026-08-20) — 여백의 좋아요 명단이 쓰기 시작하면서
 * 화면끼리 서로를 import 하는 순환이 생겼다. 공용 조각은 공용 자리에 둔다.
 */
export function UserList({
  users,
  emptyMessage,
  onSelect,
}: {
  users: UserRow[];
  emptyMessage: string;
  onSelect: (loginId: string) => void;
}) {
  if (users.length === 0) {
    return (
      <Text typography="st11" color="grey600" style={{ display: 'block' }}>
        {emptyMessage}
      </Text>
    );
  }

  return (
    <>
      {users.map((u) => (
        <button
          key={u.loginId}
          type="button"
          onClick={() => onSelect(u.loginId)}
          // 반복 행이라 부푼 그림자 대신 옅은 실선 행(SOFT_ROW)이다 — 행 수 × 큰 blur는 저사양 안드로이드의
          // 스크롤 재래스터를 부른다(설계 §6 그림자 예산). 검색 결과·팔로우 목록·좋아요 명단이 함께 바뀐다.
          style={{
            ...SOFT_ROW,
            display: 'block',
            width: '100%',
            padding: 16,
            marginBottom: 8,
            textAlign: 'left',
            cursor: 'pointer',
          }}
        >
          <Text typography="st11" style={{ display: 'block' }}>
            {u.nickname}
          </Text>
          <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 4 }}>
            @{u.loginId} · 공개 책 {u.publicBookCount}권{u.following && ' · 팔로잉'}
          </Text>
        </button>
      ))}
    </>
  );
}
