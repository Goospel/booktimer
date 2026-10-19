import { Stack } from 'expo-router';

// 앱 시작 때 위젯·Live Activity 레이아웃을 등록한다(보충 REQ-03 「위젯 레이아웃」).
import '../../widgets/ReadingWidget';
import '../../widgets/ReadingActivity';

export default function RootLayout() {
  return <Stack />;
}
