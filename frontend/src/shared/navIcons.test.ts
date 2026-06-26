import { describe, it, expect } from 'vitest'
import { readFileSync, readdirSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { NAV_ICONS } from './navIcons'

// 전 페이지 하단 네비(.link-row)가 실제 쓰는 의미 단위 아이콘 키. 하나라도 빠지면 그 라벨이
// 아이콘 없이(또는 빈 SVG로) 깨져 통일이 무너진다 — 명세로서 하드코딩한다.
const REQUIRED = [
    'home', 'back', 'books', 'history', 'search', 'user', 'personality',
    'block', 'report', 'follow', 'privacy', 'quote', 'feedback', 'users', 'lock',
]

describe('navIcons', () => {
    it('통일에 필요한 아이콘 키를 모두 보유한다', () => {
        const missing = REQUIRED.filter(k => !(k in NAV_ICONS))
        expect(missing).toEqual([])
    })

    it('모든 값이 비어있지 않은 SVG 프리미티브 문자열', () => {
        for (const [k, v] of Object.entries(NAV_ICONS)) {
            expect(v, k).toMatch(/<(path|circle|rect|line|polyline|polygon)\b/)
        }
    })

    // 참조 무결성 — 각 페이지가 <NavLinks :links="[{ icon: 'X' }]"> 로 넘기는 icon 키가 모두
    // 사전에 있어야 한다. 오타(icon: 'homee')는 Vue 템플릿 문자열이라 컴파일타임에 안 잡히고
    // 빈 아이콘으로 조용히 샌다(N-055 정신). NavIcon 자체는 :name 동적 바인딩이라 사용처
    // (NavLinks 호출부)의 icon 리터럴을 검사한다.
    it('모든 .vue의 NavLinks icon 리터럴이 사전 키의 부분집합', () => {
        const root = join(dirname(fileURLToPath(import.meta.url)), '..')
        const used = new Set<string>()
        const walk = (dir: string) => {
            for (const e of readdirSync(dir, { withFileTypes: true })) {
                const p = join(dir, e.name)
                if (e.isDirectory()) walk(p)
                else if (e.name.endsWith('.vue')) {
                    const src = readFileSync(p, 'utf8')
                    for (const m of src.matchAll(/\bicon:\s*'([^']+)'/g)) used.add(m[1])
                }
            }
        }
        walk(root)
        const unknown = [...used].filter(k => !(k in NAV_ICONS))
        expect(unknown).toEqual([])
    })
})
