import { nextTick } from 'vue'

type VtDocument = Document & {
    startViewTransition?: (cb: () => Promise<void>) => { ready?: Promise<void>; finished: Promise<void> }
}

/**
 * 지원하면 View Transition(same-document)으로 감싸고, 없으면 즉시 적용한다.
 *
 * <p>콜백은 apply 뒤 <b>nextTick까지 기다린다</b> — 브라우저는 콜백이 끝난 순간의 DOM을 새 스냅숏으로 찍는데,
 * Vue는 상태를 바꾼 다음 틱에 DOM을 패치한다. 안 기다리면 옛 화면이 옛 화면으로 전환된다(에러 없이).
 * ready·finished 거부(중첩 전환·skip·문서 hidden)는 삼킨다 — 상태는 이미 apply가 바꿨다.
 * ready를 안 받으면 hidden 탭에서 누를 때마다 unhandled rejection이 콘솔에 찍힌다.
 */
export async function withViewTransition(doc: Document, apply: () => void): Promise<void> {
    const start = (doc as VtDocument).startViewTransition
    if (!start) {
        apply()
        return
    }
    const t = start.call(doc, async () => {
        apply()
        await nextTick()
    })
    t.ready?.catch(() => {})
    try {
        await t.finished
    } catch {
        /* 전환만 건너뛰어졌다 — 화면 상태는 이미 새 값이다 */
    }
}
