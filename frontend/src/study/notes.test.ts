// 공부 필기의 순수 절반 — 목록 라벨과 자동저장 상태기계.
//
// 상태기계를 컴포넌트 밖에서 재는 이유는 「400 / 409 / 5xx가 서로 다른 갈래」라는 것이 이 기능의
// 핵심 규칙이라서다. 셋을 뭉개면 ① 400(길이 초과)에 무한 재시도가 붙어 키 입력마다 왕복하고
// ② 409(다른 곳에서 고쳐짐)에 자동저장이 계속 돌아 남의 판을 덮는다. 타이머·fetch가 얽힌
// 컴포넌트에서 재면 셋 중 어느 갈래가 죽었는지 안 보인다.
import { describe, test, expect } from 'vitest';

import { noteLabel, noteDateLabel, savedAtLabel, nextSaveState, type SaveState } from './notes';

describe('noteLabel — 목록에 뜨는 이름', () => {
    test('제목이 있으면 제목이다', () => {
        expect(noteLabel('3장 함수', '아무 본문')).toBe('3장 함수');
    });

    test('제목이 없으면 본문 첫 줄에서 마크다운 표식을 벗긴다', () => {
        expect(noteLabel(null, '# 미분계수\n- 접선의 기울기')).toBe('미분계수');
        expect(noteLabel('', '- 접선의 기울기')).toBe('접선의 기울기');
        expect(noteLabel(null, '> 인용한 문장')).toBe('인용한 문장');
        expect(noteLabel(null, '1. 첫째')).toBe('첫째');
        expect(noteLabel(null, '- [ ] 할 일')).toBe('할 일');
        expect(noteLabel(null, '**굵은 제목**')).toBe('굵은 제목');
        expect(noteLabel(null, '==형광==')).toBe('형광');
    });

    test('앞의 빈 줄은 건너뛴다 — 편집기가 빈 문단을 남길 수 있다', () => {
        expect(noteLabel(null, '\n\n  \n두 번째 줄이 첫 글이다')).toBe('두 번째 줄이 첫 글이다');
    });

    test('제목도 본문도 없으면 「제목 없음」이다', () => {
        expect(noteLabel(null, '   \n\n')).toBe('제목 없음');
    });

    test('40자를 넘으면 자르고 …을 붙인다', () => {
        expect(noteLabel(null, 'ㄱ'.repeat(41))).toBe(`${'ㄱ'.repeat(40)}…`);
        expect(noteLabel(null, 'ㄱ'.repeat(40))).toBe('ㄱ'.repeat(40));
    });
});

describe('시각 라벨', () => {
    // 로컬 시각으로 만들어 로컬 시각으로 읽는다 — 고정 UTC 문자열로 재면 이 테스트가 타임존을 잰다.
    const at = new Date(2026, 8, 10, 14, 32, 11).toISOString();

    test('savedAtLabel은 시:분이다', () => {
        expect(savedAtLabel(at)).toBe('14:32');
        expect(savedAtLabel(new Date(2026, 8, 10, 9, 5).toISOString())).toBe('09:05');
    });

    test('noteDateLabel은 날짜와 시각이다', () => {
        expect(noteDateLabel(at)).toBe('9월 10일 14:32');
    });
});

const IDLE: SaveState = { kind: 'idle' };

describe('nextSaveState — 자동저장 상태기계', () => {
    test('고치면 dirty, 디바운스가 끝나면 saving, 성공하면 saved(시각)', () => {
        const dirty = nextSaveState(IDLE, { type: 'edit' });
        expect(dirty).toEqual({ kind: 'dirty' });
        const saving = nextSaveState(dirty, { type: 'flush' });
        expect(saving).toEqual({ kind: 'saving' });
        expect(nextSaveState(saving, { type: 'ok', at: 'T' })).toEqual({ kind: 'saved', at: 'T' });
    });

    test('저장 중에 또 고치면 dirty로 되돌아 큐가 한 번 더 돈다', () => {
        expect(nextSaveState({ kind: 'saving' }, { type: 'edit' })).toEqual({ kind: 'dirty' });
    });

    test('저장 중에 고쳐 dirty가 됐으면 성공 응답이 그 사실을 지우지 않는다', () => {
        expect(nextSaveState({ kind: 'dirty' }, { type: 'ok', at: 'T' })).toEqual({ kind: 'dirty' });
    });

    test('409는 conflict — 자동저장이 멈춘다', () => {
        // 서버가 준 문구를 그대로 들고 있는다 — 「새로고침한 뒤 이어서 써 주세요」라는 다음 행동이 거기 있다.
        expect(nextSaveState({ kind: 'saving' }, { type: 'fail', status: 409, message: '다른 곳에서 고쳐진 필기예요' }))
            .toEqual({ kind: 'conflict', message: '다른 곳에서 고쳐진 필기예요' });
    });

    test('conflict에서는 무엇을 고쳐도 다시 두드리지 않는다', () => {
        const conflict: SaveState = { kind: 'conflict', message: '다른 곳에서' };
        expect(nextSaveState(conflict, { type: 'edit' })).toBe(conflict);
        expect(nextSaveState(conflict, { type: 'flush' })).toBe(conflict);
    });

    test('400은 invalid — 서버가 준 문구를 그대로 들고 있는다', () => {
        expect(nextSaveState({ kind: 'saving' }, { type: 'fail', status: 400, message: '쓴 내용은 8000자까지 쓸 수 있어요' }))
            .toEqual({ kind: 'invalid', message: '쓴 내용은 8000자까지 쓸 수 있어요' });
    });

    test('invalid는 재시도하지 않는다 — 사용자가 고치기 전엔 영원히 400이다', () => {
        const invalid: SaveState = { kind: 'invalid', message: '길어요' };
        expect(nextSaveState(invalid, { type: 'flush' })).toBe(invalid);
    });

    test('5xx·네트워크 오류는 error — 다음 변경에 재시도한다', () => {
        const failed = nextSaveState({ kind: 'saving' }, { type: 'fail', status: 500, message: '저장하지 못했어요' });
        expect(failed).toEqual({ kind: 'error', message: '저장하지 못했어요' });
        expect(nextSaveState(failed, { type: 'edit' })).toEqual({ kind: 'dirty' });
        // 「다시 저장」 버튼은 고치지 않고도 다시 보낸다 — 5xx는 같은 내용으로도 성공할 수 있다.
        expect(nextSaveState(failed, { type: 'flush' })).toEqual({ kind: 'saving' });
    });

    test('저장할 것이 없는 상태에서는 플러시가 아무 일도 하지 않는다', () => {
        expect(nextSaveState(IDLE, { type: 'flush' })).toBe(IDLE);
        const saved: SaveState = { kind: 'saved', at: 'T' };
        expect(nextSaveState(saved, { type: 'flush' })).toBe(saved);
    });
});
