// studyShortcut — 공부 측정 시작·종료 단축키 판정·표기(설계 2026-10-20-study-timer-start-stop-shortcut §6-1).
//  · 통과가 확정하는 것: 물리 키 자리(code)+Alt만으로 판정한다 · 맥·아이패드 표기는 Option+P다.
//  · 실패가 배제하는 것: key로 판정(한글 ㅔ·π·Process에서 죽음) · AltGr(Ctrl+Alt)·Shift·Meta 조합 오발.
import { describe, test, expect } from 'vitest';
import { isStudyShortcut, studyShortcutLabel } from '../src/dashboard/studyShortcut';

const ev = (o: Partial<KeyboardEvent>) =>
    ({ code: '', altKey: false, ctrlKey: false, metaKey: false, shiftKey: false, ...o }) as KeyboardEvent;

describe('studyShortcut', () => {
    test.each([
        ['Alt+P', { code: 'KeyP', altKey: true }],
        ['한글 입력 중(key ㅔ)', { code: 'KeyP', altKey: true, key: 'ㅔ' }],
        ['맥 Option(key π)', { code: 'KeyP', altKey: true, key: 'π' }],
        ['조합 중(229·isComposing)', { code: 'KeyP', altKey: true, key: 'Process', keyCode: 229, isComposing: true }],
    ])('REQ-02 · Alt+P만 단축키다 — 참: %s', (_, o) => {
        expect(isStudyShortcut(ev(o as Partial<KeyboardEvent>))).toBe(true);
    });

    test.each([
        ['Ctrl+Alt+P(AltGr)', { code: 'KeyP', altKey: true, ctrlKey: true }],
        ['Alt+Shift+P', { code: 'KeyP', altKey: true, shiftKey: true }],
        ['Meta+Alt+P', { code: 'KeyP', altKey: true, metaKey: true }],
        ['P 단독', { code: 'KeyP' }],
        ['Alt+O', { code: 'KeyO', altKey: true }],
    ])('REQ-02 · Alt+P만 단축키다 — 거짓: %s', (_, o) => {
        expect(isStudyShortcut(ev(o))).toBe(false);
    });

    test.each([
        ['MacIntel', 'Option+P'], ['iPad', 'Option+P'], ['iPhone', 'Option+P'],
        ['Win32', 'Alt+P'], ['Linux x86_64', 'Alt+P'], ['', 'Alt+P'],
    ])('REQ-07 · 맥·아이패드 표기는 Option+P, 그 밖은 Alt+P — %j', (platform, label) => {
        expect(studyShortcutLabel(platform)).toBe(label);
    });
});
