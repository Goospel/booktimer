// 명조 런타임 로드(시각 보충 §F-3 ① · §6.2). expo-font 네이티브 모듈은 지금 바이너리에 이미 있다(지문 c9ede8ad).
import { useFonts } from 'expo-font';

import { SERIF } from './tokens';

/** 불러오기가 끝났거나 실패했으면 연다 — 실패해도 앱이 뜬다(REQ-55). 명조 단계는 시스템 글꼴로 그려진다. */
export function fontsReady(loaded: boolean, error: Error | null): boolean {
  return loaded || error !== null;
}

export function useAppFonts(): boolean {
  const [loaded, error] = useFonts({ [SERIF]: require('../../assets/fonts/NotoSerifKR-Medium.ttf') });
  return fontsReady(loaded, error);
}
