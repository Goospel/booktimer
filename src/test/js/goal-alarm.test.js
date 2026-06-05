/*
 * 목표 달성 알람 — 순수 전이 로직 테스트 (Node 내장 러너, 무의존성).
 *
 * 실행: node --test src/test/js/
 *
 * 왜 이것만 테스트하나: 알람의 위험은 "상태(goalMet)가 참인 동안 매 tick마다 울리는 것"이다.
 * 따라서 검증 가치가 있는 순수 로직은 **rising edge(미달→달성) 전이에서만 1회** 발화하는지다.
 * 사운드/알림/플래시 같은 부수효과(브라우저 API)는 여기서 다루지 않는다.
 */
const test = require('node:test');
const assert = require('node:assert');
const { createGoalAlarm } = require('../../main/resources/static/js/dashboard.js');

test('미달→달성 전이에서 정확히 1회만 울린다 (매 tick 아님)', () => {
    let count = 0;
    const alarm = createGoalAlarm(() => count++);
    alarm.prime(false);
    [false, false, true, true, true].forEach((g) => alarm.update(g));
    assert.strictEqual(count, 1);
});

test('로드 시 이미 달성 상태면 울리지 않는다 (이건 전이가 아님)', () => {
    let count = 0;
    const alarm = createGoalAlarm(() => count++);
    alarm.prime(true); // 페이지 로드 시점에 이미 잔여 0
    [true, true].forEach((g) => alarm.update(g));
    assert.strictEqual(count, 0);
});

test('계속 미달이면 울리지 않는다', () => {
    let count = 0;
    const alarm = createGoalAlarm(() => count++);
    alarm.prime(false);
    [false, false, false].forEach((g) => alarm.update(g));
    assert.strictEqual(count, 0);
});

test('달성 후 미달로 내려갔다 다시 달성하면 재무장되어 또 울린다', () => {
    let count = 0;
    const alarm = createGoalAlarm(() => count++);
    alarm.prime(false);
    [true, false, true].forEach((g) => alarm.update(g));
    assert.strictEqual(count, 2);
});
