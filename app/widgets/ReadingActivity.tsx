import { Image, Text, VStack } from '@expo/ui/swift-ui';
import { font, foregroundStyle, lineLimit, monospacedDigit, padding } from '@expo/ui/swift-ui/modifiers';
import { createLiveActivity, type LiveActivityEnvironment } from 'expo-widgets';

// v1 §4.10. lower = 시작 시각, upper = 상한(시작 + 6시간 10분 — app/src/lib/liveActivity.ts activityTimes). 숫자(ms)로 넘긴다.
export type ReadingActivityProps = {
  bookTitle: string;
  lowerMs: number;
  upperMs: number;
};

// 시각 보충 §5.3 — 잠금화면 배경을 칠하지 않는다(색 리터럴 0 — REQ-60) · 여백 14 · 글자는 시스템 계층색.
// 상한을 넘겨 stale이면 시간을 secondary로 낮추고 한 줄을 더한다(OS는 스스로 흐리게 하지 않았다 — v1 U-01).
// Live Activity는 app.config의 widgets[]에 넣지 않는다(문서 — 넣으면 빌드 실패).
const ReadingActivity = (props: ReadingActivityProps, environment: LiveActivityEnvironment) => {
  'widget';
  const lower = new Date(props.lowerMs);
  const upper = new Date(props.upperMs);
  const stale = environment.isStale === true;
  const primary = foregroundStyle({ type: 'hierarchical', style: 'primary' });
  const secondary = foregroundStyle({ type: 'hierarchical', style: 'secondary' });
  const caption = [font({ textStyle: 'caption' }), secondary];
  return {
    banner: (
      <VStack alignment="leading" spacing={4} modifiers={[padding({ all: 14 })]}>
        <Text modifiers={caption}>읽는 중</Text>
        {props.bookTitle ? <Text modifiers={[font({ textStyle: 'headline' }), lineLimit(1), primary]}>{props.bookTitle}</Text> : null}
        <Text
          timerInterval={{ lower, upper }}
          countsDown={false}
          pauseTime={upper}
          modifiers={[font({ textStyle: 'title', weight: 'medium' }), monospacedDigit(), stale ? secondary : primary]}
        />
        {stale ? <Text modifiers={caption}>6시간이 지나 자동으로 멈췄어요</Text> : null}
      </VStack>
    ),
    compactLeading: <Image systemName="book.closed" />,
    compactTrailing: <Text timerInterval={{ lower, upper }} countsDown={false} pauseTime={upper} modifiers={[monospacedDigit()]} />,
    minimal: <Image systemName="book.closed" />,
  };
};

export default createLiveActivity('ReadingActivity', ReadingActivity);
