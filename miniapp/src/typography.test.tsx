import { TDSMobileProvider } from '@toss/tds-mobile';
import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import type { ReactNode } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';

import { History } from './screens/History';
import { ReadingNowCard } from './screens/Home';
import { StatItem } from './screens/Profile';
import { graph, userAgent } from './test-fixtures';
import { Avatar, CoverInitial, FilledButton, HANDWRITING, SectionTitle, Text } from './ui';

/**
 * 타이포그래피 위계 — <b>개구(Gaegu)로 갈아탄 뒤 크기·강조가 무너진 자리</b>를 못 박는다(#857 후속).
 *
 * <p>세 가지가 겹쳐 있었다. ① 웹의 크기 보정(`html{font-size:112.5%}`)은 본문이 `rem`이라 먹지만
 * 미니앱 글자는 전부 px라 <b>닿는 텍스트가 0개</b>였다. ② TDS `Text`는 굵기를
 * `font-weight: var(--tds-paragraph-font-weight)`로 그리는데 그 변수는 `fontWeight` prop을 넘겼을 때만
 * 채워져, 안 넘기면 선언이 무효가 되고 <b>굵기가 상속으로 떨어진다</b> — `div` 안이면 body의 700,
 * `button` 안이면 UA 기본 400. ③ (당시) 본문이 이미 700이라 굵기로는 더 강조할 수 없는데, 웹이 쓰는
 * 세리프(고운바탕) 축을 미니앱은 화면 제목 한 곳에서만 썼다.
 *
 * <p>그래서 위계를 <b>크기 · 색 · 세리프</b>가 맡는다. 아래 테스트가
 * 그 셋을 각각 지킨다.
 */

