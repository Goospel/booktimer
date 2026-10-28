import { readFileSync } from 'fs';
import { join } from 'path';

import { parseWidgetUrl, WIDGET_INTENT_TTL_MS, WIDGET_URL, widgetDecision, widgetIntent } from './widgetIntent';

// 위젯 측정 단추(위젯 확장 설계 REQ-180~183) — 위젯은 서버를 못 불러 앱을 열고, 앱이 그 화면의 측정 처리기를 누른다.

test('REQ-181 · 위젯 주소 셋을 동작 · 모드 · 탭 경로로 읽는다(booktimer · booktimer-dev 스킴 · 끝 / · 쿼리 · 조각 모두)', () => {
  expect(parseWidgetUrl('booktimer://widget/start')).toEqual({ action: 'start', mode: 'reading', route: '/' });
  expect(parseWidgetUrl('booktimer://widget/stop-reading')).toEqual({ action: 'stop', mode: 'reading', route: '/' });
  expect(parseWidgetUrl('booktimer://widget/stop-study')).toEqual({ action: 'stop', mode: 'study', route: '/study' });
  expect(parseWidgetUrl('booktimer-dev://widget/stop-study')).toEqual({ action: 'stop', mode: 'study', route: '/study' });
  expect(parseWidgetUrl('booktimer://widget/start/')).toEqual({ action: 'start', mode: 'reading', route: '/' });
  expect(parseWidgetUrl('booktimer://widget/start?x=1')).toEqual({ action: 'start', mode: 'reading', route: '/' });
  expect(parseWidgetUrl('booktimer://widget/stop-reading#a')).toEqual({ action: 'stop', mode: 'reading', route: '/' });
});

test('REQ-181 · 위젯 주소가 아니면 null — 앱 주소 · 빈 앱 주소 · 카카오 복귀 · 구글 역순 · 웹 주소 · widget/startx · widget/start/extra', () => {
  for (const url of [
    'booktimer:///book/1',
    'booktimer://',
    'kakaobfbfa22db12bfb12770dbac22e8215b0://oauth?code=x',
    'com.googleusercontent.apps.20889772436-j1vun6jjup350gg3l26igcc0421tanph:/oauthredirect?code=x',
    'https://booktimer.app/widget/start',
    'booktimer://widget/startx',
    'booktimer://widget/start/extra',
  ]) {
    expect(parseWidgetUrl(url)).toBeNull();
  }
});

describe('widgetIntent 한 칸', () => {
  beforeEach(() => widgetIntent.take('reading', 0) ?? widgetIntent.take('study', 0)); // 앞 테스트가 남긴 칸을 비운다

  test('REQ-182 · 요청은 그 모드가 한 번만 가져간다 — 두 번째 take는 null · 다른 모드 take는 null이고 요청이 남는다 · 뒤 put이 앞 요청을 덮는다', () => {
    widgetIntent.put({ action: 'stop', mode: 'study', at: 1000 });
    expect(widgetIntent.take('reading', 1000)).toBeNull(); // 다른 모드 화면은 못 가져간다
    expect(widgetIntent.take('study', 1000)).toEqual({ action: 'stop', mode: 'study', at: 1000 }); // 남아 있었다
    expect(widgetIntent.take('study', 1000)).toBeNull(); // 한 번만

    widgetIntent.put({ action: 'start', mode: 'reading', at: 1000 });
    widgetIntent.put({ action: 'stop', mode: 'reading', at: 2000 });
    expect(widgetIntent.take('reading', 2000)).toEqual({ action: 'stop', mode: 'reading', at: 2000 });
    expect(widgetIntent.take('reading', 2000)).toBeNull();
  });

  test('REQ-182 · 20초 경계 — at + 20,000ms는 쓰고 at + 20,001ms는 버린다(버린 뒤 남는 것 없음)', () => {
    expect(WIDGET_INTENT_TTL_MS).toBe(20_000);
    widgetIntent.put({ action: 'start', mode: 'reading', at: 1000 });
    expect(widgetIntent.take('reading', 1000 + 20_000)).toEqual({ action: 'start', mode: 'reading', at: 1000 });
    widgetIntent.put({ action: 'start', mode: 'reading', at: 1000 });
    expect(widgetIntent.take('reading', 1000 + 20_001)).toBeNull();
    expect(widgetIntent.take('reading', 1000)).toBeNull(); // 버린 요청이 다음 take에 살아나지 않는다
  });
});

test('REQ-183 · 판정 네 칸 — 시작+측정 중=none · 시작+측정 전=press · 종료+측정 중=press · 종료+측정 전=already-stopped', () => {
  expect(widgetDecision('start', true)).toBe('none');
  expect(widgetDecision('start', false)).toBe('press');
  expect(widgetDecision('stop', true)).toBe('press');
  expect(widgetDecision('stop', false)).toBe('already-stopped');
});

test('REQ-180 · 주소 상수 셋은 해석기와 왕복하고 __expo가 없다', () => {
  expect(parseWidgetUrl(WIDGET_URL.start)).toEqual({ action: 'start', mode: 'reading', route: '/' });
  expect(parseWidgetUrl(WIDGET_URL.stopReading)).toEqual({ action: 'stop', mode: 'reading', route: '/' });
  expect(parseWidgetUrl(WIDGET_URL.stopStudy)).toEqual({ action: 'stop', mode: 'study', route: '/study' });
  expect(Object.values(WIDGET_URL)).toHaveLength(3);
  for (const url of Object.values(WIDGET_URL)) expect(url).not.toMatch(/__expo/);
});

// 위젯 Button(onPress)은 위젯 확장의 격리된 동기 JS에서 돌아 서버를 못 부른다 — 앱이 뒤에 살아 있으면 손 시험이 통과하는 가짜 초록(설계 §3.2).
// 주소는 lib 상수에서만 온다 — 위젯 파일에 주소 리터럴이 없다(CLAUDE.md 개발 앱 링크 규칙 예외의 조건 · D8).
const BUTTON = /<Button\b/;
const URL_LITERAL = /['"`][a-z][a-z0-9+.-]*:\/\//i;
const offenders = (text: string) => [BUTTON, URL_LITERAL].filter((r) => r.test(text)).map(String);
const widgetFile = (name: string) => readFileSync(join(__dirname, '..', '..', 'widgets', name), 'utf8');

test("REQ-180 · 위젯 · LA 파일에 <Button과 :// 주소 리터럴이 없다(심은 픽스처 '<Button onPress={go}>' · \"'booktimer://x'\"는 같은 스캐너에서 잡힌다)", () => {
  const widget = widgetFile('ReadingWidget.tsx');
  const activity = widgetFile('ReadingActivity.tsx');
  expect(widget).toMatch(/'widget'/); // 실제 위젯 파일을 읽었다(빈 문자열이면 늘 초록)
  expect(offenders(widget)).toEqual([]);
  expect(offenders(activity)).toEqual([]);
  // 양성 대조 — 같은 스캐너가 심은 것을 잡는다
  expect(offenders('<Button onPress={go}>')).toEqual([String(BUTTON)]);
  expect(offenders("const u = 'booktimer://x';")).toEqual([String(URL_LITERAL)]);
  // 음성 대조 — 주석의 낱말 「Button」 · 따옴표 없는 설명 속 웹 주소는 걸리지 않는다
  expect(offenders('// Button은 쓰지 않는다 — https://docs.expo.dev 참고')).toEqual([]);
});
