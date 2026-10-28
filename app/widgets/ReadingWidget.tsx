import { Text, VStack } from '@expo/ui/swift-ui';
import { containerBackground, font, foregroundStyle, frame, lineLimit, monospacedDigit } from '@expo/ui/swift-ui/modifiers';
import { createWidget, type WidgetEnvironment } from 'expo-widgets';

// v1 REQ-34 · 범위 보충 REQ-106의 값(app/src/lib/widgetPayload.ts가 만든다). 날짜는 숫자(ms)로 넘긴다.
export type ReadingWidgetProps = {
  dateLabel: string;
  todayMinutes: number;
  activeStartedAt?: number;
  bookTitle?: string;
  /** 측정 중일 때 무엇을 재는가 — 대기 값(오늘 분)은 늘 독서다(REQ-106) */
  mode: 'reading' | 'study';
  // 위젯 확장(2026-10-28) — 새 칸은 모두 선택이다: 옛 JS가 남긴 값으로도 지금 그림을 그린다(REQ-187).
  /** 측정 전 큰 글(REQ-184) — 없으면 todayMinutes로 그린다(옛 값) */
  idleTitle?: string;
  /** 측정 전 작은 글(목표 · 연속) — idleTitle이 있는데 없으면 안 읽은 날 문장(큰 글 자리) */
  idleNote?: string;
  /** 독서 측정 중 머리(목표 · 연속) — 없으면 「읽는 중」 · 「공부 중」 */
  runNote?: string;
  /** 측정 단추(REQ-180) — 앱을 여는 링크. 없으면 단추 없음(로그아웃 — REQ-186) */
  action?: { label: string; url: string };
};

// 시각 보충 §5.3 — 시스템 외형을 따른다. 배경만 paper · desk 리터럴(REQ-60 — widgetColors.test.ts가 토큰과 대조),
// 글자는 시스템 계층색 · 시스템 글꼴 · 텍스트 스타일(위젯 Dynamic Type). 틴트 · 투명에선 시스템이 배경을 지운다.
// 'widget' 함수 밖의 것은 위젯 런타임에 없다(문서) — 상수 · 도우미도 함수 안에 둔다(swift-ui 부품 · 수정자는 런타임 전역).
const ReadingWidget = (props: ReadingWidgetProps, environment: WidgetEnvironment) => {
  'widget';
  const box = [
    frame({ maxWidth: Infinity, maxHeight: Infinity, alignment: 'topLeading' }),
    containerBackground(environment.colorScheme === 'dark' ? '#1C1A17' : '#F5F0E6', 'widget'),
  ];
  const primary = foregroundStyle({ type: 'hierarchical', style: 'primary' });
  const caption = [font({ textStyle: 'caption' }), foregroundStyle({ type: 'hierarchical', style: 'secondary' })];
  const value = [font({ textStyle: 'title', weight: 'medium' }), monospacedDigit(), primary];
  if (props.activeStartedAt) {
    // 상한(시작 + 6시간 10분 = 22,200,000ms)에서 멈춘다 — Live Activity와 같은 방식(시각 보충 §5.3).
    const lower = new Date(props.activeStartedAt);
    const upper = new Date(props.activeStartedAt + 22200000);
    return (
      <VStack alignment="leading" spacing={4} modifiers={box}>
        <Text modifiers={caption}>{props.mode === 'study' ? '공부 중' : '읽는 중'}</Text>
        {props.bookTitle ? <Text modifiers={[font({ textStyle: 'headline' }), lineLimit(2), primary]}>{props.bookTitle}</Text> : null}
        <Text timerInterval={{ lower, upper }} countsDown={false} pauseTime={upper} modifiers={value} />
      </VStack>
    );
  }
  const h = Math.floor(props.todayMinutes / 60);
  const m = props.todayMinutes % 60;
  return (
    <VStack alignment="leading" spacing={4} modifiers={box}>
      <Text modifiers={caption}>{props.dateLabel}</Text>
      <Text modifiers={value}>{h === 0 ? m + '분' : m === 0 ? h + '시간' : h + '시간 ' + m + '분'}</Text>
      <Text modifiers={caption}>오늘 읽은 시간</Text>
    </VStack>
  );
};

export default createWidget('ReadingWidget', ReadingWidget, {
  dateLabel: '',
  todayMinutes: 0,
  mode: 'reading',
});
