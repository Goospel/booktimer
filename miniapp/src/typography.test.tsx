import { TDSMobileProvider } from '@toss/tds-mobile';
import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import type { ReactNode } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';

import { History } from './screens/History';
import { ReadingNowCard } from './screens/Home';
import { StatItem } from './screens/Profile';
import { graph, userAgent } from './test-fixtures';
import { SectionTitle } from './ui';

/**
 * 타이포그래피 위계 — <b>개구(Gaegu)로 갈아탄 뒤 크기·강조가 무너진 자리</b>를 못 박는다(#857 후속).
 *
 * <p>세 가지가 겹쳐 있었다. ① 웹의 크기 보정(`html{font-size:112.5%}`)은 본문이 `rem`이라 먹지만
 * 미니앱 글자는 전부 px라 <b>닿는 텍스트가 0개</b>였다. ② TDS `Text`는 굵기를
 * `font-weight: var(--tds-paragraph-font-weight)`로 그리는데 그 변수는 `fontWeight` prop을 넘겼을 때만
 * 채워져, 안 넘기면 선언이 무효가 되고 <b>굵기가 상속으로 떨어진다</b> — `div` 안이면 body의 700,
 * `button` 안이면 UA 기본 400. ③ 본문이 이미 700이라 굵기로는 더 강조할 수 없는데, 웹이 쓰는
 * 세리프(고운바탕) 축을 미니앱은 화면 제목 한 곳에서만 썼다.
 *
 * <p>그래서 위계를 <b>크기 · 색 · 세리프</b> 셋이 맡고 굵기는 700 한 값으로 눕힌다. 아래 테스트가
 * 그 셋을 각각 지킨다.
 */

const css = readFileSync(new URL('./global.css', import.meta.url), 'utf8');

/** 주석을 걷어낸 css — 이 레포 주석엔 `button { font: 400 … }` 처럼 중괄호가 들어 있어, 규칙 구조를
 *  볼 땐 먼저 지워야 한다(안 지우면 `[^}]*`가 주석 속 `}`에서 멈춘다). */
