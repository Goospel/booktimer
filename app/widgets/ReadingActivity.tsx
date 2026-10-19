import { Text, VStack } from '@expo/ui/swift-ui';
import { createLiveActivity } from 'expo-widgets';

// v1 §4.10. lower = 시작 시각, upper = 상한(실제는 시작 + 6시간 10분). 숫자(ms)로 넘긴다.
export type ReadingActivityProps = {
  bookTitle: string;
  lowerMs: number;
  upperMs: number;
};

// Live Activity는 app.config의 widgets[]에 넣지 않는다(문서 — 넣으면 빌드 실패).
const ReadingActivity = (props: ReadingActivityProps) => {
  'widget';
  const layoutLabel = '레이아웃 1'; // U-25: 이 글자만 바꿔 Metro 반영을 본다
  const lower = new Date(props.lowerMs);
  const upper = new Date(props.upperMs);
  const timer = (
    <Text timerInterval={{ lower, upper }} countsDown={false} pauseTime={upper} />
  );
  return {
    banner: (
      <VStack>
        <Text>{props.bookTitle}</Text>
        {timer}
        <Text>{layoutLabel}</Text>
      </VStack>
    ),
    compactLeading: <Text>책</Text>,
    compactTrailing: timer,
    minimal: <Text>책</Text>,
  };
};

export default createLiveActivity('ReadingActivity', ReadingActivity);
