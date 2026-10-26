// Option+P 키 명령(REQ-105 · REQ-171). 화면 연결은 PR-P.
// 바이너리에 모듈이 없으면(jest — jest-expo는 모르는 모듈에 null을 준다) 조용히 빠진다.
import { requireOptionalNativeModule } from 'expo';

type KeyCommandsNative = {
  setEnabled(enabled: boolean): Promise<void>;
  addListener(event: 'onMeasureShortcut', listener: () => void): { remove(): void };
};

const native = requireOptionalNativeModule<KeyCommandsNative>('KeyCommands');

/** 버튼이 있는 화면이 보일 때 켜고 가려질 때 끈다 — 켤 때마다 지금 키 윈도에 다시 붙는다. */
export function setMeasureShortcutEnabled(enabled: boolean): void {
  void native?.setEnabled(enabled);
}

/** Option+P를 누를 때마다 부른다. 돌려준 함수로 떼어 낸다. */
export function onMeasureShortcut(listener: () => void): () => void {
  const sub = native?.addListener('onMeasureShortcut', listener);
  return () => sub?.remove();
}