const rules = css.replace(/\/\*[\s\S]*?\*\//g, '');

function render(node: ReactNode): string {
  return renderToStaticMarkup(<TDSMobileProvider userAgent={userAgent}>{node}</TDSMobileProvider>);
}

/**
 * 그 글자를 담은 요소의 여는 태그 — 정적 마크업이라 문자열로 캔다.
 *
 * <p>TDS `Text`는 `typography`·`color`·`style`을 <b>한 span의 인라인 스타일</b>로 합쳐 내보내므로
 * (`--tds-paragraph-text-font-size:var(--tds-t-st11-text-fontSize)` 꼴), 그 태그만 잘라 보면
 * 크기 토큰·흐림·세리프를 전부 판정할 수 있다.
 */
function tagOf(markup: string, text: string): string {
  const at = markup.indexOf('>' + text + '<');
  if (at < 0) return '';
  return markup.slice(markup.lastIndexOf('<', at), at);
}

describe('굵기는 자리로 정해지지 않는다', () => {
  it('button의 UA 기본 굵기를 끊는다 — 없으면 같은 <Text>가 버튼 안에서만 400으로 떨어진다', () => {
    // UA 기본 `button { font: 400 … }`이 `html body { font-weight: 700 }` 보정을 취소한다.
    // 그래서 기록 화면 날짜 줄이 「펼칠 수 있는 날(button)」만 한 줄 걸러 얇았다.
    expect(rules).toMatch(/\bbutton\s*\{[^}]*font-weight:\s*inherit/);
  });

  it('개구에 없는 굵기를 선언하지 않는다 — 500·600·900은 조용히 다른 값으로 떨어진다', () => {
    // 개구는 300·400·700만 있다. 500을 부르면 400으로 떨어져 **본문(700)보다 얇아지고**(책방 카운트
    // 숫자가 자기 라벨보다 얇던 자리), 600·900은 700으로 올림돼 주변과 똑같아진다 — 어느 쪽이든
    // 「강조를 선언했는데 화면은 그대로」다. 자리마다 고치는 대신 소스를 훑어 다시 새지 않게 한다.
    const offenders: string[] = [];
    for (const file of sourceFiles(new URL('.', import.meta.url).pathname)) {
      readFileSync(file, 'utf8')
        .split('\n')
        .forEach((line, i) => {
          if (/^\s*(\*|\/\/)/.test(line)) return; // 주석은 규칙 밖이다
          if (/font-?[Ww]eight[:=]?\s*['"]?(100|200|500|600|800|900|bolder|lighter)\b/.test(line.replace(/\s/g, ''))) {
            offenders.push(`${file.split(/[\\/]/).pop()}:${i + 1}  ${line.trim()}`);
          }
        });
    }
    expect(offenders).toEqual([]);
  });
});

/**
 * 스케일 밖의 크기 — 토큰이 못 닿는 인라인 `fontSize`도 같은 계단 위에 서야 한다.
 *
 * <p>토큰만 올리면 `<Text>`는 따라오지만 인라인 px로 적힌 칩·버튼·라벨은 옛 크기에 남아, 한 화면에
 * 두 체급이 산다. 값 하나하나를 눈으로 지키는 대신 <b>계단을 벗어난 값이 있는가</b>를 묻는다 —
 * 오늘 이 가드가 `10`·`12.5`·`13.5`를 찾아냈다.
 */
const SCALE = [11, 12, 13, 14, 15, 16, 17, 19, 24, 26, 44];

describe('인라인 크기도 같은 계단 위에 온다', () => {
  it('계단 밖의 fontSize 리터럴이 없다', () => {
    const offenders: string[] = [];
    for (const file of sourceFiles(new URL('.', import.meta.url).pathname)) {
      readFileSync(file, 'utf8')
        .split('\n')
        .forEach((line, i) => {
          if (/^\s*(\*|\/\/)/.test(line)) return;
          for (const m of line.matchAll(/fontSize:\s*([\d.]+)\b/g)) {
            if (!SCALE.includes(Number(m[1]))) {
              offenders.push(`${file.split(/[\\/]/).pop()}:${i + 1}  fontSize: ${m[1]}`);
            }
          }
        });
    }
    expect(offenders).toEqual([]);
  });
});

describe('크기 보정이 실제로 닿는다', () => {
  // 웹은 `rem`이라 `html{font-size:112.5%}`가 먹지만 미니앱은 전부 px다. 색 토큰(--adaptive*)을
  // 갈아끼운 것과 같은 수법으로 **크기 토큰 자체**를 개구 기준으로 다시 잡는다.
  it('본문·보조 토큰을 개구 기준으로 올린다', () => {
    expect(css).toMatch(/--tds-t-st11-text-fontSize:\s*15px/); // 본문 14 → 15
    expect(css).toMatch(/--tds-t-st12-text-fontSize:\s*13px/); // 보조 12 → 13
    expect(css).toMatch(/--tds-t-st13-text-fontSize:\s*12px/); // 칩·범례 11 → 12
  });

  it('제목·히어로 토큰은 층이 보이게 더 벌린다', () => {
    expect(css).toMatch(/--tds-t-st10-text-fontSize:\s*19px/); // 섹션 제목 16 → 19
    expect(css).toMatch(/--tds-t-t3-text-fontSize:\s*26px/); //  화면 제목 22 → 26
    expect(css).toMatch(/--tds-t-t2-text-fontSize:\s*44px/); //  홈 히어로 26 → 44
  });

  it('줄높이를 함께 올린다 — 크기만 올리면 커진 글자가 옛 줄 상자에 눌린다', () => {
    expect(css).toMatch(/--tds-t-st11-text-lineHeight:\s*23px/);
    expect(css).toMatch(/--tds-t-t2-text-lineHeight:\s*50px/);
  });
});

describe('본문 속 강조는 색이 진다', () => {
  it('<b>·<strong>을 세이지로 칠한다 — 개구엔 700 위가 없어 굵기로는 아무 일도 안 일어난다', () => {
    // TDS 리셋이 `b,strong{font-weight:bolder}`를 넣는데 개구엔 900이 없어 700, 즉 주변과 같아진다.
    // 탈퇴·아이디 변경 경고문의 강조가 통째로 보이지 않던 자리다. 웹 app.css가 `strong`을
    // --accent-hover로 칠하는 것과 같은 처방이다(같은 앱이 강조를 한 가지로 말한다).
    // `html` 접두는 명시도(0-0-2)다 — TDS 리셋이 우리 css 뒤에 주입돼 같은 0-0-1로는 순서에서 진다.
    expect(rules).toMatch(/html b,\s*html strong\s*\{[^}]*font-weight:\s*700/);
    expect(rules).toMatch(/html b,\s*html strong\s*\{[^}]*color:\s*var\(--adaptiveBlue700/);
  });
});

describe('섹션 제목이 본문보다 앞에 선다', () => {
  it('본문(st11)보다 큰 토큰을 쓴다 — 제목이 본문과 같은 크기면 덩어리의 시작이 안 보인다', () => {
    expect(tagOf(render(<SectionTitle>읽는 중</SectionTitle>), '읽는 중')).toContain('--tds-t-st10-text-fontSize');
  });

  it('흐린 글자가 아니다 — 제목이 자기가 이끄는 본문보다 흐리면 위계가 뒤집힌다', () => {
    expect(tagOf(render(<SectionTitle>읽는 중</SectionTitle>), '읽는 중')).not.toContain('grey600');
  });

  it('「읽는 중」 카드가 그 제목을 실제로 쓴다', () => {
    const card = render(<ReadingNowCard book={null} totalSeconds={0} />);
    expect(tagOf(card, '읽는 중')).toContain('--tds-t-st10-text-fontSize');
  });
});

describe('값(수)은 세리프로 온다', () => {
  // 웹은 제목·숫자·강조를 고운바탕으로 바꾸는 축을 이미 쓴다(app.css 39곳). 손글씨 옆의 세리프는
  // 「적어 둔 값」으로 읽혀, 크기를 덜 키우고도 눈에 먼저 든다.
  it('기록 통계값이 세리프다 — 카드에 담긴 게 그 수 하나인데 라벨과 3px 차이였다', () => {
    const history = render(<History graph={graph} />);
    expect(tagOf(history, `${graph.activeDays}일`)).toContain('Gowun Batang');
  });

  it('성장 단계 이름이 세리프다 — 그 카드가 말하려는 성취다', () => {
    const history = render(<History graph={graph} />);
    expect(tagOf(history, graph.growthStageLabel)).toContain('Gowun Batang');
  });

  it('책방 카운트 숫자가 세리프다 — 500을 부르다 400으로 떨어져 라벨보다 얇던 자리', () => {
    const stat = render(<StatItem label="팔로워" count={42} />);
    expect(tagOf(stat, '42')).toContain('Gowun Batang');
  });
});

/** 규칙 대상 소스 — 테스트·목은 제품 UI가 아니다(no-emoji 가드와 같은 경계). */
function sourceFiles(dir: string, out: string[] = []): string[] {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) sourceFiles(full, out);
    else if (/\.tsx?$/.test(entry.name) && !/\.test\.tsx?$|^dev-mock\.ts$|^test-fixtures\.ts$/.test(entry.name)) {
      out.push(full);
    }
  }
  return out;
}