const css = readFileSync(new URL('./global.css', import.meta.url), 'utf8');
/** css 주석을 걷는다 — 주석이 선택자를 인용하면 소스 단언이 공허하게 통과한다(T-205의 거울상). */
const cssCode = css.replace(/\/\*[\s\S]*?\*\//g, '');

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
    // 한때 body가 700이었고 UA 기본 `button { font: 400 … }`이 그 보정을 **버튼 안에서만** 취소해,
    // 기록 화면 날짜 줄이 「펼칠 수 있는 날(button)」만 한 줄 걸러 얇았다. 지금은 body가 400이라
    // 우연히 값이 같지만, 이 규칙이 지키는 건 값이 아니라 **「굵기는 자리로 정해지지 않는다」**는
    // 원칙이다 — 지우면 700을 명시한 부모 안의 버튼에서 그대로 재발한다.
    expect(rules).toMatch(/\bbutton\s*\{[^}]*font-weight:\s*inherit/);
  });

  it('로드되지 않은 굵기를 선언하지 않는다 — 500·600·900은 조용히 다른 값으로 떨어진다', () => {
    // 이 앱이 **실제로 받아오는 굵기는 400(고운돋움)과 700(고운바탕·개구)뿐**이다. 그 사이 값을 부르면
    // 브라우저가 가진 face로 반올림하거나 합성해, 엔진마다 다른 결과가 나온다 — 「강조를 선언했는데
    // 화면은 그대로(혹은 폰마다 다름)」다. 자리마다 고치는 대신 소스를 훑어 다시 새지 않게 한다.
    // (개구가 본문이던 시절엔 500이 400으로 떨어져 **본문 700보다 얇아지는** 형태로 드러났다 —
    //  책방 카운트 숫자가 자기 라벨보다 얇던 자리. 전제는 바뀌었어도 금지 목록은 같다.)
    const offenders: string[] = [];
    for (const file of sourceFiles(fileURLToPath(new URL('.', import.meta.url)))) {
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
const SCALE = [11, 12, 13, 14, 15, 16, 17, 19, 20, 24, 26, 54];

describe('인라인 크기도 같은 계단 위에 온다', () => {
  it('계단 밖의 fontSize 리터럴이 없다', () => {
    const offenders: string[] = [];
    for (const file of sourceFiles(fileURLToPath(new URL('.', import.meta.url)))) {
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

describe('계단이 시안 눈금으로 돌아온다', () => {
  // 옛 값들은 **개구 보정**이었다 — 같은 px에서 손글씨가 본문용 한글 폰트보다 낮고 좁아 계단 전체가
  // 한 칸씩 올라가 있었다. 본문이 고운돋움으로 바뀌며 그 보정의 근거가 사라져 시안 눈금으로 되돌린다.
  it('본문·보조 토큰이 시안 값이다', () => {
    expect(css).toMatch(/--tds-t-st11-text-fontSize:\s*14px/); // 본문 (개구 보정 15 → 14)
    expect(css).toMatch(/--tds-t-st12-text-fontSize:\s*12px/); // 보조·타임스탬프
    expect(css).toMatch(/--tds-t-st13-text-fontSize:\s*11px/); // 배지·칩
  });

  it('제목은 본문과의 간격으로 위계를 만든다 — 히어로만 크게 벌린다', () => {
    expect(css).toMatch(/--tds-t-st10-text-fontSize:\s*17px/); // 섹션 제목 (시안 16.5 → 올림)
    expect(css).toMatch(/--tds-t-t3-text-fontSize:\s*26px/); //  화면 제목
    expect(css).toMatch(/--tds-t-t2-text-fontSize:\s*54px/); //  홈 히어로 (44 → 54)
  });

  it('줄높이를 쌍으로 움직인다 — 내릴 때도 같이 내려야 줄간이 안 뜬다', () => {
    expect(css).toMatch(/--tds-t-st11-text-lineHeight:\s*22px/);
    expect(css).toMatch(/--tds-t-t2-text-lineHeight:\s*59px/);
  });
});

/**
 * 서체 축 — 기능 글자는 고운돋움, 장식만 손글씨다. 이 방향이 뒤집히면(장식이 기본, 기능이 opt-in)
 * <b>지정 안 한 다음 화면이 다시 손글씨로 태어난다</b> — 그래서 기본값이 다수를 맡는다.
 */
describe('서체 축은 기능=돋움 · 장식=손글씨다', () => {
  it('본문 스택이 고운돋움으로 시작한다 — 맨 앞이 아니면 영영 안 잡힌다', () => {
    expect(rules).toMatch(/html\s+body\s*\{[^}]*font-family:\s*'Gowun Dodum'/);
  });

  // ⚠️ 부재 단언은 **주석 걷은 소스**에 건다 — 안 그러면 규칙을 주석 처리해도 통과한다(T-205).
  it('스택에서 손글씨를 뺀다 — 폴백으로 남기면 한 단어 안에서 서체가 갈린다', () => {
    const body = rules.match(/html\s+body\s*\{[^}]*\}/)?.[0] ?? '';

    // ⚠️ 부재 단언은 **찾은 게 있을 때만** 뜻이 있다 — 선택자가 바뀌어 매칭이 빗나가면 `''`이 되고,
    //    그러면 아래 `not.toContain`이 무조건 통과한다(T-205와 같은 부류의 공허함).
    expect(body).not.toBe('');
    expect(body).not.toContain('Gaegu');
    expect(css).toContain('family=Gaegu'); // @import는 남는다 — 장식이 쓴다
  });

  it('개구 보정을 걷는다 — 죽은 스위치를 남기면 미래의 rem 한 줄에 유령 배율이 깨어난다', () => {
    const body = rules.match(/html\s+body\s*\{[^}]*\}/)?.[0] ?? '';

    expect(body).not.toContain('112.5%');
    expect(body).toMatch(/font-weight:\s*400/); // 700 보정 → 400 명시
  });

  /**
   * 장식이 <b>상속에서 명시로</b> 넘어왔는지 — 이 전환의 가장 조용한 실패 자리다. 옛 앱은 body가
   * 손글씨라 표지 이니셜·placeholder가 <b>아무것도 지정하지 않고도</b> 손글씨였다(레포 전체에서 Gaegu를
   * 명시한 tsx가 0건이었다). 기본값만 뒤집고 opt-in을 빠뜨리면 장식이 통째로, 에러 없이 사라진다.
   */
  it('표지 이니셜이 손글씨로 남는다 — 상속이 끊긴 자리라 명시가 없으면 조용히 사라진다', () => {
    const markup = render(<CoverInitial title="데미안" />);

    expect(markup).toContain('Gaegu');
  });

  it('아바타 이니셜도 같다 — 표지와 한 몸이라 한쪽만 남으면 화면에 서체가 둘이 된다', () => {
    expect(render(<Avatar nickname="구스펠" />)).toContain('Gaegu');
  });

  it('굵기까지 장식 값이다 — 개구 400은 획이 흐물해 장식으로도 약하다(상수 주석이 그렇게 말한다)', () => {
    expect(HANDWRITING.fontWeight).toBe(700);
    expect(render(<CoverInitial title="데미안" />)).toContain('700');
  });

  /**
   * 나머지 장식 자리 — 캐러셀 placeholder · 피드 인용 · 여백 카드(인용/본문) · 작성 화면 입력칸.
   * 렌더로도 잡히지만 <b>어디서 손글씨를 부르는가</b>를 한 줄로 세는 편이 「빠뜨린 자리」에 답이 된다.
   *
   * <p>핸드오프가 이름을 대 가며 개구로 지정한 자리들이라, 여기서 빠지면 이 변경의 존재 이유 절반이
   * 서사로만 남는다(리뷰 지적 — 초판은 이 자리들이 돌연변이에서 <b>살아남았다</b>).
   *
   * <p>⚠️ 세는 단위가 <b>파일이 아니라 자리</b>다. 파일 집합으로 세면 한 파일 안의 여러 자리 중 하나가
   * 빠져도 그 파일은 여전히 목록에 남아 통과한다 — 실제로 `Story.tsx`의 본문 opt-in을 지운 돌연변이가
   * 집합 방식에서 살아남았다. 숫자가 바뀌면 테스트도 바뀌어야 하는 것이 의도다: 장식 자리를 늘리는 건
   * 「기본값(기능 서체)에서 예외를 하나 더 판다」는 뜻이라 눈에 띄어야 한다.
   */
  it('장식 자리 수가 그대로다 — 하나라도 빠지면 그 자리만 조용히 기능 서체가 된다', () => {
    const callers: Record<string, number> = {};
    for (const file of sourceFiles(fileURLToPath(new URL('.', import.meta.url)))) {
      const hits = readFileSync(file, 'utf8').match(/\.\.\.HANDWRITING/g);
      if (hits !== null) callers[file.split(/[\\/]/).pop()!] = hits.length;
    }

    // Story가 셋인 이유: 여백 카드의 인용·본문, 그리고 작성 화면 입력칸(`composerField`).
    expect(callers).toEqual({ 'Home.tsx': 1, 'HomeFeed.tsx': 1, 'Story.tsx': 3, 'ui.tsx': 2 });
  });

  /**
   * 여백 <b>작성 화면</b>이 특히 중요하다 — 코드가 스스로 「쓰는 동안 보이는 것이 곧 카드」라고
   * 선언한 미리보기 자리다. 한때 `fontFamily: 'inherit'`로 body를 따랐고 그때는 body가 손글씨라
   * 우연히 맞았는데, 축이 뒤집히며 그 우연이 사라졌다(리뷰 지적 — 초판이 빠뜨린 자리).
   */
  it('작성 화면 입력칸도 손글씨다 — 쓰는 글씨와 저장된 글씨가 다르면 미리보기가 아니다', () => {
    const src = readFileSync(new URL('./screens/Story.tsx', import.meta.url), 'utf8');
    const at = src.indexOf('const composerField');
    // ⚠️ 주석을 걷고 본다 — 이 자리의 경위를 설명하는 주석이 옛 선언을 그대로 인용하고 있어,
    //    안 걷으면 부재 단언이 **주석에 걸려** 영영 실패한다(T-205의 거울상).
    const field = src.slice(at, src.indexOf('}) as const;', at)).replace(/^\s*\/\/.*$/gm, '');

    expect(field).toContain('...HANDWRITING');
    expect(field).not.toContain("fontFamily: 'inherit'");
  });
});

/**
 * 채움 주 버튼 — 위계의 새 꼭대기(채움 > primary > weak). 화면당 하나뿐이어야 축이 서므로,
 * 개수 강제는 그 버튼을 쓰는 PR에서 소스 스캔으로 한다(여기서는 <b>장치가 성립하는지</b>만 본다).
 */
describe('채움 주 버튼', () => {
  it('마커를 버튼 요소 자신에 싣는다 — 래퍼로 밀리면 테스트는 그린인데 css가 안 닿는다', () => {
    const markup = render(<FilledButton>저장</FilledButton>);
    const tag = markup.slice(markup.lastIndexOf('<button', markup.indexOf('--btn-filled')),
                            markup.indexOf('>', markup.indexOf('--btn-filled')) + 1);

    // css 선택자가 `.tds-mobile-button[style*='--btn-filled']`이므로 **같은 요소**여야 한다.
    expect(tag).toContain('tds-mobile-button');
    expect(tag).toContain('--btn-filled');
  });

  it('css에 그 마커 규칙이 있다', () => {
    expect(rules).toMatch(/\.tds-mobile-button\[style\*='--btn-filled'\]/);
  });

  /**
   * ⚠️ <b>순서가 곧 결과다.</b> `FilledButton`도 TDS primary variant라 인라인에 primary hex를 그대로
   * 들고 있어 두 규칙에 <b>동시 매칭</b>된다. 명시도·`!important`가 동급이라 나중 규칙이 이기므로,
   * 마커 규칙이 primary 재색칠보다 앞서면 채움 버튼이 조용히 연한 세이지로 돌아간다.
   */
  it('마커 규칙이 primary 재색칠보다 뒤에 온다 — 앞서면 채움이 조용히 연한 세이지가 된다', () => {
    const primary = rules.indexOf("--button-background-color:#3182f6");
    const filled = rules.indexOf("[style*='--btn-filled']");

    expect(primary).toBeGreaterThan(-1);
    expect(filled).toBeGreaterThan(primary);
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

  it('연속 일수도 세리프다 — 같은 줄에 선 세 값이 서체가 갈리면 줄이 아니라 파편으로 읽힌다', () => {
    const history = render(<History graph={graph} />);
    expect(tagOf(history, `${graph.currentStreak}일`)).toContain('Gowun Batang');
  });

  it('책방 카운트 숫자가 세리프다 — 500을 부르다 400으로 떨어져 라벨보다 얇던 자리', () => {
    const stat = render(<StatItem label="팔로워" count={42} />);
    expect(tagOf(stat, '42')).toContain('Gowun Batang');
  });
});

/**
 * 입력칸 힌트는 입력값처럼 보이지 않는다 — 위 굵기·크기와 같은 병의 <b>색</b> 판이다.
 *
 * <p>TDS `TextField`는 힌트 색을 `--text-field-box-placeholder-color: var(--adaptiveGrey500)`로 그리는데,
 * 이 앱은 색 토큰을 종이톤으로 갈아끼우면서 <b>Grey500만 빠뜨렸다</b>(100·200·600·700·800만 정의). 빈
 * `var()`는 선언을 통째로 무효로 만들어, 힌트가 fallback도 없이 `input`의 색과 굵기를 그대로 물려받는다
 * — 잉크색 700, 곧 <b>실제 입력값과 완전히 같은 글자</b>다(목 모드 실측 `rgb(44,42,36)`/700).
 *
 * <p>토큰 하나가 앱의 모든 입력칸을 동시에 고친다. 화면마다 인라인 스타일을 바르면 다음 입력칸이
 * 또 잉크색으로 태어난다 — 구멍은 컴포넌트가 아니라 팔레트에 있다.
 */
describe('입력칸 힌트는 입력값처럼 보이지 않는다', () => {
  it('TDS가 참조하는 힌트 색 토큰을 정의한다 — 비어 있으면 선언이 무효가 되고 잉크색으로 떨어진다', () => {
    expect(rules).toMatch(/--adaptiveGrey500:\s*rgba\(/);
  });

  it('반투명이다 — 불투명 회색은 종이 결 위에 덧칠한 판처럼 떠, 힌트가 아니라 옅은 값으로 읽힌다', () => {
    const alpha = rules.match(/--adaptiveGrey500:\s*rgba\([^)]*?,\s*([0-9.]+)\s*\)/);

    expect(alpha).not.toBeNull();
    // 입력값(1.0)과 확실히 구별되면서, 무엇을 적는 자리인지 읽히기는 해야 한다.
    expect(Number(alpha![1])).toBeGreaterThan(0.3);
    expect(Number(alpha![1])).toBeLessThan(0.6);
  });
});

/**
 * 흐린 글자가 실제로 흐리다 — TDS `Text`의 `color`는 <b>CSS 색 문자열</b>이지 토큰 이름이 아니다.
 *
 * <p>`<Text color="grey600">`은 인라인에 `--tds-paragraph-color: grey600`을 박고, TDS는 그 변수를
 * `color: var(--tds-paragraph-color, var(--adaptiveGrey900))`로 소비한다. `grey600`은 무효 색이라
 * 선언이 통째로 버려지고(invalid at computed-value time) <b>색이 상속으로 떨어진다</b> — 곧 본문과
 * 완전히 같은 잉크다. 목 모드 실측(2026-08-23, 페인트 강제 후) `grey600 → rgb(33,37,41)`으로 prop 없는
 * `Text`와 한 값이었고, 그렇게 죽어 있던 호출부가 <b>87곳</b>이다(grey600 81 · blue500 3 · red500 2 ·
 * grey800 1). 흐림·강조 위계가 통째로 없었다는 뜻이다.
 *
 * <p>폴백도 못 받쳐 준다 — `--adaptiveGrey900`은 독서등(밤) 블록 <b>안에만</b> 정의돼 있어 낮 모드에선
 * 미정의다. 즉 prop을 주든 안 주든 결과가 「상속」으로 같다.
 *
 * <p>고치는 자리는 호출부 87곳이 아니라 <b>래퍼 하나</b>다 — 위 Grey500 팔레트 구멍과 같은 판단이다
 * (구멍은 컴포넌트가 아니라 한 곳에 있다). 토큰 이름을 값으로 옮기면 지금 호출부는 한 글자도 안 바뀌고,
 * 새로 쓰는 사람이 같은 함정에 다시 빠지지도 않는다.
 */
describe('흐린 글자가 실제로 흐리다', () => {
  it('토큰 이름을 CSS 색으로 옮긴다 — 맨 이름은 무효값이라 상속으로 떨어진다', () => {
    const tag = tagOf(render(<Text color="grey600">흐린 글자</Text>), '흐린 글자');

    expect(tag).toMatch(/--tds-paragraph-color:\s*var\(--adaptiveGrey600/);
  });

  it('나머지 토큰도 같은 표에서 온다 — 하나만 살리면 다음 색이 또 조용히 죽는다', () => {
    const cases = [
      ['blue500', /--tds-paragraph-color:\s*var\(--adaptiveBlue500/],
      ['red500', /--tds-paragraph-color:\s*var\(--adaptiveRed500/],
      ['grey800', /--tds-paragraph-color:\s*var\(--adaptiveGrey800/],
    ] as const;

    for (const [token, expected] of cases) {
      expect(tagOf(render(<Text color={token}>{token}</Text>), token)).toMatch(expected);
    }
  });

  it('토큰마다 fallback을 함께 준다 — 어느 쪽 모드에서도 정의하지 않는 토큰이 실제로 있다(Red500)', () => {
    // `--adaptiveRed500`은 이 앱 어디서도 정의하지 않는다(웹 --danger와 TDS red가 사실상 같아 재테마를
    // 건너뛴 자리). fallback이 없으면 `var()`가 미정의로 풀려 **다시 상속**이다 — 고친 자리가 원위치한다.
    const tag = tagOf(render(<Text color="red500">경고</Text>), '경고');

    expect(tag).toMatch(/--tds-paragraph-color:\s*var\(--adaptiveRed500,\s*#[0-9A-Fa-f]{6}\)/);
  });

  it('이미 CSS 색인 값은 그대로 흘려보낸다 — 표에 없는 값을 삼키면 호출부가 조용히 다른 색이 된다', () => {
    const tag = tagOf(render(<Text color="#4F6B4C">세이지</Text>), '세이지');

    expect(tag).toMatch(/--tds-paragraph-color:\s*#4F6B4C/);
  });

  it('화면은 TDS `Text`를 직접 부르지 않는다 — 직접 부르면 그 화면만 다시 토큰 이름이 무효가 된다', () => {
    // 래퍼는 「거쳐 가는 길이 하나」일 때만 가드다. `ui.tsx`만 원본을 알고, 나머지는 래퍼를 쓴다.
    const offenders: string[] = [];
    for (const file of sourceFiles(fileURLToPath(new URL('.', import.meta.url)))) {
      const name = file.split(/[\\/]/).pop()!;
      if (name === 'ui.tsx') continue;
      readFileSync(file, 'utf8')
        .split('\n')
        .forEach((line, i) => {
          if (/^import\s*\{[^}]*\bText\b[^}]*\}\s*from\s*'@toss\/tds-mobile'/.test(line)) {
            offenders.push(`${name}:${i + 1}  ${line.trim()}`);
          }
        });
    }

    expect(offenders).toEqual([]);
  });
});

/**
 * 본문 잉크는 우리 팔레트에서 온다 — 안 잡으면 TDS 리셋의 `#212529`가 낮 모드 전체를 칠한다.
 *
 * <p>TDS는 `body { color: #212529 }`를 <b>런타임에, 우리 css보다 뒤에</b> 주입한다. `html body`가
 * `color`를 안 잡고 있어 화면 전역이 브랜드 잉크(#2C2A24)가 아니라 그 푸른기 도는 차콜이었다
 * (실측 2026-08-23 `rgb(33,37,41)`). `html body`는 0-0-2라 순서와 무관하게 이긴다 — 바로 위에서
 * 배경·폰트를 같은 이유로 그렇게 잡아 둔 자리다.
 *
 * <p>덤이 하나 더 있다. 이 한 줄이 없으면 잉크가 <b>마운트 뒤에</b> 바뀌고, 아래 독서등 전환 규칙의
 * `transition: … color 0.45s`가 매 로드마다 발화해 본문이 검정에서 잉크로 0.45초 페이드한다
 * (그 검정이 hidden 탭에서 얼어붙어 「전역이 검정」으로 오진되게 만든 값이다 — T-207).
 * 정적 css에서 잉크를 확정하면 색이 애초에 안 바뀌어 전환이 발화하지 않는다.
 */
describe('본문 잉크는 우리 팔레트에서 온다', () => {
  it('html body가 잉크를 잡는다 — 비워 두면 TDS 리셋(#212529)이 낮 모드를 칠한다', () => {
    expect(rules).toMatch(/html body\s*\{[^}]*color:\s*var\(--adaptiveGrey800/);
  });
});

/**
 * 규칙 대상 소스 — 테스트·목은 제품 UI가 아니다(no-emoji 가드와 같은 경계).
 *
 * <p>⚠️ 디렉터리는 `fileURLToPath`로 얻는다. `new URL(...).pathname`은 Windows에서 `/C:/Users/…`를
 * 돌려주고, 앞의 `/` 때문에 node가 cwd 드라이브를 덧붙여 `C:\C:\Users\…`를 훑다 ENOENT로 죽는다.
 * <b>리눅스에선 pathname이 그대로 유효해 CI가 초록이라</b>, 이 줄이 되돌아가면 Windows에서만 조용히
 * 깨진다(2026-08-21 실측 — main이 이 머신에서 2건 red였다).
 */
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

/**
 * 목표 휠의 선택 행(시안 2e) — <b>emotion 해시가 아니라 ARIA 계약으로</b> 집는다.
 *
 * <p>설계 §7 U-3은 「선택 행만 집는 안정된 selector가 있는지 미확인」이라 적고, 없으면 전 행 세리프로
 * 후퇴하는 1차안을 뒀다. 목 모드 실측에서 항목이 `role="radio"` + `aria-checked`를 다는 것을 확인해
 * <b>후퇴가 필요 없어졌다</b>(항목 73개 중 `true`가 휠당 정확히 1개).
 *
 * <p>정적 렌더로는 휠 항목이 안 나오므로(TDS가 클라이언트에서 채운다) css 소스로 잠근다.
 */
describe('목표 휠 선택 행 (시안 2e)', () => {
  it('선택 행만 세리프로 집는다 — 해시 클래스에 기대면 TDS 업그레이드에 조용히 끊긴다', () => {
    expect(cssCode).toMatch(/\.goal-wheels[^{]*\[aria-checked='true'\][^{]*\{[^}]*Gowun Batang/);
  });

  it('선택 행 크기는 !important다 — TDS가 인라인 커스텀 프로퍼티로 크기를 박는다', () => {
    expect(cssCode).toMatch(/\[aria-checked='true'\][^{]*\{[^}]*font-size:\s*23px\s*!important/);
  });
});

/**
 * 채움 주 버튼은 <b>화면당 최대 1개</b>다 (설계 D5).
 *
 * <p>설계는 「소스 스캔으로 강제한다」고 적었지만 <b>그 가드는 만들어진 적이 없었다</b> — A PR에서
 * 「실사용이 생기는 B·C 몫」으로 넘겨진 뒤 아무도 만들지 않았다. 채움이 한 화면에 여럿이면
 * 「주 동작 하나」라는 축이 곧 무너지는데, 그걸 막는 것이 아무것도 없었다.
 *
 * <p>⚠️ <b>지표는 배경이다.</b> 초판은 글자색 `#F7F2E8`로 셌는데, 독립 리뷰가 흰 글자(`#FFFFFF`)
 * 채움 버튼을 하나 더 만들어 <b>전 스위트를 초록으로 통과</b>시켰다 — 「항상 통과 쪽으로 고장난」
 * 계측기였다. 채움을 채움이게 하는 것은 글자색이 아니라 <b>채워진 배경</b>이라, 그쪽으로 옮긴다.
 *
 * <p>세는 형태가 <b>둘</b>인 것도 요점이다: 목표는 `FilledButton`(TDS 래퍼 + 마커), 서재는 맨
 * `<button>`이다(TDS가 `--button-min-height: 56px`를 박아 38px 손잡이 줄에서 혼자 솟는다 — 실측).
 * 한 형태만 세면 다른 쪽이 그물 밖이라 불변식이 있는 척만 하게 된다.
 *
 * <p>`App.tsx`도 센다 — 탭바 가운데 원이 홈의 채움 자리다(설계 D5: 「홈은 탭바 원이 그 역할」).
 * 초판은 `screens/`만 훑어 그 자리가 계측 밖이었다.
 */
describe('채움 주 버튼 개수 (설계 D5)', () => {
  /** 주석을 걷는다 — 주석이 지표 문자열을 인용하면 거짓 실패한다(설계 D5가 요구한 절차). */
  const codeOnly = (src: string) => src.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*/g, '');

  const FILLED = /<FilledButton|background: 'var\(--adaptiveBlue700/g;

  const read = (rel: string) => codeOnly(readFileSync(new URL(rel, import.meta.url), 'utf8'));

  const surfaces = [
    ...readdirSync(new URL('./screens', import.meta.url))
      .filter((f) => f.endsWith('.tsx'))
      .map((f) => ({ file: f, count: (read(`./screens/${f}`).match(FILLED) ?? []).length })),
    { file: 'App.tsx', count: (read('./App.tsx').match(FILLED) ?? []).length },
  ];

  it('한 화면에 채움 버튼이 둘 이상 서지 않는다 — 여럿이면 「주 동작 하나」라는 축이 무너진다', () => {
    expect(surfaces.filter((s) => s.count > 1)).toEqual([]);
  });

  it('채움이 선 자리는 셋뿐이다 — 서재·목표 + 홈을 대신하는 탭바 원(설계 D5가 이름을 댄 그 자리들)', () => {
    expect(surfaces.filter((s) => s.count > 0).map((s) => s.file).sort()).toEqual([
      'App.tsx',
      'Goal.tsx',
      'Library.tsx',
    ]);
  });
});
