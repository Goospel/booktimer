// 문서가 숨겨진 채 전환을 부르면 브라우저가 전환을 중단하고 ready·finished를 거부한다 — 2026-09-17 로컬 실측,
// 확장 크롬 탭이 hidden일 때 「공부 측정 시작」·「측정 종료」마다 콘솔에 InvalidStateError가 찍혔다.
// 받는 쪽이 없으면 unhandled rejection이라 화면은 멀쩡해도 콘솔 에러 0 게이트를 오염시킨다.
import { afterEach, describe, expect, it } from 'vitest'

import { withViewTransition } from './viewTransition'

const aborted = () =>
    Promise.reject(new DOMException('Transition was aborted because of invalid state', 'InvalidStateError'))

function abortingDoc(): Document {
    return {
        startViewTransition(cb: () => Promise<void>) {
            void cb()
            return { ready: aborted(), finished: aborted() }
        },
    } as unknown as Document
}

describe('withViewTransition — 브라우저가 전환을 중단할 때', () => {
    const unhandled: unknown[] = []
    const onUnhandled = (reason: unknown) => unhandled.push(reason)

    afterEach(() => {
        process.off('unhandledRejection', onUnhandled)
        unhandled.length = 0
    })

    it('ready·finished 거부를 새지 않게 받고, 상태는 그대로 적용한다', async () => {
        process.on('unhandledRejection', onUnhandled)
        let applied = false

        await withViewTransition(abortingDoc(), () => { applied = true })
        await new Promise(r => setTimeout(r, 0)) // unhandledRejection은 마이크로태스크가 빈 뒤에 나온다

        expect(applied).toBe(true)
        expect(unhandled).toEqual([])
    })
})
