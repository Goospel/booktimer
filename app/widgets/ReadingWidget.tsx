import { Circle, HStack, Link, Spacer, Text, VStack } from '@expo/ui/swift-ui';
import {
  accessibilityElement,
  accessibilityLabel,
  containerBackground,
  font,
  foregroundStyle,
  frame,
  lineLimit,
  minimumScaleFactor,
  monospacedDigit,
  padding,
  strokeBorder,
} from '@expo/ui/swift-ui/modifiers';
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
  /** 큰 위젯 이번 달 달력(REQ-189 · 190) — 없으면 큰 위젯도 작은 배치(옛 값 · 로그아웃) */
  month?: WidgetMonth;
};

/** 칸: day 0 = 빈칸 · mark 2 꽉 찬 점(목표를 채운 날) · 1 빈 원(읽었지만 목표 전) · 0 없음. rows = 일요일 시작 7칸 줄. */
export type WidgetMonth = {
  title: string;
  streak?: string;
  line: string;
  rows: { day: number; mark: 0 | 1 | 2; today: boolean }[][];
  a11y: string;
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
  // 목표 · 연속 한 줄 — 가장 긴 꼴 「목표 1시간 30분 · 123일째」도 0.92배(11pt 하한 — REQ-191)면 든다(피그마 F-W1 · F-W4 실측)
  const note = [...caption, lineLimit(1), minimumScaleFactor(0.92)];
  const value = [font({ textStyle: 'title', weight: 'medium' }), monospacedDigit(), primary];
  const headline = [font({ textStyle: 'headline', weight: 'medium' }), primary];
  // 측정 단추(위젯 확장 REQ-180) — 앱을 여는 링크다. 위젯 런타임은 서버를 못 불러 누르는 쪽 단추(onPress)를 쓰지 않는다(설계 §3.2).
  // 주소는 값(props.action)에서만 온다 — 이 파일엔 주소 글이 없다(widgetIntent.test 가드). 테두리 알약 · 아래 오른쪽(큰 위젯 끝 줄과 같은 꼴).
  const link = props.action ? (
    <Link destination={props.action.url}>
      <HStack modifiers={[padding({ horizontal: 14 }), frame({ height: 32 }), strokeBorder({ shape: 'capsule', style: { lineWidth: 1.5 } }), primary]}>
        <Text modifiers={[font({ textStyle: 'subheadline', weight: 'medium' }), primary]}>{props.action.label}</Text>
      </HStack>
    </Link>
  ) : null;
  const action = link ? (
    <HStack>
      <Spacer />
      {link}
    </HStack>
  ) : null;
  const month = props.month;
  if (environment.widgetFamily === 'systemLarge' && month) {
    // 큰 위젯 이번 달 달력(REQ-189 · 190 — 피그마 F-W2 · F-W3 안A 확정 배치). Grid는 위젯 렌더러에 없다 — 줄 HStack · 칸 VStack.
    // 표식은 모양으로 가른다(REQ-191): 꽉 찬 점 = 목표를 채운 날 · 빈 원 = 읽었지만 목표 전 · 없음 = 같은 7pt 빈 자리(줄 높이가 흔들리지 않게).
    // 빈 원은 빈 자리(Spacer)에 테두리를 얹는다 — Circle은 칠해져서 strokeBorder(= overlay)를 얹어도 꽉 찬 점이 된다(ExpoUI StrokeBorderModifier).
    const cell = frame({ maxWidth: Infinity });
    const dot = frame({ width: 7, height: 7 });
    const secondary = foregroundStyle({ type: 'hierarchical', style: 'secondary' });
    const upper = props.activeStartedAt ? new Date(props.activeStartedAt + 22200000) : null;
    return (
      <VStack alignment="leading" spacing={0} modifiers={box}>
        <HStack>
          <Text modifiers={[font({ textStyle: 'headline' }), primary]}>{month.title}</Text>
          <Spacer />
          {month.streak ? <Text modifiers={[font({ textStyle: 'subheadline', weight: 'medium' }), secondary]}>{month.streak}</Text> : null}
        </HStack>
        <Text modifiers={[font({ textStyle: 'footnote' }), secondary, lineLimit(1), padding({ top: 2 })]}>{month.line}</Text>
        <Spacer />
        <VStack spacing={4} modifiers={[accessibilityElement('ignore'), accessibilityLabel(month.a11y)]}>
          <HStack spacing={0} modifiers={[padding({ bottom: 2 })]}>
            {['일', '월', '화', '수', '목', '금', '토'].map((w) => (
              <Text key={w} modifiers={[...caption, cell]}>
                {w}
              </Text>
            ))}
          </HStack>
          {month.rows.map((row, i) => (
            <HStack key={'r' + i} spacing={0}>
              {row.map((c, j) =>
                c.day === 0 ? (
                  <Spacer key={'c' + j} modifiers={[cell]} />
                ) : (
                  <VStack key={'c' + j} spacing={3} modifiers={[cell]}>
                    <Text modifiers={[font({ textStyle: 'subheadline', weight: c.today ? 'bold' : 'regular' }), monospacedDigit(), primary]}>{c.day + ''}</Text>
                    {c.mark === 2 ? (
                      <Circle modifiers={[dot, primary]} />
                    ) : c.mark === 1 ? (
                      <Spacer modifiers={[dot, strokeBorder({ shape: 'circle', style: { lineWidth: 1.5 } }), primary]} />
                    ) : (
                      <Spacer modifiers={[dot]} />
                    )}
                  </VStack>
                ),
              )}
            </HStack>
          ))}
        </VStack>
        <Spacer />
        <HStack modifiers={[frame({ height: 32 })]}>
          {upper ? (
            <Text
              timerInterval={{ lower: new Date(props.activeStartedAt!), upper }}
              countsDown={false}
              pauseTime={upper}
              modifiers={[font({ textStyle: 'title2', weight: 'medium' }), monospacedDigit(), primary]}
            />
          ) : null}
          <Spacer />
          {link}
        </HStack>
      </VStack>
    );
  }
  if (props.activeStartedAt) {
    // 상한(시작 + 6시간 10분 = 22,200,000ms)에서 멈춘다 — Live Activity와 같은 방식(시각 보충 §5.3).
    const lower = new Date(props.activeStartedAt);
    const upper = new Date(props.activeStartedAt + 22200000);
    return (
      <VStack alignment="leading" spacing={4} modifiers={box}>
        <Text modifiers={note}>{props.runNote ?? (props.mode === 'study' ? '공부 중' : '읽는 중')}</Text>
        {props.bookTitle ? <Text modifiers={[...headline, lineLimit(1)]}>{props.bookTitle}</Text> : null}
        <Text timerInterval={{ lower, upper }} countsDown={false} pauseTime={upper} modifiers={value} />
        {action ? <Spacer /> : null}
        {action}
      </VStack>
    );
  }
  // 옛 값(idleTitle 없음 — 업데이트 사이 · 로그아웃)은 지금 그림 그대로(REQ-186 · 187)
  const h = Math.floor(props.todayMinutes / 60);
  const m = props.todayMinutes % 60;
  const title = props.idleTitle;
  // 큰 글: 작은 글이 없으면 안 읽은 날 문장(「하루 목표 30분」 — 0/목표 꼴 없음, 홈 REQ-176과 같다) · 있으면 오늘 시간(「1시간 45분」은 한 줄에 안 들어 0.7배까지 — 피그마 실측)
  return (
    <VStack alignment="leading" spacing={4} modifiers={box}>
      <Text modifiers={caption}>{props.dateLabel}</Text>
      {title === undefined ? (
        <Text modifiers={value}>{h === 0 ? m + '분' : m === 0 ? h + '시간' : h + '시간 ' + m + '분'}</Text>
      ) : props.idleNote === undefined ? (
        <Text modifiers={[...headline, lineLimit(2)]}>{title}</Text>
      ) : (
        <Text modifiers={[...value, lineLimit(1), minimumScaleFactor(0.7)]}>{title}</Text>
      )}
      {title === undefined ? <Text modifiers={caption}>오늘 읽은 시간</Text> : props.idleNote !== undefined ? <Text modifiers={note}>{props.idleNote}</Text> : null}
      {action ? <Spacer /> : null}
      {action}
    </VStack>
  );
};

export default createWidget('ReadingWidget', ReadingWidget, {
  dateLabel: '',
  todayMinutes: 0,
  mode: 'reading',
});
