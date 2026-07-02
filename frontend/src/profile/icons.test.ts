import { describe, it, expect } from 'vitest'
import { readdirSync, readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'
import { ICONS } from './icons'

const HERE = dirname(fileURLToPath(import.meta.url))

// §6 아이콘 사전이 빠짐없이 들어 있는지 — 템플릿이 쓰는 모든 name.
const REQUIRED = [
    'books', 'user', 'follow', 'unfollow', 'block', 'report',
    'external', 'clock', 'home', 'back', 'chevron', 'close',
]

describe('ICONS 사전', () => {
    it('§6의 모든 아이콘 name이 존재한다', () => {
        for (const name of REQUIRED) {
            expect(ICONS, `'${name}' 누락`).toHaveProperty(name)
        }
    })

    it('모든 값이 비지 않은 SVG 마크업 문자열', () => {
        for (const [name, markup] of Object.entries(ICONS)) {
            expect(typeof markup, name).toBe('string')
            expect(markup.trim().length, name).toBeGreaterThan(0)
            // 라인 SVG 프리미티브만(path/circle/rect) — fill/stroke는 컴포넌트가 상속 주입
            expect(markup, name).toMatch(/<(path|circle|rect)\b/)
        }
    })
})

// 무결성 — profile·shared 폴더의 .vue가 <ShopIcon name="X">로 참조하는 모든 X가 ICONS에 존재.
// (ReportModal이 shared/로 이동하면서 스캔 대상에 shared/를 추가 — 원래 커버리지 보존.)
// 정적 name만 검사(동적 :name 바인딩은 스킵). 오타난 아이콘 name이 빈 아이콘으로
// 조용히 새는 것(시각 게이트가 놓치기 쉬운)을 컴파일 단계에서 잡는다.
describe('템플릿 참조 무결성', () => {
    it('.vue가 참조하는 모든 정적 ShopIcon name이 ICONS에 있다', () => {
        const dirs = [HERE, join(HERE, '..', 'shared')]
        const re = /<ShopIcon\b[^>]*?\sname="([a-zA-Z]+)"/g
        const missing: string[] = []
        for (const dir of dirs) {
            for (const f of readdirSync(dir).filter(f => f.endsWith('.vue'))) {
                const src = readFileSync(join(dir, f), 'utf8')
                let m: RegExpExecArray | null
                while ((m = re.exec(src)) !== null) {
                    const name = m[1]
                    if (!(name in ICONS)) missing.push(`${f}: ${name}`)
                }
            }
        }
        expect(missing, `ICONS에 없는 참조: ${missing.join(', ')}`).toEqual([])
    })
})
