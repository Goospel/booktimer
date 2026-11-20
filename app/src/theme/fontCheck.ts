// 내장 Plex 등록 확인(REQ-470 · D0 U-265 ①). RN New Arch는 모르는 글꼴 이름을 로그 없이 시스템 글꼴로 그리므로
// 등록된 PostScript 이름(iOS UIAppFonts)을 직접 읽어 개발 빌드 Metro 로그에 한 줄 찍는다. console 호출은 여기에만 둔다(REQ-174).
import { getLoadedFonts } from 'expo-font';

import { font } from './tokens';

export function fontReport(loaded: string[]): string {
  // 성공 문자열은 리터럴 그대로 — D0이 Metro 로그에서 이 글자를 찾는다(font가 넷이 아니면 fontCheck.test가 빨갛다)
  const missing = Object.values(font).filter((n) => !loaded.includes(n));
  return missing.length === 0 ? '[fonts] ok 4/4' : `[fonts] missing ${missing.length}/4: ${missing.join(', ')}`;
}

export function logFontReport(): void {
  if (__DEV__) console.log(fontReport(getLoadedFonts()));
}
