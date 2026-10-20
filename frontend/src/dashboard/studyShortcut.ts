/**
 * 공부 측정 시작·종료 단축키 Alt+P(맥·아이패드 Option+P) — 설계 2026-10-20-study-timer-start-stop-shortcut.
 * 물리 키 자리(code)만 본다 — 한글 입력 중 key는 'ㅔ', 맥·아이패드 Option은 'π', 조합 중엔 'Process'다.
 * 조합 중(isComposing·229)을 거르지 않는다: 한국어는 마지막 음절이 거의 늘 조합 중이라 거르면 키가 대부분 안 먹는다.
 * Ctrl을 빼는 것은 윈도 AltGr(= Ctrl+Alt) 때문이다.
 */
export function isStudyShortcut(e: Pick<KeyboardEvent, 'code' | 'altKey' | 'ctrlKey' | 'metaKey' | 'shiftKey'>): boolean {
    return e.code === 'KeyP' && e.altKey && !e.ctrlKey && !e.metaKey && !e.shiftKey
}

/** 보이는 힌트 — 아이패드 사파리는 기본 데스크톱 모드라 platform이 'MacIntel'이다. */
export function studyShortcutLabel(platform: string): 'Alt+P' | 'Option+P' {
    return /Mac|iPhone|iPad|iPod/.test(platform) ? 'Option+P' : 'Alt+P'
}
