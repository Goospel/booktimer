// withViewTransition — 측정 시작·종료만 View Transitions로 감싼다(설계 2026-09-15-study-focus-lamp §4-④).
//
// 계측기 메모
//  · 통과가 확정하는 것: API가 없으면 즉시 적용 · 있으면 콜백 안에서 적용하고 **Vue가 DOM을 패치할 때까지
//    (nextTick) 기다린 뒤** 콜백을 끝낸다 · finished 거부를 삼킨다.
//  · 실패가 배제하는 것: nextTick을 안 기다려 새 스냅숏에 옛 DOM이 찍히기(브라우저에서만 보이는 가짜 전환) ·
//    중첩 전환·skip의 거부가 unhandled rejection으로 새기.
import { describe, test, expect } from 'vitest';
import { nextTick, ref, watch } from 'vue';
import { withViewTransition } from '../src/dashboard/viewTransition';

describe('withViewTransition', () => {
    test('API가 없으면 즉시 적용하고 끝난다(Safari<18·Firefox<144·jsdom)', async () => {
        let applied = false;
        await withViewTransition({} as Document, () => { applied = true; });
        expect(applied).toBe(true);
    });

    test('콜백 안에서 적용하고, nextTick이 돈 뒤에 콜백이 끝난다', async () => {
        const log: string[] = [];
        const doc = {
            startViewTransition(cb: () => Promise<void>) {
                const done = cb().then(() => { log.push('callback-resolved'); });
                return { finished: done };
            },
        } as unknown as Document;

        // 진짜 반응 상태를 바꿔 Vue 스케줄러에 flush를 예약시킨다 — 그래야 nextTick이 「flush 뒤」로 밀린다.
        // (flush가 없으면 nextTick과 콜백 종료가 같은 깊이라 await를 빼도 순서가 같아 이 테스트가 공허했다 — 돌연변이 실측.)
        const state = ref(0);
        const stop = watch(state, () => { log.push('flush'); });
        const p = withViewTransition(doc, () => {
            log.push('apply');
            state.value += 1;
            void nextTick(() => { log.push('tick'); });
        });
        await p;
        stop();
        expect(log).toEqual(['apply', 'flush', 'tick', 'callback-resolved']);
    });

    test('finished가 거부돼도(중첩 전환·skip) 조용히 끝난다', async () => {
        let applied = false;
        const doc = {
            startViewTransition(cb: () => Promise<void>) {
                void cb();
                return { finished: Promise.reject(new DOMException('skipped', 'AbortError')) };
            },
        } as unknown as Document;
        await expect(withViewTransition(doc, () => { applied = true; })).resolves.toBeUndefined();
        expect(applied).toBe(true);
    });
});
