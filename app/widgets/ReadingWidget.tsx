import { Text, VStack } from '@expo/ui/swift-ui';
import { createWidget } from 'expo-widgets';

// v1 REQ-34의 값. 날짜는 숫자(ms)로 넘긴다 — 위젯 함수 안에서 Date로 바꾼다.
export type ReadingWidgetProps = {
  dateLabel: string;
  todayMinutes: number;
  activeStartedAt?: number;
  bookTitle?: string;
};

// 'widget' 함수 밖의 것은 위젯 런타임에 없다(문서) — 상수·도우미도 함수 안에 둔다.
const ReadingWidget = (props: ReadingWidgetProps) => {
  'widget';
  const layoutLabel = '레이아웃 1'; // U-25: 이 글자만 바꿔 Metro 반영을 본다
  if (props.activeStartedAt) {
    return (
      <VStack>
        <Text>{props.bookTitle ?? '읽는 중'}</Text>
        <Text date={new Date(props.activeStartedAt)} dateStyle="timer" />
        <Text>{props.dateLabel}</Text>
        <Text>{layoutLabel}</Text>
      </VStack>
    );
  }
  return (
    <VStack>
      <Text>{`${props.dateLabel} · ${props.todayMinutes}분`}</Text>
      <Text>{layoutLabel}</Text>
    </VStack>
  );
};

export default createWidget('ReadingWidget', ReadingWidget, {
  dateLabel: '',
  todayMinutes: 0,
});
